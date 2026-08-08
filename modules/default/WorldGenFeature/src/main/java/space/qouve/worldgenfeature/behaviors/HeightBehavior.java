package space.qouve.worldgenfeature.behaviors;

import org.bukkit.configuration.ConfigurationSection;
import space.qouve.worldgenfeature.models.WorldGenBehavior;
import space.qouve.worldgenfeature.models.WorldGenContext;

public class HeightBehavior extends WorldGenBehavior {

    public HeightBehavior() {
        super("height");
    }

    @Override
    public boolean run(WorldGenContext context, ConfigurationSection section) {
        if (section == null) return true;

        int currentY = context.location().getBlockY();

        int minY = section.getInt("min", context.world().getMinHeight());
        int maxY = section.getInt("max", context.world().getMaxHeight());

        return currentY >= minY && currentY <= maxY;
    }
}