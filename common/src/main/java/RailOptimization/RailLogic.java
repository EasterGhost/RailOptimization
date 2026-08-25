package RailOptimization;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

import RailOptimization.config.RailOptimizationConfig;

public final class RailLogic {
	static final byte CHECKED_UNKNOWN = 0;
	static final byte CHECKED_BLOCKED = 1;
	static final byte CHECKED_POWERED = 2;

	private static volatile int railPowerLimit = 8;
	private static volatile boolean optimizationEnabled = true;
	private static volatile Block lastReusableMemoSource;

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
		railPowerLimit = RailOptimizationConfig.normalizePowerLimit(powerLimit);
	}

	static int getRailPowerLimit() {
		return railPowerLimit;
	}

	public static boolean isOptimizationEnabled() {
		return optimizationEnabled;
	}

	public static void setOptimizationEnabled(boolean enabled) {
		optimizationEnabled = enabled;
	}

	public static boolean tryCustomUpdateState(PoweredRailBlock self, BlockState state, Level level, BlockPos pos, Block sourceBlock) {
		if (!optimizationEnabled) {
			return false;
		}
		boolean currentlyPowered = RailPath.isPowered(state);
		if (canReuseConfirmedState(self, sourceBlock, level, pos.asLong(), railPowerLimit, currentlyPowered)) {
			return true;
		}
		customUpdateStateWithCurrentPowerLimit(self, state, level, pos, currentlyPowered);
		return true;
	}

	private static boolean canReuseConfirmedState(PoweredRailBlock self, Block sourceBlock, Level level, long position, int powerLimit,
			boolean currentPowered) {
		if (sourceBlock != self && sourceBlock != lastReusableMemoSource) {
			BlockState sourceState = sourceBlock.defaultBlockState();
			if (sourceState.hasBlockEntity() || sourceState.isSignalSource() || sourceState.hasAnalogOutputSignal()) {
				return false;
			}
			lastReusableMemoSource = sourceBlock;
		}
		return RailUpdateMemo.isConfirmed(level, position, powerLimit, currentPowered);
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
				shouldBePowered = RailSignalSearcher.findPoweredRailSignalFaster(self, level, pos, state, true, 0, context)
						|| RailSignalSearcher.findPoweredRailSignalFaster(self, level, pos, state, false, 0, context);
			}

			if (shouldBePowered != currentlyPowered) {
				if (shouldBePowered) {
					RailLaneUpdater.powerLane(self, level, pos, state, railShape, context, directlyPowered);
				} else {
					RailLaneUpdater.dePowerLane(self, level, pos, state, railShape, context);
				}
			} else {
				context.memo.bindLevel(level);
				context.memo.confirm(pos, currentlyPowered, RailLogic.getRailPowerLimit());
			}
		} finally {
			releaseUpdateContext(context);
		}
	}
}
