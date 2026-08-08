package space.qouve.worldgenfeature.services;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import space.qouve.worldgenfeature.WorldGenFeature;
import space.qouve.worldgenfeature.models.WorldGenConfig;
import space.qouve.worldgenfeature.models.WorldGenStructure;
import space.qouve.worldgenfeature.utils.WorldGenUtil;

import java.io.File;
import java.util.*;

/**
 * Loads all structure configs from the "configurations" folder (including
 * arbitrarily nested sub-folders), merges their "structures" sections into a
 * single temporary section, and builds the resulting {@link WorldGenStructure}
 * instances from it.
 */
public final class WorldGenService {

    private static final String CONFIG_FOLDER_NAME = "configurations";
    private static final String STRUCTURES_KEY = "structures";

    private final WorldGenFeature feature;
    private final File dataFolder;

    private final Map<String, WorldGenStructure> structures = new LinkedHashMap<>();

    public WorldGenService(WorldGenFeature feature, File dataFolder) {
        this.feature = feature;
        this.dataFolder = dataFolder;
    }

    /**
     * Loads (or reloads) all structures. Safe to call multiple times.
     */
    public void load() {
        structures.clear();

        File configurationsFolder = new File(dataFolder, CONFIG_FOLDER_NAME);
        if (!configurationsFolder.isDirectory()) {
            feature.debug("No '" + CONFIG_FOLDER_NAME + "' folder found (" + configurationsFolder.getPath() + "), skipping structure loading.");
            return;
        }

        List<File> ymlFiles = new ArrayList<>();
        collectYamlFiles(configurationsFolder, ymlFiles);

        if (ymlFiles.isEmpty()) {
            feature.debug("No YAML files found in '" + CONFIG_FOLDER_NAME + "'.");
            return;
        }

        ConfigurationSection mergedStructures = mergeStructureSections(ymlFiles);
        loadStructuresFromSection(mergedStructures);

        feature.debug("Loaded " + structures.size() + " structure(s) from " + ymlFiles.size() + " config file(s).");
    }

    public void unLoad() {
        feature.debug("Unloading " + structures.size() + " structure(s).");
        structures.clear();
    }

    /**
     * Reads the "structures" section out of every given file and merges all
     * entries into a single, in-memory section.
     */
    private ConfigurationSection mergeStructureSections(List<File> ymlFiles) {
        ConfigurationSection merged = new YamlConfiguration().createSection(STRUCTURES_KEY);

        for (File file : ymlFiles) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection structuresSection = yaml.getConfigurationSection(STRUCTURES_KEY);
            if (structuresSection == null) continue;

            for (String key : structuresSection.getKeys(false)) {
                ConfigurationSection keySection = structuresSection.getConfigurationSection(key);
                if (keySection == null) {
                    feature.debug("Structure '" + key + "' in " + file.getPath() + " is not a section, skipping.");
                    continue;
                }

                if (merged.contains(key)) {
                    feature.debug("Duplicate structure key '" + key + "' (last seen in " + file.getPath() + "), overwriting previous definition.");
                }

                copySection(keySection, merged.createSection(key));
            }
        }

        return merged;
    }

    /**
     * Builds a {@link WorldGenConfig} and {@link WorldGenStructure} for every
     * key in the merged section and stores it.
     */
    private void loadStructuresFromSection(ConfigurationSection mergedStructures) {
        for (String key : mergedStructures.getKeys(false)) {
            ConfigurationSection section = mergedStructures.getConfigurationSection(key);
            if (section == null) continue;

            try {
                // WorldGenUtil.loadConfig resolves the path as
                // new File(dataFolder, "schematics/" + file), so a value like
                // "meow/my_structure.schem" resolves into a sub-folder automatically.
                WorldGenConfig config = WorldGenUtil.loadConfig(key, section, dataFolder);
                WorldGenStructure structure = new WorldGenStructure(config, section);
                structures.put(key, structure);
            } catch (Exception e) {
                feature.debug("Failed to load structure '" + key + "': " + e.getMessage());
            }
        }
    }

    /**
     * Recursively collects every .yml/.yaml file, no matter how deeply nested.
     */
    private void collectYamlFiles(File folder, List<File> out) {
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                collectYamlFiles(f, out);
            } else if (f.isFile() && isYaml(f.getName())) {
                out.add(f);
            }
        }
    }

    private boolean isYaml(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".yml") || lower.endsWith(".yaml");
    }

    /**
     * Recursively copies a section (including nested sub-sections) into another.
     */
    private void copySection(ConfigurationSection from, ConfigurationSection to) {
        for (String key : from.getKeys(false)) {
            Object value = from.get(key);
            if (value instanceof ConfigurationSection sub) {
                copySection(sub, to.createSection(key));
            } else {
                to.set(key, value);
            }
        }
    }

    // -------------------------------------------------------------------
    // Access
    // -------------------------------------------------------------------

    public Map<String, WorldGenStructure> getStructures() {
        return Collections.unmodifiableMap(structures);
    }

    public WorldGenStructure getStructure(String key) {
        return structures.get(key);
    }

    public void reload() {
        load();
    }
}