package space.qouve.core.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import space.qouve.core.CrayonUtil;
import space.qouve.core.models.SubCommand;
import space.qouve.core.models.SubCommandProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public class CrayonUtilCommand implements CommandExecutor, TabCompleter {

    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public CrayonUtilCommand(CrayonUtil plugin) {
        ClassLoader classLoader = plugin.getClass().getClassLoader();
        ServiceLoader<SubCommandProvider> loader = ServiceLoader.load(SubCommandProvider.class, classLoader);

        java.util.Iterator<SubCommandProvider> iterator = loader.iterator();
        int count = 0;

        while (true) {
            try {
                if (!iterator.hasNext()) {
                    break;
                }
                SubCommandProvider provider = iterator.next();
                SubCommand sub = provider.createInstance(plugin);

                // FIX: Verhindert die NullPointerException, falls ein Provider fehlerhaft ist
                if (sub == null) {
                    plugin.getLogger().warning("[CrayonUtil] Der Provider '" + provider.getClass().getName() + "' hat ein NULL-SubCommand geliefert!");
                    continue;
                }

                subCommands.put(sub.getId().toLowerCase(), sub);
                plugin.getLogger().info("[CrayonUtil] SubCommand automatisch registriert: " + sub.getId());
                count++;
            } catch (Throwable t) {
                plugin.getLogger().severe("[CrayonUtil] Kritischer Fehler beim Laden eines SubCommandProviders!");
                t.printStackTrace();
            }
        }

        if (count == 0) {
            plugin.getLogger().warning("[CrayonUtil] WARNUNG: Keine SubCommands via ServiceLoader gefunden!");
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @Nullable Command cmd, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§cNutze: /" + label + " <subcommand>");
            return true;
        }

        String subCommandKey = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandKey);

        if (subCommand == null) {
            sender.sendMessage("§cBefehl nicht gefunden.");
            return true;
        }

        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, args.length - 1);

        subCommand.run(sender, subArgs);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @Nullable Command cmd, @NotNull String label, @NotNull String[] args) {
        if (subCommands.isEmpty()) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String currentArg = args[0].toLowerCase();

            for (String subName : subCommands.keySet()) {
                if (subName.startsWith(currentArg)) {
                    completions.add(subName);
                }
            }
            return completions;
        }

        if (args.length > 1) {
            String subCommandKey = args[0].toLowerCase();
            SubCommand subCommand = subCommands.get(subCommandKey);

            if (subCommand != null) {
                String[] subArgs = new String[args.length - 1];
                System.arraycopy(args, 1, subArgs, 0, args.length - 1);

                List<String> subCompletions = subCommand.onTabComplete(sender, subArgs);

                return subCompletions != null ? subCompletions : List.of();
            }
        }

        return List.of();
    }
}