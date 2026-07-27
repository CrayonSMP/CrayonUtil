package space.qouve.worldgenfeature.models;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.Yaml;
import space.qouve.worldgenfeature.WorldGenFeature;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Repräsentiert eine patches/mypatch.yml.
 *
 * Eine MultiPatch-Config steuert das Spawning einer Gruppe von Schematics
 * (gewichtet zufällig), ohne dass die einzelnen Schematics selbst enabled
 * sein müssen.
 *
 * Beispiel-YAML:
 * <pre>
 * enabled: true
 * worlds:
 *   - world
 * chance: 0.15
 * attempts: 1
 * min_y: 60
 * max_y: 80
 * allowed_ground:
 *   - minecraft:grass_block
 * offset_y: 0
 * rotation: RANDOM
 * mirror: NONE
 * override_air: true
 * prevent_overlap: true
 * patch:
 *   enabled: true
 *   min-spawns: 3
 *   max-spawns: 6
 *   min-distance: 5
 *   max-distance: 25
 * schematics:
 *   - name: small_ruin
 *     weight: 10
 *     offset_y: -2
 *     rotation: RANDOM
 *     mirror: NONE
 *     override_air: true
 *   - name: medium_ruin
 *     weight: 5
 *   - name: large_ruin
 *     weight: 1
 *     override_air: false
 * biomes:
 *   "minecraft:forest":
 *     chance: 0.3
 *     patch:
 *       min-spawns: 5
 *       max-spawns: 10
 * </pre>
 */
public class MultiPatchConfig {

    // -------------------------------------------------------------------------
    // SchematicEntry — ein Eintrag in der schematics-Liste
    // -------------------------------------------------------------------------

    /**
     * Ein gewichteter Schematic-Eintrag innerhalb eines Patches.
     * Optionale Felder überschreiben die globalen Patch-Werte.
     */
    public static class SchematicEntry {
        private final String name;
        private final int weight;

        // Optionale Overrides (null = nicht gesetzt → globalen Wert nehmen)
        private final Integer offsetY;
        private final StructureConfig.RotationMode rotation;
        private final StructureConfig.MirrorMode mirror;
        private final Boolean overrideAir;
        private final List<String> allowedGround;

        private SchematicEntry(String name, int weight,
                               Integer offsetY,
                               StructureConfig.RotationMode rotation,
                               StructureConfig.MirrorMode mirror,
                               Boolean overrideAir,
                               List<String> allowedGround) {
            this.name         = name;
            this.weight       = weight;
            this.offsetY      = offsetY;
            this.rotation     = rotation;
            this.mirror       = mirror;
            this.overrideAir  = overrideAir;
            this.allowedGround = allowedGround;
        }

        public static SchematicEntry from(Map<?, ?> map, String patchName, Logger log) {
            String name   = map.containsKey("name")   ? map.get("name").toString()   : null;
            int    weight = map.containsKey("weight")  ? toInt(map.get("weight"), 1)  : 1;

            if (name == null || name.isBlank()) {
                log.warning("[WorldGen] MultiPatch '" + patchName + "': schematic entry missing 'name', skipping.");
                return null;
            }

            Integer offsetY = map.containsKey("offset_y") ? toInt(map.get("offset_y"), 0) : null;

            StructureConfig.RotationMode rotation = map.containsKey("rotation")
                    ? StructureConfig.RotationMode.parse(map.get("rotation").toString(), patchName, log)
                    : null;

            StructureConfig.MirrorMode mirror = map.containsKey("mirror")
                    ? StructureConfig.MirrorMode.parse(map.get("mirror").toString(), patchName, log)
                    : null;

            Boolean overrideAir = map.containsKey("override_air")
                    ? Boolean.parseBoolean(map.get("override_air").toString())
                    : null;

            List<String> allowedGround = null;
            if (map.containsKey("allowed_ground") && map.get("allowed_ground") instanceof List<?> list) {
                allowedGround = new ArrayList<>();
                for (Object o : list) allowedGround.add(o.toString());
            }

            return new SchematicEntry(name, Math.max(1, weight), offsetY, rotation, mirror, overrideAir, allowedGround);
        }

        /** Wählt gewichtet zufällig eine SchematicEntry aus der Liste. */
        public static SchematicEntry pickWeighted(List<SchematicEntry> entries, Random random) {
            int total = entries.stream().mapToInt(e -> e.weight).sum();
            int roll  = random.nextInt(total);
            int cursor = 0;
            for (SchematicEntry e : entries) {
                cursor += e.weight;
                if (roll < cursor) return e;
            }
            return entries.get(entries.size() - 1);
        }

        // --- Getter mit Fallback auf Patch-Globalwert ---

        public String getName() { return name; }
        public int    getWeight() { return weight; }

        public int resolveOffsetY(int globalOffsetY) {
            return offsetY != null ? offsetY : globalOffsetY;
        }

