package space.qouve.worldgenfeature.behaviors;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import space.qouve.worldgenfeature.models.WorldGenBehavior;
import space.qouve.worldgenfeature.models.WorldGenContext;

public class HighestBlockBehavior extends WorldGenBehavior {

    private final int defaultOffset;

    public HighestBlockBehavior() {
        super("highest-block");
        this.defaultOffset = 0;
    }

    @Override
    public boolean run(WorldGenContext context, ConfigurationSection section) {
        Location loc = context.location();
        World world = context.world();

        int blockX = loc.getBlockX();
        int blockZ = loc.getBlockZ();
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;

        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            world.getChunkAtAsync(chunkX, chunkZ, true).thenAccept(chunk -> {
                int highestY = world.getHighestBlockYAt(blockX, blockZ);
                int offset = section.isInt("offset") ? section.getInt("offset") : defaultOffset;

                loc.setY(highestY + offset);

            });

            return false;
        }

        int highestY = world.getHighestBlockYAt(blockX, blockZ);
        int offset = section.isInt("offset") ? section.getInt("offset") : defaultOffset;

        loc.setY(highestY + offset);

        return true;
    }
}