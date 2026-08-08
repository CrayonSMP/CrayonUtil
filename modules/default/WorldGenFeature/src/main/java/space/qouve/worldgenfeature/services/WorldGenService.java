package space.qouve.worldgenfeature.services;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import space.qouve.worldgenfeature.WorldGenFeature;
import space.qouve.worldgenfeature.models.WorldGenConfig;
import space.qouve.worldgenfeature.models.WorldGenStructure;
import space.qouve.worldgenfeature.utils.WorldGenUtil;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lädt alle Struktur-Konfigurationen einmalig beim Start und hält sie im Speicher gecacht,
 * um jegliche Festplattenzugriffe zur Laufzeit (oder während des Chunk-Generierens) zu verhindern.
 */
public final class WorldGenService {

    private static final String CONFIG_FOLDER_NAME = "configurations";
    private static final String STRUCTURES_KEY = "structures";

    private final WorldGenFeature feature;
    private final File dataFolder;

    // Thread-sicherer Cache für alle geladenen Strukturen
    private final Map<String, WorldGenStructure> structures = new ConcurrentHashMap<>();

    // Cache für bereits eingelesene Yaml-Dateien, falls man sie separat cachen möchte
    private boolean isLoaded = false;

    public WorldGenService(WorldGenFeature feature, File dataFolder) {
        this.feature = feature;
        this.dataFolder = dataFolder;
    }

    /**
     * Lädt alle Strukturen einmalig von der Festplatte und cacht sie im Arbeitsspeicher.
     */
    public synchronized void load() {
        if (isLoaded && !structures.isEmpty()) {
            return; // Verhindert mehrfaches unnötiges Laden
        }

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

        isLoaded = true;
        feature.debug("Successfully loaded and cached " + structures.size() + " structure(s) from " + ymlFiles.size() + " config file(s).");
    }

    public synchronized void unLoad() {
        feature.debug("Unloading " + structures.size() + " structure(s).");
        structures.clear();
        isLoaded = false;
    }

    private ConfigurationSection mergeStructureSections(List<File> ymlFiles) {
        ConfigurationSection merged = new YamlConfiguration().createSection(STRUCTURES_KEY);

        for (File file : ymlFiles) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection structuresSection = yaml.getConfigurationSection(STRUCTURES_KEY);
            if (structuresSection == null) continue;

            for (String key : structuresSection.getKeys(false)) {
                ConfigurationSection keySection = structuresSection.getConfigurationSection(key);
                if (keySection == null) continue;

                copySection(keySection, merged.createSection(key));
            }
        }

        return merged;
    }

    private void loadStructuresFromSection(ConfigurationSection mergedStructures) {
        for (String key : mergedStructures.getKeys(false)) {
            ConfigurationSection section = mergedStructures.getConfigurationSection(key);
            if (section == null) continue;

            try {
                WorldGenConfig config = WorldGenUtil.loadConfig(key, section, dataFolder);
                WorldGenStructure structure = new WorldGenStructure(config, section);
                structures.put(key, structure);
            } catch (Exception e) {
                feature.debug("Failed to load structure '" + key + "': " + e.getMessage());
            }
        }
    }

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

    public Map<String, WorldGenStructure> getStructures() {
        return Collections.unmodifiableMap(structures);
    }

    public WorldGenStructure getStructure(String key) {
        return structures.get(key);
    }

    public synchronized void reload() {
        isLoaded = false;
        load();
    }
}