        public org.bukkit.block.structure.StructureRotation resolveRotation(
                StructureConfig.RotationMode globalRotation, Random random) {
            return rotation != null ? rotation.resolve(random) : globalRotation.resolve(random);
        }

        public org.bukkit.block.structure.Mirror resolveMirror(
                StructureConfig.MirrorMode globalMirror, Random random) {
            return mirror != null ? mirror.resolve(random) : globalMirror.resolve(random);
        }

        public boolean resolveOverrideAir(boolean globalOverrideAir) {
            return overrideAir != null ? overrideAir : globalOverrideAir;
        }

        public List<String> resolveAllowedGround(List<String> globalAllowedGround) {
            return allowedGround != null ? allowedGround : globalAllowedGround;
        }
    }

    // -------------------------------------------------------------------------
    // BiomeOverride — identisch zur StructureConfig-Variante, aber ohne patch-
    // inception (ein Patch-Biom-Override kann die PatchConfig überschreiben)
    // -------------------------------------------------------------------------
    public record BiomeOverride(
            double chance,         boolean hasChance,
            List<String> allowedGround, boolean hasAllowedGround,
            int minY,              boolean hasMinY,
            int maxY,              boolean hasMaxY,
            int offsetY,           boolean hasOffsetY,
            StructureConfig.RotationMode rotation, boolean hasRotation,
            StructureConfig.MirrorMode   mirror,   boolean hasMirror,
            boolean overrideAir,   boolean hasOverrideAir,
            StructureConfig.PatchConfig patch, boolean hasPatch
    ) {
        public static BiomeOverride from(ConfigurationSection parent, String biomeKey,
                                         String patchName, Logger log) {
            ConfigurationSection s = parent.getConfigurationSection(biomeKey);
            if (s == null) {
                return new BiomeOverride(
                        0, false, Collections.emptyList(), false,
                        0, false, 320, false, 0, false,
                        StructureConfig.RotationMode.NONE, false,
                        StructureConfig.MirrorMode.NONE, false,
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
                    hasChance      ? s.getDouble("chance")                                                      : 0,   hasChance,
                    hasGround      ? s.getStringList("allowed_ground")                                          : Collections.emptyList(), hasGround,
                    hasMinY        ? s.getInt("min_y")                                                          : 0,   hasMinY,
                    hasMaxY        ? s.getInt("max_y")                                                          : 320, hasMaxY,
                    hasOffsetY     ? s.getInt("offset_y")                                                       : 0,   hasOffsetY,
                    hasRotation    ? StructureConfig.RotationMode.parse(s.getString("rotation"), patchName, log): StructureConfig.RotationMode.NONE, hasRotation,
                    hasMirror      ? StructureConfig.MirrorMode.parse(s.getString("mirror"),     patchName, log): StructureConfig.MirrorMode.NONE,   hasMirror,
                    hasOverrideAir ? s.getBoolean("override_air")                                               : true, hasOverrideAir,
                    hasPatch       ? StructureConfig.PatchConfig.from(s.getConfigurationSection("patch"))       : null, hasPatch
            );
        }
    }

    // -------------------------------------------------------------------------
    // Felder
    // -------------------------------------------------------------------------
    private final String patchName;

    private boolean enabled;
    private List<String> allowedWorlds;
    private double chance;
    private int attempts;
    private int minY;
    private int maxY;
    private int offsetY;
    private List<String> allowedGround;
    private StructureConfig.RotationMode rotationMode;
    private StructureConfig.MirrorMode   mirrorMode;
    private boolean overrideAir;
    private boolean preventOverlap;
    private StructureConfig.PatchConfig patchConfig;

    private final List<SchematicEntry>        schematics    = new ArrayList<>();
    private final Map<String, BiomeOverride>  biomeOverrides = new HashMap<>();

    private MultiPatchConfig(String patchName) { this.patchName = patchName; }

    // -------------------------------------------------------------------------
    // Laden
    // -------------------------------------------------------------------------
    public static MultiPatchConfig load(File file, String patchName, Logger log) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        MultiPatchConfig c = new MultiPatchConfig(patchName);

        c.enabled        = yaml.getBoolean("enabled", false);
        c.allowedWorlds  = yaml.getStringList("worlds");
        c.chance         = clampChance(yaml.getDouble("chance", 0.05), patchName, log);
        c.attempts       = Math.max(1, yaml.getInt("attempts", 1));
        c.minY           = yaml.getInt("min_y", 0);
        c.maxY           = yaml.getInt("max_y", 320);
        c.offsetY        = yaml.getInt("offset_y", 0);
        c.allowedGround  = yaml.getStringList("allowed_ground");
        c.rotationMode   = StructureConfig.RotationMode.parse(yaml.getString("rotation", "NONE"), patchName, log);
        c.mirrorMode     = StructureConfig.MirrorMode.parse(yaml.getString("mirror", "NONE"), patchName, log);
        c.overrideAir    = yaml.getBoolean("override_air", true);
        c.preventOverlap = yaml.getBoolean("prevent_overlap", true);
        c.patchConfig    = StructureConfig.PatchConfig.from(yaml.getConfigurationSection("patch"));

        if (c.minY > c.maxY) {
            WorldGenFeature.getFeature().debug("[WorldGen] MultiPatch " + patchName
                    + ": min_y > max_y. No positions will ever pass the Y check.");
        }

        // Schematics — per SnakeYAML lesen um namespace-Keys sicher zu parsen
        try (FileInputStream fis = new FileInputStream(file)) {
            Map<String, Object> raw = new Yaml().load(fis);
            if (raw == null) return c;

            // schematics-Liste
            if (raw.get("schematics") instanceof List<?> rawList) {
                for (Object item : rawList) {
                    if (item instanceof Map<?, ?> map) {
                        SchematicEntry entry = SchematicEntry.from(map, patchName, log);
                        if (entry != null) c.schematics.add(entry);
                    }
                }
            }

            if (c.schematics.isEmpty()) {
                log.warning("[WorldGen] MultiPatch '" + patchName + "' has no valid schematics defined.");
            }

            // biomes — Doppelpunkt-sicheres Parsen via SnakeYAML-Keys + Bukkit-Section
            if (raw.get("biomes") instanceof Map<?, ?> rawBiomes) {
                ConfigurationSection biomesSection = yaml.getConfigurationSection("biomes");
                for (Object keyObj : rawBiomes.keySet()) {
                    String biomeKey = keyObj.toString().toLowerCase(Locale.ROOT);
                    BiomeOverride override = (biomesSection != null)
                            ? BiomeOverride.from(biomesSection, keyObj.toString(), patchName, log)
                            : new BiomeOverride(0, false, Collections.emptyList(), false,
                            0, false, 320, false, 0, false,
                            StructureConfig.RotationMode.NONE, false,
                            StructureConfig.MirrorMode.NONE, false,
                            true, false, null, false);
                    c.biomeOverrides.put(biomeKey, override);
                }
            }

        } catch (IOException e) {
            log.warning("[WorldGen] MultiPatch '" + patchName + "': failed to read file: " + e.getMessage());
        }

        return c;
    }

