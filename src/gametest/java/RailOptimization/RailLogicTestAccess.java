package RailOptimization;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.Level;

public final class RailLogicTestAccess {
	public static final int MODE_OPTIMIZED = 0;
	public static final int MODE_VANILLA = -1;

	private static final Long2IntOpenHashMap POSITION_MODES = new Long2IntOpenHashMap();
	private static boolean positionBasedTestMode;

	private RailLogicTestAccess() {
	}

	public static void enablePositionBasedTestMode() {
		RailLogic.setOptimizationEnabled(true);
		POSITION_MODES.clear();
		positionBasedTestMode = true;
	}

	public static void forceVanillaAt(BlockPos pos) {
		if (positionBasedTestMode) {
			POSITION_MODES.put(pos.asLong(), MODE_VANILLA);
		}
	}

	public static void forcePowerLimitAt(BlockPos pos, int powerLimit) {
		if (positionBasedTestMode) {
			POSITION_MODES.put(pos.asLong(), RailLogic.clampRailPowerLimit(powerLimit));
		}
	}

	public static boolean isPositionBasedTestModeEnabled() {
		return positionBasedTestMode;
	}

	public static int positionMode(BlockPos pos) {
		return POSITION_MODES.get(pos.asLong());
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

	public static boolean isMemoConfirmed(Level level, BlockPos pos, int powerLimit, boolean powered) {
		return RailUpdateMemo.isConfirmed(level, pos.asLong(), powerLimit, powered);
	}

	public static boolean hasNeighborSignalFast(Level level, BlockPos pos) {
		return RailNeighborSignalChecker.hasNeighborSignalFast(level, pos, new MutableBlockPos());
	}
}
