package RailOptimization;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

public final class RailLogic {
	static final byte CHECKED_UNKNOWN = 0;
	static final byte CHECKED_BLOCKED = 1;
	static final byte CHECKED_POWERED = 2;
	static final int MAX_RAIL_POWER_LIMIT = 64;

	private static final int TEST_MODE_OPTIMIZED = 0;
	private static final int TEST_MODE_VANILLA = -1;

	private static volatile int railPowerLimit = 8;
	private static volatile boolean optimizationEnabled = true;
	private static volatile Block lastReusableMemoSource;

	private static boolean useTestPositionModes;
	private static final Long2IntOpenHashMap testPositionModes = new Long2IntOpenHashMap();

	private static final class ContextPool {
		RailUpdateContext context;
		int walkDepth;
	}

	private static final ThreadLocal<ContextPool> CONTEXT_POOL = ThreadLocal.withInitial(ContextPool::new);

	private RailLogic() {
	}

	private static RailUpdateContext newUpdateContext() {
		ContextPool pool = CONTEXT_POOL.get();
		if (pool.walkDepth > 0) {
			return new RailUpdateContext(railPowerLimit);
		}

		pool.walkDepth = 1;
		RailUpdateContext context = pool.context;
		if (context == null || context.railPowerLimit != railPowerLimit) {
			context = new RailUpdateContext(railPowerLimit);
			pool.context = context;
		} else {
			context.reset();
		}
		return context;
	}

	private static void releaseUpdateContext(RailUpdateContext context) {
		ContextPool pool = CONTEXT_POOL.get();
		if (context == pool.context) {
			pool.walkDepth = 0;
		}
	}

	public static void setRailPowerLimit(int powerLimit) {
		railPowerLimit = clampRailPowerLimit(powerLimit);
	}

	static int clampRailPowerLimit(int powerLimit) {
		return Math.clamp(powerLimit, 1, MAX_RAIL_POWER_LIMIT);
	}

	static int getRailPowerLimit() {
		return railPowerLimit;
	}

	public static boolean isOptimizationEnabled() {
		return optimizationEnabled;
	}

	public static void setOptimizationEnabled(boolean enabled) {
		if (useTestPositionModes) {
			return;
		}

		optimizationEnabled = enabled;
	}

	static void enablePositionBasedTestMode() {
		optimizationEnabled = true;
		useTestPositionModes = true;
		testPositionModes.clear();
	}

	static void forceVanillaAtForTesting(BlockPos pos) {
		if (useTestPositionModes) {
			testPositionModes.put(pos.asLong(), TEST_MODE_VANILLA);
		}
	}

	static void forcePowerLimitAtForTesting(BlockPos pos, int powerLimit) {
		if (useTestPositionModes) {
			testPositionModes.put(pos.asLong(), clampRailPowerLimit(powerLimit));
		}
	}

	public static boolean tryCustomUpdateState(PoweredRailBlock self, BlockState state, Level level, BlockPos pos, Block sourceBlock) {
		if (useTestPositionModes) {
			return tryCustomUpdateStateWithTestModes(self, state, level, pos, sourceBlock);
		}
		if (!optimizationEnabled) {
			return false;
		}
		boolean currentlyPowered = RailPath.isPowered(state);
		if (canReuseConfirmedState(self, sourceBlock, pos.asLong(), railPowerLimit, currentlyPowered)) {
			return true;
		}
		customUpdateStateWithCurrentPowerLimit(self, state, level, pos, currentlyPowered);
		return true;
	}

	private static boolean tryCustomUpdateStateWithTestModes(PoweredRailBlock self, BlockState state, Level level, BlockPos pos, Block sourceBlock) {
		int testMode = testPositionModes.get(pos.asLong());
		if (testMode == TEST_MODE_VANILLA || !optimizationEnabled) {
			return false;
		}

		int effectiveLimit = testMode == TEST_MODE_OPTIMIZED ? railPowerLimit : testMode;
		boolean currentlyPowered = RailPath.isPowered(state);
		if (canReuseConfirmedState(self, sourceBlock, pos.asLong(), effectiveLimit, currentlyPowered)) {
			return true;
		}

		if (testMode == TEST_MODE_OPTIMIZED) {
			customUpdateStateWithCurrentPowerLimit(self, state, level, pos, currentlyPowered);
			return true;
		}

		int configuredPowerLimit = railPowerLimit;
		railPowerLimit = testMode;
		try {
			customUpdateStateWithCurrentPowerLimit(self, state, level, pos, currentlyPowered);
		} finally {
			railPowerLimit = configuredPowerLimit;
		}
		return true;
	}

	private static boolean canReuseConfirmedState(PoweredRailBlock self, Block sourceBlock, long position, int powerLimit, boolean currentPowered) {
		if (sourceBlock != self && sourceBlock != lastReusableMemoSource) {
			BlockState sourceState = sourceBlock.defaultBlockState();
			if (sourceState.hasBlockEntity()
					|| sourceState.isSignalSource()
					|| sourceState.hasAnalogOutputSignal()) {
				return false;
			}
			lastReusableMemoSource = sourceBlock;
		}
		return RailUpdateMemo.isConfirmed(position, powerLimit, currentPowered);
	}

	private static void customUpdateStateWithCurrentPowerLimit(PoweredRailBlock self, BlockState state, Level level, BlockPos pos, boolean currentlyPowered) {
		RailUpdateContext context = newUpdateContext();
		try {
			boolean directlyPowered = context.hasNeighborSignal(level, pos);
			if (currentlyPowered && directlyPowered) {
				return;
			}

			RailShape railShape = RailPath.railShape(state);
			boolean shouldBePowered = directlyPowered;
			if (!shouldBePowered) {
				shouldBePowered = RailSignalSearcher.findPoweredRailSignalFaster(
						self, level, pos, state, true, 0, context)
						|| RailSignalSearcher.findPoweredRailSignalFaster(
								self, level, pos, state, false, 0, context);
			}

			if (shouldBePowered != currentlyPowered) {
				if (shouldBePowered) {
					RailLaneUpdater.powerLane(self, level, pos, state, railShape, context, directlyPowered);
				} else {
					RailLaneUpdater.dePowerLane(self, level, pos, state, railShape, context);
				}
			} else {
				context.memo.confirm(pos, currentlyPowered, RailLogic.getRailPowerLimit());
			}
		} finally {
			releaseUpdateContext(context);
		}
	}

}
