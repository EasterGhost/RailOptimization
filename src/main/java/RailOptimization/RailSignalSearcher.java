package RailOptimization;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.chunk.LevelChunk;

final class RailSignalSearcher {
	static final int COMPLEX_PATH = -1;
	private static final int SEARCH_NOT_FOUND = -1;
	private static final byte AXIS_NONE = 0;
	private static final byte AXIS_EAST_WEST = 1;
	private static final byte AXIS_NORTH_SOUTH = 2;
	private static final RailShape[] RAIL_SHAPES = RailShape.values();
	private static final byte[] RAIL_AXIS = new byte[RailShape.values().length];
	static final byte[] STEP_X;
	static final byte[] STEP_Y;
	static final byte[] STEP_Z;
	static final byte[] STEP_BELOW;
	static final RailShape[] STEP_FLAT;
	private static final Direction[] SIGNAL_DIRECTIONS = new Direction[] {
			Direction.DOWN, Direction.UP, Direction.NORTH,
			Direction.SOUTH, Direction.WEST, Direction.EAST
	};

	static {
		RAIL_AXIS[RailShape.EAST_WEST.ordinal()] = AXIS_EAST_WEST;
		RAIL_AXIS[RailShape.ASCENDING_EAST.ordinal()] = AXIS_EAST_WEST;
		RAIL_AXIS[RailShape.ASCENDING_WEST.ordinal()] = AXIS_EAST_WEST;
		RAIL_AXIS[RailShape.NORTH_SOUTH.ordinal()] = AXIS_NORTH_SOUTH;
		RAIL_AXIS[RailShape.ASCENDING_NORTH.ordinal()] = AXIS_NORTH_SOUTH;
		RAIL_AXIS[RailShape.ASCENDING_SOUTH.ordinal()] = AXIS_NORTH_SOUTH;

		int stepCount = RailShape.values().length << 1;
		RailShape[] flatShapes = new RailShape[stepCount];
		byte[] stepX = new byte[stepCount];
		byte[] stepY = new byte[stepCount];
		byte[] stepZ = new byte[stepCount];
		byte[] stepBelow = new byte[stepCount];

		setStep(flatShapes, stepX, stepY, stepZ, stepBelow,
				RailShape.NORTH_SOUTH, true, RailShape.NORTH_SOUTH, 0, 0, 1, true);
		setStep(flatShapes, stepX, stepY, stepZ, stepBelow,
				RailShape.NORTH_SOUTH, false, RailShape.NORTH_SOUTH, 0, 0, -1, true);
		setStep(flatShapes, stepX, stepY, stepZ, stepBelow,
				RailShape.EAST_WEST, true, RailShape.EAST_WEST, -1, 0, 0, true);
		setStep(flatShapes, stepX, stepY, stepZ, stepBelow,
				RailShape.EAST_WEST, false, RailShape.EAST_WEST, 1, 0, 0, true);
		setStep(flatShapes, stepX, stepY, stepZ, stepBelow,
				RailShape.ASCENDING_EAST, true, RailShape.EAST_WEST, -1, 0, 0, true);
		setStep(flatShapes, stepX, stepY, stepZ, stepBelow,
				RailShape.ASCENDING_EAST, false, RailShape.EAST_WEST, 1, 1, 0, false);
		setStep(flatShapes, stepX, stepY, stepZ, stepBelow,
				RailShape.ASCENDING_WEST, true, RailShape.EAST_WEST, -1, 1, 0, false);
		setStep(flatShapes, stepX, stepY, stepZ, stepBelow,
				RailShape.ASCENDING_WEST, false, RailShape.EAST_WEST, 1, 0, 0, true);
		setStep(flatShapes, stepX, stepY, stepZ, stepBelow,
				RailShape.ASCENDING_NORTH, true, RailShape.NORTH_SOUTH, 0, 0, 1, true);
		setStep(flatShapes, stepX, stepY, stepZ, stepBelow,
				RailShape.ASCENDING_NORTH, false, RailShape.NORTH_SOUTH, 0, 1, -1, false);
		setStep(flatShapes, stepX, stepY, stepZ, stepBelow,
				RailShape.ASCENDING_SOUTH, true, RailShape.NORTH_SOUTH, 0, 1, 1, false);
		setStep(flatShapes, stepX, stepY, stepZ, stepBelow,
				RailShape.ASCENDING_SOUTH, false, RailShape.NORTH_SOUTH, 0, 0, -1, true);

		STEP_X = stepX;
		STEP_Y = stepY;
		STEP_Z = stepZ;
		STEP_BELOW = stepBelow;
		STEP_FLAT = flatShapes;
	}

