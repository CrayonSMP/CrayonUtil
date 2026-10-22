package space.qouve.worldmanager.listeners;

import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.WorldManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import space.qouve.core.utils.ItemHelper;
import space.qouve.worldmanager.services.BorderManager;

import java.util.Objects;

public class PlayerFirstJoinListener implements Listener {

    private final BorderManager borderManager;

    public PlayerFirstJoinListener(BorderManager borderManager) {
        this.borderManager = borderManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (borderManager.getStartDate().isAfter(borderManager.getStartDate().plusDays(1))) return;

        if (!player.hasPlayedBefore()) {
            borderManager.getStartItemKeys().forEach(itemKey -> {
                player.give(Objects.requireNonNull(ItemHelper.create(Key.of(itemKey), 1)));
            });
        }
    }
}
