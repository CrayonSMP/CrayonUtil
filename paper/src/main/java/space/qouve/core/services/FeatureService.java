package space.qouve.core.services;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import space.qouve.core.CrayonUtil;
import space.qouve.core.models.Feature;
import space.qouve.core.models.FeatureProvider;

import java.util.*;

public class FeatureService {

    private final CrayonUtil plugin;
    private final Map<String, Feature> registeredFeatures = new HashMap<>();
    private final Map<String, Feature> activeFeatures = new HashMap<>();
    private final Map<String, List<Listener>> featureListeners = new HashMap<>();

    public FeatureService(CrayonUtil plugin) {
        this.plugin = plugin;
    }

    public void registerFeature(Feature feature) {
        registeredFeatures.put(feature.getId().toLowerCase(), feature);
    }

    public void discoverAndLoadFeatures() {
        if (registeredFeatures.isEmpty()) {
            ClassLoader classLoader = plugin.getClass().getClassLoader();
            ServiceLoader<FeatureProvider> loader = ServiceLoader.load(FeatureProvider.class, classLoader);

            for (FeatureProvider provider : loader) {
                try {
                    Feature feature = provider.createInstance(plugin);
                    registerFeature(feature);
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to auto-discover a Feature provider!");
                    e.printStackTrace();
                }
            }
        }
        loadFeatures();
    }

    public void loadFeatures() {
        for (Feature feature : registeredFeatures.values()) {
            boolean isEnabled = plugin.getConfig().getBoolean("features." + feature.getFeatureCategorry().toString().toLowerCase() + "." + feature.getId().toLowerCase(), false);
            if (isEnabled) {
                activateFeatureInstance(feature);
            }
        }
    }

    private boolean activateFeatureInstance(Feature feature) {
        try {
            feature.onEnable();
            if (feature instanceof Listener listener) {
                Bukkit.getPluginManager().registerEvents(listener, plugin);
            }
            activeFeatures.put(feature.getId().toLowerCase(), feature);
            plugin.getLogger().info("[FeatureManager] Successfully activated Feature: " + feature.getId());
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to activate Feature: " + feature.getId());
            e.printStackTrace();
            return false;
        }
    }

    public void unloadFeatures() {
        // FIX: Kopie der Keys erstellen, um ConcurrentModificationException zu verhindern
        List<String> activeIds = new ArrayList<>(activeFeatures.keySet());
        for (String id : activeIds) {
            unloadSingleFeature(id);
        }
    }

    public boolean isFeatureActive(String id) {
        return activeFeatures.containsKey(id.toLowerCase());
    }

    public Map<String, Feature> getRegisteredFeatures() {
        return registeredFeatures;
    }

    public Set<String> getRegisteredFeatureIds() {
        return registeredFeatures.keySet();
    }

    public Feature getActiveFeature(String id) {
        if (id == null) return null;
        return activeFeatures.get(id.toLowerCase());
    }

    public boolean loadSingleFeature(String id) {
        String lowerId = id.toLowerCase();
        Feature feature = registeredFeatures.get(lowerId);
        if (feature == null || activeFeatures.containsKey(lowerId)) return false;
        return activateFeatureInstance(feature);
    }

    public boolean unloadSingleFeature(String id) {
        String lowerId = id.toLowerCase();
        Feature feature = activeFeatures.remove(lowerId);
        if (feature == null) return false;
        try {
            feature.onDisable();

            // OPTIMIERUNG: Falls die Feature-Klasse selbst Events registriert hat, hier entladen
            if (feature instanceof Listener listener) {
                HandlerList.unregisterAll(listener);
            }

            unregisterListeners(lowerId);
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to deactivate single Feature: " + feature.getId());
            e.printStackTrace();
            return false;
        }
    }

    public void reloadSingleFeature(String id) {
        String lowerId = id.toLowerCase();
        // Hier wurde die Logik belassen, entlädt und lädt das Feature einzeln
        unloadSingleFeature(lowerId);
        loadSingleFeature(lowerId);
    }

    public void reloadAllFeatures() {
        // 1. Sichere Kopie der Keys, um ConcurrentModificationException zu verhindern
        List<String> activeIds = new ArrayList<>(activeFeatures.keySet());

        // 2. Alle aktiven Features sauber entladen (onDisable() ausführen + Listener kicken)
        for (String id : activeIds) {
            unloadSingleFeature(id);
        }

        // 3. WICHTIG: Für JEDES registrierte Feature die config.yml frisch von der Festplatte laden!
        for (Feature feature : registeredFeatures.values()) {
            feature.reloadConfig();
        }

        // 4. Features basierend auf der frisch geladenen Config neu aktivieren
        loadFeatures();
    }

    public void registerListeners(String id, List<Listener> listeners) {
        String lowerId = id.toLowerCase();
        listeners.forEach(listener -> {
            Bukkit.getPluginManager().registerEvents(listener, plugin);
            featureListeners.computeIfAbsent(lowerId, k -> new ArrayList<>()).add(listener);
        });
    }

    public void unregisterListeners(String id) {
        // FIX: Verhindert NullPointerException, wenn ein Feature keine Extra-Listeners registriert hatte
        List<Listener> listeners = featureListeners.remove(id.toLowerCase());
        if (listeners != null) {
            listeners.forEach(HandlerList::unregisterAll);
        }
    }
}