	private RailSignalSearcher() {
	}

	private static void setStep(RailShape[] flatShapes, byte[] stepX, byte[] stepY,
			byte[] stepZ, byte[] stepBelow, RailShape railShape, boolean forward,
			RailShape flatShape, int x, int y, int z, boolean below) {
		int index = (railShape.ordinal() << 1) | (forward ? 0 : 1);
		flatShapes[index] = flatShape;
		stepX[index] = (byte) x;
		stepY[index] = (byte) y;
		stepZ[index] = (byte) z;
		stepBelow[index] = (byte) (below ? 1 : 0);
	}

	static boolean supportsFastSearch(RailShape railShape) {
		return RAIL_AXIS[railShape.ordinal()] != AXIS_NONE;
	}

	static boolean hasNeighborSignalFast(
			Level level, BlockPos pos, MutableBlockPos scratchPos) {
		int x = pos.getX();
		int z = pos.getZ();
		int chunkX = x >> 4;
		int chunkZ = z >> 4;
		LevelChunk chunk = level.getChunk(chunkX, chunkZ);

		return hasNeighborSignalFast(
				level, pos, scratchPos, chunk, chunkX, chunkZ);
	}

	@SuppressWarnings("null")
	static boolean hasNeighborSignalFast(Level level, BlockPos pos, MutableBlockPos scratchPos, LevelChunk chunk, int chunkX, int chunkZ) {
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();

		int localX = x & 15;
		int localZ = z & 15;
		if (localX >= 2 && localX <= 13 && localZ >= 2 && localZ <= 13 && y >= level.getMinY() + 2 && y <= level.getMaxY() - 2) {
			return hasNeighborSignalInChunk(level, chunk, x, y, z, scratchPos);
		}

		for (Direction direction : SIGNAL_DIRECTIONS) {
			int neighborX = x + direction.getStepX();
			int neighborY = y + direction.getStepY();
			int neighborZ = z + direction.getStepZ();
			scratchPos.set(neighborX, neighborY, neighborZ);
			BlockState neighborState = getBlockState(level, chunk, chunkX, chunkZ, scratchPos);

			if (neighborState.getSignal(level, scratchPos, direction) > 0) {
				return true;
			}
			if (!neighborState.isRedstoneConductor(level, scratchPos)) {
				continue;
			}

			Direction opposite = direction.getOpposite();
			for (Direction directDirection : SIGNAL_DIRECTIONS) {
				if (directDirection == opposite) {
					continue;
				}
				scratchPos.set(
						neighborX + directDirection.getStepX(),
						neighborY + directDirection.getStepY(),
						neighborZ + directDirection.getStepZ());
				BlockState directState = getBlockState(
						level, chunk, chunkX, chunkZ, scratchPos);
				if (directState.getDirectSignal(level, scratchPos, directDirection) > 0) {
					return true;
				}
			}
		}
		return false;
	}

