package space.qouve.core.services;

import it.sauronsoftware.cron4j.Scheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class RestartService {

    public RestartService(Plugin plugin, LanguageService languageService, FileConfiguration config) {
        if (!config.getBoolean("auto-restart.enabled")) return;

        Scheduler scheduler = new Scheduler();
        scheduler.schedule(config.getString("auto-restart.cron"), () -> {
            new BukkitRunnable() {
                int countdown = 10 * 60;

                @Override
                public void run() {
                    if (countdown <= 0) {
                        Bukkit.getServer().restart();
                        cancel();
                        return;
                    }

                    if (countdown == 600 || countdown == 300 || countdown == 60 || countdown == 30 || countdown <= 10) {
                        String timeStr;
                        Component unitComp;

                        if (countdown >= 60) {
                            int minutes = countdown / 60;
                            timeStr = String.valueOf(minutes);
                            unitComp = languageService.get(minutes == 1 ? "auto_restart.unit.minute" : "auto_restart.unit.minutes");
                        } else {
                            timeStr = String.valueOf(countdown);
                            unitComp = languageService.get(countdown == 1 ? "auto_restart.unit.second" : "auto_restart.unit.seconds");
                        }

                        Component message = languageService.get(
                                "auto_restart.notification",
                                Placeholder.unparsed("time", timeStr),
                                Placeholder.component("unit", unitComp)
                        );

                        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(message));
                    }

                    countdown--;
                }
            }.runTaskTimerAsynchronously(plugin, 0L, 20L);
        });
        scheduler.start();
    }
}