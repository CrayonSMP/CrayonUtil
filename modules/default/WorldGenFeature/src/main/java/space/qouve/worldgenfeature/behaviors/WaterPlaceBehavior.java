package space.qouve.worldgenfeature.behaviors;

import org.bukkit.configuration.ConfigurationSection;
import space.qouve.worldgenfeature.models.WorldGenBehavior;
import space.qouve.worldgenfeature.models.WorldGenContext;

import java.io.IOException;

public class WaterPlaceBehavior extends WorldGenBehavior {
    public WaterPlaceBehavior() {
        super("water-place");
    }

    @Override
    public boolean run(WorldGenContext context, ConfigurationSection section) throws IOException {
        if (context.location().add(0, -1, 0).getBlock().isLiquid()) {
            return true;
        }
        return false;
    }
}
