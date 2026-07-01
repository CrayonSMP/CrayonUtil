package space.qouve.core;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import space.qouve.core.commands.CrayonUtilCommand;
import space.qouve.core.services.FeatureService;
import space.qouve.core.services.LanguageService;

import java.util.List;

public class CrayonUtil extends JavaPlugin {
    private static CrayonUtil instance;
    private static LanguageService languageService;
    private static FeatureService featureService;

    @Override
    public void onEnable() {
        instance = this;
        long currentTime = System.currentTimeMillis();

        saveDefaultConfig();
        reloadConfig();

        Bukkit.getLogger().info("Loading features...");
        languageService = new LanguageService(getConfig().getString("language", "en"), this);
        featureService = new FeatureService(this);
        featureService.discoverAndLoadFeatures();

        final CrayonUtilCommand executor = new CrayonUtilCommand(this);

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final com.mojang.brigadier.CommandDispatcher<io.papermc.paper.command.brigadier.CommandSourceStack> dispatcher = event.registrar().getDispatcher();

            // Hauptbefehl registrieren
            dispatcher.register(
                    io.papermc.paper.command.brigadier.Commands.literal("crayonutil")
                            .requires(source -> true)
                            .executes(ctx -> {
                                executor.onCommand(ctx.getSource().getSender(), null, "crayonutil", new String[0]);
                                return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                            })
                            .then(io.papermc.paper.command.brigadier.Commands.argument("args", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                    .suggests((ctx, builder) -> {
                                        String[] args;
                                        try {
                                            String input = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "args");
                                            args = input.split(" ", -1);
                                        } catch (IllegalArgumentException e) {
                                            args = new String[]{""};
                                        }

                                        List<String> completions = executor.onTabComplete(ctx.getSource().getSender(), null, "crayonutil", args);
                                        if (completions != null) {
                                            completions.forEach(builder::suggest);
                                        }
                                        return builder.buildFuture();
                                    })
                                    .executes(ctx -> {
                                        String input = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "args");
                                        String[] args = input.split(" ");

                                        executor.onCommand(ctx.getSource().getSender(), null, "crayonutil", args);
                                        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                                    })
                            )
            );

            for (String alias : List.of("cu", "crayon")) {
                dispatcher.register(
                        io.papermc.paper.command.brigadier.Commands.literal(alias)
                                .redirect(dispatcher.getRoot().getChild("crayonutil"))
                );
            }
        });

        Bukkit.getLogger().info("Plugin loaded in " + (System.currentTimeMillis() - currentTime) + " ms");
    }

    @Override
    public void onDisable() {

    }

    public void reload() {
        reloadConfig();
        languageService.reloadLanguage(getConfig().getString("language", "en"));
        featureService.reloadAllFeatures();
    }

    public static CrayonUtil getInstance() {
        return instance;
    }

    public static LanguageService getLanguageService() {
        return languageService;
    }

    public static FeatureService getFeatureService() {
        return featureService;
    }
}