	@SuppressWarnings("null")
	private static boolean hasNeighborSignalInChunk(Level level, LevelChunk chunk, int x, int y, int z, MutableBlockPos scratchPos) {
		for (Direction direction : SIGNAL_DIRECTIONS) {
			int neighborX = x + direction.getStepX();
			int neighborY = y + direction.getStepY();
			int neighborZ = z + direction.getStepZ();
			scratchPos.set(neighborX, neighborY, neighborZ);
			BlockState neighborState = chunk.getBlockState(scratchPos);

			if (neighborState.getSignal(level, scratchPos, direction) > 0) {
				return true;
			}
			if (!neighborState.isRedstoneConductor(level, scratchPos)) {
				continue;
			}

			Direction opposite = direction.getOpposite();
			for (Direction directDirection : SIGNAL_DIRECTIONS) {
				if (directDirection == opposite) {
					continue;
				}
				scratchPos.set(
						neighborX + directDirection.getStepX(),
						neighborY + directDirection.getStepY(),
						neighborZ + directDirection.getStepZ());
				BlockState directState = chunk.getBlockState(scratchPos);
				if (directState.getDirectSignal(level, scratchPos, directDirection) > 0) {
					return true;
				}
			}
		}
		return false;
	}

	private static BlockState getBlockState(
			Level level, LevelChunk chunk, int chunkX, int chunkZ, BlockPos pos) {
		if ((pos.getX() >> 4) == chunkX
				&& (pos.getZ() >> 4) == chunkZ
				&& level.isInValidBounds(pos)) {
			return chunk.getBlockState(pos);
		}
		return level.getBlockState(pos);
	}

	private static int findPoweredRailSignalAt(PoweredRailBlock self, Level world, int x, int y, int z, boolean forward, int distance, RailShape expectedShape, RailUpdateContext context) {
		long posKey = BlockPos.asLong(x, y, z);
		byte cacheFlags = checkedPosFlags(forward, expectedShape);
		int cachedCost = context.getPoweredSearchCost(posKey, cacheFlags);
		if (cachedCost >= 0) {
			return distance + cachedCost < RailLogic.getRailPowerLimit()
					? distance + cachedCost
					: SEARCH_NOT_FOUND;
		}

		context.scratchPos.set(x, y, z);
		BlockState blockState = context.getBlockState(world, context.scratchPos);

		if (!blockState.is(self)) {
			return SEARCH_NOT_FOUND;
		}

		int railData = railData(blockState);
		RailShape actualShape = RAIL_SHAPES[railData & RailStateAccess.SHAPE_MASK];

		if (isMismatchedRailAxis(expectedShape, actualShape)
				|| (railData & RailStateAccess.POWERED_MASK) == 0) {
			return SEARCH_NOT_FOUND;
		}

		if (context.hasNeighborSignal(world, context.scratchPos)) {
			context.cachePoweredSearchCost(posKey, cacheFlags, 0);
			return distance;
		}

		int poweredDistance = findPoweredRailSignalFromState(
				self, world, x, y, z, blockState, forward, distance + 1, context);
		if (poweredDistance != SEARCH_NOT_FOUND) {
			context.cachePoweredSearchCost(posKey, cacheFlags, poweredDistance - distance);
		}
		return poweredDistance;
	}

	private static boolean isMismatchedRailAxis(RailShape expected, RailShape actual) {
		if (expected == RailShape.EAST_WEST) {
			return actual == RailShape.NORTH_SOUTH
					|| actual == RailShape.ASCENDING_NORTH
					|| actual == RailShape.ASCENDING_SOUTH;
		}
		return actual == RailShape.EAST_WEST
				|| actual == RailShape.ASCENDING_EAST
				|| actual == RailShape.ASCENDING_WEST;
	}

	private static byte checkedPosFlags(boolean forward, RailShape expectedShape) {
		byte flags = RailSearchCache.SEARCH;
		if (forward) {
			flags |= RailSearchCache.SEARCH_FORWARD;
		}
		if (expectedShape == RailShape.NORTH_SOUTH) {
			flags |= RailSearchCache.SEARCH_NORTH_SOUTH;
		}
		return flags;
	}

	static boolean findPoweredRailSignalFaster(PoweredRailBlock self, Level level, BlockPos pos, BlockState state, boolean forward, int distance, RailUpdateContext context) {
		return findPoweredRailSignalFromState(self, level, pos.getX(), pos.getY(), pos.getZ(), state, forward, distance, context) != SEARCH_NOT_FOUND;
	}

