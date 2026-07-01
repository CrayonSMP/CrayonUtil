package space.qouve.confetticannon.listeners;

import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.util.Vector;
import space.qouve.confetticannon.ConfettiCannon;
import space.qouve.confetticannon.particles.ConfettiSpawner;
import space.qouve.confetticannon.particles.particleengine.ConfettiParticleParticleEngine;
import space.qouve.confetticannon.particles.particlelib.ConfettiParticleParticleLib;
import space.qouve.core.utils.*;

import java.util.Objects;

public class ConfettiListener implements Listener {

    private static final Vector UP_VECTOR = new Vector(0, 1, 0);

    private final String cannonItemKey;
    private final String configParticleKey;
    private ItemStack particleItemStack = null;

    // Dynamisches Interface statt fester Klasse
    private final ConfettiSpawner confettiSpawner;

    private final String triggerSoundName;
    private final float triggerVolume;
    private final float triggerPitch;

    private final String breakSoundName;
    private final float breakVolume;
    private final float breakPitch;

    public ConfettiListener() {
        var config = ConfettiCannon.getFeatureConfig();
        this.cannonItemKey = config.getString("cannon-item", "minecraft:crossbow");
        this.configParticleKey = config.getString("particle-item", "minecraft:leather_chestplate");

        // Engine-Mode auslesen und passenden Spawner zuweisen
        String mode = config.getString("engine-mode", "MODERN").toUpperCase();
        if (mode.equals("LEGACY") || mode.equals("PARTICLELIB")) {
            this.confettiSpawner = new ConfettiParticleParticleLib();
            ConfettiCannon.getFeature().getPlugin().getLogger().info("[ConfettiCannon] Using LEGACY (ParticleLib) engine.");
        } else {
            this.confettiSpawner = new ConfettiParticleParticleEngine();
            ConfettiCannon.getFeature().getPlugin().getLogger().info("[ConfettiCannon] Using MODERN (ParticleEngine) engine.");
        }

        this.triggerSoundName = config.getString("trigger-sound.sound", "block.note_block.pling");
        this.triggerVolume = (float) config.getDouble("trigger-sound.volume", 1.0);
        this.triggerPitch = (float) config.getDouble("trigger-sound.pitch", 1.0);

        this.breakSoundName = config.getString("break-sound.sound", "entity.item.break");
        this.breakVolume = (float) config.getDouble("break-sound.volume", 1.0);
        this.breakPitch = (float) config.getDouble("break-sound.pitch", 1.0);

        tryToLoadParticleItem();
    }

    private void tryToLoadParticleItem() {
        try {
            Key itemKey = Key.of(this.configParticleKey);
            this.particleItemStack = ItemHelper.create(itemKey, 1);
        } catch (Exception e) {
            // Ignorieren, wird im Event abgefangen
        }
    }

    private void debug(String message) {
        ConfettiCannon.getFeature().debug(message);
    }

    @EventHandler
    public void onPlayerInteract(final PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }

        if (!Objects.equals(ItemHelper.getKey(item), this.cannonItemKey)) {
            return;
        }

        if (player.hasCooldown(item)) return;

        if (this.particleItemStack == null) {
            tryToLoadParticleItem();

            if (this.particleItemStack == null) {
                debug("ERROR: Could not load particle item: " + this.configParticleKey + ". Make sure the item key is correct!");
                return;
            }
        }

        if (item.getItemMeta() instanceof Damageable damageable) {
            int maxDurability = item.getType().getMaxDurability();

            if (maxDurability > 0) {
                int newDurability = damageable.getDamage() + 1;

                if (newDurability >= maxDurability) {
                    debug("Item " + item.getType() + " of player " + player.getName() + " has no durability and was removed.");
                    item.setAmount(0);
                    player.playSound(player.getLocation(), breakSoundName, breakVolume, breakPitch);
                } else {
                    damageable.setDamage(newDurability);
                    item.setItemMeta(damageable);
                }
            }
        }

        Location spawnLoc = player.getEyeLocation();
        Vector direction = spawnLoc.getDirection().normalize();
        Vector right = direction.clone().crossProduct(UP_VECTOR).normalize();

        spawnLoc.add(direction.multiply(0.5));
        spawnLoc.add(right.multiply(0.25));
        spawnLoc.subtract(0, 0.3, 0);

        player.setCooldown(item.getType(), ConfettiCannon.getFeatureConfig().getInt("cooldown", 5) * 20);

        player.playSound(spawnLoc, triggerSoundName, triggerVolume, triggerPitch);

        // Nutzt jetzt das polymorphe Interface
        confettiSpawner.spawnConfettiShower(player, spawnLoc, this.particleItemStack);
    }
}