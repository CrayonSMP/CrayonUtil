package space.qouve.core;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import space.qouve.core.commands.CrayonUtilCommand;
import space.qouve.core.services.FeatureService;
import space.qouve.core.services.LanguageService;
import space.qouve.core.services.RestartService;

import java.util.List;

public class CrayonUtil extends JavaPlugin {
    private static CrayonUtil instance;
    private static LanguageService languageService;
    private static FeatureService featureService;

    @Override
    public void onEnable() {
        instance = this;
        long currentTime = System.currentTimeMillis();

        saveDefaultConfig();
        reloadConfig();

        Bukkit.getLogger().info("Loading features...");
        languageService = new LanguageService(getConfig().getString("language", "en"), this);
        featureService = new FeatureService(this);
        featureService.discoverAndLoadFeatures();
        new RestartService(this, languageService, getConfig());

        PluginCommand command = this.getCommand("crayonutil");
        if (command != null) {
            CrayonUtilCommand executor = new CrayonUtilCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("Could not register command 'crayonutil'! Make sure it is defined in your plugin.yml.");
        }

        Bukkit.getLogger().info("Plugin loaded in " + (System.currentTimeMillis() - currentTime) + " ms");
    }

    @Override
    public void onDisable() {

    }

    public void reload() {
        reloadConfig();
        languageService.reloadLanguage(getConfig().getString("language", "en"));
        featureService.reloadAllFeatures();
    }

    public static CrayonUtil getInstance() {
        return instance;
    }

    public static LanguageService getLanguageService() {
        return languageService;
    }

    public static FeatureService getFeatureService() {
        return featureService;
    }
}