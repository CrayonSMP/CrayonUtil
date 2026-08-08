package space.qouve.worldgenfeature;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import space.qouve.worldgenfeature.models.PendingSpawn;
import space.qouve.worldgenfeature.models.WorldGenConfig;
import space.qouve.worldgenfeature.models.WorldGenContext;
import space.qouve.worldgenfeature.models.WorldGenStructure;
import space.qouve.worldgenfeature.utils.WorldEditUtil;

import java.util.List;
import java.util.logging.Level;

final class StructureSpawnProcessor {

    private StructureSpawnProcessor() {
    }

    static void process(WorldGenFeature feature, World world, List<PendingSpawn> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        for (PendingSpawn candidate : candidates) {
            WorldGenStructure structure = candidate.structure();
            WorldGenConfig config = structure.getConfig();

            try {
                Location baseLocation = new Location(world, candidate.x(), candidate.y(), candidate.z());

                WorldGenContext context = WorldGenContext.of(
                        world,
                        baseLocation,
                        config.schematicFile(),
                        config.rotation(),
                        Mirror.NONE,
                        candidate.overrideAir(),
                        config.structureKey(),
                        feature
                );

                if (!structure.runBehaviors(context)) {
                    continue;
                }

                WorldEditUtil.placeWithWorldEdit(context);

            } catch (Exception e) {
                Bukkit.getLogger().log(Level.SEVERE, "[WorldGenFeature] Fehler beim Verarbeiten der Struktur '"
                        + config.structureKey() + "'!", e);
            }
        }
    }
}