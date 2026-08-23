package RailOptimization;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.RailShape;

public final class RailLogic {
	private static final BooleanProperty POWERED = PoweredRailBlock.POWERED;
	static final Direction[] EAST_WEST_DIR = new Direction[] { Direction.WEST, Direction.EAST };
	static final Direction[] NORTH_SOUTH_DIR = new Direction[] { Direction.SOUTH, Direction.NORTH };

	private static final int UPDATE_FORCE_PLACE = Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_CLIENTS;
	private static final boolean[] RAIL_ASCENDING = new boolean[RailShape.values().length];
	static final byte CHECKED_UNKNOWN = 0;
	static final byte CHECKED_BLOCKED = 1;
	static final byte CHECKED_POWERED = 2;
	static final int MAX_RAIL_POWER_LIMIT = 64;
	private static final int TEST_MODE_OPTIMIZED = 0;
	private static final int TEST_MODE_VANILLA = -1;

	private static volatile int railPowerLimit = 8;
	private static volatile boolean optimizationEnabled = true;
	private static boolean useTestPositionModes;
	private static final Long2IntOpenHashMap testPositionModes = new Long2IntOpenHashMap();

	private static final class ContextPool {
		RailUpdateContext context;
		int walkDepth;
	}

	private static final ThreadLocal<ContextPool> CONTEXT_POOL = ThreadLocal.withInitial(ContextPool::new);

	static {
		RAIL_ASCENDING[RailShape.ASCENDING_EAST.ordinal()] = true;
		RAIL_ASCENDING[RailShape.ASCENDING_WEST.ordinal()] = true;
		RAIL_ASCENDING[RailShape.ASCENDING_NORTH.ordinal()] = true;
		RAIL_ASCENDING[RailShape.ASCENDING_SOUTH.ordinal()] = true;
	}

	private RailLogic() {
	}

