package space.qouve.worldgenfeature;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.generator.BlockPopulator;
import org.jetbrains.annotations.NotNull;
import space.qouve.worldgenfeature.WorldGenFeature;
import space.qouve.worldgenfeature.models.WorldGenConfig;
import space.qouve.worldgenfeature.models.WorldGenContext;
import space.qouve.worldgenfeature.models.WorldGenStructure;
import space.qouve.worldgenfeature.utils.WorldEditUtil;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class StructurePopulator extends BlockPopulator {

    private final WorldGenFeature feature;
    private final List<WorldGenStructure> structures;

    public StructurePopulator(WorldGenFeature feature, List<WorldGenStructure> structures) {
        this.feature = feature;
        this.structures = structures;
    }

    @Override
    public void populate(@NotNull World world, @NotNull Random random, @NotNull Chunk chunk) {
        for (WorldGenStructure structure : structures) {
            WorldGenConfig config = structure.getConfig();

            // 1. Chance check
            if (config.chance() < 100.0 && ThreadLocalRandom.current().nextDouble(100.0) > config.chance()) {
                continue;
            }

            // 2. Select a random coordinate inside the chunk
            int chunkX = chunk.getX() << 4;
            int chunkZ = chunk.getZ() << 4;
            int x = chunkX + random.nextInt(16);
            int z = chunkZ + random.nextInt(16);

            Block highestBlock = world.getHighestBlockAt(x, z);
            Location baseLocation = highestBlock.getLocation();

            // 3. Create initial context (rotation from config)
            WorldGenContext context = WorldGenContext.of(
                    world,
                    baseLocation,
                    config.schematicFile(),
                    config.rotation(),
                    org.bukkit.block.structure.Mirror.NONE,
                    config.overrideAir(),
                    config.structureKey(),
                    feature
            );

            // 4. Run Behaviors (checks and context modifications)
            if (!structure.runBehaviors(context)) {
                continue;
            }

            // 5. Delegate placement to WorldEditUtil (handles async loading, chunks & placement)[cite: 14]
            WorldEditUtil.placeWithWorldEdit(context);
        }
    }
}