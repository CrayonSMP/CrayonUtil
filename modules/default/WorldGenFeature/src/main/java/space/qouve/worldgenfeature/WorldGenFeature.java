package space.qouve.worldgenfeature;

import com.google.auto.service.AutoService;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import space.qouve.core.CrayonUtil;
import space.qouve.core.models.Feature;
import space.qouve.core.models.FeatureCategorry;
import space.qouve.core.models.FeatureProvider;
import space.qouve.worldgenfeature.models.MultiPatchConfig;
import space.qouve.worldgenfeature.models.StructureConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@SuppressWarnings("UnstableApiUsage")
public class WorldGenFeature extends Feature {

    private static WorldGenFeature instance;

    private final Map<String, File>           structureFiles   = new HashMap<>();
    private final Map<String, StructureConfig> structureConfigs = new HashMap<>();
    private final Map<String, MultiPatchConfig> multiPatches    = new HashMap<>();

    private final Map<String, Set<Long>>  occupiedChunksByWorld = new ConcurrentHashMap<>();
    private final Map<String, Integer>    placementStats        = new ConcurrentHashMap<>();

    private final Map<UUID, Location> pos1Map = new HashMap<>();
    private final Map<UUID, Location> pos2Map = new HashMap<>();

    // Tracks players who are toggled into the blacklist block picker mode
    private final Set<UUID> blacklistModePlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private YamlConfiguration config;

    public WorldGenFeature(CrayonUtil plugin) {
        super("worldgenfeature", FeatureCategorry.DEFAULT, "WorldGen", plugin);
    }