	static boolean isAscending(RailShape railShape) {
		return RAIL_ASCENDING[railShape.ordinal()];
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

	@SuppressWarnings("null")
	public static boolean tryCustomUpdateState(
			PoweredRailBlock self, BlockState state, Level level, BlockPos pos, Block sourceBlock) {
		if (useTestPositionModes) {
			return tryCustomUpdateStateWithTestModes(self, state, level, pos, sourceBlock);
		}
		if (!optimizationEnabled) {
			return false;
		}
		boolean currentlyPowered = RailSignalSearcher.isPowered(state);
		if (canReuseConfirmedState(sourceBlock, pos.asLong(), railPowerLimit, currentlyPowered)) {
			return true;
		}
		customUpdateStateWithCurrentPowerLimit(self, state, level, pos, currentlyPowered);
		return true;
	}

	@SuppressWarnings("null")
	private static boolean tryCustomUpdateStateWithTestModes(
			PoweredRailBlock self, BlockState state, Level level, BlockPos pos, Block sourceBlock) {
		int testMode = testPositionModes.get(pos.asLong());
		if (testMode == TEST_MODE_VANILLA || !optimizationEnabled) {
			return false;
		}

		int effectiveLimit = testMode == TEST_MODE_OPTIMIZED ? railPowerLimit : testMode;
		boolean currentlyPowered = RailSignalSearcher.isPowered(state);
		if (canReuseConfirmedState(sourceBlock, pos.asLong(), effectiveLimit, currentlyPowered)) {
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

	private static boolean canReuseConfirmedState(
			Block sourceBlock, long position, int powerLimit, boolean currentPowered) {
		return sourceBlock != Blocks.TRAPPED_CHEST
				&& RailUpdateMemo.isConfirmed(position, powerLimit, currentPowered);
	}

	@SuppressWarnings("null")
	private static void customUpdateStateWithCurrentPowerLimit(
			PoweredRailBlock self, BlockState state, Level level, BlockPos pos, boolean currentlyPowered) {
		RailUpdateContext context = newUpdateContext();
		try {
			boolean directlyPowered = context.hasNeighborSignal(level, pos);
			if (currentlyPowered && directlyPowered) {
				return;
			}

			RailShape railShape = RailSignalSearcher.railShape(state);
			boolean shouldBePowered = directlyPowered;
			if (!shouldBePowered) {
				shouldBePowered = RailSignalSearcher.findPoweredRailSignalFaster(
						self, level, pos, state, true, 0, context)
						|| RailSignalSearcher.findPoweredRailSignalFaster(
								self, level, pos, state, false, 0, context);
			}

			if (shouldBePowered != currentlyPowered) {
				if (shouldBePowered) {
					powerLane(self, level, pos, state, railShape, context, directlyPowered);
				} else {
					dePowerLane(self, level, pos, state, railShape, context);
				}
			} else {
				context.memo.confirm(pos, currentlyPowered, RailLogic.getRailPowerLimit());
			}
		} finally {
			releaseUpdateContext(context);
		}
	}

	private static void powerLane(PoweredRailBlock self, Level world, BlockPos pos, BlockState mainState, RailShape railShape, RailUpdateContext context, boolean directlyPowered) {
		if (!RailSignalSearcher.supportsFastSearch(railShape)) {
			return;
		}

		context.memo.beginWalk();
		RailUpdateMemo.trackContext(context.memo);
		context.beginPowering();
		RailSearchCache checkedPos = context.searchCache;
		RailChangeList changedRails = context.changeList;
		int firstDirectionCount;
		int secondDirectionCount;
		RailUpdateMemo.beginLaneWrite();
		try {
			setRailPowerState(world, pos, mainState, true, changedRails, context);
			checkedPos.put(pos.asLong(), CHECKED_POWERED);
			firstDirectionCount = setRailPositionsPower(
					self, world, pos, mainState, context, true, directlyPowered, changedRails);
			secondDirectionCount = setRailPositionsPower(
					self, world, pos, mainState, context, false, directlyPowered, changedRails);
		} finally {
			RailUpdateMemo.endLaneWrite();
		}

		updateChangedRails(world, pos, mainState, railShape, firstDirectionCount, secondDirectionCount,
				changedRails, context);
	}

	private static void dePowerLane(PoweredRailBlock self, Level world, BlockPos pos, BlockState mainState, RailShape railShape, RailUpdateContext context) {
		if (!RailSignalSearcher.supportsFastSearch(railShape)) {
			return;
		}

		context.memo.beginWalk();
		RailUpdateMemo.trackContext(context.memo);
		context.beginDepowering();
		RailChangeList changedRails = context.changeList;
		int firstDirectionCount;
		int secondDirectionCount;
		RailUpdateMemo.beginLaneWrite();
		try {
			setRailPowerState(world, pos, mainState, false, changedRails, context);
			firstDirectionCount = setRailPositionsDePower(
					self, world, pos, mainState, true, context, changedRails);
			secondDirectionCount = setRailPositionsDePower(
					self, world, pos, mainState, false, context, changedRails);
		} finally {
			RailUpdateMemo.endLaneWrite();
		}

		updateChangedRails(world, pos, mainState, railShape, firstDirectionCount, secondDirectionCount, changedRails, context);
	}

	private static Direction[] getRailDirections(RailShape railShape) {
		return switch (railShape) {
			case NORTH_SOUTH -> NORTH_SOUTH_DIR;
			case EAST_WEST -> EAST_WEST_DIR;
			default -> null;
		};
	}

	@SuppressWarnings("null")
	private static int setRailPositionsPower(PoweredRailBlock self, Level world, BlockPos pos, BlockState sourceState, RailUpdateContext context, boolean forward, boolean directlyPowered, RailChangeList changedRails) {
		int count = 0;
		RailSearchCache checkedPos = context.searchCache;
		MutableBlockPos cursor = context.railCursor;
		cursor.set(pos.getX(), pos.getY(), pos.getZ());
		BlockState previousState = sourceState;
		RailShape sourceShape = RailSignalSearcher.railShape(sourceState);
		RailShape directFlatShape = directlyPowered && getRailDirections(sourceShape) != null ? sourceShape : null;
		boolean directPath = directlyPowered;

		for (int i = 1; i <= railPowerLimit; ++i) {
			long previousPos = cursor.asLong();
			int previousY = cursor.getY();
			BlockState state = RailSignalSearcher.findNextRailState(
					self, world, cursor, previousState, forward, context);
			if (state == null) {
				break;
			}

			boolean continuesDirectFlatPath = directFlatShape != null && cursor.getY() == previousY
					&& RailSignalSearcher.railShape(state) == directFlatShape;
			if (!continuesDirectFlatPath) {
				directFlatShape = null;
			}
			boolean continuesDirectPath = directPath && (continuesDirectFlatPath || RailSignalSearcher.connectsBackTo(self, world, cursor, state, previousPos, previousState, context));
			if (!continuesDirectPath) {
				directPath = false;
			}

			long posKey = cursor.asLong();
			byte checked = checkedPos.get(posKey);

			if (checked != CHECKED_UNKNOWN) {
				if (checked == CHECKED_BLOCKED) {
					break;
				}
				previousState = state;
				count++;
				continue;
			}

			if (RailSignalSearcher.isPowered(state) || (!continuesDirectPath && !(context.hasNeighborSignal(world, cursor) ||
					RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, true, 0, context) ||
					RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, false, 0, context)))) {
				checkedPos.put(posKey, CHECKED_BLOCKED);
				break;
			}

			checkedPos.put(posKey, CHECKED_POWERED);
			setRailPowerState(world, cursor, state, true, changedRails, context);
			previousState = state;
			count++;
		}

		return count;
	}

	@SuppressWarnings("null")
	private static int setRailPositionsDePower(PoweredRailBlock self, Level world, BlockPos pos, BlockState sourceState, boolean forward, RailUpdateContext context, RailChangeList changedRails) {
		RailShape sourceShape = RailSignalSearcher.railShape(sourceState);
		int straightCount = RailSignalSearcher.countStraightRailsToDepower(self, world, pos, sourceShape, forward, context, context.straightRailStates);
		if (straightCount != RailSignalSearcher.COMPLEX_PATH) {
			return setStraightRailPositionsDePower(world, pos, sourceShape, forward, straightCount, context, changedRails);
		}
		int connectedCount = RailSignalSearcher.countConnectedRailsToDepower(
				self, world, pos, sourceState, forward, context,
				context.straightRailStates, context.connectedRailPositions);
		if (connectedCount != RailSignalSearcher.COMPLEX_PATH) {
			return setConnectedRailPositionsDePower(world, connectedCount, context, changedRails);
		}

		int count = 0;
		RailSearchCache checkedPos = context.searchCache;
		MutableBlockPos cursor = context.railCursor;
		cursor.set(pos.getX(), pos.getY(), pos.getZ());
		BlockState previousState = sourceState;

		for (int i = 1; i <= railPowerLimit; ++i) {
			BlockState state = RailSignalSearcher.findNextRailState(self, world, cursor, previousState, forward, context);
			if (state == null) {
				break;
			}

			long posKey = cursor.asLong();
			byte checked = checkedPos.get(posKey);

			if (checked == CHECKED_BLOCKED) {
				break;
			}

			if (!RailSignalSearcher.isPowered(state) || context.hasNeighborSignal(world, cursor) ||
					RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, true, 0, context) ||
					RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, false, 0, context)) {
				checkedPos.put(posKey, CHECKED_BLOCKED);
				break;
			}

			setRailPowerState(world, cursor, state, false, changedRails, context);
			checkedPos.put(posKey, CHECKED_BLOCKED);
			previousState = state;
			count++;
		}

