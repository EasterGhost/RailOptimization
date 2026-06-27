package RailOptimization;

import net.minecraft.core.BlockPos;

public final class RailLogicTestAccess {
    private RailLogicTestAccess() {
    }

    public static void enablePositionBasedTestMode() {
        RailLogic.enablePositionBasedTestMode();
    }

    public static void forceVanillaAt(BlockPos pos) {
        RailLogic.forceVanillaAtForTesting(pos);
    }
}
