package RailOptimization;

import org.jspecify.annotations.NonNull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.chunk.LevelChunk;

final class RailDepowerPlanner {
	static final int NEEDS_GENERAL_QUERY = -1;
	static final int KEEP_POWERED = -2;

	private RailDepowerPlanner() {
	}

	static int findSupplyOrPlan(PoweredRailBlock self, Level level, @NonNull BlockPos pos, BlockState state, RailUpdateContext context) {
		int limit = context.railPowerLimit;
		int first = scan(self, level, pos, state, true, context, 0, 0, limit);
		if (first < 0)
			return first;
		long firstChecks = context.pendingConnectionChecks;
		int second = scan(self, level, pos, state, false, context, limit, 0, limit);
		if (second < 0)
			return second;
		if (!validateConnections(self, level, pos, state, true, context, 0, firstChecks)
				|| !validateConnections(self, level, pos, state, false, context, limit, context.pendingConnectionChecks)) {
			return NEEDS_GENERAL_QUERY;
		}

		if (first == limit) {
			first = scan(self, level, pos, state, true, context, 0, limit, limit * 2);
			if (first < 0)
				return first;
		}
		if (second == limit) {
			second = scan(self, level, pos, state, false, context, limit, limit, limit * 2);
			if (second < 0)
				return second;
		}
		return (first << 8) | second;
	}

	@SuppressWarnings("null")
	private static int scan(PoweredRailBlock self, Level level, @NonNull BlockPos origin, BlockState sourceState, boolean forward, RailUpdateContext context,
			int offset, int start, int end) {
		MutableBlockPos cursor = context.railCursor;
		BlockState previousState;
		if (start == 0) {
			cursor.set(origin);
			previousState = sourceState;
		} else {
			long position = context.connectedRailPositions[offset + start - 1];
			cursor.set(BlockPos.getX(position), BlockPos.getY(position), BlockPos.getZ(position));
			previousState = context.straightRailStates[offset + start - 1];
		}
		int previousShape = RailPath.railData(previousState) & RailStateAccess.SHAPE_MASK;
		int poweredLength = start;
		int limit = context.railPowerLimit;
		long connectionChecks = 0;
		LevelChunk chunk = context.getChunk(level, cursor);
		int chunkX = cursor.getX() >> 4;
		int chunkZ = cursor.getZ() >> 4;
		walk: for (int index = start + 1; index <= end; ++index) {
			int previousY = cursor.getY();
			int step = (previousShape << 1) | (forward ? 0 : 1);
			int x = cursor.getX() + RailPath.STEP_X[step];
			int y = cursor.getY() + RailPath.STEP_Y[step];
			int z = cursor.getZ() + RailPath.STEP_Z[step];
			RailShape axisShape = RailPath.STEP_FLAT[step];
			boolean checkBelow = RailPath.STEP_BELOW[step] != 0;
			int nextChunkX = x >> 4;
			int nextChunkZ = z >> 4;
			BlockState state;
			boolean primary = true;
			while (true) {
				context.scratchPos.set(x, y, z);
				if (nextChunkX == chunkX && nextChunkZ == chunkZ) {
					state = chunk.getBlockState(context.scratchPos);
				} else if (!level.isInValidBounds(context.scratchPos)) {
					state = level.getBlockState(context.scratchPos);
				} else {
					chunk = context.getChunk(level, context.scratchPos);
					chunkX = nextChunkX;
					chunkZ = nextChunkZ;
					state = chunk.getBlockState(context.scratchPos);
				}
				if (RailPath.isSameRailWithAxis(self, state, axisShape))
					break;
				if (!primary || !checkBelow)
					break walk;
				primary = false;
				--y;
			}
			if (!RailPath.isPowered(state)) {
				break;
			}

			cursor.set(x, y, z);
			if (context.hasNeighborSignal(level, cursor)) {
				return index <= limit ? KEEP_POWERED : Math.max(0, index - limit - 1);
			}
			int shape = RailPath.railData(state) & RailStateAccess.SHAPE_MASK;
			boolean needsConnectionCheck = shape != previousShape || RailPath.isAscending(shape) || !primary;
			if (index <= limit) {
				if (needsConnectionCheck)
					connectionChecks |= 1L << (index - 1);
				context.straightRailStates[offset + index - 1] = state;
				context.connectedRailPositions[offset + index - 1] = cursor.asLong();
			} else if (needsConnectionCheck
					&& !RailPath.connectsBackTo(self, level, cursor, state, forward, previousY, previousState, context)) {
				return NEEDS_GENERAL_QUERY;
			}
			poweredLength = index;
			previousState = state;
			previousShape = shape;
		}
		context.pendingConnectionChecks = connectionChecks;
		return Math.min(limit, poweredLength);
	}

	private static boolean validateConnections(PoweredRailBlock self, Level level, BlockPos origin, BlockState sourceState, boolean forward,
			RailUpdateContext context, int offset, long checks) {
		while (checks != 0) {
			int index = Long.numberOfTrailingZeros(checks);
			checks &= checks - 1;
			long position = context.connectedRailPositions[offset + index];
			BlockState state = context.straightRailStates[offset + index];
			int previousY = index == 0 ? origin.getY() : BlockPos.getY(context.connectedRailPositions[offset + index - 1]);
			BlockState previousState = index == 0 ? sourceState : context.straightRailStates[offset + index - 1];
			context.railCursor.set(BlockPos.getX(position), BlockPos.getY(position), BlockPos.getZ(position));
			if (!RailPath.connectsBackTo(self, level, context.railCursor, state, forward, previousY, previousState, context))
				return false;
		}
		return true;
	}
}
