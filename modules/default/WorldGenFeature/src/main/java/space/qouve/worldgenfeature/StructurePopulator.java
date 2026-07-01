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
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
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
import java.util.Random;

public class StructurePopulator extends BlockPopulator {

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
                        int blockY = random.nextInt(maxY - minY + 1) + minY;

                        try {
                            org.bukkit.Material groundType = limitedRegion.getType(blockX, blockY, blockZ);
                            if (groundType == org.bukkit.Material.AIR) continue;
                            String groundMat = groundType.getKey().toString();
                            List<String> allowedGround = config.getAllowedGround(biomeKey);
                            if (allowedGround != null && !allowedGround.contains(groundMat)) continue;
                            spawnLocations.add(new Location(world, blockX, blockY + config.getOffsetY(biomeKey) + 1, blockZ));
                        } catch (Exception ignored) {}
                    }

                    if (spawnLocations.size() < patch.minSpawns()) continue;

                } else {
                    // --- NORMALE LOGIK ---
                    int blockY = random.nextInt(maxY - minY + 1) + minY;
                    try {
                        org.bukkit.Material groundType = limitedRegion.getType(centerX, blockY, centerZ);
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

                    Bukkit.getScheduler().runTask(CrayonUtil.getInstance(), () -> {
                        try {
                            placeWithWorldEdit(world, spawnLoc, file, rotation, mirror, overrideAir);
                        } catch (Exception e) {
                            feature.debug("Failed to spawn structure '" + name + "': " + e.getMessage());
                        }
                    });
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
                        int blockY = random.nextInt(maxY - minY + 1) + minY;

                        try {
                            org.bukkit.Material groundType = limitedRegion.getType(blockX, blockY, blockZ);
                            if (groundType == org.bukkit.Material.AIR) continue;
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
                    int blockY = random.nextInt(maxY - minY + 1) + minY;
                    try {
                        org.bukkit.Material groundType = limitedRegion.getType(centerX, blockY, centerZ);
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

                    Bukkit.getScheduler().runTask(CrayonUtil.getInstance(), () -> {
                        try {
                            placeWithWorldEdit(world, spawnLoc, file, rotation, mirror, overrideAir);
                        } catch (Exception e) {
                            feature.debug("Failed to spawn multi-patch schematic '"
                                    + req.entry.getName() + "': " + e.getMessage());
                        }
                    });
                }
                return;
            }
        }
    }

    private record SpawnRequest(Location location, MultiPatchConfig.SchematicEntry entry) {}

    private void placeWithWorldEdit(World world, Location location, File file,
                                    StructureRotation rotation, Mirror mirror,
                                    boolean overrideAir) throws Exception {
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) throw new IllegalArgumentException("Unknown schematic format: " + file.getName());

        Clipboard clipboard;
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            clipboard = reader.read();
        }

        WorldGenFeature feature = WorldGenFeature.getFeature();
        if (feature != null) {
            List<String> blacklist = feature.getFeatureConfig().getStringList("blacklisted-blocks");
            if (blacklist != null && !blacklist.isEmpty()) {
                BlockVector3 to = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
                BlockVector3 origin = clipboard.getOrigin();

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

                for (BlockVector3 srcPt : clipboard.getRegion()) {
                    com.sk89q.worldedit.world.block.BlockState schematicState = clipboard.getBlock(srcPt);
                    if (schematicState == null) continue;

                    BlockVector3 relativeTranslated = transform.apply(srcPt.subtract(origin).toVector3()).toBlockPoint();
                    BlockVector3 targetPt = relativeTranslated.add(to);

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
        }

        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);

        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(weWorld)
                .build()) {

            ClipboardHolder holder = new ClipboardHolder(clipboard);

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
            if (!transform.isIdentity()) holder.setTransform(transform);

            BlockVector3 to = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());

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
        }
    }
}