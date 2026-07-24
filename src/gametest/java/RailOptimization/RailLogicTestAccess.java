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

    public static void forcePowerLimitAt(BlockPos pos, int powerLimit) {
        RailLogic.forcePowerLimitAtForTesting(pos, powerLimit);
    }

    public static int clampPowerLimit(int powerLimit) {
        return RailLogic.clampRailPowerLimit(powerLimit);
    }

    public static int maximumPowerLimit() {
        return RailLogic.MAX_RAIL_POWER_LIMIT;
    }

    public static int currentPowerLimit() {
        return RailLogic.getRailPowerLimit();
    }
}
