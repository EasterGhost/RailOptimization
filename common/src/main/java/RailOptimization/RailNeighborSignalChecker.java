package RailOptimization;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

final class RailNeighborSignalChecker {
	private static final Direction[] SIGNAL_DIRECTIONS = new Direction[] { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST,
			Direction.EAST };
	private static final Direction[][] DIRECT_SIGNAL_DIRECTIONS = createDirectSignalDirections();

	private RailNeighborSignalChecker() {
	}

	static boolean hasNeighborSignalFast(
			Level level, BlockPos pos, MutableBlockPos scratchPos) {
		int x = pos.getX();
		int z = pos.getZ();
		int chunkX = x >> 4;
		int chunkZ = z >> 4;
		LevelChunk chunk = level.getChunk(chunkX, chunkZ);

		return hasNeighborSignalFast(level, pos, scratchPos, chunk, chunkX, chunkZ);
	}

	static BlockState belowStateWhenNoNeighborSignal(Level level, BlockPos pos, MutableBlockPos scratchPos, LevelChunk chunk, int chunkX, int chunkZ) {
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();

		int localX = x & 15;
		int localZ = z & 15;
		if (localX >= 2 && localX <= 13 && localZ >= 2 && localZ <= 13 && y >= level.getMinY() + 2 && y <= level.getMaxY() - 2) {
			return belowStateWhenNoNeighborSignalInChunk(level, chunk, x, y, z, scratchPos);
		}
		return belowStateWhenNoNeighborSignal(level, chunk, chunkX, chunkZ, x, y, z, scratchPos);
	}

	static boolean hasNeighborSignalFast(Level level, BlockPos pos, MutableBlockPos scratchPos, LevelChunk chunk, int chunkX, int chunkZ) {
		return belowStateWhenNoNeighborSignal(level, pos, scratchPos, chunk, chunkX, chunkZ) == null;
	}

	@SuppressWarnings("null")
	private static BlockState belowStateWhenNoNeighborSignal(Level level, LevelChunk chunk, int chunkX, int chunkZ, int x, int y, int z,
			MutableBlockPos scratchPos) {
		BlockState belowState = null;
		for (Direction direction : SIGNAL_DIRECTIONS) {
			int neighborX = x + direction.getStepX();
			int neighborY = y + direction.getStepY();
			int neighborZ = z + direction.getStepZ();
			scratchPos.set(neighborX, neighborY, neighborZ);
			BlockState neighborState = getBlockState(level, chunk, chunkX, chunkZ, scratchPos);
			if (direction == Direction.DOWN) {
				belowState = neighborState;
			}

			if (neighborState.getSignal(level, scratchPos, direction) > 0) {
				return null;
			}
			if (!neighborState.isRedstoneConductor(level, scratchPos)) {
				continue;
			}

			for (Direction directDirection : DIRECT_SIGNAL_DIRECTIONS[direction.ordinal()]) {
				scratchPos.set(
						neighborX + directDirection.getStepX(),
						neighborY + directDirection.getStepY(),
						neighborZ + directDirection.getStepZ());
				BlockState directState = getBlockState(level, chunk, chunkX, chunkZ, scratchPos);
				if (directState.getDirectSignal(level, scratchPos, directDirection) > 0) {
					return null;
				}
			}
		}
		return belowState;
	}

	@SuppressWarnings("null")
	private static BlockState belowStateWhenNoNeighborSignalInChunk(Level level, LevelChunk chunk, int x, int y, int z, MutableBlockPos scratchPos) {
		BlockState belowState = null;
		for (Direction direction : SIGNAL_DIRECTIONS) {
			int neighborX = x + direction.getStepX();
			int neighborY = y + direction.getStepY();
			int neighborZ = z + direction.getStepZ();
			scratchPos.set(neighborX, neighborY, neighborZ);
			BlockState neighborState = chunk.getBlockState(scratchPos);
			if (direction == Direction.DOWN) {
				belowState = neighborState;
			}

			if (neighborState.getSignal(level, scratchPos, direction) > 0) {
				return null;
			}
			if (!neighborState.isRedstoneConductor(level, scratchPos)) {
				continue;
			}

			for (Direction directDirection : DIRECT_SIGNAL_DIRECTIONS[direction.ordinal()]) {
				scratchPos.set(
						neighborX + directDirection.getStepX(),
						neighborY + directDirection.getStepY(),
						neighborZ + directDirection.getStepZ());
				BlockState directState = chunk.getBlockState(scratchPos);
				if (directState.getDirectSignal(level, scratchPos, directDirection) > 0) {
					return null;
				}
			}
		}
		return belowState;
	}

	private static Direction[][] createDirectSignalDirections() {
		Direction[][] directions = new Direction[Direction.values().length][];
		for (Direction direction : SIGNAL_DIRECTIONS) {
			Direction opposite = direction.getOpposite();
			Direction[] directDirections = new Direction[SIGNAL_DIRECTIONS.length - 1];
			int index = 0;
			for (Direction directDirection : SIGNAL_DIRECTIONS) {
				if (directDirection != opposite) {
					directDirections[index++] = directDirection;
				}
			}
			directions[direction.ordinal()] = directDirections;
		}
		return directions;
	}

	private static BlockState getBlockState(Level level, LevelChunk chunk, int chunkX, int chunkZ, BlockPos pos) {
		if ((pos.getX() >> 4) == chunkX
				&& (pos.getZ() >> 4) == chunkZ
				&& level.isInValidBounds(pos)) {
			return chunk.getBlockState(pos);
		}
		return level.getBlockState(pos);
	}
}
