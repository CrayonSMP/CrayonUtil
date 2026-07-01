package space.qouve.confetticannon.particles.particlelib;

import me.pm7.particlelib.data.keyframe.ValueRange;
import me.pm7.particlelib.emitter.ParticleEmitterBurst;
import me.pm7.particlelib.particlebuilder.ParticleBuilderCustomItem;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.util.Vector;
import space.qouve.confetticannon.ConfettiCannon;
import space.qouve.confetticannon.particles.ConfettiSpawner;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ConfettiParticleParticleLib implements ConfettiSpawner {

    private final Random random = new Random();

    private final List<Color> confettiColors = new ArrayList<>();

    public ConfettiParticleParticleLib() {
        loadColorsFromConfig();
    }

    private void loadColorsFromConfig() {
        List<String> colorStrings = ConfettiCannon.getFeatureConfig().getStringList("confetti-colors");

        if (colorStrings.isEmpty()) {
            confettiColors.add(Color.RED);
            confettiColors.add(Color.BLUE);
            confettiColors.add(Color.GREEN);
            return;
        }

        for (String colorStr : colorStrings) {
            colorStr = colorStr.trim();
            if (colorStr.startsWith("#")) {
                try {
                    java.awt.Color awtColor = java.awt.Color.decode(colorStr);
                    confettiColors.add(Color.fromRGB(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue()));
                } catch (NumberFormatException e) {
                    ConfettiCannon.getFeature().debug("Invalid hex code in config: " + colorStr);
                }
            } else {
                Color bukkitColor = getColorByName(colorStr);
                if (bukkitColor != null) {
                    confettiColors.add(bukkitColor);
                } else {
                    ConfettiCannon.getFeature().debug("Invalid color name in config: " + colorStr);
                }
            }
        }

        if (confettiColors.isEmpty()) {
            confettiColors.add(Color.WHITE);
        }
    }

    private Color getColorByName(String name) {
        try {
            return (Color) Color.class.getField(name.toUpperCase()).get(null);
        } catch (Exception e) {
            return null;
        }
    }

    public void spawnConfettiShower(Player player, Location location, ItemStack baseItemTemplate) {
        Vector lookDirection = player.getLocation().getDirection();

        GravityConfettiParticleLib basePhysics = new GravityConfettiParticleLib()
                .initialSpeed(15.0, 22.5)
                .gravityStrength(0.08)
                .dragMultiplier(0.92)
                .flutterStrength(1)
                .restingTicks(140);

        for (int i = 0; i < 150; i++) {
            Color randomColor = confettiColors.get(random.nextInt(confettiColors.size()));
            ItemStack coloredItem = applyColorToItem(baseItemTemplate, randomColor);

            Vector launchVector = lookDirection.clone()
                    .add(new Vector(
                            (random.nextDouble() - 0.5) * 0.4,
                            random.nextDouble() * 0.3,
                            (random.nextDouble() - 0.5) * 0.4
                    ))
                    .normalize();

            GravityConfettiParticleLib particlePhysics = basePhysics.clone().setLaunchDirection(launchVector);

            ParticleBuilderCustomItem builder = new ParticleBuilderCustomItem()
                    .gravity(particlePhysics)
                    .item(coloredItem)
                    .particleLifeTicks(new ValueRange<>(200, 300))
                    .velocityOverridesRotation(false);

            ParticleEmitterBurst emitter = new ParticleEmitterBurst(1, builder, location);
            emitter.start();
        }
    }

    private ItemStack applyColorToItem(ItemStack template, Color color) {
        if (template.getItemMeta() instanceof LeatherArmorMeta meta) {
            ItemStack clonedItem = template.clone();
            LeatherArmorMeta clonedMeta = (LeatherArmorMeta) clonedItem.getItemMeta();
            clonedMeta.setColor(color);
            clonedItem.setItemMeta(clonedMeta);
            return clonedItem;
        }
        return template;
    }
}