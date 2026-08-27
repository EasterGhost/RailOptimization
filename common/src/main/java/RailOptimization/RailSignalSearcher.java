package RailOptimization;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

final class RailSignalSearcher {
	static final int COMPLEX_PATH = -1;
	private static final int SEARCH_NOT_FOUND = -1;
	private static final int SEARCH_NOT_FOUND_BELOW_BLOCKED = -2;

	private RailSignalSearcher() {
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

	static boolean findPoweredRailSignalFaster(PoweredRailBlock self, Level level, BlockPos pos, BlockState state, boolean forward, int distance,
			RailUpdateContext context) {
		return findPoweredRailSignalFromState(self, level, pos.getX(), pos.getY(), pos.getZ(), state, forward, distance, context) >= 0;
	}

	static int countStraightRailsToDepower(PoweredRailBlock self, Level level, BlockPos pos, RailShape railShape, boolean forward, RailUpdateContext context) {
		byte axis = RailPath.RAIL_AXIS[railShape.ordinal()];
		if (axis == RailPath.AXIS_NONE) {
			return COMPLEX_PATH;
		}
		BlockState[] railStates = context.straightRailStates;

		int stepIndex = (railShape.ordinal() << 1) | (forward ? 0 : 1);
		int stepX = RailPath.STEP_X[stepIndex];
		int stepY = RailPath.STEP_Y[stepIndex];
		int stepZ = RailPath.STEP_Z[stepIndex];
		boolean stepBelow = RailPath.STEP_BELOW[stepIndex] != 0;
		RailShape flatShape = axis == RailPath.AXIS_EAST_WEST ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;

		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		int powerLimit = RailLogic.getRailPowerLimit();
		int poweredLength = 0;

		context.scratchPos.set(x, y - 1, z);
		if (RailPath.isPoweredRailWithAxis(self, context.getBlockState(level, context.scratchPos), flatShape)) {
			return COMPLEX_PATH;
		}

		for (int index = 1; index <= powerLimit * 2; ++index) {
			x += stepX;
			y += stepY;
			z += stepZ;
			context.scratchPos.set(x, y, z);
			BlockState state = context.getBlockState(level, context.scratchPos);
			if (RailPath.isPoweredRailWithAxis(self, state, flatShape)) {
				if (RailPath.railShape(state) != railShape) {
					return COMPLEX_PATH;
				}
				if (stepBelow) {
					context.scratchPos.set(x, y - 1, z);
					if (RailPath.isPoweredRailWithAxis(self, context.getBlockState(level, context.scratchPos), flatShape)) {
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
			if (RailPath.isPoweredRailWithAxis(self, belowState, flatShape)) {
				return COMPLEX_PATH;
			}
			break;
		}

		return Math.min(powerLimit, poweredLength);
	}

	static int countConnectedRailsToDepower(PoweredRailBlock self, Level level, BlockPos pos, BlockState sourceState, boolean forward,
			RailUpdateContext context) {
		if (!RailPath.supportsFastSearch(RailPath.railShape(sourceState))) {
			return COMPLEX_PATH;
		}
		BlockState[] railStates = context.straightRailStates;
		long[] railPositions = context.connectedRailPositions;

		MutableBlockPos cursor = context.railCursor;
		cursor.set(pos.getX(), pos.getY(), pos.getZ());
		BlockState previousState = sourceState;
		RailShape previousShape = RailPath.railShape(sourceState);
		long previousPosition = pos.asLong();
		int powerLimit = RailLogic.getRailPowerLimit();
		int poweredLength = 0;

		for (int index = 1; index <= powerLimit * 2; ++index) {
			BlockState state = RailPath.findNextRailState(self, level, cursor, previousState, forward, context);
			if (state == null || !RailPath.isPowered(state)) {
				break;
			}

			long position = cursor.asLong();
			RailShape currentShape = RailPath.railShape(state);
			if (currentShape != previousShape
					&& !RailPath.connectsBackTo(self, level, cursor, state, previousPosition, previousState, context)) {
				return COMPLEX_PATH;
			}
			if (context.hasNeighborSignal(level, cursor)) {
				return Math.max(0, Math.min(powerLimit, index - powerLimit - 1));
			}
			if (index <= powerLimit) {
				railStates[index - 1] = state;
				railPositions[index - 1] = position;
			}
			poweredLength = index;
			previousState = state;
			previousShape = currentShape;
			previousPosition = position;
		}

		return Math.min(powerLimit, poweredLength);
	}

	private static int findPoweredRailSignalAt(PoweredRailBlock self, Level world, int x, int y, int z, boolean forward, int distance,
			RailShape expectedShape, RailUpdateContext context) {
		long posKey = BlockPos.asLong(x, y, z);
		byte cacheFlags = checkedPosFlags(forward, expectedShape);
		int cachedCost = context.getPoweredSearchCost(posKey, cacheFlags);
		if (cachedCost >= 0) {
			return distance + cachedCost < RailLogic.getRailPowerLimit() ? distance + cachedCost : SEARCH_NOT_FOUND;
		}

		context.scratchPos.set(x, y, z);
		BlockState blockState = context.getBlockState(world, context.scratchPos);
		if (!blockState.is(self)) {
			return SEARCH_NOT_FOUND;
		}

		int railData = RailPath.railData(blockState);
		RailShape actualShape = RailPath.RAIL_SHAPES[railData & RailStateAccess.SHAPE_MASK];
		if (RailPath.isMismatchedRailAxis(expectedShape, actualShape) || (railData & RailStateAccess.POWERED_MASK) == 0) {
			return SEARCH_NOT_FOUND;
		}

		BlockState belowState = context.belowStateWhenNoNeighborSignal(world, context.scratchPos, blockState);
		if (belowState == null) {
			context.cachePoweredSearchCost(posKey, cacheFlags, 0);
			return distance;
		}

		int poweredDistance = findPoweredRailSignalFromState(
				self, world, x, y, z, blockState, forward, distance + 1, context);
		if (poweredDistance >= 0) {
			context.cachePoweredSearchCost(posKey, cacheFlags, poweredDistance - distance);
			return poweredDistance;
		}
		return !RailPath.isPoweredRailWithAxis(self, belowState, expectedShape)
				? SEARCH_NOT_FOUND_BELOW_BLOCKED
				: SEARCH_NOT_FOUND;
	}

	private static int findPoweredRailSignalFromState(PoweredRailBlock self, Level level, int x, int y, int z, BlockState state,
			boolean forward, int distance, RailUpdateContext context) {
		if (distance >= RailLogic.getRailPowerLimit()) {
			return SEARCH_NOT_FOUND;
		}

		RailShape railShape = RailPath.railShape(state);
		if (RailPath.RAIL_AXIS[railShape.ordinal()] == RailPath.AXIS_NONE) {
			return SEARCH_NOT_FOUND;
		}

		int stepIndex = (railShape.ordinal() << 1) | (forward ? 0 : 1);
		int nextX = x + RailPath.STEP_X[stepIndex];
		int nextY = y + RailPath.STEP_Y[stepIndex];
		int nextZ = z + RailPath.STEP_Z[stepIndex];
		RailShape flatShape = RailPath.STEP_FLAT[stepIndex];

		int poweredDistance = findPoweredRailSignalAt(
				self, level, nextX, nextY, nextZ, forward, distance, flatShape, context);
		if (poweredDistance >= 0) {
			return poweredDistance;
		}
		if (poweredDistance == SEARCH_NOT_FOUND_BELOW_BLOCKED || RailPath.STEP_BELOW[stepIndex] == 0) {
			return SEARCH_NOT_FOUND;
		}

		poweredDistance = findPoweredRailSignalAt(
				self, level, nextX, nextY - 1, nextZ, forward, distance, flatShape, context);
		return poweredDistance >= 0 ? poweredDistance : SEARCH_NOT_FOUND;
	}
}
