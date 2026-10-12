package RailOptimization;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

final class RailSignalSearcher {
	static final int COMPLEX_PATH = -1;

	private RailSignalSearcher() {
	}

	static boolean findPoweredRailSignalFaster(PoweredRailBlock self, Level level, BlockPos pos, BlockState state, boolean forward, int distance,
			RailUpdateContext context) {
		int shape = RailPath.railData(state) & RailStateAccess.SHAPE_MASK;
		int limit = context.railPowerLimit;
		byte axis = RailPath.RAIL_AXIS[shape];
		if (distance >= limit || axis == RailPath.AXIS_NONE) {
			return false;
		}
		int direction = forward ? 0 : 1;
		int step = (shape << 1) | direction;
		int stepX = RailPath.STEP_X[step];
		int stepZ = RailPath.STEP_Z[step];
		byte oppositeAxis = axis == RailPath.AXIS_EAST_WEST ? RailPath.AXIS_NORTH_SOUTH : RailPath.AXIS_EAST_WEST;
		byte flags = (byte) (RailSearchCache.SEARCH | (forward ? RailSearchCache.SEARCH_FORWARD : 0)
				| (axis == RailPath.AXIS_NORTH_SOUTH ? RailSearchCache.SEARCH_NORTH_SOUTH : 0));
		int x = pos.getX() + stepX;
		int y = pos.getY() + RailPath.STEP_Y[step];
		int z = pos.getZ() + stepZ;
		int depth = distance;
		long[] positions = context.connectedRailPositions;
		boolean checkBelow = RailPath.STEP_BELOW[step] != 0;
		while (true) {
			long position = BlockPos.asLong(x, y, z);
			positions[depth] = position;
			int cost = context.getPoweredSearchCost(position, flags);
			if (cost < 0) {
				context.scratchPos.set(x, y, z);
				BlockState candidate = context.getBlockState(level, context.scratchPos);
				if (!candidate.is(self)) {
					if (!checkBelow) return false;
					--y;
					checkBelow = false;
					continue;
				}
				int data = RailPath.railData(candidate);
				shape = data & RailStateAccess.SHAPE_MASK;
				byte railAxis = RailPath.RAIL_AXIS[shape];
				if (railAxis == oppositeAxis || (data & RailStateAccess.POWERED_MASK) == 0) return false;
				if (context.hasNeighborSignal(level, context.scratchPos)) {
					context.cachePoweredSearchCost(position, flags, 0);
					cost = 0;
				} else {
					if (depth + 1 >= limit || railAxis == RailPath.AXIS_NONE) return false;
					step = (shape << 1) | direction;
					x += stepX;
					y += RailPath.STEP_Y[step];
					z += stepZ;
					++depth;
					checkBelow = RailPath.STEP_BELOW[step] != 0;
					continue;
				}
			}
			if (depth + cost >= limit) return false;
			cacheSearchPath(context, positions, flags, distance, depth - 1, depth + cost);
			return true;
		}
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
		RailShape flatShape = axis == RailPath.AXIS_EAST_WEST ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;

		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		int powerLimit = RailLogic.getRailPowerLimit();
		int poweredLength = 0;

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
				if (context.hasNeighborSignal(level, context.scratchPos)) {
					return Math.max(0, Math.min(powerLimit, index - powerLimit - 1));
				}
				if (index <= powerLimit) {
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
		int previousY = pos.getY();
		int powerLimit = RailLogic.getRailPowerLimit();
		int poweredLength = 0;

		for (int index = 1; index <= powerLimit * 2; ++index) {
			BlockState state = RailPath.findNextRailState(self, level, cursor, previousState, forward, context);
			if (state == null || !RailPath.isPowered(state)) {
				break;
			}

			RailShape currentShape = RailPath.railShape(state);
			if (currentShape != previousShape
					&& !RailPath.connectsBackTo(self, level, cursor, state, forward, previousY, previousState, context)) {
				return COMPLEX_PATH;
			}
			if (context.hasNeighborSignal(level, cursor)) {
				return Math.max(0, Math.min(powerLimit, index - powerLimit - 1));
			}
			if (index <= powerLimit) {
				railStates[index - 1] = state;
				railPositions[index - 1] = cursor.asLong();
			}
			poweredLength = index;
			previousState = state;
			previousShape = currentShape;
			previousY = cursor.getY();
		}

		return Math.min(powerLimit, poweredLength);
	}

	private static void cacheSearchPath(RailUpdateContext context, long[] positions, byte flags, int first, int last, int poweredDistance) {
		for (int depth = last; depth >= first; --depth) {
			context.cachePoweredSearchCost(positions[depth], flags, poweredDistance - depth);
		}
	}
}
