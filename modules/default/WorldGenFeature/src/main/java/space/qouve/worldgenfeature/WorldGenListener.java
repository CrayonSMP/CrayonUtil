package space.qouve.worldgenfeature;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.plugin.Plugin;
import space.qouve.worldgenfeature.WorldGenFeature;
import space.qouve.worldgenfeature.models.WorldGenStructure;

import java.util.List;

public class WorldGenListener implements Listener {

    private final WorldGenFeature feature;
    private final List<String> allowedWorlds;
    private final StructurePopulator populator;

    public WorldGenListener(WorldGenFeature feature, ConfigurationSection configSec, StructurePopulator populator) {
        this.feature = feature;

        this.allowedWorlds = configSec.getStringList("allowed-worlds");
        this.populator = populator;

        for (World world : Bukkit.getWorlds()) {
            tryRegister(world);
        }
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        tryRegister(event.getWorld());
    }

    private void tryRegister(World world) {
        String worldKey = world.getKey().toString();
        String worldName = world.getName();

        boolean matches = allowedWorlds.stream().anyMatch(allowed ->
                allowed.equalsIgnoreCase(worldKey) || allowed.equalsIgnoreCase(worldName)
        );

        if (matches) {
            world.getPopulators().add(populator);

            feature.debug("Registered WorldGenPopulator for world: " + worldName + " (" + worldKey + ")");
        }
    }
}