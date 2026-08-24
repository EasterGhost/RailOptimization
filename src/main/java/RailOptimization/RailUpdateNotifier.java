package RailOptimization;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

final class RailUpdateNotifier {

	private RailUpdateNotifier() {
	}

	@SuppressWarnings("null")
	static void notifyNeighborChanged(Level level, int x, int y, int z, Block sourceBlock, MutableBlockPos scratchPos) {
		scratchPos.set(x, y, z);
		level.updateNeighborsAt(scratchPos, sourceBlock);
	}

	@SuppressWarnings("null")
	private static void notifyBlockChanged(Level level, int x, int y, int z, Block sourceBlock, MutableBlockPos scratchPos) {
		scratchPos.set(x, y, z);
		level.neighborChanged(scratchPos.immutable(), sourceBlock, null);
	}

	static void updateRails(boolean eastWest, Level world, BlockPos pos, BlockState mainState, int firstDirectionCount, int secondDirectionCount,
			MutableBlockPos scratchPos) {
		Block block = mainState.getBlock();
		Direction[] directions = eastWest ? RailLogic.EAST_WEST_DIR : RailLogic.NORTH_SOUTH_DIR;

		if (firstDirectionCount == 0 && secondDirectionCount > 0) {
			updateRailSection(world, pos, block, directions[1], 1, secondDirectionCount, scratchPos);
			updateRailSection(world, pos, block, directions[0], 0, firstDirectionCount, scratchPos);
			return;
		}

		for (int i = 0; i < directions.length; ++i) {
			int countAmt = i == 0 ? firstDirectionCount : secondDirectionCount;

			if (i == 1 && countAmt == 0) {
				continue;
			}

			updateRailSection(world, pos, block, directions[i], i, countAmt, scratchPos);
		}
	}

	private static void updateRailSection(Level world, BlockPos pos, Block block, Direction dir, int directionIndex, int countAmt, MutableBlockPos scratchPos) {
		final int baseX = pos.getX();
		final int baseY = pos.getY();
		final int baseZ = pos.getZ();

		final int stepX = dir.getStepX();
		final int stepZ = dir.getStepZ();

		int x = baseX + stepX * countAmt;
		int z = baseZ + stepZ * countAmt;

		notifyNeighborChanged(world, x, baseY, z, block, scratchPos);
		notifyBlockChanged(world, x + stepX, baseY, z + stepZ, block, scratchPos);
		notifyNeighborChanged(world, x, baseY - 1, z, block, scratchPos);
		notifyBlockChanged(world, x + stepX, baseY - 1, z + stepZ, block, scratchPos);

		for (int c = countAmt - 1; c >= directionIndex; c--) {
			x = baseX + stepX * c;
			z = baseZ + stepZ * c;

			notifyNeighborChanged(world, x, baseY, z, block, scratchPos);
			notifyNeighborChanged(world, x, baseY - 1, z, block, scratchPos);
		}
	}
}
