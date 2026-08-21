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
	private static final byte[] RAIL_AXIS = new byte[RailShape.values().length];
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
	}

	private RailSignalSearcher() {
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
	static boolean hasNeighborSignalFast(
			Level level, BlockPos pos, MutableBlockPos scratchPos,
			LevelChunk chunk, int chunkX, int chunkZ) {
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();

		int localX = x & 15;
		int localZ = z & 15;
		if (localX >= 2 && localX <= 13 && localZ >= 2 && localZ <= 13) {
			scratchPos.set(x - 2, y - 2, z - 2);
			boolean lowerCornerValid = level.isInValidBounds(scratchPos);
			scratchPos.set(x + 2, y + 2, z + 2);
			if (lowerCornerValid && level.isInValidBounds(scratchPos)) {
				return hasNeighborSignalInChunk(level, chunk, x, y, z, scratchPos);
			}
		}

		for (Direction direction : SIGNAL_DIRECTIONS) {
			int neighborX = x + direction.getStepX();
			int neighborY = y + direction.getStepY();
			int neighborZ = z + direction.getStepZ();
			scratchPos.set(neighborX, neighborY, neighborZ);
			BlockState neighborState = getBlockState(
					level, chunk, chunkX, chunkZ, scratchPos);

			if (neighborState.getSignal(level, scratchPos, direction) > 0) {
				return true;
			}
			if (!neighborState.isRedstoneConductor(level, scratchPos)) {
				continue;
			}

			for (Direction directDirection : SIGNAL_DIRECTIONS) {
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
	private static boolean hasNeighborSignalInChunk(
			Level level, LevelChunk chunk, int x, int y, int z, MutableBlockPos scratchPos) {
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

			for (Direction directDirection : SIGNAL_DIRECTIONS) {
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

	private static int findPoweredRailSignalAt(PoweredRailBlock self, Level world, int x, int y, int z,
			boolean forward, int distance, RailShape expectedShape, RailUpdateContext context) {
		long posKey = BlockPos.asLong(x, y, z);
		byte cacheFlags = checkedPosFlags(forward, expectedShape);
		int cachedCost = context.getPoweredSearchCost(posKey, cacheFlags);
		if (cachedCost >= 0 && distance + cachedCost < RailLogic.getRailPowerLimit()) {
			return distance + cachedCost;
		}

		context.scratchPos.set(x, y, z);
		BlockState blockState = context.getBlockState(world, context.scratchPos);

		if (!blockState.is(self)) {
			return SEARCH_NOT_FOUND;
		}

		RailShape actualShape = blockState.getValue(PoweredRailBlock.SHAPE);

		if (isMismatchedRailAxis(expectedShape, actualShape) || !blockState.getValue(PoweredRailBlock.POWERED)) {
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

	static boolean findPoweredRailSignalFaster(PoweredRailBlock self, Level level,
			BlockPos pos, BlockState state, boolean forward, int distance,
			RailUpdateContext context) {
		return findPoweredRailSignalFromState(
				self, level, pos.getX(), pos.getY(), pos.getZ(), state, forward,
				distance, context) != SEARCH_NOT_FOUND;
	}

	static int countStraightRailsToDepower(
			PoweredRailBlock self, Level level, BlockPos pos, RailShape railShape,
			boolean forward, RailUpdateContext context, BlockState[] railStates) {
		if (railShape != RailShape.EAST_WEST && railShape != RailShape.NORTH_SOUTH) {
			return COMPLEX_PATH;
		}

		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		int stepX = railShape == RailShape.EAST_WEST ? (forward ? -1 : 1) : 0;
		int stepZ = railShape == RailShape.NORTH_SOUTH ? (forward ? 1 : -1) : 0;
		int powerLimit = RailLogic.getRailPowerLimit();
		int poweredLength = 0;

		context.scratchPos.set(x, y - 1, z);
		if (isPoweredRailWithAxis(self, context.getBlockState(level, context.scratchPos), railShape)) {
			return COMPLEX_PATH;
		}

		for (int index = 1; index <= powerLimit * 2; ++index) {
			x += stepX;
			z += stepZ;
			context.scratchPos.set(x, y, z);
			BlockState state = context.getBlockState(level, context.scratchPos);
			if (isPoweredRailWithAxis(self, state, railShape)) {
				if (state.getValue(PoweredRailBlock.SHAPE) != railShape) {
					return COMPLEX_PATH;
				}
				context.scratchPos.set(x, y - 1, z);
				if (isPoweredRailWithAxis(self, context.getBlockState(level, context.scratchPos), railShape)) {
					return COMPLEX_PATH;
				}
				context.scratchPos.set(x, y, z);
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
			if (isPoweredRailWithAxis(self, belowState, railShape)) {
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

		boolean checkBelow = true;
		RailShape railShape = state.getValue(PoweredRailBlock.SHAPE);

		switch (railShape) {
			case NORTH_SOUTH -> z += forward ? 1 : -1;
			case EAST_WEST -> x += forward ? -1 : 1;
			case ASCENDING_EAST -> {
				if (forward) {
					--x;
				} else {
					++x;
					++y;
					checkBelow = false;
				}
				railShape = RailShape.EAST_WEST;
			}
			case ASCENDING_WEST -> {
				if (forward) {
					--x;
					++y;
					checkBelow = false;
				} else {
					++x;
				}
				railShape = RailShape.EAST_WEST;
			}
			case ASCENDING_NORTH -> {
				if (forward) {
					++z;
				} else {
					--z;
					++y;
					checkBelow = false;
				}
				railShape = RailShape.NORTH_SOUTH;
			}
			case ASCENDING_SOUTH -> {
				if (forward) {
					++z;
					++y;
					checkBelow = false;
				} else {
					--z;
				}
				railShape = RailShape.NORTH_SOUTH;
			}
			default -> {
				return SEARCH_NOT_FOUND;
			}
		}

		int poweredDistance = findPoweredRailSignalAt(self, level, x, y, z, forward, distance, railShape, context);
		if (poweredDistance != SEARCH_NOT_FOUND) {
			return poweredDistance;
		}
		return checkBelow
				? findPoweredRailSignalAt(self, level, x, y - 1, z, forward, distance, railShape, context)
				: SEARCH_NOT_FOUND;
	}

	static BlockState findNextRailState(PoweredRailBlock self, Level level, MutableBlockPos railPos, BlockState state,
			boolean forward, RailUpdateContext context) {
		MutableBlockPos scratchPos = context.scratchPos;
		int x = railPos.getX();
		int y = railPos.getY();
		int z = railPos.getZ();
		boolean checkBelow = true;
		RailShape railShape = state.getValue(PoweredRailBlock.SHAPE);

		switch (railShape) {
			case NORTH_SOUTH -> z += forward ? 1 : -1;
			case EAST_WEST -> x += forward ? -1 : 1;
			case ASCENDING_EAST -> {
				if (forward) {
					--x;
				} else {
					++x;
					++y;
					checkBelow = false;
				}
				railShape = RailShape.EAST_WEST;
			}
			case ASCENDING_WEST -> {
				if (forward) {
					--x;
					++y;
					checkBelow = false;
				} else {
					++x;
				}
				railShape = RailShape.EAST_WEST;
			}
			case ASCENDING_NORTH -> {
				if (forward) {
					++z;
				} else {
					--z;
					++y;
					checkBelow = false;
				}
				railShape = RailShape.NORTH_SOUTH;
			}
			case ASCENDING_SOUTH -> {
				if (forward) {
					++z;
					++y;
					checkBelow = false;
				} else {
					--z;
				}
				railShape = RailShape.NORTH_SOUTH;
			}
			default -> {
				return null;
			}
		}

		scratchPos.set(x, y, z);
		BlockState nextState = context.getBlockState(level, scratchPos);
		if (isSameRailWithAxis(self, nextState, railShape)) {
			railPos.set(scratchPos);
			return nextState;
		}

		if (!checkBelow) {
			return null;
		}

		scratchPos.set(x, y - 1, z);
		nextState = context.getBlockState(level, scratchPos);
		if (isSameRailWithAxis(self, nextState, railShape)) {
			railPos.set(scratchPos);
			return nextState;
		}

		return null;
	}

	static boolean connectsBackTo(PoweredRailBlock self, Level level,
			BlockPos railPos, BlockState state, long expectedPreviousPos, BlockState previousState,
			RailUpdateContext context) {
		if (directionConnectsBackTo(
				self, level, railPos, state, true, expectedPreviousPos, previousState, context)) {
			return true;
		}
		return directionConnectsBackTo(
				self, level, railPos, state, false, expectedPreviousPos, previousState, context);
	}

	private static boolean directionConnectsBackTo(
			PoweredRailBlock self, Level level, BlockPos railPos, BlockState state, boolean forward,
			long expectedPreviousPos, BlockState previousState, RailUpdateContext context) {
		int x = railPos.getX();
		int y = railPos.getY();
		int z = railPos.getZ();
		boolean checkBelow = true;
		RailShape railShape = state.getValue(PoweredRailBlock.SHAPE);

		switch (railShape) {
			case NORTH_SOUTH -> z += forward ? 1 : -1;
			case EAST_WEST -> x += forward ? -1 : 1;
			case ASCENDING_EAST -> {
				if (forward) {
					--x;
				} else {
					++x;
					++y;
					checkBelow = false;
				}
				railShape = RailShape.EAST_WEST;
			}
			case ASCENDING_WEST -> {
				if (forward) {
					--x;
					++y;
					checkBelow = false;
				} else {
					++x;
				}
				railShape = RailShape.EAST_WEST;
			}
			case ASCENDING_NORTH -> {
				if (forward) {
					++z;
				} else {
					--z;
					++y;
					checkBelow = false;
				}
				railShape = RailShape.NORTH_SOUTH;
			}
			case ASCENDING_SOUTH -> {
				if (forward) {
					++z;
					++y;
					checkBelow = false;
				} else {
					--z;
				}
				railShape = RailShape.NORTH_SOUTH;
			}
			default -> {
				return false;
			}
		}

		if (BlockPos.asLong(x, y, z) == expectedPreviousPos) {
			return isSameRailWithAxis(self, previousState, railShape);
		}
		if (!checkBelow || BlockPos.asLong(x, y - 1, z) != expectedPreviousPos
				|| !isSameRailWithAxis(self, previousState, railShape)) {
			return false;
		}

		context.scratchPos.set(x, y, z);
		return !isSameRailWithAxis(
				self, context.getBlockState(level, context.scratchPos), railShape);
	}

	private static boolean isSameRailWithAxis(PoweredRailBlock self, BlockState state, RailShape expectedShape) {
		return state.is(self) &&
				!isMismatchedRailAxis(expectedShape, state.getValue(PoweredRailBlock.SHAPE));
	}

	private static boolean isPoweredRailWithAxis(
			PoweredRailBlock self, BlockState state, RailShape expectedShape) {
		return isSameRailWithAxis(self, state, expectedShape)
				&& state.getValue(PoweredRailBlock.POWERED);
	}

}
