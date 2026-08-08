package space.qouve.worldgenfeature.models;

import space.qouve.worldgenfeature.behaviors.GroundBlockBehavior;
import space.qouve.worldgenfeature.behaviors.HeightBehavior;
import space.qouve.worldgenfeature.behaviors.HighestBlockBehavior;
import space.qouve.worldgenfeature.behaviors.PatchSpawnBehavior;

public final class WorldGenBehaviors {

    private WorldGenBehaviors() {}

    public static void registerAll() {
        new PatchSpawnBehavior();
        new HighestBlockBehavior();
        new HeightBehavior();
        new GroundBlockBehavior();
    }
}