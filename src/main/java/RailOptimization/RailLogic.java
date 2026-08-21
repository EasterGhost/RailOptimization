package RailOptimization;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
		RailChangeList changeList;
	}

	private static final ThreadLocal<Integer> WALK_DEPTH = ThreadLocal.withInitial(() -> 0);
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
		if (WALK_DEPTH.get() > 0) {
			return new RailUpdateContext(railPowerLimit);
		}

		WALK_DEPTH.set(1);
		ContextPool pool = CONTEXT_POOL.get();
		RailUpdateContext context = pool.context;
		if (context == null || context.railPowerLimit != railPowerLimit) {
			context = new RailUpdateContext(railPowerLimit);
			pool.context = context;
			pool.changeList = new RailChangeList(railPowerLimit * 2 + 1);
		} else {
			context.reset();
			pool.changeList.reset();
		}
		return context;
	}

	private static void releaseUpdateContext(RailUpdateContext context) {
		ContextPool pool = CONTEXT_POOL.get();
		if (context == pool.context) {
			WALK_DEPTH.set(0);
		}
	}

	private static RailChangeList newChangeList(RailUpdateContext context) {
		ContextPool pool = CONTEXT_POOL.get();
		if (context == pool.context) {
			return pool.changeList;
		}
		return new RailChangeList(railPowerLimit * 2 + 1);
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
			PoweredRailBlock self, BlockState state, Level level, BlockPos pos) {
		if (useTestPositionModes) {
			return tryCustomUpdateStateWithTestModes(self, state, level, pos);
		}
		if (!optimizationEnabled) {
			return false;
		}
		if (RailUpdateMemo.isConfirmed(pos.asLong(), railPowerLimit, state.getValue(POWERED))) {
			return true;
		}
		customUpdateStateWithCurrentPowerLimit(self, state, level, pos);
		return true;
	}

	@SuppressWarnings("null")
	private static boolean tryCustomUpdateStateWithTestModes(
			PoweredRailBlock self, BlockState state, Level level, BlockPos pos) {
		int testMode = testPositionModes.get(pos.asLong());
		if (testMode == TEST_MODE_VANILLA || !optimizationEnabled) {
			return false;
		}

		int effectiveLimit = testMode == TEST_MODE_OPTIMIZED ? railPowerLimit : testMode;
		if (RailUpdateMemo.isConfirmed(pos.asLong(), effectiveLimit, state.getValue(POWERED))) {
			return true;
		}

		if (testMode == TEST_MODE_OPTIMIZED) {
			customUpdateStateWithCurrentPowerLimit(self, state, level, pos);
			return true;
		}

		int configuredPowerLimit = railPowerLimit;
		railPowerLimit = testMode;
		try {
			customUpdateStateWithCurrentPowerLimit(self, state, level, pos);
		} finally {
			railPowerLimit = configuredPowerLimit;
		}
		return true;
	}

	@SuppressWarnings("null")
	private static void customUpdateStateWithCurrentPowerLimit(
			PoweredRailBlock self, BlockState state, Level level, BlockPos pos) {
		boolean currentlyPowered = state.getValue(POWERED);
		RailUpdateContext context = newUpdateContext();
		try {
			boolean directlyPowered = context.hasNeighborSignal(level, pos);
			if (currentlyPowered && directlyPowered) {
				return;
			}

			RailShape railShape = state.getValue(PoweredRailBlock.SHAPE);
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
			}
		} finally {
			releaseUpdateContext(context);
		}
	}

	private static void powerLane(PoweredRailBlock self, Level world, BlockPos pos,
			BlockState mainState, RailShape railShape, RailUpdateContext context, boolean directlyPowered) {
		if (!RailSignalSearcher.supportsFastSearch(railShape)) {
			return;
		}

		context.memo.beginWalk();
		RailUpdateMemo.trackContext(context.memo);
		context.beginPowering();
		RailSearchCache checkedPos = context.searchCache;
		RailChangeList changedRails = newChangeList(context);
		setRailPowerState(world, pos, mainState, true, changedRails, context);
		checkedPos.put(pos.asLong(), CHECKED_POWERED);
		int firstDirectionCount = setRailPositionsPower(
				self, world, pos, mainState, context, true, directlyPowered, changedRails);
		int secondDirectionCount = setRailPositionsPower(
				self, world, pos, mainState, context, false, directlyPowered, changedRails);

		updateChangedRails(world, pos, mainState, railShape, firstDirectionCount, secondDirectionCount,
				changedRails, context);
	}

	private static void dePowerLane(PoweredRailBlock self, Level world, BlockPos pos,
			BlockState mainState, RailShape railShape, RailUpdateContext context) {
		if (!RailSignalSearcher.supportsFastSearch(railShape)) {
			return;
		}

		context.memo.beginWalk();
		RailUpdateMemo.trackContext(context.memo);
		context.beginDepowering();
		RailChangeList changedRails = newChangeList(context);
		setRailPowerState(world, pos, mainState, false, changedRails, context);

		int firstDirectionCount = setRailPositionsDePower(
				self, world, pos, mainState, true, context, changedRails);
		int secondDirectionCount = setRailPositionsDePower(
				self, world, pos, mainState, false, context, changedRails);

		updateChangedRails(world, pos, mainState, railShape, firstDirectionCount, secondDirectionCount,
				changedRails, context);
	}

	private static Direction[] getRailDirections(RailShape railShape) {
		return switch (railShape) {
			case NORTH_SOUTH -> NORTH_SOUTH_DIR;
			case EAST_WEST -> EAST_WEST_DIR;
			default -> null;
		};
	}

	@SuppressWarnings("null")
	private static int setRailPositionsPower(PoweredRailBlock self, Level world, BlockPos pos,
			BlockState sourceState, RailUpdateContext context, boolean forward,
			boolean directlyPowered, RailChangeList changedRails) {
		int count = 0;
		RailSearchCache checkedPos = context.searchCache;
		MutableBlockPos cursor = context.railCursor;
		cursor.set(pos.getX(), pos.getY(), pos.getZ());
		BlockState previousState = sourceState;
		RailShape sourceShape = sourceState.getValue(PoweredRailBlock.SHAPE);
		RailShape directFlatShape = directlyPowered && getRailDirections(sourceShape) != null
				? sourceShape
				: null;
		boolean directPath = directlyPowered;

		for (int i = 1; i <= railPowerLimit; ++i) {
			long previousPos = cursor.asLong();
			int previousY = cursor.getY();
			BlockState state = RailSignalSearcher.findNextRailState(
					self, world, cursor, previousState, forward, context);
			if (state == null) {
				break;
			}

			boolean continuesDirectFlatPath = directFlatShape != null
					&& cursor.getY() == previousY
					&& state.getValue(PoweredRailBlock.SHAPE) == directFlatShape;
			if (!continuesDirectFlatPath) {
				directFlatShape = null;
			}
			boolean continuesDirectPath = directPath && (continuesDirectFlatPath ||
					RailSignalSearcher.connectsBackTo(
							self, world, cursor, state, previousPos, previousState, context));
			if (!continuesDirectPath) {
				directPath = false;
			}

			long posKey = cursor.asLong();
			byte checked = checkedPos.get(posKey);

			if (checked != CHECKED_UNKNOWN) {
				if (checked == CHECKED_BLOCKED)
					break;
				previousState = state;
				count++;
				continue;
			}

			if (state.getValue(POWERED) || (!continuesDirectPath && !(context.hasNeighborSignal(world, cursor) ||
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
	private static int setRailPositionsDePower(PoweredRailBlock self, Level world, BlockPos pos,
			BlockState sourceState, boolean forward, RailUpdateContext context, RailChangeList changedRails) {
		RailShape sourceShape = sourceState.getValue(PoweredRailBlock.SHAPE);
		int straightCount = RailSignalSearcher.countStraightRailsToDepower(
				self, world, pos, sourceShape, forward, context, context.straightRailStates);
		if (straightCount != RailSignalSearcher.COMPLEX_PATH) {
			return setStraightRailPositionsDePower(
					world, pos, sourceShape, forward, straightCount, context, changedRails);
		}

		int count = 0;
		RailSearchCache checkedPos = context.searchCache;
		MutableBlockPos cursor = context.railCursor;
		cursor.set(pos.getX(), pos.getY(), pos.getZ());
		BlockState previousState = sourceState;

		for (int i = 1; i <= railPowerLimit; ++i) {
			BlockState state = RailSignalSearcher.findNextRailState(
					self, world, cursor, previousState, forward, context);
			if (state == null) {
				break;
			}

			long posKey = cursor.asLong();
			byte checked = checkedPos.get(posKey);

			if (checked == CHECKED_BLOCKED) {
				break;
			}

			if (!state.getValue(POWERED) ||
					context.hasNeighborSignal(world, cursor) ||
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

	private static int setStraightRailPositionsDePower(
			Level world, BlockPos pos, RailShape railShape, boolean forward, int count,
			RailUpdateContext context, RailChangeList changedRails) {
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
			context.searchCache.put(cursor.asLong(), CHECKED_BLOCKED);
		}
		return count;
	}

	@SuppressWarnings("null")
	private static void setRailPowerState(Level world, BlockPos pos, BlockState state, boolean powered,
			RailChangeList changedRails, RailUpdateContext context) {
		RailUpdateMemo.beginLaneWrite();
		try {
			world.setBlock(pos, state.setValue(POWERED, powered), UPDATE_FORCE_PLACE);
		} finally {
			RailUpdateMemo.endLaneWrite();
		}
		context.memo.confirm(pos, powered, getRailPowerLimit());
		changedRails.add(pos, state);
	}

	private static void updateChangedRails(Level world, BlockPos pos, BlockState mainState,
			RailShape railShape, int firstDirectionCount, int secondDirectionCount, RailChangeList changedRails,
			RailUpdateContext context) {
		Direction[] directions = getRailDirections(railShape);
		if (directions != null && !changedRails.hasSlope()) {
			RailUpdateNotifier.updateRails(railShape == RailShape.EAST_WEST, world, pos, mainState,
					firstDirectionCount,
					secondDirectionCount, context.scratchPos);
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

	private static final class RailChangeList {
		private final long[] positions;
		private final boolean[] ascending;
		private int size;
		private boolean hasSlope;

		private RailChangeList(int capacity) {
			this.positions = new long[capacity];
			this.ascending = new boolean[capacity];
		}

		private void add(BlockPos pos, BlockState state) {
			positions[size] = pos.asLong();
			boolean isAscending = RailLogic.isAscending(state.getValue(PoweredRailBlock.SHAPE));
			ascending[size] = isAscending;
			hasSlope |= isAscending;
			size++;
		}

		private void reset() {
			size = 0;
			hasSlope = false;
		}

		private int size() {
			return size;
		}

		private boolean hasSlope() {
			return hasSlope;
		}

		private long position(int index) {
			return positions[index];
		}

		private boolean isAscending(int index) {
			return ascending[index];
		}
	}
}
