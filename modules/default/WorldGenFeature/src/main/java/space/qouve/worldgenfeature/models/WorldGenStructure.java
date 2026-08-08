package space.qouve.worldgenfeature.models;

import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import space.qouve.worldgenfeature.utils.WorldGenUtil;

import java.io.File;
import java.util.Map;

public class WorldGenStructure {

    private final WorldGenConfig config;
    private final File schematic;

    public WorldGenStructure(WorldGenConfig worldGenConfig, ConfigurationSection rootSection) {
        this.config = worldGenConfig;
        this.schematic = worldGenConfig.schematicFile();
    }

    public BiomeConfig getBiomeConfig(Biome biome) {
        String biomeKey = biome.getKey().toString().toLowerCase();
        String biomeName = biome.getKey().toString();

        BiomeConfig exact = config.biomeConfigs().get(biomeKey);
        if (exact == null) {
            exact = config.biomeConfigs().get(biomeName);
        }
        if (exact != null) {
            return exact;
        }

        if (config.biomeConfigs().isEmpty()) {
            return new BiomeConfig(config.chance(), config.overrideAir(), config.globalBehaviors());
        }

        return null;
    }

    public boolean runBehaviors(WorldGenContext context) {
        Biome biome = context.location().getBlock().getBiome();
        BiomeConfig biomeConfig = getBiomeConfig(biome);

        if (biomeConfig == null) {
            return false;
        }

        for (Map.Entry<WorldGenBehavior, ConfigurationSection> entry : biomeConfig.behaviors().entrySet()) {
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

    public File getSchematic() {
        return schematic;
    }

    public WorldGenConfig getConfig() {
        return config;
    }
}