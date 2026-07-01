package space.qouve.confetticannon.particles.particleengine;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.util.Vector;
import space.qouve.confetticannon.ConfettiCannon;
import space.qouve.confetticannon.particles.ConfettiSpawner;
import space.qouve.particleengine.data.Direction;
import space.qouve.particleengine.data.gradient.GradientColor;
import space.qouve.particleengine.data.gradient.GradientVector;
import space.qouve.particleengine.data.keyframe.ValueRange;
import space.qouve.particleengine.emitter.ParticleEmitterBurst;
import space.qouve.particleengine.particlebuilder.ParticleBuilderCustomItem;

import java.util.ArrayList;
import java.util.List;

public class ConfettiParticleParticleEngine implements ConfettiSpawner {

    private static final double SPREAD_ANGLE = 18.0;

    private final List<Color> confettiColors = new ArrayList<>();

    public ConfettiParticleParticleEngine() {
        loadColorsFromConfig();
    }

    private void loadColorsFromConfig() {
        List<String> colorStrings =
                ConfettiCannon.getFeatureConfig().getStringList("confetti-colors");

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
                    java.awt.Color awt = java.awt.Color.decode(colorStr);
                    confettiColors.add(Color.fromRGB(awt.getRed(), awt.getGreen(), awt.getBlue()));
                } catch (NumberFormatException e) {
                    ConfettiCannon.getFeature().debug("Invalid hex code in config: " + colorStr);
                }
            } else {
                Color bukkit = getColorByName(colorStr);
                if (bukkit != null) {
                    confettiColors.add(bukkit);
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
        Vector look = player.getLocation().getDirection();
        double lookYawDeg   = Math.toDegrees(Math.atan2(look.getX(), look.getZ()));
        double lookPitchDeg = Math.toDegrees(Math.asin(
                Math.max(-1.0, Math.min(1.0, look.getY()))  // clamp for safety
        ));

        Direction dirMin = new Direction(
                lookYawDeg   - SPREAD_ANGLE,
                lookPitchDeg - SPREAD_ANGLE * 0.5
        );
        Direction dirMax = new Direction(
                lookYawDeg   + SPREAD_ANGLE,
                lookPitchDeg + SPREAD_ANGLE * 0.5
        );

        GravityConfettiParticleEngine physics = new GravityConfettiParticleEngine()
                .initialSpeed(15.0, 22.5)
                .gravityStrength(0.08)
                .dragMultiplier(0.92)
                .flutterStrength(1.0)
                .restingTicks(140);

        ItemStack renderItem = ensureTintableItem(baseItemTemplate);

        int colorCount = confettiColors.size();
        int particlesPerColor = 150 / colorCount;
        int remainder = 150 % colorCount;

        for (int c = 0; c < colorCount; c++) {
            Color color = confettiColors.get(c);
            int count = particlesPerColor + (c < remainder ? 1 : 0);
            if (count == 0) continue;

            ParticleBuilderCustomItem builder = new ParticleBuilderCustomItem()
                    .gravity(physics.clone())
                    .item(renderItem.clone())
                    .colorOverLifetime(new GradientColor(color))
                    .particleLifeTicks(new ValueRange<>(200, 300))
                    .scaleOverLifetime(new GradientVector(new Vector(0.15, 0.15, 0.15)))
                    .initialMovementDirection(new ValueRange<>(dirMin, dirMax))
                    .velocityOverridesRotation(false);

            new ParticleEmitterBurst(count, builder, location).start();
        }
    }

    private ItemStack ensureTintableItem(ItemStack template) {
        if (template != null && template.getItemMeta() instanceof LeatherArmorMeta) {
            return template.clone();
        }return new ItemStack(Material.LEATHER_CHESTPLATE);
    }
}