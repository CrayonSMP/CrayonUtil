package space.qouve.worldgenfeature;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import space.qouve.worldgenfeature.models.MultiPatchConfig;
import space.qouve.worldgenfeature.models.StructureConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@SuppressWarnings("UnstableApiUsage")
public class WorldGenCommand {

    private final WorldGenFeature feature;

    public WorldGenCommand(WorldGenFeature feature) {
        this.feature = feature;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("worldgen")
                .requires(source -> source.getSender().hasPermission("worldgen.admin"))
                .then(Commands.literal("list").executes(ctx -> handleListStructures(ctx.getSource())))
                .then(Commands.literal("save")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> handleSave(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("load")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> handleLoad(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> handleDelete(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("info")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> handleInfo(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("patch")
                        .then(Commands.literal("list").executes(ctx -> handleListPatches(ctx.getSource())))
                        .then(Commands.literal("create")
                                .then(Commands.argument("patchname", StringArgumentType.word())
                                        .then(Commands.argument("args", StringArgumentType.greedyString())
                                                .executes(ctx -> handlePatchCreate(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "patchname"),
                                                        StringArgumentType.getString(ctx, "args")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("delete")
                                .then(Commands.argument("patchname", StringArgumentType.word())
                                        .executes(ctx -> handlePatchDelete(ctx.getSource(), StringArgumentType.getString(ctx, "patchname")))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("patchname", StringArgumentType.word())
                                        .executes(ctx -> handlePatchInfo(ctx.getSource(), StringArgumentType.getString(ctx, "patchname")))))
                )
                .then(Commands.literal("stats").executes(ctx -> handleStats(ctx.getSource())))
                .then(Commands.literal("reload").executes(ctx -> handleReload(ctx.getSource())))
                .then(Commands.literal("blacklist")
                        .executes(ctx -> handleBlacklistToggle(ctx.getSource()))
                        .then(Commands.literal("list").executes(ctx -> handleBlacklistList(ctx.getSource()))));
    }

    private int handleListStructures(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        sender.sendMessage("§6=== Registered Structures ===");
        Set<String> names = feature.getStructureNames();
        if (names.isEmpty()) {
            sender.sendMessage("§7No structures registered.");
            return Command.SINGLE_SUCCESS;
        }
        for (String name : names) {
            StructureConfig cfg = feature.getConfig(name);
            String status = (cfg != null && cfg.isEnabled()) ? "§a[Enabled]" : "§c[Disabled]";
            sender.sendMessage("§e - " + name + " " + status);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int handleListPatches(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        sender.sendMessage("§6=== Registered Multi-Patches ===");
        Set<String> names = feature.getMultiPatchNames();
        if (names.isEmpty()) {
            sender.sendMessage("§7No multi-patches registered.");
            return Command.SINGLE_SUCCESS;
        }
        for (String name : names) {
            MultiPatchConfig cfg = feature.getMultiPatchConfig(name);
            String status = (cfg != null && cfg.isEnabled()) ? "§a[Enabled]" : "§c[Disabled]";
            sender.sendMessage("§e - " + name + " " + status);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int handleSave(CommandSourceStack source, String name) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return Command.SINGLE_SUCCESS;
        }

        com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(wePlayer);

        Region region;
        try {
            region = session.getSelection(wePlayer.getWorld());
        } catch (IncompleteRegionException e) {
            player.sendMessage("§cPlease make a complete WorldEdit selection first!");
            return Command.SINGLE_SUCCESS;
        }

        World world = player.getWorld();
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setOrigin(min);

        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(wePlayer.getWorld())
                .build()) {

            ForwardExtentCopy forwardExtentCopy = new ForwardExtentCopy(
                    editSession, region, clipboard, region.getMinimumPoint()
            );
            forwardExtentCopy.setCopyingEntities(false);
            forwardExtentCopy.setCopyingBiomes(false);
            Operations.complete(forwardExtentCopy);

            File structuresDir = new File(feature.getLinkFolder(), "structures/schematics");
            if (!structuresDir.exists()) structuresDir.mkdirs();

            File schemFile = new File(structuresDir, name + ".schem");
            schemFile.getParentFile().mkdirs();

            try (ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(new FileOutputStream(schemFile))) {
                writer.write(clipboard);
            }

            File configsDir = new File(feature.getLinkFolder(), "structures/configurations");
            File configFile = new File(configsDir, name + ".yml");
            configFile.getParentFile().mkdirs();

            if (!configFile.exists()) {
                var yaml = new YamlConfiguration();
                yaml.set("enabled", true);
                yaml.set("worlds", List.of(world.getName()));
                yaml.set("chance", 0.05);
                yaml.set("attempts", 4);
                yaml.set("min_y", min.y());
                yaml.set("max_y", max.y());
                yaml.set("offset_y", 0);
                yaml.set("allowed_ground", List.of("minecraft:grass_block"));
                yaml.set("rotation", "RANDOM");
                yaml.set("mirror", "NONE");
                yaml.set("prevent_overlap", true);
                yaml.createSection("biomes");
                yaml.save(configFile);
            }

            StructureConfig newConfig = StructureConfig.load(configFile, name, feature.getPlugin().getLogger());
            feature.registerStructureRuntime(name, newConfig);

            player.sendMessage("§aSuccessfully saved structure blueprint to " + name + ".schem and registered configurations.");

        } catch (Exception e) {
            player.sendMessage("§cAn error occurred during saving: " + e.getMessage());
            e.printStackTrace();
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleLoad(CommandSourceStack source, String name) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return Command.SINGLE_SUCCESS;
        }

        File file = feature.getStructureFile(name);
        if (file == null || !file.exists()) {
            player.sendMessage("§cStructure configuration or file '" + name + "' not found.");
            return Command.SINGLE_SUCCESS;
        }

        player.sendMessage("§7Pasting structure '" + name + "' at your feet...");

        try {
            com.sk89q.worldedit.extent.clipboard.Clipboard clipboard;
            try (com.sk89q.worldedit.extent.clipboard.io.ClipboardReader reader =
                         com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats.findByFile(file).getReader(new java.io.FileInputStream(file))) {
                clipboard = reader.read();
            }

            try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                    .world(BukkitAdapter.adapt(player.getWorld()))
                    .build()) {

                ClipboardHolder holder = new ClipboardHolder(clipboard);
                BlockVector3 to = BlockVector3.at(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ());

                ForwardExtentCopy copy = new ForwardExtentCopy(
                        holder.getClipboard(),
                        holder.getClipboard().getRegion(),
                        holder.getClipboard().getOrigin(),
                        editSession,
                        to
                );
                copy.setCopyingEntities(false);
                copy.setCopyingBiomes(false);

                com.sk89q.worldedit.function.mask.MaskIntersection mask = new com.sk89q.worldedit.function.mask.MaskIntersection();
                mask.add(new com.sk89q.worldedit.function.mask.AbstractMask() {
                    @Override
                    public boolean test(BlockVector3 vector) {
                        com.sk89q.worldedit.world.block.BlockState state = holder.getClipboard().getBlock(vector);
                        return state != null && !state.getBlockType().equals(BlockTypes.BARRIER);
                    }
                });
                copy.setSourceMask(mask);

                Operations.complete(copy);
                player.sendMessage("§aStructure successfully placed!");
            }
        } catch (Exception e) {
            player.sendMessage("§cFailed to paste structure: " + e.getMessage());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleDelete(CommandSourceStack source, String name) {
        CommandSender sender = source.getSender();
        File file = feature.getStructureFile(name);

        if (file == null || !file.exists()) {
            sender.sendMessage("§cStructure '" + name + "' does not exist or is not loaded.");
            return Command.SINGLE_SUCCESS;
        }

        boolean fileDeleted = file.delete();

        File configsDir = new File(feature.getLinkFolder(), "structures/configurations");
        File configFile = new File(configsDir, name + ".yml");
        boolean configDeleted = false;
        if (configFile.exists()) {
            configDeleted = configFile.delete();
        }

        if (fileDeleted || configDeleted) {
            try {
                feature.loadData();
                sender.sendMessage("§aSuccessfully deleted structure '" + name + "' from files and reloaded memory.");
            } catch (IOException e) {
                sender.sendMessage("§eStructure files deleted, but failed to reload internal data: " + e.getMessage());
            }
        } else {
            sender.sendMessage("§cFailed to delete files for structure '" + name + "'.");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleInfo(CommandSourceStack source, String name) {
        CommandSender sender = source.getSender();
        StructureConfig config = feature.getConfig(name);

        if (config == null) {
            sender.sendMessage("§cStructure '" + name + "' configuration is not loaded.");
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage("§6=== Structure Info: " + name + " ===");
        sender.sendMessage("§eEnabled: " + (config.isEnabled() ? "§aYes" : "§cNo"));
        sender.sendMessage("§eChance: §f" + config.getChance());
        sender.sendMessage("§eAttempts: §f" + config.getAttempts());
        sender.sendMessage("§eHeight Range (Y): §f" + config.getMinY() + " -> " + config.getMaxY());
        sender.sendMessage("§eOffset Y: §f" + config.getOffsetY());
        sender.sendMessage("§eAllowed Ground: §f" + String.join(", ", config.getAllowedGround()));
        sender.sendMessage("§eWorlds: §f" + String.join(", ", config.getAllowedWorlds()));
        sender.sendMessage("§ePrevent Overlap: §f" + config.isPreventOverlap());
        return Command.SINGLE_SUCCESS;
    }

    private int handlePatchDelete(CommandSourceStack source, String patchName) {
        CommandSender sender = source.getSender();
        File patchesDir = new File(feature.getLinkFolder(), "patches");
        File patchFile = new File(patchesDir, patchName + ".yml");

        if (!patchFile.exists()) {
            sender.sendMessage("§cMulti-Patch '" + patchName + "' does not exist.");
            return Command.SINGLE_SUCCESS;
        }

        if (patchFile.delete()) {
            try {
                feature.loadData();
                sender.sendMessage("§aSuccessfully deleted multi-patch '" + patchName + ".yml' and reloaded memory.");
            } catch (IOException e) {
                sender.sendMessage("§ePatch file deleted, but failed to reload internal data: " + e.getMessage());
            }
        } else {
            sender.sendMessage("§cFailed to delete file for patch '" + patchName + "'.");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int handlePatchInfo(CommandSourceStack source, String patchName) {
        CommandSender sender = source.getSender();
        MultiPatchConfig config = feature.getMultiPatchConfig(patchName);

        if (config == null) {
            sender.sendMessage("§cMulti-Patch '" + patchName + "' configuration is not loaded.");
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage("§6=== Multi-Patch Info: " + patchName + " ===");
        sender.sendMessage("§eEnabled: " + (config.isEnabled() ? "§aYes" : "§cNo"));
        sender.sendMessage("§eChance: §f" + config.getChance());
        sender.sendMessage("§eAttempts: §f" + config.getAttempts());
        sender.sendMessage("§eHeight Range (Y): §f" + config.getMinY() + " -> " + config.getMaxY());
        sender.sendMessage("§eAllowed Ground: §f" + String.join(", ", config.getAllowedGround()));
        sender.sendMessage("§eWorlds: §f" + String.join(", ", config.getAllowedWorlds()));

        var patchSettings = config.getPatchConfig();
        if (patchSettings != null) {
            sender.sendMessage("§6-- Inner Patch Settings --");
            sender.sendMessage(" §ePatch Enabled: §f" + patchSettings.enabled());
            sender.sendMessage(" §eSpawns Range: §f" + patchSettings.minSpawns() + " -> " + patchSettings.maxSpawns());
            sender.sendMessage(" §eDistance Range: §f" + patchSettings.minDist() + " -> " + patchSettings.maxDist());
        }

        if (!config.getSchematics().isEmpty()) {
            sender.sendMessage("§6-- Tied Schematics --");
            config.getSchematics().forEach(entry ->
                    sender.sendMessage(" §e• " + entry.getName() + " §7(Weight: " + entry.getWeight() + ")")
            );
        }
        return Command.SINGLE_SUCCESS;
    }

    private int handlePatchCreate(CommandSourceStack source, String patchName, String arguments) {
        CommandSender sender = source.getSender();

        File patchesDir = new File(feature.getLinkFolder(), "patches");
        if (!patchesDir.exists()) patchesDir.mkdirs();

        File patchFile = new File(patchesDir, patchName + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("enabled", true);
        yaml.set("worlds", Collections.emptyList());
        yaml.set("chance", 0.05);
        yaml.set("attempts", 1);
        yaml.set("min_y", 60);
        yaml.set("max_y", 90);
        yaml.set("offset_y", 0);
        yaml.set("allowed_ground", List.of("minecraft:grass_block"));
        yaml.set("rotation", "RANDOM");
        yaml.set("mirror", "NONE");
        yaml.set("override_air", true);
        yaml.set("prevent_overlap", true);

        var patchSection = yaml.createSection("patch");
        patchSection.set("enabled", true);
        patchSection.set("min-spawns", 2);
        patchSection.set("max-spawns", 5);
        patchSection.set("min-distance", 3);
        patchSection.set("max-distance", 12);

        yaml.createSection("biomes");

        String[] tokens = arguments.split("\\s+");
        for (String t : tokens) {
            if (!t.contains("=")) continue;
            String[] kv = t.split("=", 2);
            String key = kv[0].toLowerCase().trim();
            String val = kv[1].trim();

            try {
                parsePatchKV(yaml, patchSection, key, val);
            } catch (Exception e) {
                sender.sendMessage("§e[Warning] Failed parsing config argument '" + t + "': " + e.getMessage());
            }
        }

        try {
            yaml.save(patchFile);
            MultiPatchConfig config = MultiPatchConfig.load(patchFile, patchName, feature.getPlugin().getLogger());
            feature.registerMultiPatchRuntime(patchName, config);
            sender.sendMessage("§aMulti-Patch template '" + patchName + ".yml' created successfully!");
        } catch (IOException e) {
            sender.sendMessage("§cCould not save multi-patch layout file: " + e.getMessage());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleStats(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        sender.sendMessage("§6=== WorldGen Feature Statistics ===");
        var stats = feature.getPlacementStats();
        if (stats.isEmpty()) {
            sender.sendMessage("§7No runtime chunk placements have occurred yet since boot.");
            return Command.SINGLE_SUCCESS;
        }
        stats.forEach((key, count) -> sender.sendMessage("§e - " + key + ": §f" + count + " spawns"));
        return Command.SINGLE_SUCCESS;
    }

    private int handleReload(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        try {
            feature.loadData();
            sender.sendMessage("§aWorldGen configurations and schematics reloaded successfully!");
        } catch (Exception e) {
            sender.sendMessage("§cFailed reloading datasets: " + e.getMessage());
        }
        return Command.SINGLE_SUCCESS;
    }

    private int handleBlacklistToggle(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can toggle block blacklist picker mode.");
            return Command.SINGLE_SUCCESS;
        }

        boolean active = feature.toggleBlacklistMode(player.getUniqueId());
        if (active) {
            player.sendMessage("§a[WorldGen] Blacklist setup mode enabled. Right-click any block to toggle it in the global blacklist.");
        } else {
            player.sendMessage("§c[WorldGen] Blacklist setup mode disabled.");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int handleBlacklistList(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        sender.sendMessage("§6=== Globally Blacklisted Blocks ===");
        List<String> blacklist = feature.getFeatureConfig().getStringList("blacklisted-blocks");
        if (blacklist.isEmpty()) {
            sender.sendMessage("§7The blacklist is currently empty.");
            return Command.SINGLE_SUCCESS;
        }
        for (String blockKey : blacklist) {
            sender.sendMessage("§e - " + blockKey);
        }
        return Command.SINGLE_SUCCESS;
    }

    private void parsePatchKV(YamlConfiguration yaml, org.bukkit.configuration.ConfigurationSection patch, String key, String val) {
        switch (key) {
            case "chance" -> yaml.set("chance", Double.parseDouble(val));
            case "attempts" -> yaml.set("attempts", Integer.parseInt(val));
            case "min_y" -> yaml.set("min_y", Integer.parseInt(val));
            case "max_y" -> yaml.set("max_y", Integer.parseInt(val));
            case "offset_y" -> yaml.set("offset_y", Integer.parseInt(val));
            case "override_air" -> yaml.set("override_air", Boolean.parseBoolean(val));
            case "prevent_overlap" -> yaml.set("prevent_overlap", Boolean.parseBoolean(val));
            case "min-spawns" -> patch.set("min-spawns", Integer.parseInt(val));
            case "max-spawns" -> patch.set("max-spawns", Integer.parseInt(val));
            case "min-distance" -> patch.set("min-distance", Integer.parseInt(val));
            case "max-distance" -> patch.set("max-distance", Integer.parseInt(val));
            case "allowed_ground" -> {
                List<String> list = new ArrayList<>();
                for (String s : val.split(",")) if (!s.isBlank()) list.add(s.trim());
                yaml.set("allowed_ground", list);
            }
            case "schematics" -> {
                List<java.util.Map<String, Object>> schematicList = new ArrayList<>();
                for (String entry : val.split(",")) {
                    String[] parts = entry.trim().split(":", 2);
                    if (parts[0].isBlank()) continue;
                    java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("name", parts[0].trim());
                    if (parts.length == 2) {
                        try { map.put("weight", Integer.parseInt(parts[1].trim())); }
                        catch (NumberFormatException ignored) { map.put("weight", 1); }
                    } else {
                        map.put("weight", 1);
                    }
                    schematicList.add(map);
                }
                yaml.set("schematics", schematicList);
            }
        }
    }
}