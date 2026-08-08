package space.qouve.worldgenfeature.behaviors;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import space.qouve.worldgenfeature.models.WorldGenBehavior;
import space.qouve.worldgenfeature.models.WorldGenContext;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GroundBlockBehavior extends WorldGenBehavior {

    // Cache: ConfigurationSection (identitätsbasiert, gleiche Instanz pro Struktur-Config,
    // seit WorldGenService alles einmalig beim Start lädt) -> aufgelöste Materialien.
    // Vorher wurde section.getStringList("materials") + Material.matchMaterial(...) für
    // JEDEN einzelnen Struktur-Spawn neu ausgeführt, obwohl sich die Liste nie ändert.
    private static final Map<ConfigurationSection, Set<Material>> RESOLVED_MATERIALS_CACHE = new ConcurrentHashMap<>();

    public GroundBlockBehavior() {
        super("ground-block");
    }

    @Override
    public boolean run(WorldGenContext context, ConfigurationSection section) throws IOException {
        if (section == null) return true;

        Set<Material> allowedMaterials = RESOLVED_MATERIALS_CACHE.computeIfAbsent(section, this::resolveMaterials);
        if (allowedMaterials.isEmpty()) {
            return true;
        }

        int yOffset = section.getInt("offset", -1);

        Location loc = context.location().clone().add(0, yOffset, 0);
        Block block = loc.getBlock();

        return allowedMaterials.contains(block.getType());
    }

    private Set<Material> resolveMaterials(ConfigurationSection section) {
        List<String> allowedMaterialNames = section.getStringList("materials");
        Set<Material> resolved = new HashSet<>(allowedMaterialNames.size());

        for (String allowed : allowedMaterialNames) {
            Material mat = Material.matchMaterial(allowed);
            if (mat != null) {
                resolved.add(mat);
                continue;
            }

            // Fallback: manche Konfigurationswerte kommen als Namespaced-Key-String rein,
            // den matchMaterial nicht direkt auflöst - einmalig gegen alle Materialien
            // vergleichen (nur beim ersten Auflösen dieser Section, danach gecached).
            for (Material candidate : Material.values()) {
                if (allowed.equalsIgnoreCase(candidate.name()) || allowed.equalsIgnoreCase(candidate.getKey().toString())) {
                    resolved.add(candidate);
                    break;
                }
            }
        }

        return resolved;
    }
}