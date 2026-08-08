package space.qouve.worldgenfeature.models;

import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds the configuration data of a single structure from the YAML file.
 *
 * @param structureKey The unique identifier (e.g. "ancient_tree")
 * @param schematicFile The corresponding .schem / .schematic file
 * @param rotation Default rotation of the structure (can be changed randomly later)
 * @param chance Spawn probability (0.0 - 100.0)
 * @param blocks List of allowed blocks
 * @param overrideAir Whether air blocks are allowed to be overwritten
 */
public record WorldGenConfig(
        String structureKey,
        File schematicFile,
        StructureRotation rotation,
        double chance,
        List<String> blocks,
        boolean overrideAir,
        ConfigurationSection rootConfigSection
) {
}