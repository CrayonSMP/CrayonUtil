package space.qouve.confetticannon.particles;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface ConfettiSpawner {
    void spawnConfettiShower(Player player, Location location, ItemStack baseItemTemplate);
}