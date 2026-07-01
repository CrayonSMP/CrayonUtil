package space.qouve.worldmanager;

import com.google.auto.service.AutoService;
import org.bukkit.configuration.file.YamlConfiguration;
import space.qouve.core.CrayonUtil;
import space.qouve.core.models.Feature;
import space.qouve.core.models.FeatureCategorry;
import space.qouve.core.models.FeatureProvider;
import space.qouve.worldmanager.services.BorderManager;

public class WorldBorderManager extends Feature {

    private static YamlConfiguration config;
    private static WorldBorderManager instance;

    public WorldBorderManager(CrayonUtil plugin) {
        super("worldbordermanager", FeatureCategorry.DEFAULT, "WorldBorderManager", plugin);
    }

    @Override
    public void onEnable() {
        instance = this;
        config = getConfig();

        BorderManager borderManager = new BorderManager(this);
    }

    @Override
    public void onDisable() {
        instance = null;
        config = null;
    }

    @Override
    public String getId() {
        return "worldbordermanager";
    }

    @AutoService(FeatureProvider.class)
    public static class Provider implements FeatureProvider {
        @Override
        public Feature createInstance(CrayonUtil plugin) {
            return new WorldBorderManager(plugin);
        }
    }

    public static YamlConfiguration getFeatureConfig() {
        return config;
    }

    public static WorldBorderManager getFeature() {
        return instance;
    }
}