		return count;
	}

	private static int setConnectedRailPositionsDePower(
			Level world, int count, RailUpdateContext context, RailChangeList changedRails) {
		MutableBlockPos cursor = context.railCursor;
		for (int index = 0; index < count; ++index) {
			long position = context.connectedRailPositions[index];
			cursor.set(BlockPos.getX(position), BlockPos.getY(position), BlockPos.getZ(position));
			setRailPowerState(
					world, cursor, context.straightRailStates[index], false, changedRails, context);
		}
		return count;
	}

	private static int setStraightRailPositionsDePower(Level world, BlockPos pos, RailShape railShape, boolean forward, int count, RailUpdateContext context, RailChangeList changedRails) {
		int stepIndex = (railShape.ordinal() << 1) | (forward ? 0 : 1);
		int stepX = RailSignalSearcher.STEP_X[stepIndex];
		int stepY = RailSignalSearcher.STEP_Y[stepIndex];
		int stepZ = RailSignalSearcher.STEP_Z[stepIndex];
		MutableBlockPos cursor = context.railCursor;
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();

		for (int index = 0; index < count; ++index) {
			x += stepX;
			y += stepY;
			z += stepZ;
			cursor.set(x, y, z);
			setRailPowerState(world, cursor, context.straightRailStates[index], false, changedRails, context);
		}
		return count;
	}

	@SuppressWarnings("null")
	private static void setRailPowerState(Level world, BlockPos pos, BlockState state, boolean powered,
			RailChangeList changedRails, RailUpdateContext context) {
		world.setBlock(pos, state.setValue(POWERED, powered), UPDATE_FORCE_PLACE);
		context.memo.confirm(pos, powered, getRailPowerLimit());
		changedRails.add(pos, state);
	}

	private static void updateChangedRails(Level world, BlockPos pos, BlockState mainState,
			RailShape railShape, int firstDirectionCount, int secondDirectionCount, RailChangeList changedRails,
			RailUpdateContext context) {
		Direction[] directions = getRailDirections(railShape);
		if (directions != null && !changedRails.hasSlope()) {
			RailUpdateNotifier.updateRails(railShape == RailShape.EAST_WEST, world, pos, mainState, firstDirectionCount, secondDirectionCount, context.scratchPos);
			return;
		}

		Block block = mainState.getBlock();
		MutableBlockPos scratchPos = context.scratchPos;
		for (int i = changedRails.size() - 1; i >= 0; i--) {
			long railPos = changedRails.position(i);
			int x = BlockPos.getX(railPos);
			int y = BlockPos.getY(railPos);
			int z = BlockPos.getZ(railPos);
			RailUpdateNotifier.notifyNeighborChanged(world, x, y, z, block, scratchPos);
			RailUpdateNotifier.notifyNeighborChanged(world, x, y - 1, z, block, scratchPos);

			if (changedRails.isAscending(i)) {
				RailUpdateNotifier.notifyNeighborChanged(world, x, y + 1, z, block, scratchPos);
			}
		}
	}
}
