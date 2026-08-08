package space.qouve.worldgenfeature.models;

import org.bukkit.configuration.ConfigurationSection;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for a single behavior check (e.g. "min distance", "ground allowed", ...).
 * Every behavior has a unique id and is auto-registered so it can be looked up by that id
 * from configs.
 */
public abstract class WorldGenBehavior {

    private static final Map<String, WorldGenBehavior> REGISTRY = new ConcurrentHashMap<>();

    private final String id;

    protected WorldGenBehavior(String id) {
        this.id = id;

        if (REGISTRY.putIfAbsent(id, this) != null) {
            throw new IllegalStateException("Duplicate WorldGenBehavior id: " + id);
        }
    }

    public String getId() {
        return id;
    }

    /**
     * Runs the actual check. Returns true if the behavior is satisfied.
     */
    public abstract boolean run(WorldGenContext context, ConfigurationSection section) throws IOException;

    /**
     * Optional config validation, called once when the config is loaded.
     * Override to check for required keys / types. Default: no-op.
     */
    public void validate(ConfigurationSection section) {
    }

    // -------------------------------------------------------------------
    // Combinators (similar to java.util.function.Predicate)
    // -------------------------------------------------------------------

    public WorldGenBehavior negate() {
        WorldGenBehavior self = this;
        return new WorldGenBehavior(id + "!negated") {
            @Override
            public boolean run(WorldGenContext context, ConfigurationSection section) throws IOException {
                return !self.run(context, section);
            }
        };
    }

    public WorldGenBehavior and(WorldGenBehavior other) {
        WorldGenBehavior self = this;
        return new WorldGenBehavior(id + "!and!" + other.id) {
            @Override
            public boolean run(WorldGenContext context, ConfigurationSection section) throws IOException {
                return self.run(context, section) && other.run(context, section);
            }
        };
    }

    public WorldGenBehavior or(WorldGenBehavior other) {
        WorldGenBehavior self = this;
        return new WorldGenBehavior(id + "!or!" + other.id) {
            @Override
            public boolean run(WorldGenContext context, ConfigurationSection section) throws IOException {
                return self.run(context, section) || other.run(context, section);
            }
        };
    }

    // -------------------------------------------------------------------
    // Registry access
    // -------------------------------------------------------------------

    public static WorldGenBehavior byId(String id) {
        return REGISTRY.get(id);
    }

    public static Map<String, WorldGenBehavior> all() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    // -------------------------------------------------------------------
    // Identity based on id
    // -------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorldGenBehavior other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "WorldGenBehavior{" + id + "}";
    }
}