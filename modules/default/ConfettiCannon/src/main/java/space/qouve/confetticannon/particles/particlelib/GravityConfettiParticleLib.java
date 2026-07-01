package space.qouve.confetticannon.particles.particlelib;

import me.pm7.particlelib.data.keyframe.ValueRange;
import me.pm7.particlelib.particle.Particle;
import me.pm7.particlelib.physics.Gravity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Display;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class GravityConfettiParticleLib extends Gravity implements ConfigurationSerializable {

    private static final Random random = new Random();
    private static Plugin pluginInstance = null;

    private static final Vector3f ZERO_VECTOR = new Vector3f(0, 0, 0);
    private static final Quaternionf IDENTITY_QUATERNION = new Quaternionf();
    private static final Transformation RESET_TRANSFORMATION = new Transformation(ZERO_VECTOR, IDENTITY_QUATERNION, ZERO_VECTOR, IDENTITY_QUATERNION);

    private static final Quaternionf ROTATION_90_Z = new Quaternionf().rotationAxis((float) Math.toRadians(90), 0, 0, 1);

    private ValueRange<Double> initialSpeed;
    private double gravityStrength;
    private double dragMultiplier;
    private double flutterStrength;
    private int restingTicks;

    private boolean grounded  = false;
    private boolean firstTick = true;
    private Vector launchDirection = null;
    private int tickCounter = 0;
    private int ticksOnGround = 0;

    private final Vector internalDirection = new Vector();

    public GravityConfettiParticleLib(ValueRange<Double> initialSpeed, double gravityStrength,
                                      double dragMultiplier, double flutterStrength, int restingTicks) {
        this.initialSpeed    = initialSpeed;
        this.gravityStrength = gravityStrength;
        this.dragMultiplier  = dragMultiplier;
        this.flutterStrength = flutterStrength;
        this.restingTicks    = restingTicks;
        ensurePluginInstance();
    }

    public GravityConfettiParticleLib() {
        this(new ValueRange<>(2.0, 4.0), 0.04, 0.97, 0.25, 80);
    }

    @SuppressWarnings("unchecked")
    public GravityConfettiParticleLib(Map<String, Object> map) {
        this.initialSpeed    = (ValueRange<Double>) map.get("initialSpeed");
        this.gravityStrength = (double) map.get("gravityStrength");
        this.dragMultiplier  = (double) map.get("dragMultiplier");
        this.flutterStrength = (double) map.get("flutterStrength");
        this.restingTicks    = (int)    map.get("restingTicks");
        ensurePluginInstance();
    }

    private void ensurePluginInstance() {
        if (pluginInstance == null) {
            Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
            if (plugins.length > 0) {
                pluginInstance = plugins[0];
            }
        }
    }

    @Override
    public void applyGravity(Particle particle, int step) {
        Display display = particle.getDisplay();
        if (display == null || !display.isValid()) return;

        if (grounded) {
            ticksOnGround += step;
            if (ticksOnGround >= restingTicks) {
                display.setTransformation(RESET_TRANSFORMATION);
                if (pluginInstance != null) {
                    Bukkit.getScheduler().runTask(pluginInstance, display::remove);
                } else {
                    display.remove(); // Fallback
                }
            }
            return;
        }

        @NotNull Vector velocity = particle.getVelocity();

        if (firstTick) {
            firstTick = false;
            if (launchDirection != null) {
                velocity.copy(launchDirection).multiply(getInitialSpeed());
            }
            display.setBillboard(Display.Billboard.CENTER);
            display.setBrightness(new Display.Brightness(15, 15));

            Transformation currentTransformation = display.getTransformation();
            display.setTransformation(new Transformation(
                    currentTransformation.getTranslation(),
                    ROTATION_90_Z,
                    currentTransformation.getScale(),
                    currentTransformation.getRightRotation()
            ));

            Location displayLoc = display.getLocation();
            displayLoc.setYaw(0F);
            displayLoc.setPitch(0F);
            display.teleport(displayLoc);
        }

        Location loc = display.getLocation();
        if (loc.getWorld() == null) return;

        double velX = velocity.getX();
        double velY = velocity.getY();
        double velZ = velocity.getZ();

        for (int i = 0; i < step; i++) {
            velY -= gravityStrength;
            velX *= dragMultiplier;
            velY *= dragMultiplier;
            velZ *= dragMultiplier;

            if (flutterStrength > 0) {
                velX += (random.nextDouble() * 2 - 1) * flutterStrength * 0.05;
                velZ += (random.nextDouble() * 2 - 1) * flutterStrength * 0.05;
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
        boolean shouldRayTrace = velocityLengthSq > 0.25 || (tickCounter % 2 == 0 && velocityLengthSq > 1e-7);

        if (shouldRayTrace) {
            double velocityLength = Math.sqrt(velocityLengthSq);

            internalDirection.setX(totalX).setY(totalY).setZ(totalZ).normalize();

            double rayTraceDistance = velocityLength + (particle.getSize() * 0.5);
            if (rayTraceDistance > 6.0) rayTraceDistance = 6.0;

            try {
                var hit = loc.getWorld().rayTraceBlocks(loc, internalDirection, rayTraceDistance);

                if (hit != null && hit.getHitBlock() != null && hit.getHitBlockFace() != null) {
                    Vector hitPos = hit.getHitPosition();
                    Vector normal = hit.getHitBlockFace().getDirection();
                    double offset = particle.getSize() * 0.35;

                    loc.setX(hitPos.getX() + normal.getX() * offset);
                    loc.setY(hitPos.getY() + normal.getY() * offset);
                    loc.setZ(hitPos.getZ() + normal.getZ() * offset);

                    velocity.zero();
                    grounded = true;
                    particle.teleport(loc);
                    return;
                }
            } catch (Exception e) {

            }
        }

        if (velocityLengthSq > 1e-6) {
            loc.add(totalX, totalY, totalZ);
            particle.teleport(loc);
        }
    }

    @Override
    public double getInitialSpeed() {
        return random.nextDouble() * (initialSpeed.getV2() - initialSpeed.getV1()) + initialSpeed.getV1();
    }

    @Override
    public GravityConfettiParticleLib clone() {
        GravityConfettiParticleLib c = new GravityConfettiParticleLib(initialSpeed, gravityStrength, dragMultiplier, flutterStrength, restingTicks);
        c.launchDirection = this.launchDirection;
        return c;
    }

    public GravityConfettiParticleLib setLaunchDirection(Vector direction) { this.launchDirection = direction.clone().normalize(); return this; }
    public GravityConfettiParticleLib initialSpeed(double min, double max)  { this.initialSpeed = new ValueRange<>(min, max); return this; }
    public GravityConfettiParticleLib initialSpeed(double speed)            { this.initialSpeed = new ValueRange<>(speed); return this; }
    public GravityConfettiParticleLib initialSpeed(ValueRange<Double> v)    { this.initialSpeed = v; return this; }
    public GravityConfettiParticleLib gravityStrength(double v)             { this.gravityStrength = v; return this; }
    public GravityConfettiParticleLib dragMultiplier(double v)              { this.dragMultiplier = v; return this; }
    public GravityConfettiParticleLib flutterStrength(double v)             { this.flutterStrength = v; return this; }
    public GravityConfettiParticleLib restingTicks(int v)                   { this.restingTicks = v; return this; }
    public boolean isGrounded()                                  { return grounded; }

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