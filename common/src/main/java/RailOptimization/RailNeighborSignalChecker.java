package RailOptimization;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

final class RailNeighborSignalChecker {
	private static final Direction[] SGN_DIR = new Direction[] { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST,
			Direction.EAST };
	private static final Direction[][] DIRECT_SGN_DIR = createDirectSignalDirections();

	private RailNeighborSignalChecker() {
	}

	static boolean hasNeighborSignalFast(Level level, BlockPos pos, MutableBlockPos scratchPos, LevelChunk chunk, int chunkX, int chunkZ) {
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();

		int localX = x & 15;
		int localZ = z & 15;
		if (localX >= 2 && localX <= 13 && localZ >= 2 && localZ <= 13 && y >= level.getMinY() + 2 && y <= level.getMaxY() - 2) {
			return hasNeighborSignalInChunk(level, chunk, x, y, z, scratchPos);
		}
		if (!level.isInValidBounds(scratchPos.set(x - 2, y - 2, z - 2))
				|| !level.isInValidBounds(scratchPos.set(x + 2, y + 2, z + 2))) {
			return level.hasNeighborSignal(scratchPos.set(x, y, z));
		}
		return hasNeighborSignalInWindow(level, chunk, chunkX, chunkZ, x, y, z, scratchPos);
	}

	@SuppressWarnings("null")
	private static boolean hasNeighborSignalInWindow(Level level, LevelChunk chunk, int chunkX, int chunkZ, int x, int y, int z,
			MutableBlockPos scratchPos) {
		int origin = (chunkX & 1) | ((chunkZ & 1) << 1);
		LevelChunk chunk00 = origin == 0 ? chunk : null;
		LevelChunk chunk10 = origin == 1 ? chunk : null;
		LevelChunk chunk01 = origin == 2 ? chunk : null;
		LevelChunk chunk11 = origin == 3 ? chunk : null;
		for (Direction direction : SGN_DIR) {
			int neighborX = x + direction.getStepX();
			int neighborY = y + direction.getStepY();
			int neighborZ = z + direction.getStepZ();
			scratchPos.set(neighborX, neighborY, neighborZ);
			int slot = ((neighborX >> 4) & 1) | ((neighborZ >> 3) & 2);
			LevelChunk readChunk = switch (slot) {
				case 0 -> chunk00;
				case 1 -> chunk10;
				case 2 -> chunk01;
				default -> chunk11;
			};
			if (readChunk == null) {
				readChunk = level.getChunk(neighborX >> 4, neighborZ >> 4);
				switch (slot) {
					case 0 -> chunk00 = readChunk;
					case 1 -> chunk10 = readChunk;
					case 2 -> chunk01 = readChunk;
					default -> chunk11 = readChunk;
				}
			}
			BlockState neighborState = readChunk.getBlockState(scratchPos);
			if (neighborState.getSignal(level, scratchPos, direction) > 0) return true;
			if (!neighborState.isRedstoneConductor(level, scratchPos)) continue;
			for (Direction directDirection : DIRECT_SGN_DIR[direction.ordinal()]) {
				int directX = neighborX + directDirection.getStepX();
				int directY = neighborY + directDirection.getStepY();
				int directZ = neighborZ + directDirection.getStepZ();
				scratchPos.set(directX, directY, directZ);
				slot = ((directX >> 4) & 1) | ((directZ >> 3) & 2);
				readChunk = switch (slot) {
					case 0 -> chunk00;
					case 1 -> chunk10;
					case 2 -> chunk01;
					default -> chunk11;
				};
				if (readChunk == null) {
					readChunk = level.getChunk(directX >> 4, directZ >> 4);
					switch (slot) {
						case 0 -> chunk00 = readChunk;
						case 1 -> chunk10 = readChunk;
						case 2 -> chunk01 = readChunk;
						default -> chunk11 = readChunk;
					}
				}
				BlockState directState = readChunk.getBlockState(scratchPos);
				if (directState.getDirectSignal(level, scratchPos, directDirection) > 0) return true;
			}
		}
		return false;
	}

	@SuppressWarnings("null")
	private static boolean hasNeighborSignalInChunk(Level level, LevelChunk chunk, int x, int y, int z, MutableBlockPos scratchPos) {
		for (Direction direction : SGN_DIR) {
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

			for (Direction directDirection : DIRECT_SGN_DIR[direction.ordinal()]) {
				scratchPos.set(neighborX + directDirection.getStepX(), neighborY + directDirection.getStepY(), neighborZ + directDirection.getStepZ());
				BlockState directState = chunk.getBlockState(scratchPos);
				if (directState.getDirectSignal(level, scratchPos, directDirection) > 0) {
					return true;
				}
			}
		}
		return false;
	}

	private static Direction[][] createDirectSignalDirections() {
		Direction[][] directions = new Direction[Direction.values().length][];
		for (Direction direction : SGN_DIR) {
			Direction opposite = direction.getOpposite();
			Direction[] directDirections = new Direction[SGN_DIR.length - 1];
			int index = 0;
			for (Direction directDirection : SGN_DIR) {
				if (directDirection != opposite) {
					directDirections[index++] = directDirection;
				}
			}
			directions[direction.ordinal()] = directDirections;
		}
		return directions;
	}
}
