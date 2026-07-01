package space.qouve.confetticannon;

import com.google.auto.service.AutoService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import space.qouve.confetticannon.listeners.ConfettiListener;
import space.qouve.core.CrayonUtil;
import space.qouve.core.models.Feature;
import space.qouve.core.models.FeatureCategorry;
import space.qouve.core.models.FeatureProvider;

import java.util.List;

public class ConfettiCannon extends Feature {

    private static YamlConfiguration config;
    private static ConfettiCannon instance;
    public static boolean enabled = false;

    public ConfettiCannon(CrayonUtil plugin) {
        super("confetticannon", FeatureCategorry.DEFAULT, "ConfettiCannon", plugin);
    }

    @Override
    public void onEnable() {
        instance = this;
        config = getConfig();

        String mode = config.getString("engine-mode", "LEGACY").toUpperCase();

        // Validierung der Abhängigkeiten je nach Modus
        if (mode.equals("LEGACY") || mode.equals("PARTICLELIB")) {
            if (!Bukkit.getPluginManager().isPluginEnabled("ParticleLib")) {
                this.getPlugin().getLogger().warning("ParticleLib not found! ConfettiCannon (Legacy Mode) will be disabled.");
                CrayonUtil.getFeatureService().unloadSingleFeature(getId());
                return;
            }
        } else {
            // Default zu MODERN / PARTICLEENGINE
            if (!Bukkit.getPluginManager().isPluginEnabled("ParticleEngine")) {
                this.getPlugin().getLogger().warning("ParticleEngine not found! ConfettiCannon (Modern Mode) will be disabled.");
                CrayonUtil.getFeatureService().unloadSingleFeature(getId());
                return;
            }
        }

        // Listener registrieren
        List<Listener> listeners = List.of(
                new ConfettiListener()
        );
        registerListeners(getId(), listeners);
        enabled = true;
    }

    @Override
    public void onDisable() {
        unregisterListeners(getId());
        enabled = false;
        instance = null;
        config = null;
    }

    @Override
    public String getId() {
        return "confetticannon";
    }

    @AutoService(FeatureProvider.class)
    public static class Provider implements FeatureProvider {
        @Override
        public Feature createInstance(CrayonUtil plugin) {
            return new ConfettiCannon(plugin);
        }
    }

    public static YamlConfiguration getFeatureConfig() {
        return config;
    }

    public static ConfettiCannon getFeature() {
        return instance;
    }
}