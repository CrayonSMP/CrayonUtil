package space.qouve.confetticannon.particles.particleengine;

import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import space.qouve.particleengine.data.keyframe.ValueRange;
import space.qouve.particleengine.particle.Particle;
import space.qouve.particleengine.physics.Gravity;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;


public class GravityConfettiParticleEngine extends Gravity implements ConfigurationSerializable {

    private static final Random RANDOM = new Random();

    private final Vector internalDirection = new Vector();

    private ValueRange<Double> initialSpeed;
    private double gravityStrength;
    private double dragMultiplier;
    private double flutterStrength;
    private int restingTicks;

    private Vector launchDirection = null;
    private boolean grounded       = false;
    private int ticksOnGround      = 0;
    private int tickCounter        = 0;


    public GravityConfettiParticleEngine(ValueRange<Double> initialSpeed,
                                         double gravityStrength,
                                         double dragMultiplier,
                                         double flutterStrength,
                                         int restingTicks) {
        this.initialSpeed    = initialSpeed;
        this.gravityStrength = gravityStrength;
        this.dragMultiplier  = dragMultiplier;
        this.flutterStrength = flutterStrength;
        this.restingTicks    = restingTicks;
    }

    public GravityConfettiParticleEngine() {
        this(new ValueRange<>(2.0, 4.0), 0.04, 0.97, 0.25, 80);
    }

    @SuppressWarnings("unchecked")
    public GravityConfettiParticleEngine(Map<String, Object> map) {
        this.initialSpeed    = (ValueRange<Double>) map.get("initialSpeed");
        this.gravityStrength = (double) map.get("gravityStrength");
        this.dragMultiplier  = (double) map.get("dragMultiplier");
        this.flutterStrength = (double) map.get("flutterStrength");
        this.restingTicks    = (int)    map.get("restingTicks");
    }

    @Override
    public void applyGravity(Particle particle, int step) {
        if (grounded) {
            // Once a piece has landed, just count down until it's time to despawn it
            // instead of waiting out its full particleLifeTicks range.
            ticksOnGround += step;
            if (ticksOnGround >= restingTicks) {
                particle.remove();
            }
            return;
        }

        Vector velocity = particle.getVelocity();

        double velX = velocity.getX();
        double velY = velocity.getY();
        double velZ = velocity.getZ();

        for (int i = 0; i < step; i++) {
            velY -= gravityStrength;
            velX *= dragMultiplier;
            velY *= dragMultiplier;
            velZ *= dragMultiplier;

            if (flutterStrength > 0) {
                velX += (RANDOM.nextDouble() * 2 - 1) * flutterStrength * 0.05;
                velZ += (RANDOM.nextDouble() * 2 - 1) * flutterStrength * 0.05;
            }
        }

        velocity.setX(velX);
        velocity.setY(velY);
        velocity.setZ(velZ);

        double totalX = velX * 0.05 * step;
        double totalY = velY * 0.05 * step;
        double totalZ = velZ * 0.05 * step;

        double velocityLengthSq = totalX * totalX + totalY * totalY + totalZ * totalZ;

        tickCounter += step;

        boolean shouldRayTrace = velocityLengthSq > 0.25
                || (tickCounter % 2 == 0 && velocityLengthSq > 1e-7);

        Location loc = particle.getLocation();
        if (loc.getWorld() == null) return;

        if (shouldRayTrace) {
            double velocityLength = Math.sqrt(velocityLengthSq);

            internalDirection.setX(totalX).setY(totalY).setZ(totalZ).normalize();

            double rayDistance = Math.min(velocityLength + particle.getSize() * 0.5, 6.0);

            try {
                var hit = loc.getWorld().rayTraceBlocks(loc, internalDirection, rayDistance);

                if (hit != null && hit.getHitBlock() != null && hit.getHitBlockFace() != null) {
                    Vector hitPos = hit.getHitPosition();
                    Vector normal = hit.getHitBlockFace().getDirection();
                    double offset = particle.getSize() * 0.35;

                    loc = loc.clone();
                    loc.setX(hitPos.getX() + normal.getX() * offset);
                    loc.setY(hitPos.getY() + normal.getY() * offset);
                    loc.setZ(hitPos.getZ() + normal.getZ() * offset);

                    velocity.zero();
                    grounded = true;

                    particle.teleport(loc);
                    return;
                }
            } catch (Exception ignored) {

            }
        }

        if (velocityLengthSq > 1e-6) {
            particle.teleport(loc.clone().add(totalX, totalY, totalZ));
        }
    }

    @Override
    public double getInitialSpeed() {
        return RANDOM.nextDouble() * (initialSpeed.getV2() - initialSpeed.getV1())
                + initialSpeed.getV1();
    }


    @Override
    public GravityConfettiParticleEngine clone() {
        GravityConfettiParticleEngine c = new GravityConfettiParticleEngine(initialSpeed, gravityStrength,
                dragMultiplier, flutterStrength, restingTicks);
        c.launchDirection = this.launchDirection;
        return c;
    }

    public GravityConfettiParticleEngine setLaunchDirection(Vector direction) {
        this.launchDirection = direction.clone().normalize();
        return this;
    }

    public Vector getLaunchDirection() {
        return launchDirection;
    }

    public GravityConfettiParticleEngine initialSpeed(double min, double max) {
        this.initialSpeed = new ValueRange<>(min, max);
        return this;
    }

    public GravityConfettiParticleEngine initialSpeed(double speed) {
        this.initialSpeed = new ValueRange<>(speed);
        return this;
    }

    public GravityConfettiParticleEngine initialSpeed(ValueRange<Double> v) {
        this.initialSpeed = v;
        return this;
    }

    public GravityConfettiParticleEngine gravityStrength(double v) {
        this.gravityStrength = v;
        return this;
    }

    public GravityConfettiParticleEngine dragMultiplier(double v) {
        this.dragMultiplier = v;
        return this;
    }

    public GravityConfettiParticleEngine flutterStrength(double v) {
        this.flutterStrength = v;
        return this;
    }

    public GravityConfettiParticleEngine restingTicks(int v) {
        this.restingTicks = v;
        return this;
    }

    public boolean isGrounded() {
        return grounded;
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("type",            "confetti");
        map.put("initialSpeed",    initialSpeed);
        map.put("gravityStrength", gravityStrength);
        map.put("dragMultiplier",  dragMultiplier);
        map.put("flutterStrength", flutterStrength);
        map.put("restingTicks",    restingTicks);
        return map;
    }
}