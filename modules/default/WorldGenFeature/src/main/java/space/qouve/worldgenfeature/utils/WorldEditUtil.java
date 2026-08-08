package space.qouve.worldgenfeature.utils;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.mask.AbstractMask;
import com.sk89q.worldedit.function.mask.MaskIntersection;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.Block;

import space.qouve.core.CrayonUtil;
import space.qouve.worldgenfeature.models.WorldGenContext;
import space.qouve.worldgenfeature.WorldGenFeature;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

@SuppressWarnings("UnstableApiUsage")
public final class WorldEditUtil {

    private static final Map<File, Clipboard> CLIPBOARD_CACHE = new ConcurrentHashMap<>();

    // Performance: Vorab gecachte Blacklists pro Feature, um String-Listen-Suchen in Schleifen zu beschleunigen
    private static final Map<WorldGenFeature, Set<String>> BLACKLIST_CACHE = new ConcurrentHashMap<>();

    // Eigener, begrenzter Thread-Pool statt ForkJoinPool.commonPool() zu verwenden.
    // Blockierendes Datei-IO (Schematic laden) auf dem Common-Pool auszuführen kann
    // Paper/Bukkit-interne Async-Tasks und andere Plugins mit ausbremsen ("Pool-Starvation").
    private static final ExecutorService PLACEMENT_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "WorldGenFeature-Placement-" + counter.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            }
    );

    private WorldEditUtil() {
        // Utility class
    }

    // Drosselung: statt jede fertige Platzierung sofort im nächsten Tick auszuführen,
    // landet sie in dieser Queue. Ein Repeating-Task verarbeitet davon nur eine begrenzte
    // Anzahl pro Tick. Ohne das können, wenn viele Chunks gleichzeitig fertig laden
    // (z. B. schnelles Fliegen/Erkunden), mehrere volle Blacklist-Scans + WorldEdit-Copies
    // im selben Tick landen und einen spürbaren TPS-Einbruch verursachen.
    private static final java.util.Queue<Runnable> PLACEMENT_QUEUE = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static volatile boolean tickerStarted = false;
    private static final int MAX_PLACEMENTS_PER_TICK = 1;

    private static void ensureTickerStarted() {
        if (tickerStarted) return;
        synchronized (WorldEditUtil.class) {
            if (tickerStarted) return;
            Bukkit.getScheduler().runTaskTimer(CrayonUtil.getInstance(), () -> {
                for (int i = 0; i < MAX_PLACEMENTS_PER_TICK; i++) {
                    Runnable task = PLACEMENT_QUEUE.poll();
                    if (task == null) break;
                    task.run();
                }
            }, 1L, 1L);
            tickerStarted = true;
        }
    }

    /**
     * Erlaubt anderen Klassen (z. B. Behaviors wie PatchSpawnBehavior), eigene teure
     * Main-Thread-Arbeit über dieselbe gedrosselte Queue laufen zu lassen statt sie
     * synchron/unkontrolliert direkt auszuführen. Wichtig für alles, was selbst wieder
     * runBehaviors()/placeWithWorldEdit() aufruft (z. B. in einer Schleife über mehrere
     * Sub-Strukturen), da sich diese Aufrufe sonst pro Tick aufsummieren und die
     * Main-Thread-Drosselung umgehen können.
     */
    public static void scheduleDeferred(Runnable task) {
        ensureTickerStarted();
        PLACEMENT_QUEUE.offer(task);
    }

    public static void placeWithWorldEdit(WorldGenContext context) {
        File file = context.file();
        String debugName = context.debugName();
        WorldGenFeature feature = context.feature();

        // 1. Asynchrones Laden & Pre-Compute (auf eigenem Pool, nicht dem Common-Pool)
        CompletableFuture.supplyAsync(() -> {
            try {
                Clipboard clipboard = loadClipboard(file);
                AffineTransform transform = context.buildTransform();
                BlockVector3 to = context.toBlockVector3();
                BlockVector3 origin = clipboard.getOrigin();

                int[] bounds = computeTransformedChunkBounds(clipboard.getRegion(), transform, origin, to);
                return new PreparedData(clipboard, transform, to, origin, bounds);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, PLACEMENT_EXECUTOR).whenComplete((data, error) -> {
            if (error != null) {
                String msg = "Failed to read schematic or prepare placement for '" + debugName + "': " + error.getMessage();
                feature.debug(msg);
                Bukkit.getLogger().log(Level.SEVERE, "[WorldGenFeature] " + msg, error);
                return;
            }

            org.bukkit.World world = context.world();

            // 2. Chunks asynchron vorladen (nur bereits geladene/ladende Nachbarn, kein Force-Generate mehr)
            ensureChunksLoaded(world, data.bounds[0], data.bounds[1], data.bounds[2], data.bounds[3])
                    .whenComplete((v, loadError) -> {
                        if (loadError != null) {
                            String msg = "Failed to preload chunks for '" + debugName + "': " + loadError.getMessage();
                            feature.debug(msg);
                            Bukkit.getLogger().log(Level.SEVERE, "[WorldGenFeature] " + msg, loadError);
                            return;
                        }

                        // 3. Ausführung auf dem Main Thread - gedrosselt über die Placement-Queue,
                        // damit nicht mehrere volle Struktur-Placements im selben Tick landen
                        ensureTickerStarted();
                        PLACEMENT_QUEUE.offer(() -> {
                            try {
                                finishPlacement(context, data);
                            } catch (Exception e) {
                                String msg = "Failed to spawn structure '" + debugName + "': " + e.getMessage();
                                feature.debug(msg);
                                Bukkit.getLogger().log(Level.SEVERE, "[WorldGenFeature] " + msg, e);
                            }
                        });
                    });
        });
    }

    private record PreparedData(Clipboard clipboard, AffineTransform transform, BlockVector3 to, BlockVector3 origin, int[] bounds) {}

    private static Clipboard loadClipboard(File file) throws Exception {
        Clipboard cached = CLIPBOARD_CACHE.get(file);
        if (cached != null) return cached;

        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) throw new IllegalArgumentException("Unknown schematic format: " + file.getName());

        Clipboard clipboard;
        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis);
             ClipboardReader reader = format.getReader(bis)) {
            clipboard = reader.read();
        }
        CLIPBOARD_CACHE.put(file, clipboard);
        return clipboard;
    }

    private static int[] computeTransformedChunkBounds(Region region, AffineTransform transform,
                                                       BlockVector3 origin, BlockVector3 to) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        int[][] corners = {
                {min.x(), min.z()}, {min.x(), max.z()},
                {max.x(), min.z()}, {max.x(), max.z()}
        };

        int minChunkX = Integer.MAX_VALUE, maxChunkX = Integer.MIN_VALUE;
        int minChunkZ = Integer.MAX_VALUE, maxChunkZ = Integer.MIN_VALUE;

        for (int[] corner : corners) {
            BlockVector3 relative = BlockVector3.at(corner[0], min.y(), corner[1]).subtract(origin);
            BlockVector3 transformed = transform.apply(relative.toVector3()).toBlockPoint().add(to);

            int cx = transformed.x() >> 4;
            int cz = transformed.z() >> 4;

            minChunkX = Math.min(minChunkX, cx);
            maxChunkX = Math.max(maxChunkX, cx);
            minChunkZ = Math.min(minChunkZ, cz);
            maxChunkZ = Math.max(maxChunkZ, cz);
        }

        return new int[]{minChunkX, maxChunkX, minChunkZ, maxChunkZ};
    }

    private static CompletableFuture<Void> ensureChunksLoaded(org.bukkit.World world, int minChunkX, int maxChunkX,
                                                              int minChunkZ, int maxChunkZ) {
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();

        // generate=true ist hier nötig: finishPlacement() läuft auf dem Main-Thread und
        // MUSS in Zielchunks schreiben können, die die Struktur überlappt. Wenn wir diese
        // Chunks hier nicht async vorgenerieren, generiert Bukkit sie beim ersten
        // Block-Zugriff in finishPlacement() (world.getBlockAt/WorldEdit-Copy) stattdessen
        // SYNCHRON auf dem Main-Thread - das blockiert den Tick direkt und ist schlimmer
        // als die async-Variante hier. Puffer bewusst klein (1 statt vorher 3) gehalten,
        // damit die async-Vorgenerierung nicht unnötig viele Nachbarchunks (und damit
        // potenziell erneut populate()/weitere Struktur-Spawns) auslöst - 1 Chunk Rand
        // reicht für Rundungsfälle an der Bounding-Box-Grenze.
        for (int cx = minChunkX - 1; cx <= maxChunkX + 1; cx++) {
            for (int cz = minChunkZ - 1; cz <= maxChunkZ + 1; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    futures.add(world.getChunkAtAsync(cx, cz, true));
                }
            }
        }

        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private static void finishPlacement(WorldGenContext context, PreparedData data) {
        org.bukkit.World world = context.world();
        boolean overrideAir = context.overrideAir();
        WorldGenFeature feature = context.feature();
        String debugName = context.debugName();

        // Performance: Blacklist einmalig als HashSet cachen
        Set<String> blacklist = BLACKLIST_CACHE.computeIfAbsent(feature, f -> {
            List<String> list = f.getFeatureConfig().getStringList("blacklisted-blocks");
            return list.isEmpty() ? Set.of() : new HashSet<>(list);
        });

        if (!blacklist.isEmpty()) {
            for (BlockVector3 srcPt : data.clipboard.getRegion()) {
                BlockState schematicState = data.clipboard.getBlock(srcPt);
                if (schematicState == null) continue;

                BlockVector3 relativeTranslated = data.transform.apply(srcPt.subtract(data.origin).toVector3()).toBlockPoint();
                BlockVector3 targetPt = relativeTranslated.add(data.to);

                Block targetBlock = world.getBlockAt(targetPt.x(), targetPt.y(), targetPt.z());
                String targetMatKey = targetBlock.getType().getKey().toString();

                if (blacklist.contains(targetMatKey)) {
                    boolean isSchematicAir = schematicState.getBlockType().equals(BlockTypes.AIR)
                            || schematicState.getBlockType().equals(BlockTypes.CAVE_AIR)
                            || schematicState.getBlockType().equals(BlockTypes.VOID_AIR);

                    if (isSchematicAir) {
                        if (overrideAir) {
                            feature.debug("Placement cancelled: Blacklisted block '" + targetMatKey + "' would be overwritten by AIR at " + targetPt);
                            return;
                        }
                    } else {
                        feature.debug("Placement cancelled: Blacklisted block '" + targetMatKey + "' would be overwritten at " + targetPt);
                        return;
                    }
                }
            }
        }

        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);

        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(weWorld)
                .maxBlocks(-1)
                .build()) {

            ClipboardHolder holder = new ClipboardHolder(data.clipboard);
            if (!data.transform.isIdentity()) holder.setTransform(data.transform);

            ForwardExtentCopy copy = new ForwardExtentCopy(
                    holder.getClipboard(),
                    holder.getClipboard().getRegion(),
                    holder.getClipboard().getOrigin(),
                    editSession,
                    data.to
            );

            copy.setTransform(holder.getTransform());
            copy.setCopyingEntities(false);
            copy.setCopyingBiomes(false);

            MaskIntersection mask = new MaskIntersection();

            if (!overrideAir) {
                mask.add(new AbstractMask() {
                    @Override
                    public boolean test(BlockVector3 vector) {
                        BlockState state = holder.getClipboard().getBlock(vector);
                        if (state == null) return false;
                        var type = state.getBlockType();
                        return !type.equals(BlockTypes.AIR)
                                && !type.equals(BlockTypes.CAVE_AIR)
                                && !type.equals(BlockTypes.VOID_AIR);
                    }
                });
            }

            mask.add(new AbstractMask() {
                @Override
                public boolean test(BlockVector3 vector) {
                    BlockState state = holder.getClipboard().getBlock(vector);
                    return state != null && !state.getBlockType().equals(BlockTypes.BARRIER);
                }
            });

            copy.setSourceMask(mask);
            Operations.complete(copy);

            WorldGenUtil.saveToPDC(feature, world, data.to, debugName);

        } catch (Exception e) {
            String msg = "Failed to paste structure '" + debugName + "': " + e.getMessage();
            feature.debug(msg);
            Bukkit.getLogger().log(Level.SEVERE, "[WorldGenFeature] " + msg, e);
        }
    }
}