package space.qouve.worldgenfeature.utils;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import space.qouve.worldgenfeature.WorldGenFeature;
import space.qouve.worldgenfeature.models.BiomeConfig;
import space.qouve.worldgenfeature.models.StructureRecord;
import space.qouve.worldgenfeature.models.WorldGenBehavior;
import space.qouve.worldgenfeature.models.WorldGenConfig;

import java.io.File;
import java.util.*;
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
        double globalChance = section.getDouble("chance", 100.0);
        List<String> biomes = section.getStringList("biomes");
        boolean enabled = section.getBoolean("enabled", true);
        boolean globalOverrideAir = section.getBoolean("override-air", false);

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

        Map<WorldGenBehavior, ConfigurationSection> globalBehaviors = parseBehaviorList(
                section.getMapList("behaviors")
        );

        Map<String, BiomeConfig> biomeConfigsMap = new LinkedHashMap<>();
        ConfigurationSection biomesSec = section.getConfigurationSection("biome-behaviors");

        if (biomesSec != null) {
            for (String biomeKey : biomesSec.getKeys(false)) {
                ConfigurationSection biomeSubSec = biomesSec.getConfigurationSection(biomeKey);
                if (biomeSubSec != null) {
                    // Chance und OverrideAir vom Biom übernehmen oder auf Global zurückfallen
                    double biomeChance = biomeSubSec.getDouble("chance", globalChance);
                    boolean biomeOverrideAir = biomeSubSec.getBoolean("override-air", globalOverrideAir);

                    Map<WorldGenBehavior, ConfigurationSection> resolvedBiomeBehaviors = new LinkedHashMap<>(globalBehaviors);
                    Map<WorldGenBehavior, ConfigurationSection> specificBehaviors = parseBehaviorList(
                            biomeSubSec.getMapList("behaviors")
                    );
                    resolvedBiomeBehaviors.putAll(specificBehaviors);

                    biomeConfigsMap.put(biomeKey.toLowerCase(), new BiomeConfig(biomeChance, biomeOverrideAir, resolvedBiomeBehaviors));
                }
            }
        }

        for (String biome : biomes) {
            biomeConfigsMap.putIfAbsent(biome.toLowerCase(), new BiomeConfig(globalChance, globalOverrideAir, globalBehaviors));
        }

        return new WorldGenConfig(key, schematicFile, rotation, globalChance, biomes, biomeConfigsMap, globalBehaviors, globalOverrideAir, enabled, section);
    }

    public static Map<WorldGenBehavior, ConfigurationSection> parseBehaviorList(List<Map<?, ?>> behaviorList) {
        Map<WorldGenBehavior, ConfigurationSection> map = new LinkedHashMap<>();
        if (behaviorList == null) return map;

        for (Map<?, ?> entry : behaviorList) {
            for (Map.Entry<?, ?> mapEntry : entry.entrySet()) {
                String id = String.valueOf(mapEntry.getKey());
                WorldGenBehavior behavior = WorldGenBehavior.byId(id);

                if (behavior != null) {
                    ConfigurationSection behaviorSec = null;
                    Object rawValue = mapEntry.getValue();

                    if (rawValue instanceof Map<?, ?> innerMap) {
                        MemoryConfiguration memConfig = new MemoryConfiguration();
                        for (Map.Entry<?, ?> innerEntry : innerMap.entrySet()) {
                            memConfig.set(String.valueOf(innerEntry.getKey()), innerEntry.getValue());
                        }
                        behaviorSec = memConfig;
                    }

                    map.put(behavior, behaviorSec);
                }
            }
        }
        return map;
    }

    public static void saveToPDC(WorldGenFeature feature, World world, BlockVector3 pos, String key) {
        Chunk chunk = world.getChunkAt(pos.x() >> 4, pos.z() >> 4);
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        NamespacedKey pdcKey = new NamespacedKey(feature.getPlugin(), "structure_records");

        List<String> records = new ArrayList<>();
        if (pdc.has(pdcKey, PersistentDataType.LIST.strings())) {
            List<String> retrieved = pdc.get(pdcKey, PersistentDataType.LIST.strings());
            if (retrieved != null) {
                records = new ArrayList<>(retrieved);
            }
        }

        StructureRecord record = new StructureRecord(
                UUID.randomUUID(),
                key,
                System.currentTimeMillis(),
                pos.x(), pos.y(), pos.z()
        );

        records.add(record.serialize());
        pdc.set(pdcKey, PersistentDataType.LIST.strings(), records);
    }
}