	static int countStraightRailsToDepower(PoweredRailBlock self, Level level, BlockPos pos, RailShape railShape, boolean forward, RailUpdateContext context, BlockState[] railStates) {
		byte axis = RAIL_AXIS[railShape.ordinal()];
		if (axis == AXIS_NONE) {
			return COMPLEX_PATH;
		}

		int stepIndex = (railShape.ordinal() << 1) | (forward ? 0 : 1);
		int stepX = STEP_X[stepIndex];
		int stepY = STEP_Y[stepIndex];
		int stepZ = STEP_Z[stepIndex];
		boolean stepBelow = STEP_BELOW[stepIndex] != 0;
		RailShape flatShape = axis == AXIS_EAST_WEST ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;

		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		int powerLimit = RailLogic.getRailPowerLimit();
		int poweredLength = 0;

		context.scratchPos.set(x, y - 1, z);
		if (isPoweredRailWithAxis(self, context.getBlockState(level, context.scratchPos), flatShape)) {
			return COMPLEX_PATH;
		}

		for (int index = 1; index <= powerLimit * 2; ++index) {
			x += stepX;
			y += stepY;
			z += stepZ;
			context.scratchPos.set(x, y, z);
			BlockState state = context.getBlockState(level, context.scratchPos);
			if (isPoweredRailWithAxis(self, state, flatShape)) {
				if (railShape(state) != railShape) {
					return COMPLEX_PATH;
				}
				if (stepBelow) {
					context.scratchPos.set(x, y - 1, z);
					if (isPoweredRailWithAxis(self, context.getBlockState(level, context.scratchPos), flatShape)) {
						return COMPLEX_PATH;
					}
					context.scratchPos.set(x, y, z);
				}
				if (context.hasNeighborSignal(level, context.scratchPos)) {
					return Math.max(0, Math.min(powerLimit, index - powerLimit - 1));
				}
				if (index - 1 < railStates.length) {
					railStates[index - 1] = state;
				}
				poweredLength = index;
				continue;
			}

			context.scratchPos.set(x, y - 1, z);
			BlockState belowState = context.getBlockState(level, context.scratchPos);
			if (isPoweredRailWithAxis(self, belowState, flatShape)) {
				return COMPLEX_PATH;
			}
			break;
		}

		return Math.min(powerLimit, poweredLength);
	}

	private static int findPoweredRailSignalFromState(PoweredRailBlock self, Level level, int x, int y, int z,
			BlockState state, boolean forward, int distance, RailUpdateContext context) {
		if (distance >= RailLogic.getRailPowerLimit()) {
			return SEARCH_NOT_FOUND;
		}

		RailShape railShape = railShape(state);
		if (RAIL_AXIS[railShape.ordinal()] == AXIS_NONE) {
			return SEARCH_NOT_FOUND;
		}

		int stepIndex = (railShape.ordinal() << 1) | (forward ? 0 : 1);
		int nextX = x + STEP_X[stepIndex];
		int nextY = y + STEP_Y[stepIndex];
		int nextZ = z + STEP_Z[stepIndex];
		RailShape flatShape = STEP_FLAT[stepIndex];

		int poweredDistance = findPoweredRailSignalAt(self, level, nextX, nextY, nextZ, forward, distance, flatShape, context);
		if (poweredDistance != SEARCH_NOT_FOUND) {
			return poweredDistance;
		}
		return STEP_BELOW[stepIndex] != 0
				? findPoweredRailSignalAt(self, level, nextX, nextY - 1, nextZ, forward, distance, flatShape, context)
				: SEARCH_NOT_FOUND;
	}

