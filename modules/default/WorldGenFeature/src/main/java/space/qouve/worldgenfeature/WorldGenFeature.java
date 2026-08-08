package space.qouve.worldgenfeature;

import com.google.auto.service.AutoService;
import com.sk89q.worldedit.bukkit.BukkitPlayer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import space.qouve.core.CrayonUtil;
import space.qouve.core.models.Feature;
import space.qouve.core.models.FeatureCategorry;
import space.qouve.core.models.FeatureProvider;
import space.qouve.worldgenfeature.models.WorldGenBehaviors;
import space.qouve.worldgenfeature.models.WorldGenStructure;
import space.qouve.worldgenfeature.services.WorldGenService;

import java.io.IOException;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class WorldGenFeature extends Feature {

    private static WorldGenFeature instance;
    private YamlConfiguration config;
    private WorldGenService worldGenService;
    private StructurePopulator populator;

    public WorldGenFeature(CrayonUtil plugin) {
        super("worldgenfeature", FeatureCategorry.DEFAULT, "WorldGen", plugin);
    }

    @Override
    public void onEnable() throws IOException {
        instance = this;

        saveConfig();
        reloadConfig();
        config = getFeature().getConfig();

        WorldGenBehaviors.registerAll();

        worldGenService = new WorldGenService(this, getLinkFolder());
        worldGenService.load();

        ConfigurationSection featureConfig = config;

        populator = new StructurePopulator(this, worldGenService.getStructures().values().stream().toList());
        WorldGenListener regListener = new WorldGenListener(this, featureConfig, populator);
        registerListeners(getId(), List.of(regListener));

    }

    @Override
    public void onDisable() {
        worldGenService.unLoad();
        for (World world : Bukkit.getWorlds()) {
            world.getPopulators().remove(populator);
            debug("Unregistered WorldGenPopulator for world: " + world.getName() + " (" + world.getKey().toString() + ")");
        }

        unregisterListeners(getId());
    }

    public void reload() {
        worldGenService.reload();
    }

    public static WorldGenFeature getFeature()                    { return instance; }
    public YamlConfiguration getFeatureConfig() { return config; }

    @AutoService(FeatureProvider.class)
    public static class Provider implements FeatureProvider {
        @Override
        public Feature createInstance(CrayonUtil plugin) { return new WorldGenFeature(plugin); }
    }
}