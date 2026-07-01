package space.qouve.core.services;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LanguageService {

    private final Plugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private YamlConfiguration config;

    public LanguageService(String language, Plugin plugin) {
        this.plugin = plugin;
        this.config = loadConfig(language);
    }

    public void reloadLanguage(String language) {
        this.config = loadConfig(language);
    }

    public YamlConfiguration loadConfig(String language) {
        File langFolder = new File(plugin.getDataFolder(), "lang");
        File file = new File(langFolder, language + ".yml");

        if (!file.exists()) {
            if (plugin.getResource("lang/" + language + ".yml") != null) {
                plugin.saveResource("lang/" + language + ".yml", false);
            } else {
                plugin.getLogger().warning("Language '" + language + "' not found! Falling back to en.yml.");
                file = new File(langFolder, "en.yml");
                if (!file.exists()) {
                    plugin.saveResource("lang/en.yml", false);
                }
            }
        }

        return YamlConfiguration.loadConfiguration(file);
    }

    public String getRaw(String path) {
        return config.getString(path, "<red>Message not found: " + path + "</red>");
    }

    public Component get(String path, TagResolver... resolvers) {
        String raw = getRaw(path);
        if (resolvers.length == 0) {
            return miniMessage.deserialize(raw);
        }
        return miniMessage.deserialize(raw, TagResolver.resolver(resolvers));
    }

    public List<Component> getLore(String path, TagResolver... resolvers) {
        List<Component> lore = new ArrayList<>();
        lore.add(get(path, resolvers));
        return lore;
    }

    public Component getRawMapped(String rawMiniMessage) {
        return miniMessage.deserialize(rawMiniMessage);
    }
}