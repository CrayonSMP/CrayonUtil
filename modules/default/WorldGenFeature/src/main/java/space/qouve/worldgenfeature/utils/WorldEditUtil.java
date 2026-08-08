package space.qouve.worldgenfeature.utils;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;

import space.qouve.core.CrayonUtil;
import space.qouve.worldgenfeature.models.WorldGenContext;
import space.qouve.worldgenfeature.WorldGenFeature;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("UnstableApiUsage")
public final class WorldEditUtil {

    private static final Map<File, Clipboard> CLIPBOARD_CACHE = new ConcurrentHashMap<>();

    private WorldEditUtil() {
        // Utility class
    }

    /**
     * Lädt das Clipboard (inklusive Caching) und startet den asynchronen Placement-Prozess
     * über den WorldGenContext.
     */
    public static void placeWithWorldEdit(WorldGenContext context) {
        File file = context.file();
        String debugName = context.debugName();
        WorldGenFeature feature = context.feature();

        CompletableFuture.supplyAsync(() -> {
            try {
                return loadClipboard(file);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((clipboard, error) -> {
            if (error != null) {
                feature.debug("Failed to read schematic '" + debugName + "': " + error.getMessage());
                return;
            }

            // Mit geladenem Clipboard anreichern
            WorldGenContext filledContext = context.withClipboard(clipboard);

            // Zurück auf den Main-Thread wechseln für Weltoperationen und Chunk-Prüfungen
            Bukkit.getScheduler().runTask(CrayonUtil.getInstance(), () -> {
                try {
                    AffineTransform transform = filledContext.buildTransform();
                    BlockVector3 to = filledContext.toBlockVector3();
                    BlockVector3 origin = clipboard.getOrigin();

                    int[] bounds = computeTransformedChunkBounds(clipboard.getRegion(), transform, origin, to);

                    ensureChunksLoaded(filledContext.world(), bounds[0], bounds[1], bounds[2], bounds[3])
                            .whenComplete((v, loadError) -> Bukkit.getScheduler().runTask(CrayonUtil.getInstance(), () -> {
                                if (loadError != null) {
                                    feature.debug("Failed to preload chunks for '" + debugName + "': " + loadError.getMessage());
                                    return;
                                }
                                try {
                                    finishPlacement(filledContext);
                                } catch (Exception e) {
                                    feature.debug("Failed to spawn structure '" + debugName + "': " + e.getMessage());
                                }
                            }));
                } catch (Exception e) {
                    feature.debug("Failed to prepare structure placement '" + debugName + "': " + e.getMessage());
                }
            });
        });
    }

    private static Clipboard loadClipboard(File file) throws Exception {
        Clipboard cached = CLIPBOARD_CACHE.get(file);
        if (cached != null) return cached;

        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) throw new IllegalArgumentException("Unknown schematic format: " + file.getName());

        Clipboard clipboard;
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
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

    private static CompletableFuture<Void> ensureChunksLoaded(World world, int minChunkX, int maxChunkX,
                                                              int minChunkZ, int maxChunkZ) {
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();

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

    private static void finishPlacement(WorldGenContext context) {
        World world = context.world();
        Clipboard clipboard = context.clipboard();
        AffineTransform transform = context.buildTransform();
        BlockVector3 to = context.toBlockVector3();
        boolean overrideAir = context.overrideAir();
        WorldGenFeature feature = context.feature();
        String debugName = context.debugName();

        List<String> blacklist = feature.getFeatureConfig().getStringList("blacklisted-blocks");
        if (blacklist != null && !blacklist.isEmpty()) {
            BlockVector3 origin = clipboard.getOrigin();

            for (BlockVector3 srcPt : clipboard.getRegion()) {
                com.sk89q.worldedit.world.block.BlockState schematicState = clipboard.getBlock(srcPt);
                if (schematicState == null) continue;

                BlockVector3 relativeTranslated = transform.apply(srcPt.subtract(origin).toVector3()).toBlockPoint();
                BlockVector3 targetPt = relativeTranslated.add(to);

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
                .build()) {

            ClipboardHolder holder = new ClipboardHolder(clipboard);
            if (!transform.isIdentity()) holder.setTransform(transform);

            com.sk89q.worldedit.function.operation.ForwardExtentCopy copy =
                    new com.sk89q.worldedit.function.operation.ForwardExtentCopy(
                            holder.getClipboard(),
                            holder.getClipboard().getRegion(),
                            holder.getClipboard().getOrigin(),
                            editSession,
                            to
                    );

            copy.setTransform(holder.getTransform());
            copy.setCopyingEntities(false);
            copy.setCopyingBiomes(false);

            com.sk89q.worldedit.function.mask.MaskIntersection mask =
                    new com.sk89q.worldedit.function.mask.MaskIntersection();

            if (!overrideAir) {
                mask.add(new com.sk89q.worldedit.function.mask.AbstractMask() {
                    @Override
                    public boolean test(BlockVector3 vector) {
                        com.sk89q.worldedit.world.block.BlockState state = holder.getClipboard().getBlock(vector);
                        if (state == null) return false;
                        return !state.getBlockType().equals(BlockTypes.AIR)
                                && !state.getBlockType().equals(BlockTypes.CAVE_AIR)
                                && !state.getBlockType().equals(BlockTypes.VOID_AIR);
                    }
                });
            }

            mask.add(new com.sk89q.worldedit.function.mask.AbstractMask() {
                @Override
                public boolean test(BlockVector3 vector) {
                    com.sk89q.worldedit.world.block.BlockState state = holder.getClipboard().getBlock(vector);
                    return state != null && !state.getBlockType().equals(BlockTypes.BARRIER);
                }
            });

            copy.setSourceMask(mask);
            Operations.complete(copy);
        } catch (Exception e) {
            feature.debug("Failed to paste structure '" + debugName + "': " + e.getMessage());
        }
    }
}