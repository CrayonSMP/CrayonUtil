package space.qouve.worldgenfeature.behaviors;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import space.qouve.worldgenfeature.models.WorldGenBehavior;
import space.qouve.worldgenfeature.models.WorldGenContext;
import space.qouve.worldgenfeature.models.WorldGenStructure;
import space.qouve.worldgenfeature.utils.WorldEditUtil;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class PatchSpawnBehavior extends WorldGenBehavior {

    private static final int MAX_COUNT = 32;

    public PatchSpawnBehavior() {
        super("patch-spawn");
    }

    @Override
    public boolean run(WorldGenContext context, ConfigurationSection section) {
        if (context.isPatch()) {
            return true;
        }

        int count = Math.min(section.getInt("count", 3), MAX_COUNT);
        double radius = section.getDouble("radius", 16.0);
        List<String> patchStructureKeys = section.getStringList("structures");
        if (patchStructureKeys.isEmpty()) return true;

        World world = context.world();

        for (int i = 0; i < count; i++) {
            WorldEditUtil.scheduleDeferred(() -> spawnSinglePatch(context, world, radius, patchStructureKeys));
        }

        return true;
    }

    private void spawnSinglePatch(WorldGenContext context, World world, double radius, List<String> patchStructureKeys) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        double offsetX = (random.nextDouble() - 0.5) * 2 * radius;
        double offsetZ = (random.nextDouble() - 0.5) * 2 * radius;
        Location targetLoc = context.location().clone().add(offsetX, 0, offsetZ);

        int blockX = targetLoc.getBlockX();
        int blockZ = targetLoc.getBlockZ();
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;

        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }

        targetLoc.setY(randomY(world));

        String chosenKey = patchStructureKeys.get(random.nextInt(patchStructureKeys.size()));
        WorldGenStructure subStructure = context.feature().genService().getStructure(chosenKey);
        if (subStructure == null) return;

        WorldGenContext patchContext = WorldGenContext.of(
                world,
                targetLoc,
                subStructure.getConfig().schematicFile(),
                subStructure.getConfig().rotation(),
                org.bukkit.block.structure.Mirror.NONE,
                subStructure.getConfig().overrideAir(),
                subStructure.getConfig().structureKey(),
                context.feature()
        ).withIsPatch(true);

        if (subStructure.runBehaviors(patchContext)) {
            WorldEditUtil.placeWithWorldEdit(patchContext);
        }
    }

    private int randomY(World world) {
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        return minY + ThreadLocalRandom.current().nextInt(maxY - minY + 1);
    }
}