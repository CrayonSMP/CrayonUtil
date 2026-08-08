package space.qouve.worldgenfeature.utils;

import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import space.qouve.worldgenfeature.models.WorldGenBehavior;
import space.qouve.worldgenfeature.models.WorldGenConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class WorldGenUtil {

    private WorldGenUtil() {
    }

    public static StructureRotation getRandomRotation() {
        StructureRotation[] rotations = {
                StructureRotation.NONE,
                StructureRotation.CLOCKWISE_90,
                StructureRotation.CLOCKWISE_180,
                StructureRotation.COUNTERCLOCKWISE_90
        };
        return rotations[ThreadLocalRandom.current().nextInt(rotations.length)];
    }

    public static WorldGenConfig loadConfig(String key, ConfigurationSection section, File dataFolder) {
        File schematicFile = new File(dataFolder, "schematics/" + section.getString("file"));
        double chance = section.getDouble("chance", 100.0);
        List<String> blocks = section.getStringList("blocks");

        String rotationStr = section.getString("rotation", "NONE").toUpperCase();

        StructureRotation rotation;
        if (rotationStr.equals("RANDOM")) {
            rotation = getRandomRotation();
        } else {
            try {
                rotation = StructureRotation.valueOf(rotationStr);
            } catch (IllegalArgumentException e) {
                rotation = StructureRotation.NONE;
            }
        }

        boolean overrideAir = section.getBoolean("override-air", false);

        return new WorldGenConfig(key, schematicFile, rotation, chance, blocks, overrideAir, section);
    }
}