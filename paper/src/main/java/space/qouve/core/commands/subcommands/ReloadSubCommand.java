package space.qouve.core.commands.subcommands;

import com.google.auto.service.AutoService;
import org.bukkit.command.CommandSender;
import space.qouve.core.CrayonUtil;
import space.qouve.core.models.Feature;
import space.qouve.core.models.SubCommand;
import space.qouve.core.models.SubCommandProvider;

import java.util.ArrayList;
import java.util.List;

public class ReloadSubCommand extends SubCommand {

    public ReloadSubCommand(CrayonUtil plugin) {
        super("reload", plugin);
    }

    @Override
    public void run(CommandSender sender, String[] args) {
        if (!sender.hasPermission("CrayonUtil.reload")) {
            sender.sendMessage(CrayonUtil.getLanguageService().get("no_permission"));
            return;
        }

        var featureService = CrayonUtil.getFeatureService();

        if (args.length > 0) {
            String featureId = args[0].toLowerCase();
            Feature feature = featureService.getRegisteredFeatures().get(featureId);

            if (feature != null) {
                feature.reloadConfig();

                featureService.reloadSingleFeature(featureId);

                feature.onFeatureReload();

                sender.sendMessage("§a[CrayonUtil] Das Feature '" + feature.getId() + "' wurde erfolgreich neu geladen!");
            } else {
                sender.sendMessage("§c[CrayonUtil] Das Feature '" + featureId + "' existiert nicht.");
            }
        } else {
            sender.sendMessage(CrayonUtil.getLanguageService().get("reload.all"));
            plugin.reload();

            featureService.reloadAllFeatures();
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String currentArg = args[0].toLowerCase();

            for (String featureId : CrayonUtil.getFeatureService().getRegisteredFeatureIds()) {
                if (featureId.startsWith(currentArg)) {
                    completions.add(featureId);
                }
            }
            return completions;
        }
        return List.of();
    }

    @AutoService(SubCommandProvider.class)
    public static class Provider implements SubCommandProvider {
        @Override
        public SubCommand createInstance(CrayonUtil plugin) {
            return new ReloadSubCommand(plugin);
        }
    }
}