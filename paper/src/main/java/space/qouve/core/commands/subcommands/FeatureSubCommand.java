package space.qouve.core.commands.subcommands;

import com.google.auto.service.AutoService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import space.qouve.core.models.FeatureCategorry;
import org.bukkit.command.CommandSender;
import space.qouve.core.CrayonUtil;
import space.qouve.core.models.Feature;
import space.qouve.core.models.SubCommand;
import space.qouve.core.models.SubCommandProvider;
import space.qouve.core.services.FeatureService;
import space.qouve.core.services.LanguageService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FeatureSubCommand extends SubCommand {

    public FeatureSubCommand(CrayonUtil plugin) {
        super("feature", plugin);
    }

    @Override
    public void run(CommandSender sender, String[] args) {
        LanguageService lang = CrayonUtil.getLanguageService();

        if (!sender.hasPermission("CrayonUtil.feature")) {
            sender.sendMessage(lang.get("no_permission"));
            return;
        }

        if (args.length == 0) {
            sendHelp(sender, lang);
            return;
        }

        String action = args[0].toLowerCase();
        FeatureService service = CrayonUtil.getFeatureService();

        if (action.equals("list")) {
            Map<String, Feature> registered = service.getRegisteredFeatures();

            sender.sendMessage(lang.get("feature.header"));

            Map<FeatureCategorry, List<Feature>> categorized = new java.util.HashMap<>();
            for (Feature f : registered.values()) {
                categorized.computeIfAbsent(f.getFeatureCategorry(), k -> new java.util.ArrayList<>()).add(f);
            }

            for (Map.Entry<FeatureCategorry, List<Feature>> entry : categorized.entrySet()) {
                FeatureCategorry category = entry.getKey();
                List<Feature> featuresInCat = entry.getValue();

                TextComponent.Builder rowBuilder = Component.text();

                rowBuilder.append(Component.text(category.name() + ": ", NamedTextColor.GRAY));

                for (int i = 0; i < featuresInCat.size(); i++) {
                    Feature f = featuresInCat.get(i);
                    String id = f.getId();

                    boolean isActive = service.isFeatureActive(id);
                    boolean isConfigEnabled = plugin.getConfig().getBoolean("features." + f.getFeatureCategorry().name().toLowerCase() + "." + id, false);
                    boolean debug = f.getConfig() != null && f.getConfig().getBoolean("debug", false);

                    TextColor featureColor = isActive ? NamedTextColor.GREEN : NamedTextColor.RED;

                    Component featureNode = Component.text(f.getPrefix())
                            .color(featureColor)
                            .hoverEvent(HoverEvent.showText(Component.text()
                                    .append(Component.text("=== Feature Info ===", NamedTextColor.GOLD)).append(Component.newline())
                                    .append(Component.text("ID: ", NamedTextColor.GRAY)).append(Component.text(id, NamedTextColor.WHITE)).append(Component.newline())
                                    .append(Component.text("Kategorie: ", NamedTextColor.GRAY)).append(Component.text(category.name(), NamedTextColor.WHITE)).append(Component.newline())
                                    .append(Component.text("Prefix: ", NamedTextColor.GRAY)).append(Component.text(f.getPrefix(), NamedTextColor.WHITE)).append(Component.newline())
                                    .append(Component.newline())
                                    .append(Component.text("Status (Aktiv): ", NamedTextColor.GRAY)).append(isActive ? Component.text("✔ JA", NamedTextColor.GREEN) : Component.text("✘ NEIN", NamedTextColor.RED)).append(Component.newline())
                                    .append(Component.text("In Config aktiv: ", NamedTextColor.GRAY)).append(isConfigEnabled ? Component.text("✔ JA", NamedTextColor.GREEN) : Component.text("✘ NEIN", NamedTextColor.RED)).append(Component.newline())
                                    .append(Component.text("Debug-Modus: ", NamedTextColor.GRAY)).append(debug ? Component.text("AN", NamedTextColor.GREEN) : Component.text("AUS", NamedTextColor.DARK_GRAY)).append(Component.newline())
                                    .build()
                            ));

                    rowBuilder.append(featureNode);
                    if (i < featuresInCat.size() - 1) {
                        rowBuilder.append(Component.text(", ", NamedTextColor.GRAY));
                    }
                }

                sender.sendMessage(rowBuilder.build());
            }
            return;
        }

        // Arguments checks for single-feature manipulation
        if (args.length < 2) {
            sender.sendMessage(lang.get("feature.specify_id"));
            return;
        }

        String featureId = args[1].toLowerCase();
        Map<String, Feature> registered = service.getRegisteredFeatures();

        if (!registered.containsKey(featureId)) {
            sender.sendMessage(lang.get("feature.not_found", Placeholder.parsed("id", featureId)));
            return;
        }

        // Feature-Objekt holen, um die Kategorie auszulesen
        Feature targetFeature = registered.get(featureId);
        String categoryName = targetFeature.getFeatureCategorry().name().toLowerCase();

        switch (action) {
            case "enable" -> {
                plugin.getConfig().set("features." + categoryName + "." + featureId, true);
                plugin.saveConfig();
                if (service.loadSingleFeature(featureId)) {
                    sender.sendMessage(lang.get("feature.enabled_permanent", Placeholder.parsed("id", featureId)));
                } else {
                    sender.sendMessage(lang.get("feature.enabled_already", Placeholder.parsed("id", featureId)));
                }
            }
            case "disable" -> {
                plugin.getConfig().set("features." + categoryName + "." + featureId, false);
                plugin.saveConfig();
                if (service.unloadSingleFeature(featureId)) {
                    sender.sendMessage(lang.get("feature.disabled_permanent", Placeholder.parsed("id", featureId)));
                } else {
                    sender.sendMessage(lang.get("feature.disabled_already", Placeholder.parsed("id", featureId)));
                }
            }
            case "load" -> {
                if (service.loadSingleFeature(featureId)) {
                    sender.sendMessage(lang.get("feature.loaded_temporary", Placeholder.parsed("id", featureId)));
                } else {
                    sender.sendMessage(lang.get("feature.loaded_failed", Placeholder.parsed("id", featureId)));
                }
            }
            case "unload" -> {
                if (service.unloadSingleFeature(featureId)) {
                    sender.sendMessage(lang.get("feature.unloaded_temporary", Placeholder.parsed("id", featureId)));
                } else {
                    sender.sendMessage(lang.get("feature.unloaded_failed", Placeholder.parsed("id", featureId)));
                }
            }
            default -> sendHelp(sender, lang);
        }
    }

    private void sendHelp(CommandSender sender, LanguageService lang) {
        sender.sendMessage(lang.get("feature.help.header"));
        sender.sendMessage(lang.get("feature.help.list"));
        sender.sendMessage(lang.get("feature.help.enable"));
        sender.sendMessage(lang.get("feature.help.disable"));
        sender.sendMessage(lang.get("feature.help.load"));
        sender.sendMessage(lang.get("feature.help.unload"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String currentArg = args[0].toLowerCase();
            List<String> subActions = List.of("enable", "disable", "load", "unload", "list");
            for (String action : subActions) {
                if (action.startsWith(currentArg)) completions.add(action);
            }
        }
        else if (args.length == 2 && !args[0].equalsIgnoreCase("list")) {
            String currentArg = args[1].toLowerCase();
            FeatureService service = CrayonUtil.getFeatureService();

            for (String featureId : service.getRegisteredFeatureIds()) {
                if (featureId.startsWith(currentArg)) {
                    completions.add(featureId);
                }
            }
        }

        return completions;
    }

    @AutoService(SubCommandProvider.class)
    public static class Provider implements SubCommandProvider {
        @Override
        public SubCommand createInstance(CrayonUtil plugin) {
            return new FeatureSubCommand(plugin);
        }
    }
}