	static BlockState findNextRailState(PoweredRailBlock self, Level level, MutableBlockPos railPos, BlockState state, boolean forward, RailUpdateContext context) {
		MutableBlockPos scratchPos = context.scratchPos;
		int x = railPos.getX();
		int y = railPos.getY();
		int z = railPos.getZ();
		RailShape railShape = railShape(state);
		if (RAIL_AXIS[railShape.ordinal()] == AXIS_NONE) {
			return null;
		}

		int stepIndex = (railShape.ordinal() << 1) | (forward ? 0 : 1);
		int nextX = x + STEP_X[stepIndex];
		int nextY = y + STEP_Y[stepIndex];
		int nextZ = z + STEP_Z[stepIndex];
		RailShape flatShape = STEP_FLAT[stepIndex];

		scratchPos.set(nextX, nextY, nextZ);
		BlockState nextState = context.getBlockState(level, scratchPos);
		if (isSameRailWithAxis(self, nextState, flatShape)) {
			railPos.set(scratchPos);
			return nextState;
		}

		if (STEP_BELOW[stepIndex] == 0) {
			return null;
		}

		scratchPos.set(nextX, nextY - 1, nextZ);
		nextState = context.getBlockState(level, scratchPos);
		if (isSameRailWithAxis(self, nextState, flatShape)) {
			railPos.set(scratchPos);
			return nextState;
		}

		return null;
	}

	static boolean connectsBackTo(PoweredRailBlock self, Level level, BlockPos railPos, BlockState state, long expectedPreviousPos, BlockState previousState, RailUpdateContext context) {
		if (directionConnectsBackTo(self, level, railPos, state, true, expectedPreviousPos, previousState, context)) {
			return true;
		}
		return directionConnectsBackTo(self, level, railPos, state, false, expectedPreviousPos, previousState, context);
	}

	private static boolean directionConnectsBackTo(PoweredRailBlock self, Level level, BlockPos railPos, BlockState state, boolean forward, long expectedPreviousPos, BlockState previousState, RailUpdateContext context) {
		int x = railPos.getX();
		int y = railPos.getY();
		int z = railPos.getZ();
		RailShape railShape = railShape(state);
		if (RAIL_AXIS[railShape.ordinal()] == AXIS_NONE) {
			return false;
		}

		int stepIndex = (railShape.ordinal() << 1) | (forward ? 0 : 1);
		int nextX = x + STEP_X[stepIndex];
		int nextY = y + STEP_Y[stepIndex];
		int nextZ = z + STEP_Z[stepIndex];
		RailShape flatShape = STEP_FLAT[stepIndex];

		if (BlockPos.asLong(nextX, nextY, nextZ) == expectedPreviousPos) {
			return isSameRailWithAxis(self, previousState, flatShape);
		}
		if (STEP_BELOW[stepIndex] == 0 || BlockPos.asLong(nextX, nextY - 1, nextZ) != expectedPreviousPos
				|| !isSameRailWithAxis(self, previousState, flatShape)) {
			return false;
		}

		context.scratchPos.set(nextX, nextY, nextZ);
		return !isSameRailWithAxis(self, context.getBlockState(level, context.scratchPos), flatShape);
	}

	private static boolean isSameRailWithAxis(PoweredRailBlock self, BlockState state, RailShape expectedShape) {
		return state.is(self) && !isMismatchedRailAxis(expectedShape, railShape(state));
	}

	private static boolean isPoweredRailWithAxis(
			PoweredRailBlock self, BlockState state, RailShape expectedShape) {
		if (!state.is(self)) {
			return false;
		}
		int railData = railData(state);
		return !isMismatchedRailAxis(
				expectedShape, RAIL_SHAPES[railData & RailStateAccess.SHAPE_MASK])
				&& (railData & RailStateAccess.POWERED_MASK) != 0;
	}

	static RailShape railShape(BlockState state) {
		return RAIL_SHAPES[railData(state) & RailStateAccess.SHAPE_MASK];
	}

	static boolean isPowered(BlockState state) {
		return (railData(state) & RailStateAccess.POWERED_MASK) != 0;
	}

	private static int railData(BlockState state) {
		return ((RailStateAccess) (Object) state).railoptimization$getRailData();
	}
}
