package space.qouve.worldgenfeature.models;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;

public record BiomeConfig(
        double chance,
        boolean overrideAir,
        Map<WorldGenBehavior, ConfigurationSection> behaviors
) {
}