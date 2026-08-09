package space.qouve.worldgenfeature.models;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;

import space.qouve.worldgenfeature.WorldGenFeature;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public record WorldGenContext(
        World world,
        Clipboard clipboard,
        File file,
        Location location,
        StructureRotation rotation,
        Mirror mirror,
        boolean overrideAir,
        String debugName,
        WorldGenFeature feature,
        boolean isPatch,
        AtomicBoolean yResolved
) {

    public static WorldGenContext of(World world, Location location, File file,
                                     StructureRotation rotation, Mirror mirror,
                                     boolean overrideAir, String debugName,
                                     WorldGenFeature feature) {
        return new WorldGenContext(world, null, file, location, rotation, mirror,
                overrideAir, debugName, feature, false, new AtomicBoolean(false)); // Standardmäßig kein Patch
    }

    public WorldGenContext withClipboard(Clipboard clipboard) {
        return new WorldGenContext(world, clipboard, file, location, rotation, mirror,
                overrideAir, debugName, feature, isPatch, yResolved);
    }

    public WorldGenContext withIsPatch(boolean isPatch) {
        return new WorldGenContext(world, clipboard, file, location, rotation, mirror,
                overrideAir, debugName, feature, isPatch, yResolved);
    }

    public BlockVector3 toBlockVector3() {
        return BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public AffineTransform buildTransform() {
        AffineTransform transform = new AffineTransform();
        switch (rotation) {
            case CLOCKWISE_90        -> transform = transform.rotateY(-90);
            case CLOCKWISE_180       -> transform = transform.rotateY(-180);
            case COUNTERCLOCKWISE_90 -> transform = transform.rotateY(90);
            default -> {}
        }
        switch (mirror) {
            case LEFT_RIGHT -> transform = transform.scale(-1, 1, 1);
            case FRONT_BACK -> transform = transform.scale(1, 1, -1);
            default -> {}
        }
        return transform;
    }
}