    @Override
    public void onEnable() throws IOException {
        instance = this;

        saveConfig();
        reloadConfig();
        config = getFeature().getConfig();

        loadData();

        for (World world : Bukkit.getWorlds()) {
            registerPopulator(world);
        }

        // Main event listener for World Load and Blacklist Mode Interaction
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onWorldLoad(WorldLoadEvent event) {
                registerPopulator(event.getWorld());
            }

            @EventHandler
            public void onPlayerInteract(PlayerInteractEvent event) {
                if (event.getHand() != EquipmentSlot.HAND) return;
                if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

                Player player = event.getPlayer();
                if (!blacklistModePlayers.contains(player.getUniqueId())) return;

                Block block = event.getClickedBlock();
                if (block == null) return;

                event.setCancelled(true);
                String blockKey = block.getType().getKey().toString();

                List<String> blacklist = new ArrayList<>(config.getStringList("blacklisted-blocks"));
                if (blacklist.contains(blockKey)) {
                    blacklist.remove(blockKey);
                    player.sendMessage("§c[WorldGen] Removed §f" + blockKey + " §cfrom the blacklist.");
                } else {
                    blacklist.add(blockKey);
                    player.sendMessage("§a[WorldGen] Added §f" + blockKey + " §ato the blacklist.");
                }

                config.set("blacklisted-blocks", blacklist);
                try {
                    // Saves directly back to the feature's default config file location
                    config.save(new File(plugin.getDataFolder(), "features/worldgenfeature.yml"));
                } catch (IOException e) {
                    player.sendMessage("§4[WorldGen] Failed to save config: " + e.getMessage());
                }
            }
        }, plugin);

        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(
                    new WorldGenCommand(this).build().build(),
                    "WorldGen structure management",
                    List.of("wg")
            );
        });

        debug("WorldGenFeature enabled. Loaded " + structureFiles.size()
                + " structure(s), " + multiPatches.size() + " multi-patch(es).");
    }

    @Override
    public void onDisable() {
        structureFiles.clear();
        structureConfigs.clear();
        multiPatches.clear();
        occupiedChunksByWorld.clear();
        placementStats.clear();
        pos1Map.clear();
        pos2Map.clear();
        blacklistModePlayers.clear();
        instance = null;
        debug("WorldGenFeature disabled.");
    }

    public synchronized void loadData() throws IOException {
        structureFiles.clear();
        structureConfigs.clear();
        multiPatches.clear();
        placementStats.clear();

        File schematicsDir = new File(getLinkFolder(), "structures/schematics");
        File configsDir    = new File(getLinkFolder(), "structures/configurations");

        if (!schematicsDir.exists()) schematicsDir.mkdirs();
        if (!configsDir.exists())    configsDir.mkdirs();

        int[] counter = {0, 0};

        try (Stream<Path> walk = Files.walk(schematicsDir.toPath())) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String s = p.toString();
                        return s.endsWith(".schem") || s.endsWith(".nbt");
                    })
                    .forEach(path -> {
                        File file = path.toFile();

                        String relativePath = schematicsDir.toPath().relativize(path).toString()
                                .replaceAll("\\.(schem|nbt)$", "")
                                .replace("\\", "/");

                        ClipboardFormat format = ClipboardFormats.findByFile(file);
                        if (format == null) {
                            debug("[WorldGen] Unrecognized format, skipping: " + relativePath);
                            counter[1]++;
                            return;
                        }

                        File configFile = new File(configsDir, relativePath + ".yml");
                        if (!configFile.exists()) {
                            configFile.getParentFile().mkdirs();
                            createDefaultConfig(configFile, relativePath);
                            debug("Created default config for: " + relativePath);
                        }

                        StructureConfig config = StructureConfig.load(configFile, relativePath, plugin.getLogger());
                        structureFiles.put(relativePath, file);
                        structureConfigs.put(relativePath, config);
                        placementStats.put(relativePath, 0);
                        counter[0]++;

                        debug("Loaded structure: " + relativePath + " | " + config.toDebugString());
                    });
        }

        debug("[WorldGen] Loaded " + counter[0] + " structure(s)"
                + (counter[1] > 0 ? ", skipped " + counter[1] + " due to errors." : "."));

        File patchesDir = new File(getLinkFolder(), "patches");
        if (!patchesDir.exists()) patchesDir.mkdirs();

        File[] patchFiles = patchesDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (patchFiles != null) {
            for (File pf : patchFiles) {
                String patchName = pf.getName().replace(".yml", "");
                MultiPatchConfig patchConfig = MultiPatchConfig.load(pf, patchName, plugin.getLogger());
                multiPatches.put(patchName, patchConfig);
                debug("[WorldGen] Loaded multi-patch: " + patchName
                        + " | enabled=" + patchConfig.isEnabled()
                        + " schematics=" + patchConfig.getSchematics().size());
            }
        }

        debug("[WorldGen] Loaded " + multiPatches.size() + " multi-patch(es).");
    }

    public void registerStructureRuntime(String name, StructureConfig config) {
        File schematicsDir = new File(getLinkFolder(), "structures/schematics");
        File file = new File(schematicsDir, name + ".schem");
        structureFiles.put(name, file);
        structureConfigs.put(name, config);
        placementStats.put(name, 0);
    }

    public void registerMultiPatchRuntime(String name, MultiPatchConfig config) {
        multiPatches.put(name, config);
    }

    private void createDefaultConfig(File file, String structureName) {
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("enabled", false);
        yaml.set("worlds", Collections.emptyList());
        yaml.set("chance", 0.05);
        yaml.set("attempts", 4);
        yaml.set("min_y", 0);
        yaml.set("max_y", 320);
        yaml.set("offset_y", 0);
        yaml.set("allowed_ground", List.of("minecraft:grass_block"));
        yaml.set("rotation", "RANDOM");
        yaml.set("mirror", "NONE");
        yaml.set("prevent_overlap", true);
        yaml.createSection("biomes");
        try {
            yaml.save(file);
        } catch (Exception e) {
            debug("[WorldGen] Could not save default config for '" + structureName + "': " + e.getMessage());
        }
    }

    private void registerPopulator(World world) {
        boolean alreadyRegistered = world.getPopulators().stream()
                .anyMatch(p -> p instanceof StructurePopulator);
        if (alreadyRegistered) {
            debug("Populator already registered for: " + world.getName() + " – skipping.");
            return;
        }
        world.getPopulators().add(new StructurePopulator());
        debug("Populator registered for world: " + world.getName());
    }

    public void setPos1(UUID uuid, Location loc) { pos1Map.put(uuid, loc); }
    public Location getPos1(UUID uuid)           { return pos1Map.get(uuid); }
    public void setPos2(UUID uuid, Location loc) { pos2Map.put(uuid, loc); }
    public Location getPos2(UUID uuid)           { return pos2Map.get(uuid); }

    public boolean toggleBlacklistMode(UUID uuid) {
        if (blacklistModePlayers.contains(uuid)) {
            blacklistModePlayers.remove(uuid);
            return false;
        } else {
            blacklistModePlayers.add(uuid);
            return true;
        }
    }

    public boolean isChunkOccupied(World world, long chunkKey) {
        Set<Long> set = occupiedChunksByWorld.get(world.getName());
        return set != null && set.contains(chunkKey);
    }

    public void markChunkOccupied(World world, long chunkKey) {
        occupiedChunksByWorld
                .computeIfAbsent(world.getName(), k -> ConcurrentHashMap.newKeySet())
                .add(chunkKey);
    }

    public void recordPlacement(String structureName) {
        placementStats.merge(structureName, 1, Integer::sum);
    }

    public Map<String, Integer>  getPlacementStats()              { return Collections.unmodifiableMap(placementStats); }
    public void                  debugVerbose(String message)     { debug("[VERBOSE] " + message); }
    public File                  getStructureFile(String name)    { return structureFiles.get(name); }
    public StructureConfig       getConfig(String name)           { return structureConfigs.get(name); }
    public Set<String>           getStructureNames()              { return Collections.unmodifiableSet(structureFiles.keySet()); }
    public MultiPatchConfig      getMultiPatchConfig(String name) { return multiPatches.get(name); }
    public Set<String>           getMultiPatchNames()             { return Collections.unmodifiableSet(multiPatches.keySet()); }
    public static WorldGenFeature getFeature()                    { return instance; }
    public YamlConfiguration getFeatureConfig() { return config; }

    @AutoService(FeatureProvider.class)
    public static class Provider implements FeatureProvider {
        @Override
        public Feature createInstance(CrayonUtil plugin) { return new WorldGenFeature(plugin); }
    }
}