package space.qouve.worldgenfeature.models;

import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WorldGenStructure {

    private final WorldGenConfig config;
    private final File schematic;
    private final Map<WorldGenBehavior, ConfigurationSection> behaviors;

    public WorldGenStructure(WorldGenConfig worldGenConfig, ConfigurationSection rootSection) {
        this.config = worldGenConfig;
        this.schematic = worldGenConfig.schematicFile();
        this.behaviors = new LinkedHashMap<>();
        resolveBehaviors(rootSection);
    }

    public boolean runBehaviors(WorldGenContext context) {
        for (Map.Entry<WorldGenBehavior, ConfigurationSection> entry : behaviors.entrySet()) {
            WorldGenBehavior behavior = entry.getKey();
            ConfigurationSection section = entry.getValue();

            try {
                boolean success = behavior.run(context, section);
                if (!success) {
                    return false;
                }
            } catch (Exception e) {
                context.feature().debug("Error executing behavior " + behavior.getId() + ": " + e.getMessage());
                return false;
            }
        }
        return true;
    }

    public void resolveBehaviors(ConfigurationSection rootSection) {
        if (rootSection == null) return;

        List<Map<?, ?>> behaviorList = rootSection.getMapList("behaviors");

        for (Map<?, ?> entry : behaviorList) {
            for (Map.Entry<?, ?> mapEntry : entry.entrySet()) {
                String id = String.valueOf(mapEntry.getKey());
                WorldGenBehavior behavior = WorldGenBehavior.byId(id);

                if (behavior != null) {
                    ConfigurationSection behaviorSec = null;

                    Object rawValue = mapEntry.getValue();
                    if (rawValue instanceof Map<?, ?> innerMap) {
                        behaviorSec = rootSection.createSection("temp_" + id);
                        for (Map.Entry<?, ?> innerEntry : innerMap.entrySet()) {
                            behaviorSec.set(String.valueOf(innerEntry.getKey()), innerEntry.getValue());
                        }
                    }

                    behaviors.put(behavior, behaviorSec);
                }
            }
        }
    }

    public File getSchematic() {
        return schematic;
    }

    public Map<WorldGenBehavior, ConfigurationSection> getBehaviors() {
        return behaviors;
    }

    public WorldGenConfig getConfig() {
        return config;
    }
}