    // -------------------------------------------------------------------------
    // Biom-Abfragen
    // -------------------------------------------------------------------------
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

    public StructureConfig.RotationMode getRotationMode(String biome) {
        BiomeOverride o = biomeOverrides.get(biome.toLowerCase(Locale.ROOT));
        return (o != null && o.hasRotation()) ? o.rotation() : rotationMode;
    }

    public StructureConfig.MirrorMode getMirrorMode(String biome) {
        BiomeOverride o = biomeOverrides.get(biome.toLowerCase(Locale.ROOT));
        return (o != null && o.hasMirror()) ? o.mirror() : mirrorMode;
    }

    public boolean getOverrideAir(String biome) {
        BiomeOverride o = biomeOverrides.get(biome.toLowerCase(Locale.ROOT));
        return (o != null && o.hasOverrideAir()) ? o.overrideAir() : overrideAir;
    }

    public StructureConfig.PatchConfig getPatchConfig(String biome) {
        BiomeOverride o = biomeOverrides.get(biome.toLowerCase(Locale.ROOT));
        return (o != null && o.hasPatch()) ? o.patch() : patchConfig;
    }

    // -------------------------------------------------------------------------
    // Hilfsmethoden
    // -------------------------------------------------------------------------
    private static double clampChance(double v, String name, Logger log) {
        if (v < 0 || v > 1) {
            log.warning("[WorldGen] MultiPatch " + name + ": chance " + v + " outside [0,1]. Clamping.");
            return Math.max(0.0, Math.min(1.0, v));
        }
        return v;
    }

    private static int toInt(Object o, int fallback) {
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return fallback; }
    }

    // -------------------------------------------------------------------------
    // Getter
    // -------------------------------------------------------------------------
    public String              getPatchName()      { return patchName; }
    public boolean             isEnabled()         { return enabled; }
    public List<String>        getAllowedWorlds()   { return allowedWorlds; }
    public double              getChance()         { return chance; }
    public int                 getAttempts()       { return attempts; }
    public int                 getMinY()           { return minY; }
    public int                 getMaxY()           { return maxY; }
    public int                 getOffsetY()        { return offsetY; }
    public List<String>        getAllowedGround()  { return allowedGround; }
    public StructureConfig.RotationMode getRotationMode() { return rotationMode; }
    public StructureConfig.MirrorMode   getMirrorMode()   { return mirrorMode; }
    public boolean             isOverrideAir()     { return overrideAir; }
    public boolean             isPreventOverlap()  { return preventOverlap; }
    public StructureConfig.PatchConfig getPatchConfig() { return patchConfig; }
    public List<SchematicEntry>        getSchematics()  { return Collections.unmodifiableList(schematics); }
    public Map<String, BiomeOverride>  getBiomeOverrides() { return Collections.unmodifiableMap(biomeOverrides); }


}