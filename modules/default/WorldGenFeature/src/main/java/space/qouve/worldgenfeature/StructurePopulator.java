package space.qouve.worldgenfeature;

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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.generator.BlockPopulator;

import space.qouve.core.CrayonUtil;
import space.qouve.worldgenfeature.models.MultiPatchConfig;
import space.qouve.worldgenfeature.models.StructureConfig;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class StructurePopulator extends BlockPopulator {

    /**
     * Cache für bereits von der Platte gelesene Schematics.
     * Vermeidet, dass bei jedem einzelnen Placement erneut Disk-I/O anfällt.
     * Ein Clipboard wird hier nur lesend verwendet (Transform wird über den
     * ClipboardHolder / ForwardExtentCopy angewendet, nicht auf dem Clipboard
     * selbst), daher ist das gleichzeitige Nutzen einer Instanz für mehrere
     * Placements unproblematisch.
     */
    private static final Map<File, Clipboard> CLIPBOARD_CACHE = new ConcurrentHashMap<>();

    @Override
    public void populate(org.bukkit.generator.WorldInfo worldInfo, Random random, int chunkX, int chunkZ, org.bukkit.generator.LimitedRegion limitedRegion) {
        WorldGenFeature feature = WorldGenFeature.getFeature();
        if (feature == null) return;

        World world = Bukkit.getWorld(worldInfo.getUID());
        if (world == null) return;

        long chunkKey = Chunk.getChunkKey(chunkX, chunkZ);
        if (feature.isChunkOccupied(world, chunkKey)) return;

        int chunkStartX = chunkX << 4;
        int chunkStartZ = chunkZ << 4;

        Biome bukkitBiome = world.getBiome(chunkStartX, 64, chunkStartZ);
        String biomeKey   = bukkitBiome.getKey().toString().toLowerCase();

        String worldName = world.getName();
        String worldKey  = world.getKey().toString();

// =====================================================================
// 1) Normale Strukturen
// =====================================================================
        List<String> names = new ArrayList<>(feature.getStructureNames());
        Collections.shuffle(names, random);

        for (String name : names) {
            StructureConfig config = feature.getConfig(name);
            if (config == null || !config.isEnabled()) continue;

            if (!config.getAllowedWorlds().isEmpty()) {
                if (!config.getAllowedWorlds().contains(worldName)
                        && !config.getAllowedWorlds().contains(worldKey)) continue;
            }

            if (!config.isBiomeAllowed(biomeKey)) continue;

            double chance = config.getChance(biomeKey);
            if (random.nextDouble() > chance) continue;

            int attempts = config.getAttempts();
            StructureConfig.PatchConfig patch = config.getPatchConfig(biomeKey);

            for (int i = 0; i < attempts; i++) {
                int centerX = chunkStartX + random.nextInt(16);
                int centerZ = chunkStartZ + random.nextInt(16);

                int minY = config.getMinY(biomeKey);
                int maxY = config.getMaxY(biomeKey);
                if (minY >= maxY) continue;

                List<Location> spawnLocations = new ArrayList<>();

                if (patch != null && patch.enabled()) {
                    // --- PATCH LOGIK ---
                    int targetCount = random.nextInt(patch.maxSpawns() - patch.minSpawns() + 1) + patch.minSpawns();

                    for (int j = 0; j < 80 && spawnLocations.size() < targetCount; j++) {
                        double distance = patch.minDist() + random.nextDouble() * (patch.maxDist() - patch.minDist());
                        double angle    = random.nextDouble() * 2 * Math.PI;
                        int blockX = centerX + (int) (distance * Math.cos(angle));
                        int blockZ = centerZ + (int) (distance * Math.sin(angle));

                        // Holt das ABSOLUT höchste Y der Welt
                        int blockY = getHighestValidY(world, limitedRegion, blockX, blockZ);

                        // Validierung gegen deine min/max Config-Grenzen
                        if (blockY < minY || blockY > maxY) continue;

                        try {
                            Material groundType;
                            try {
                                groundType = limitedRegion.getType(blockX, blockY, blockZ);
                            } catch (Exception e) {
                                groundType = world.getBlockAt(blockX, blockY, blockZ).getType();
                            }

                            String groundMat = groundType.getKey().toString();
                            List<String> allowedGround = config.getAllowedGround(biomeKey);
                            if (allowedGround != null && !allowedGround.isEmpty() && !allowedGround.contains(groundMat)) {
                                continue;
                            }

                            spawnLocations.add(new Location(world, blockX, blockY + config.getOffsetY(biomeKey) + 1, blockZ));
                        } catch (Exception ignored) {}
                    }

                    if (spawnLocations.size() < patch.minSpawns()) continue;

                } else {
                    // --- NORMALE LOGIK ---
                    int blockY = getHighestValidY(world, limitedRegion, centerX, centerZ);

                    // Validierung gegen deine min/max Config-Grenzen
                    if (blockY < minY || blockY > maxY) continue;

                    try {
                        Material groundType = limitedRegion.getType(centerX, blockY, centerZ);
                        String groundMat = groundType.getKey().toString();
                        List<String> allowedGround = config.getAllowedGround(biomeKey);
                        if (allowedGround != null && !allowedGround.contains(groundMat)) continue;
                        spawnLocations.add(new Location(world, centerX, blockY + config.getOffsetY(biomeKey) + 1, centerZ));
                    } catch (Exception ignored) {
                        continue;
                    }
                }

                if (config.isPreventOverlap()) feature.markChunkOccupied(world, chunkKey);

                File file = feature.getStructureFile(name);
                if (file == null || !file.exists()) continue;

                boolean overrideAir = config.isOverrideAir(biomeKey);

                for (Location spawnLoc : spawnLocations) {
                    feature.recordPlacement(name);
                    StructureRotation rotation = config.resolveRotation(biomeKey, random);
                    Mirror mirror              = config.resolveMirror(biomeKey, random);

                    // placeWithWorldEdit kümmert sich jetzt selbst darum, vom
                    // aktuellen Thread (Chunk-Gen-Thread) auf den Main-Thread
                    // zu wechseln - siehe Methode weiter unten.
                    placeWithWorldEdit(world, spawnLoc, file, rotation, mirror, overrideAir, name, feature);
                }
                return;
            }
        }

// =====================================================================
// 2) Multi-Patches
// =====================================================================
        List<String> patchNames = new ArrayList<>(feature.getMultiPatchNames());
        Collections.shuffle(patchNames, random);

        for (String patchName : patchNames) {
            if (feature.isChunkOccupied(world, chunkKey)) return;

            MultiPatchConfig patchConfig = feature.getMultiPatchConfig(patchName);
            if (patchConfig == null || !patchConfig.isEnabled()) continue;

            if (!patchConfig.getAllowedWorlds().isEmpty()) {
                if (!patchConfig.getAllowedWorlds().contains(worldName)
                        && !patchConfig.getAllowedWorlds().contains(worldKey)) continue;
            }

            if (!patchConfig.isBiomeAllowed(biomeKey)) continue;

            double chance = patchConfig.getChance(biomeKey);
            if (random.nextDouble() > chance) continue;

            List<MultiPatchConfig.SchematicEntry> schematics = patchConfig.getSchematics();
            if (schematics.isEmpty()) continue;

            int attempts = patchConfig.getAttempts();
            StructureConfig.PatchConfig patch = patchConfig.getPatchConfig(biomeKey);

            for (int i = 0; i < attempts; i++) {
                int centerX = chunkStartX + random.nextInt(16);
                int centerZ = chunkStartZ + random.nextInt(16);

                int minY = patchConfig.getMinY(biomeKey);
                int maxY = patchConfig.getMaxY(biomeKey);
                if (minY >= maxY) continue;

                List<SpawnRequest> spawnRequests = new ArrayList<>();

                if (patch != null && patch.enabled()) {
                    // --- PATCH LOGIK ---
                    int targetCount = random.nextInt(patch.maxSpawns() - patch.minSpawns() + 1) + patch.minSpawns();

                    for (int j = 0; j < 80 && spawnRequests.size() < targetCount; j++) {
                        double distance = patch.minDist() + random.nextDouble() * (patch.maxDist() - patch.minDist());
                        double angle    = random.nextDouble() * 2 * Math.PI;
                        int blockX = centerX + (int) (distance * Math.cos(angle));
                        int blockZ = centerZ + (int) (distance * Math.sin(angle));

                        // Holt das ABSOLUT höchste Y der Welt
                        int blockY = getHighestValidY(world, limitedRegion, blockX, blockZ);

                        // Validierung gegen deine min/max Config-Grenzen
                        if (blockY < minY || blockY > maxY) continue;

                        try {
                            Material groundType = limitedRegion.getType(blockX, blockY, blockZ);
                            String groundMat = groundType.getKey().toString();

                            MultiPatchConfig.SchematicEntry entry =
                                    MultiPatchConfig.SchematicEntry.pickWeighted(schematics, random);

                            List<String> allowedGround = entry.resolveAllowedGround(patchConfig.getAllowedGround(biomeKey));
                            if (allowedGround != null && !allowedGround.contains(groundMat)) continue;

                            int offsetY = entry.resolveOffsetY(patchConfig.getOffsetY(biomeKey));
                            Location loc = new Location(world, blockX, blockY + offsetY + 1, blockZ);
                            spawnRequests.add(new SpawnRequest(loc, entry));
                        } catch (Exception ignored) {}
                    }

                    if (spawnRequests.size() < patch.minSpawns()) continue;

                } else {
                    // --- EINZELNER SPAWN ---
                    int blockY = getHighestValidY(world, limitedRegion, centerX, centerZ);

                    // Validierung gegen deine min/max Config-Grenzen
                    if (blockY < minY || blockY > maxY) continue;

                    try {
                        Material groundType = limitedRegion.getType(centerX, blockY, centerZ);
                        String groundMat = groundType.getKey().toString();

                        MultiPatchConfig.SchematicEntry entry =
                                MultiPatchConfig.SchematicEntry.pickWeighted(schematics, random);

                        List<String> allowedGround = entry.resolveAllowedGround(patchConfig.getAllowedGround(biomeKey));
                        if (allowedGround != null && !allowedGround.contains(groundMat)) continue;

                        int offsetY = entry.resolveOffsetY(patchConfig.getOffsetY(biomeKey));
                        Location loc = new Location(world, centerX, blockY + offsetY + 1, centerZ);
                        spawnRequests.add(new SpawnRequest(loc, entry));
                    } catch (Exception ignored) {
                        continue;
                    }
                }

                if (patchConfig.isPreventOverlap()) feature.markChunkOccupied(world, chunkKey);

                StructureConfig.RotationMode globalRotation = patchConfig.getRotationMode(biomeKey);
                StructureConfig.MirrorMode   globalMirror   = patchConfig.getMirrorMode(biomeKey);
                boolean globalOverrideAir = patchConfig.getOverrideAir(biomeKey);

                for (SpawnRequest req : spawnRequests) {
                    File file = feature.getStructureFile(req.entry.getName());
                    if (file == null || !file.exists()) {
                        feature.debug("MultiPatch '" + patchName + "': schematic file not found for '"
                                + req.entry.getName() + "'");
                        continue;
                    }

                    feature.recordPlacement(patchName + "/" + req.entry.getName());

                    StructureRotation rotation = req.entry.resolveRotation(globalRotation, random);
                    Mirror mirror              = req.entry.resolveMirror(globalMirror, random);
                    boolean overrideAir        = req.entry.resolveOverrideAir(globalOverrideAir);
                    Location spawnLoc          = req.location;

                    placeWithWorldEdit(world, spawnLoc, file, rotation, mirror, overrideAir,
                            patchName + "/" + req.entry.getName(), feature);
                }
                return;
            }
        }
    }

    /**
     * Sucht vom absoluten Maximum der Welt nach unten nach dem höchsten echten Block.
     */
    private int getHighestValidY(World world, org.bukkit.generator.LimitedRegion region, int x, int z) {
        int worldMax = world.getMaxHeight() - 1;
        int worldMin = world.getMinHeight();

        for (int y = worldMax; y >= worldMin; y--) {
            try {
                Material mat = region.getType(x, y, z);

                // Überspringe unvollständige, Luft- oder "Deko"-Blöcke
                if (mat.isAir() || isIgnoredBlock(mat)) {
                    continue;
                }

                // Der allerhöchste, valide Block der Welt an diesen X/Z Koordinaten
                return y;
            } catch (Exception ignored) {
                // Falls die Koordinate außerhalb der geladenen LimitedRegion liegt
            }
        }
        return -1;
    }

    /**
     * Hilfsmethode, um zu definieren welche Materialien ignoriert werden sollen.
     */
    private boolean isIgnoredBlock(Material mat) {
        String name = mat.name();
        return name.contains("LEAVES")
                || name.contains("GRASS")
                || name.contains("FERN")
                || name.contains("FLOWER")
                || name.contains("PLANT")
                || name.contains("VINE")
                || mat == Material.SNOW
                || mat == Material.MOSS_CARPET;
    }

    private record SpawnRequest(Location location, MultiPatchConfig.SchematicEntry entry) {}

    // =====================================================================
    // WorldEdit Placement - Fixed für 26.2
    // =====================================================================
    //
    // WICHTIG: Der alte Code hat pro Block im Schematic world.getBlockAt(...).getType()
    // aufgerufen, während er bereits "synchron" im Main-Thread-Task lief. Sobald ein
    // Zielblock in einem noch nicht existierenden/geladenen Chunk lag (sehr üblich bei
    // Struktur-Placements nahe Chunkgrenzen während der Weltgenerierung), hat Paper
    // intern einen SYNCHRONEN Chunk-Load/-Generate ausgelöst -> Watchdog-Freeze.
    //
    // Fix: Bevor irgendein world.getBlockAt() aufgerufen wird, werden alle betroffenen
    // Chunks per world.getChunkAtAsync(...) (nicht-blockierend) vorab sichergestellt.
    // Erst wenn diese Future abgeschlossen ist, wird die Blacklist-Prüfung + der
    // eigentliche WorldEdit-Paste ausgeführt.
    //
    // Zusätzlich: Schematics werden jetzt gecacht (kein wiederholtes Disk-I/O) und das
    // Lesen von der Platte passiert asynchron statt im Main-Thread.

    private void placeWithWorldEdit(World world, Location location, File file,
                                    StructureRotation rotation, Mirror mirror,
                                    boolean overrideAir, String debugName, WorldGenFeature feature) {

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

            // Zurück auf den Main-Thread hüpfen, bevor wir Welt-bezogene Berechnungen anstellen.
            Bukkit.getScheduler().runTask(CrayonUtil.getInstance(), () -> {
                try {
                    AffineTransform transform = buildTransform(rotation, mirror);
                    BlockVector3 to     = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
                    BlockVector3 origin = clipboard.getOrigin();

                    int[] bounds = computeTransformedChunkBounds(clipboard.getRegion(), transform, origin, to);

                    ensureChunksLoaded(world, bounds[0], bounds[1], bounds[2], bounds[3])
                            .whenComplete((v, loadError) -> Bukkit.getScheduler().runTask(CrayonUtil.getInstance(), () -> {
                                if (loadError != null) {
                                    feature.debug("Failed to preload chunks for '" + debugName + "': " + loadError.getMessage());
                                    return;
                                }
                                try {
                                    finishPlacement(world, clipboard, transform, to, overrideAir, feature, debugName);
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

    private Clipboard loadClipboard(File file) throws Exception {
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

    private AffineTransform buildTransform(StructureRotation rotation, Mirror mirror) {
        AffineTransform transform = new AffineTransform();
        switch (rotation) {
            case CLOCKWISE_90        -> transform = transform.rotateY(-90);
            case CLOCKWISE_180       -> transform = transform.rotateY(-180);
            case COUNTERCLOCKWISE_90 -> transform = transform.rotateY(90);
            default -> {}
        }
        switch (mirror) {
            case LEFT_RIGHT -> transform = transform.scale(-1, 1, 1);
            case FRONT_BACK -> transform = transform.scale(1, 1, -1);
            default -> {}
        }
        return transform;
    }

    /**
     * Berechnet, welcher Chunk-Bereich (minChunkX, maxChunkX, minChunkZ, maxChunkZ)
     * durch die transformierte + verschobene Region abgedeckt wird.
     */
    private int[] computeTransformedChunkBounds(Region region, AffineTransform transform,
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

    /**
     * Stellt sicher, dass alle Chunks im angegebenen Bereich (+1 Rand als Sicherheitsmarge
     * für Rotation/Mirror-Randfälle und WorldEdit-Randzugriffe) geladen sind, OHNE
     * dabei den Main-Thread zu blockieren. Nutzt Papers nicht-blockierendes
     * getChunkAtAsync(...).
     */
    private CompletableFuture<Void> ensureChunksLoaded(World world, int minChunkX, int maxChunkX,
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

    /**
     * Führt die eigentliche Blacklist-Prüfung + den WorldEdit-Paste aus.
     * Wird erst aufgerufen, nachdem sichergestellt wurde, dass alle betroffenen
     * Chunks geladen sind - world.getBlockAt() kann hier also nie mehr einen
     * synchronen Chunk-Load auslösen.
     */
    private void finishPlacement(World world, Clipboard clipboard, AffineTransform transform,
                                 BlockVector3 to, boolean overrideAir,
                                 WorldGenFeature feature, String debugName) {

        List<String> blacklist = feature.getFeatureConfig().getStringList("blacklisted-blocks");
        if (blacklist != null && !blacklist.isEmpty()) {
            BlockVector3 origin = clipboard.getOrigin();

            for (BlockVector3 srcPt : clipboard.getRegion()) {
                com.sk89q.worldedit.world.block.BlockState schematicState = clipboard.getBlock(srcPt);
                if (schematicState == null) continue;

                BlockVector3 relativeTranslated = transform.apply(srcPt.subtract(origin).toVector3()).toBlockPoint();
                BlockVector3 targetPt = relativeTranslated.add(to);

                // Jetzt sicher: der Chunk dieser Koordinate ist garantiert bereits geladen.
                org.bukkit.block.Block targetBlock = world.getBlockAt(targetPt.x(), targetPt.y(), targetPt.z());
                String targetMatKey = targetBlock.getType().getKey().toString();

                if (blacklist.contains(targetMatKey)) {
                    boolean isSchematicAir = schematicState.getBlockType().equals(BlockTypes.AIR)
                            || schematicState.getBlockType().equals(BlockTypes.CAVE_AIR);

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
                mask.add(new com.sk89q.worldedit.function.mask.ExistingBlockMask(holder.getClipboard()));
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