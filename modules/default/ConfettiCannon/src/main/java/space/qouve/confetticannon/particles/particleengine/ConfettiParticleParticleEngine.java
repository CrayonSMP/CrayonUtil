package space.qouve.confetticannon.particles.particleengine;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import space.qouve.confetticannon.ConfettiCannon;
import space.qouve.confetticannon.particles.ConfettiSpawner;
import space.qouve.particleengine.data.Direction;
import space.qouve.particleengine.data.gradient.GradientColor;
import space.qouve.particleengine.data.gradient.GradientVector;
import space.qouve.particleengine.data.gradient.RangedGradientVector;
import space.qouve.particleengine.data.keyframe.ValueRange;
import space.qouve.particleengine.emitter.ParticleEmitterBurst;
import space.qouve.particleengine.particlebuilder.ParticleBuilder;
import space.qouve.particleengine.particlebuilder.ParticleBuilderCube;
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

    /**
     * Spawns a burst of colored confetti in front of the player.
     *
     * @param player            the player to spawn the confetti in front of
     * @param location          the spawn location
     * @param baseItemTemplate  optional custom item to render each confetti piece with. Pass
     *                          {@code null} to use the engine's built-in tinted confetti cube,
     *                          which is guaranteed to render in color with no extra setup.
     *                          If you supply your own item, its model MUST define a
     *                          {@code minecraft:potion} tint layer (via a resource pack) for
     *                          colors to actually show - the engine tints items through the
     *                          {@code POTION_CONTENTS} data component now, not leather dye.
     */
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

        // Tuned for a floaty paper-confetti feel rather than a cannon-fired projectile:
        // slow launch speed, light gravity, strong drag so it decelerates into a gentle drift,
        // and noticeable flutter so it doesn't fall in a straight line.
        GravityConfettiParticleEngine physics = new GravityConfettiParticleEngine()
                .initialSpeed(4.0, 7.0)
                .gravityStrength(0.02)
                .dragMultiplier(0.90)
                .flutterStrength(2.5)
                .restingTicks(100);

        int colorCount = confettiColors.size();
        int particlesPerColor = 150 / colorCount;
        int remainder = 150 % colorCount;

        for (int c = 0; c < colorCount; c++) {
            Color color = confettiColors.get(c);
            int count = particlesPerColor + (c < remainder ? 1 : 0);
            if (count == 0) continue;

            ParticleBuilder builder = buildConfettiParticleBuilder(
                    baseItemTemplate, physics, color, dirMin, dirMax);

            new ParticleEmitterBurst(count, builder, location).start();
        }
    }

    /**
     * Builds the particle builder used to render one color of confetti.
     * <p>
     * Falls back to {@link ParticleBuilderCube} (the engine's built-in flat/cube particle,
     * shipped with a resource pack model wired up to the potion tint) when no custom item is
     * given, since that's the only rendering path guaranteed to show color without extra
     * resource pack work on your end.
     */
    private ParticleBuilder buildConfettiParticleBuilder(ItemStack baseItemTemplate,
                                                         GravityConfettiParticleEngine physics,
                                                         Color color,
                                                         Direction dirMin,
                                                         Direction dirMax) {
        // Flatten one axis so the cube reads as a small paper flake instead of a little
        // block, and give each particle a random starting orientation plus a continuous
        // random tumble so it flutters as it falls - this is what sells the confetti look.
        Vector flakeScale = new Vector(0.35, 0.35, 0.05);
        ValueRange<Vector> randomStartOrientation =
                new ValueRange<>(new Vector(0, 0, 0), new Vector(360, 360, 360));
        RangedGradientVector randomTumble =
                new RangedGradientVector(new Vector(-260, -260, -260), new Vector(260, 260, 260));

        if (baseItemTemplate != null) {
            return new ParticleBuilderCustomItem()
                    .gravity(physics.clone())
                    .item(baseItemTemplate.clone())
                    .colorOverLifetime(new GradientColor(color))
                    .particleLifeTicks(new ValueRange<>(200, 300))
                    .scaleOverLifetime(new GradientVector(flakeScale))
                    .initialRotation(randomStartOrientation)
                    .rotationSpeedOverLifetime(randomTumble)
                    .initialMovementDirection(new ValueRange<>(dirMin, dirMax))
                    .velocityOverridesRotation(false);
        }

        return new ParticleBuilderCube()
                .gravity(physics.clone())
                .shaded(false)
                .colorOverLifetime(new GradientColor(color))
                .particleLifeTicks(new ValueRange<>(200, 300))
                .scaleOverLifetime(new GradientVector(flakeScale))
                .initialRotation(randomStartOrientation)
                .rotationSpeedOverLifetime(randomTumble)
                .initialMovementDirection(new ValueRange<>(dirMin, dirMax))
                .velocityOverridesRotation(false);
    }
}