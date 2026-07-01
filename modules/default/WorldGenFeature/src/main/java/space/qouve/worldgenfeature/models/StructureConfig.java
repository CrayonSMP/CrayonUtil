package space.qouve.worldgenfeature.models;

import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.Yaml;
import space.qouve.worldgenfeature.WorldGenFeature;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class StructureConfig {

    public enum RotationMode {
        NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90, RANDOM;

        public static RotationMode parse(String value, String structureName, Logger log) {
            if (value == null || value.isBlank()) return NONE;
            try {
                return valueOf(value.toUpperCase(Locale.ROOT).trim());
            } catch (IllegalArgumentException e) {
                log.warning("[WorldGen] Unknown rotation '" + value + "' in " + structureName
                        + ". Valid values: " + Arrays.toString(values()) + ". Defaulting to NONE.");
                return NONE;
            }
        }

        public StructureRotation resolve(Random random) {
            if (this == RANDOM) {
                StructureRotation[] all = StructureRotation.values();
                return all[random.nextInt(all.length)];
            }
            return switch (this) {
                case CLOCKWISE_90        -> StructureRotation.CLOCKWISE_90;
                case CLOCKWISE_180       -> StructureRotation.CLOCKWISE_180;
                case COUNTERCLOCKWISE_90 -> StructureRotation.COUNTERCLOCKWISE_90;
                default                  -> StructureRotation.NONE;
            };
        }
    }

    public enum MirrorMode {
        NONE, LEFT_RIGHT, FRONT_BACK, RANDOM;

        public static MirrorMode parse(String value, String structureName, Logger log) {
            if (value == null || value.isBlank()) return NONE;
            try {
                return valueOf(value.toUpperCase(Locale.ROOT).trim());
            } catch (IllegalArgumentException e) {
                log.warning("[WorldGen] Unknown mirror '" + value + "' in " + structureName
                        + ". Valid values: " + Arrays.toString(values()) + ". Defaulting to NONE.");
                return NONE;
            }
        }

        public Mirror resolve(Random random) {
            if (this == RANDOM) {
                Mirror[] all = Mirror.values();
                return all[random.nextInt(all.length)];
            }
            return switch (this) {
                case LEFT_RIGHT -> Mirror.LEFT_RIGHT;
                case FRONT_BACK -> Mirror.FRONT_BACK;
                default         -> Mirror.NONE;
            };
        }
    }

    public record BiomeOverride(
            double chance,              boolean hasChance,
            List<String> allowedGround, boolean hasAllowedGround,
            int minY,                   boolean hasMinY,
            int maxY,                   boolean hasMaxY,
            int offsetY,                boolean hasOffsetY,
            RotationMode rotation,      boolean hasRotation,
            MirrorMode mirror,          boolean hasMirror,
            boolean overrideAir,        boolean hasOverrideAir,
            PatchConfig patch,          boolean hasPatch
    ) {
        public static BiomeOverride from(ConfigurationSection parent, String biomeKey,
                                         String structureName, Logger log) {
            ConfigurationSection s = parent.getConfigurationSection(biomeKey);
            if (s == null) {
                return new BiomeOverride(
                        0, false, Collections.emptyList(), false,
                        0, false, 320, false, 0, false,
                        RotationMode.NONE, false, MirrorMode.NONE, false,
                        true, false, null, false);
            }

            boolean hasChance      = s.contains("chance");
            boolean hasGround      = s.contains("allowed_ground");
            boolean hasMinY        = s.contains("min_y");
            boolean hasMaxY        = s.contains("max_y");
            boolean hasOffsetY     = s.contains("offset_y");
            boolean hasRotation    = s.contains("rotation");
            boolean hasMirror      = s.contains("mirror");
            boolean hasOverrideAir = s.contains("override_air");
            boolean hasPatch       = s.contains("patch");

            return new BiomeOverride(
                    hasChance      ? s.getDouble("chance")                                          : 0,   hasChance,
                    hasGround      ? s.getStringList("allowed_ground")                              : Collections.emptyList(), hasGround,
                    hasMinY        ? s.getInt("min_y")                                              : 0,   hasMinY,
                    hasMaxY        ? s.getInt("max_y")                                              : 320, hasMaxY,
                    hasOffsetY     ? s.getInt("offset_y")                                           : 0,   hasOffsetY,
                    hasRotation    ? RotationMode.parse(s.getString("rotation"), structureName, log): RotationMode.NONE, hasRotation,
                    hasMirror      ? MirrorMode.parse(s.getString("mirror"),     structureName, log): MirrorMode.NONE,   hasMirror,
                    hasOverrideAir ? s.getBoolean("override_air")                                   : true, hasOverrideAir,
                    hasPatch       ? PatchConfig.from(s.getConfigurationSection("patch"))           : null, hasPatch
            );
        }
    }

    private final String structureName;

    private boolean enabled;
    private List<String> allowedWorlds;
    private double chance;
    private int attempts;
    private int minY;
    private int maxY;
    private int offsetY;
    private List<String> allowedGround;
    private RotationMode rotationMode;
    private MirrorMode mirrorMode;
    private boolean preventOverlap;
    private boolean overrideAir;
    private PatchConfig patchConfig;

    private final Map<String, BiomeOverride> biomeOverrides = new HashMap<>();

    private StructureConfig(String structureName) {
        this.structureName = structureName;
    }

    public static StructureConfig load(File file, String structureName, Logger log) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        StructureConfig c = new StructureConfig(structureName);

        c.enabled        = yaml.getBoolean("enabled", false);
        c.allowedWorlds  = yaml.getStringList("worlds");
        c.chance         = clampChance(yaml.getDouble("chance", 0.05), structureName, log);
        c.attempts       = Math.max(1, yaml.getInt("attempts", 1));
        c.minY           = yaml.getInt("min_y", 0);
        c.maxY           = yaml.getInt("max_y", 320);
        c.offsetY        = yaml.getInt("offset_y", 0);
        c.allowedGround  = yaml.getStringList("allowed_ground");
        c.rotationMode   = RotationMode.parse(yaml.getString("rotation", "NONE"), structureName, log);
        c.mirrorMode     = MirrorMode.parse(yaml.getString("mirror", "NONE"), structureName, log);
        c.preventOverlap = yaml.getBoolean("prevent_overlap", true);
        c.overrideAir    = yaml.getBoolean("override_air", true);
        c.patchConfig    = PatchConfig.from(yaml.getConfigurationSection("patch"));

        if (c.minY > c.maxY) {
            WorldGenFeature.getFeature().debug("[WorldGen] " + structureName + ": min_y (" + c.minY
                    + ") > max_y (" + c.maxY + "). No positions will ever pass the Y check.");
        }

        // Doppelpunkt-sicheres Biome-Parsen via SnakeYAML
        try (FileInputStream fis = new FileInputStream(file)) {
            Map<String, Object> raw = new Yaml().load(fis);
            if (raw != null && raw.get("biomes") instanceof Map<?, ?> rawBiomes) {
                ConfigurationSection biomesSection = yaml.getConfigurationSection("biomes");
                for (Object keyObj : rawBiomes.keySet()) {
                    String biomeKey = keyObj.toString().toLowerCase(Locale.ROOT);
                    BiomeOverride override = (biomesSection != null)
                            ? BiomeOverride.from(biomesSection, keyObj.toString(), structureName, log)
                            : new BiomeOverride(0, false, Collections.emptyList(), false,
                            0, false, 320, false, 0, false,
                            RotationMode.NONE, false, MirrorMode.NONE, false,
                            true, false, null, false);
                    c.biomeOverrides.put(biomeKey, override);
                }
            }
        } catch (IOException e) {
            log.warning("[WorldGen] " + structureName + ": Failed to read biome keys: " + e.getMessage());
        }

        return c;
    }

    public boolean isBiomeAllowed(String biome) {
        if (biomeOverrides.isEmpty()) return true;
        return biomeOverrides.containsKey(biome.toLowerCase(Locale.ROOT));
    }

    public double getChance(String biome) {
        BiomeOverride o = biomeOverrides.get(biome.toLowerCase(Locale.ROOT));
        return (o != null && o.hasChance()) ? o.chance() : chance;
    }

    public List<String> getAllowedGround(String biome) {
        BiomeOverride o = biomeOverrides.get(biome.toLowerCase(Locale.ROOT));
        if (o != null && o.hasAllowedGround()) return o.allowedGround();
        return (allowedGround == null || allowedGround.isEmpty()) ? null : allowedGround;
    }

    public StructureRotation resolveRotation(Random random) {
        return rotationMode.resolve(random);
    }

    public Mirror resolveMirror(Random random) {
        return mirrorMode.resolve(random);
    }

    public boolean isWorldAllowed(String worldKey) {
        return allowedWorlds == null || allowedWorlds.isEmpty() || allowedWorlds.contains(worldKey);
    }

    public String toDebugString() {
        return String.format(
                "[%s] enabled=%b worlds=%s chance=%.3f attempts=%d y=%d..%d offset=%d rotation=%s mirror=%s preventOverlap=%b overrideAir=%b biomeOverrides=%s ground=%s",
                structureName, enabled, allowedWorlds, chance, attempts,
                minY, maxY, offsetY, rotationMode, mirrorMode, preventOverlap, overrideAir,
                biomeOverrides.keySet(), allowedGround);
    }

    private static double clampChance(double v, String name, Logger log) {
        if (v < 0 || v > 1) {
            log.warning("[WorldGen] " + name + ": chance " + v + " is outside [0,1]. Clamping.");
            return Math.max(0.0, Math.min(1.0, v));
        }
        return v;
    }

    public String getStructureName()       { return structureName; }
    public boolean isEnabled()             { return enabled; }
    public List<String> getAllowedWorlds()  { return allowedWorlds; }
    public double getChance()              { return chance; }
    public int getAttempts()               { return attempts; }
    public int getMinY()                   { return minY; }
    public int getMaxY()                   { return maxY; }
    public int getOffsetY()                { return offsetY; }
    public List<String> getAllowedGround() { return allowedGround; }
    public RotationMode getRotationMode()  { return rotationMode; }
    public MirrorMode getMirrorMode()      { return mirrorMode; }
    public boolean isPreventOverlap()      { return preventOverlap; }
    public boolean isOverrideAir()         { return overrideAir; }

    public boolean isOverrideAir(String biome) {
        BiomeOverride o = biomeOverrides.get(biome.toLowerCase(Locale.ROOT));
        return (o != null && o.hasOverrideAir()) ? o.overrideAir() : overrideAir;
    }

    public int getMinY(String biome) {
        BiomeOverride o = biomeOverrides.get(biome.toLowerCase(Locale.ROOT));
        return (o != null && o.hasMinY()) ? o.minY() : minY;
    }

    public int getMaxY(String biome) {
        BiomeOverride o = biomeOverrides.get(biome.toLowerCase(Locale.ROOT));
        return (o != null && o.hasMaxY()) ? o.maxY() : maxY;
    }

    public int getOffsetY(String biome) {
        BiomeOverride o = biomeOverrides.get(biome.toLowerCase(Locale.ROOT));
        return (o != null && o.hasOffsetY()) ? o.offsetY() : offsetY;
    }

    public StructureRotation resolveRotation(String biome, Random random) {
        BiomeOverride o = biomeOverrides.get(biome.toLowerCase(Locale.ROOT));
        return (o != null && o.hasRotation())
                ? o.rotation().resolve(random)
                : rotationMode.resolve(random);
    }

    public Mirror resolveMirror(String biome, Random random) {
        BiomeOverride o = biomeOverrides.get(biome.toLowerCase(Locale.ROOT));
        return (o != null && o.hasMirror())
                ? o.mirror().resolve(random)
                : mirrorMode.resolve(random);
    }

    public PatchConfig getPatchConfig(String biome) {
        BiomeOverride o = biomeOverrides.get(biome.toLowerCase(Locale.ROOT));
        return (o != null && o.hasPatch()) ? o.patch() : patchConfig;
    }

    public Map<String, BiomeOverride> getBiomeOverrides() {
        return Collections.unmodifiableMap(biomeOverrides);
    }

    // -------------------------------------------------------------------------
    // PatchConfig
    // -------------------------------------------------------------------------
    public record PatchConfig(
            boolean enabled,
            int minSpawns,
            int maxSpawns,
            int minDist,
            int maxDist
    ) {
        public static PatchConfig from(ConfigurationSection s) {
            if (s == null) return new PatchConfig(false, 1, 1, 0, 0);
            return new PatchConfig(
                    s.getBoolean("enabled", true),
                    Math.max(1, s.getInt("min-spawns",   1)),
                    Math.max(1, s.getInt("max-spawns",   1)),
                    Math.max(0, s.getInt("min-distance", 0)),
                    Math.max(0, s.getInt("max-distance", 0))
            );
        }
    }

    public PatchConfig getPatchConfig() { return patchConfig; }
}