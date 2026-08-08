package space.qouve.worldgenfeature.models;

import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Holds the configuration data of a single structure from the YAML file.
 *
 * @param structureKey The unique identifier (e.g. "ancient_tree")
 * @param schematicFile The corresponding .schem / .schematic file
 * @param rotation Default rotation of the structure (can be changed randomly later)
 * @param chance Spawn probability (0.0 - 100.0)
 * @param biomes List of allowed biomes
 * @param overrideAir Whether air blocks are allowed to be overwritten
 * @param enabled Whether the structure is allowed to spawn independently
 * @param rootConfigSection The root configuration section for this structure (for further customization)
 * @param biomeConfigs Map of biome-specific configurations (biome name -> BiomeConfig)
 * @param globalBehaviors Map of global behaviors (behavior -> configuration section)
 */

public record WorldGenConfig(
        String structureKey,
        File schematicFile,
        StructureRotation rotation,
        double chance,
        List<String> biomes,
        Map<String, BiomeConfig> biomeConfigs,
        Map<WorldGenBehavior, ConfigurationSection> globalBehaviors,
        boolean overrideAir,
        boolean enabled,
        ConfigurationSection rootConfigSection
) {
}