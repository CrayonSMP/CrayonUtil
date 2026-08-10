package space.qouve.worldgenfeature;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldInitEvent;
import space.qouve.worldgenfeature.models.ChunkSpawnKey;
import space.qouve.worldgenfeature.models.PendingSpawn;
import space.qouve.worldgenfeature.utils.StructureSpawnQueue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WorldGenListener implements Listener {

    private final WorldGenFeature feature;
    private final Set<String> allowedWorlds;
    private final StructurePopulator populator;
    private final StructureSpawnQueue queue;

    public WorldGenListener(WorldGenFeature feature, ConfigurationSection configSec, StructurePopulator populator, StructureSpawnQueue queue) {
        this.feature = feature;

        List<String> rawWorlds = configSec.getStringList("allowed-worlds");
        this.allowedWorlds = new HashSet<>(rawWorlds.size());
        for (String world : rawWorlds) {
            this.allowedWorlds.add(world.toLowerCase());
        }

        this.populator = populator;
        this.queue = queue;

        for (World world : Bukkit.getWorlds()) {
            tryRegister(world);
        }
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        tryRegister(event.getWorld());
    }

    private void tryRegister(World world) {
        String worldKey = world.getKey().toString().toLowerCase();
        String worldName = world.getName().toLowerCase();

        if (allowedWorlds.contains(worldKey) || allowedWorlds.contains(worldName)) {
            if (!world.getPopulators().contains(populator)) {
                world.getPopulators().add(populator);
                feature.debug("Registered WorldGenPopulator for world: " + world.getName() + " (" + world.getKey() + ")");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) {
            return;
        }

        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();

        ChunkSpawnKey key = new ChunkSpawnKey(world.getUID(), chunk.getX(), chunk.getZ());

        List<PendingSpawn> candidates = queue.drain(key);
        if (candidates.isEmpty()) {
            return;
        }

        Bukkit.getScheduler().runTask(feature.getPlugin(), () -> {
            if (chunk.isLoaded()) {
                StructureSpawnProcessor.process(feature, world, candidates);
            }
        });
    }
}