package space.qouve.core.models;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import space.qouve.core.CrayonUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public abstract class Feature {

    private final String id;
    private final FeatureCategorry featureCategorry;
    private final String prefix;
    protected final CrayonUtil plugin;
    private final File linkFolder;
    private YamlConfiguration config;
    private boolean debugEnabled = false;
    private File configFile;

    public Feature(String id, FeatureCategorry featureCategorry, String prefix, CrayonUtil plugin) {
        this.id = id.toLowerCase();
        this.featureCategorry = featureCategorry;
        this.prefix = prefix;
        this.plugin = plugin;
        this.linkFolder = new File(plugin.getDataFolder(), "features/" + this.id);

        reloadConfig();
    }

    public abstract void onEnable() throws IOException;

    public abstract void onDisable();

    public void reloadConfig() {
        if (!linkFolder.exists()) {
            linkFolder.mkdirs();
        }

        this.configFile = new File(linkFolder, "config.yml");

        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }

        this.config = YamlConfiguration.loadConfiguration(configFile);

        InputStream defConfigStream = plugin.getResource("features/" + id + "/config.yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));
            this.config.setDefaults(defConfig);
        }

        this.debugEnabled = this.config.getBoolean("debug", false);
    }

    public void saveConfig() {
        if (config == null || configFile == null) return;
        try {
            config.save(configFile);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save configuration for features: " + id);
            e.printStackTrace();
        }
    }

    protected void saveResource(String resourcePath, boolean replace) {
        if (resourcePath == null || resourcePath.isEmpty()) return;

        resourcePath = resourcePath.replace('\\', '/');
        String jarPath = "features/" + id + "/" + resourcePath;

        InputStream in = plugin.getResource(jarPath);
        if (in == null) {
            plugin.getLogger().warning("Resource '" + jarPath + "' was not found inside the JAR!");
            return;
        }

        File outFile = new File(linkFolder, resourcePath);
        int lastIndex = resourcePath.lastIndexOf('/');
        File outDir = new File(linkFolder, resourcePath.substring(0, Math.max(lastIndex, 0)));

        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        try {
            if (!outFile.exists() || replace) {
                java.nio.file.Files.copy(in, outFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to copy resource: " + resourcePath);
            e.printStackTrace();
        }
    }

    public void onReload() {
        reloadConfig();
        saveConfig();
        onFeatureReload();
    }

    public void onFeatureReload() {

    }

    public String getId() {
        return id;
    }

    public FeatureCategorry getFeatureCategorry() {
        return featureCategorry;
    }

    public CrayonUtil getPlugin() {
        return plugin;
    }

    public File getLinkFolder() {
        return linkFolder;
    }

    public YamlConfiguration getConfig() {
        return config;
    }

    public File getConfigFile() {
        return configFile;
    }

    public String getPrefix() {
        return prefix;
    }

    public void debug(String msg) {
        if (debugEnabled) {
            this.plugin.getLogger().info("[" + this.prefix + "] " + msg);
        }
    }

    public void registerListeners(String id, List<Listener> listeners) {
        CrayonUtil.getFeatureService().registerListeners(id, listeners);
    }

    public void unregisterListeners(String id) {
        CrayonUtil.getFeatureService().unregisterListeners(id);
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }
}