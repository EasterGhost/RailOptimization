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
    private static void notifyBlockChanged(Level level, int x, int y, int z, Block sourceBlock,
            MutableBlockPos scratchPos) {
        scratchPos.set(x, y, z);
        level.neighborChanged(scratchPos.immutable(), sourceBlock, null);
    }

    static void updateRails(boolean eastWest, Level world, BlockPos pos,
            BlockState mainState, int firstDirectionCount, int secondDirectionCount, MutableBlockPos scratchPos) {
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

    private static void updateRailSection(Level world, BlockPos pos, Block block, Direction dir,
            int directionIndex, int countAmt, MutableBlockPos scratchPos) {
        final int baseX = pos.getX();
        final int baseY = pos.getY();
        final int baseZ = pos.getZ();

        final int stepX = dir.getStepX();
        final int stepZ = dir.getStepZ();

        for (int c = countAmt; c >= directionIndex; c--) {
            int x = baseX + stepX * c;
            int y = baseY;
            int z = baseZ + stepZ * c;

            boolean hasEndPos = c == countAmt;
            int endX = x + stepX;
            int endZ = z + stepZ;

            notifyNeighborChanged(world, x, y, z, block, scratchPos);
            if (hasEndPos) {
                notifyBlockChanged(world, endX, y, endZ, block, scratchPos);
            }
            notifyNeighborChanged(world, x, y - 1, z, block, scratchPos);
            if (hasEndPos) {
                notifyBlockChanged(world, endX, y - 1, endZ, block, scratchPos);
            }
        }
    }
}
