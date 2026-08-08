package space.qouve.worldgenfeature;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import space.qouve.worldgenfeature.models.*;
import space.qouve.worldgenfeature.utils.StructureSpawnQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

public class StructurePopulator extends BlockPopulator {

    private final WorldGenFeature feature;
    private final List<WorldGenStructure> structures;
    private final StructureSpawnQueue spawnQueue;

    public StructurePopulator(WorldGenFeature feature, List<WorldGenStructure> structures, StructureSpawnQueue spawnQueue) {
        this.feature = feature;
        this.structures = structures;
        this.spawnQueue = spawnQueue;
    }

    @Override
    public void populate(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull LimitedRegion limitedRegion) {
        List<PendingSpawn> candidates = new ArrayList<>();

        int baseChunkX = chunkX << 4;
        int baseChunkZ = chunkZ << 4;

        for (int i = 0, structuresSize = structures.size(); i < structuresSize; i++) {
            WorldGenStructure structure = structures.get(i);
            try {
                WorldGenConfig config = structure.getConfig();
                if (config == null || !config.enabled()) {
                    continue;
                }

                // Erweiterten Puffer (2 bis 14) nutzen, damit Strukturen nicht in Nachbarchunks überstehen
                int worldX = baseChunkX + 2 + random.nextInt(12);
                int worldZ = baseChunkZ + 2 + random.nextInt(12);
                int sampleY = 64;

                if (!limitedRegion.isInRegion(worldX, sampleY, worldZ)) {
                    continue;
                }

                Biome biome = worldInfo.vanillaBiomeProvider().getBiome(worldInfo, worldX, sampleY, worldZ);
                BiomeConfig biomeConfig = structure.getBiomeConfig(biome);
                if (biomeConfig == null) {
                    continue;
                }

                if (biomeConfig.chance() < 100.0 && (random.nextDouble() * 100.0) > biomeConfig.chance()) {
                    continue;
                }

                // Performante, optimierte Höhensuche statt blindem Loop von ganz oben
                int groundY = findHighestValidYOptimized(worldInfo, limitedRegion, worldX, worldZ);
                if (groundY == -1) {
                    continue;
                }

                candidates.add(new PendingSpawn(structure, worldX, groundY, worldZ, biomeConfig.overrideAir()));

            } catch (Exception e) {
                Bukkit.getLogger().log(Level.SEVERE, "[WorldGenFeature] Fehler beim Vorbereiten der Struktur in Chunk ("
                        + chunkX + ", " + chunkZ + ")!", e);
            }
        }

        if (candidates.isEmpty()) {
            return;
        }

        UUID worldId = worldInfo.getUID();
        ChunkSpawnKey key = new ChunkSpawnKey(worldId, chunkX, chunkZ);
        spawnQueue.queue(key, candidates);
    }

    /**
     * Stark beschleunigte Höhensuche: Startet in einer typischen Region (z. B. Y=100 oder Max-Höhe)
     * und prüft effizient, vermeidet das komplette Durchkämmen von 300+ Blöcken, wenn nicht nötig.
     */
    private int findHighestValidYOptimized(WorldInfo worldInfo, LimitedRegion region, int x, int z) {
        int maxY = Math.min(120, worldInfo.getMaxHeight() - 1); // Startet meist direkt über normalem Terrain
        int minY = worldInfo.getMinHeight();

        try {
            // Erst von Y=120 nach unten suchen, ob da direkt fester Boden ist
            for (int y = maxY; y >= minY; y--) {
                Material mat = region.getType(x, y, z);
                if (!mat.isAir()) {
                    return y;
                }
            }

            // Falls das Terrain höher liegt (z. B. in Bergen), einmalig von ganz oben nach Y=120 scannen
            for (int y = worldInfo.getMaxHeight() - 1; y > maxY; y--) {
                Material mat = region.getType(x, y, z);
                if (!mat.isAir()) {
                    return y;
                }
            }
        } catch (Exception ignored) {
            return -1;
        }

        return -1;
    }
}