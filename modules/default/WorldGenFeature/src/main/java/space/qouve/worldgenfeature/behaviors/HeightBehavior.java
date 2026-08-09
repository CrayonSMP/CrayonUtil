package space.qouve.worldgenfeature.behaviors;

import org.bukkit.configuration.ConfigurationSection;
import space.qouve.worldgenfeature.models.WorldGenBehavior;
import space.qouve.worldgenfeature.models.WorldGenContext;

import java.util.concurrent.ThreadLocalRandom;

public class HeightBehavior extends WorldGenBehavior {

    public HeightBehavior() {
        super("height");
    }

    @Override
    public boolean run(WorldGenContext context, ConfigurationSection section) {
        if (section == null) return true;

        int worldMinY = context.world().getMinHeight();
        int worldMaxY = context.world().getMaxHeight() - 1;

        int minY = Math.max(section.getInt("min", worldMinY), worldMinY);
        int maxY = Math.min(section.getInt("max", worldMaxY + 1), worldMaxY);

        if (minY > maxY) {
            return false;
        }

        if (context.yResolved().get()) {
            int currentY = context.location().getBlockY();
            return currentY >= minY && currentY <= maxY;
        }

        int randomY = minY + ThreadLocalRandom.current().nextInt(maxY - minY + 1);
        context.location().setY(randomY);
        context.yResolved().set(true);

        return true;
    }
}