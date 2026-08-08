package space.qouve.worldgenfeature.models;

import space.qouve.worldgenfeature.behaviors.WaterPlaceBehavior;

public final class WorldGenBehaviors {

    private WorldGenBehaviors() {}

    public static void registerAll() {
        new WaterPlaceBehavior();
    }
}