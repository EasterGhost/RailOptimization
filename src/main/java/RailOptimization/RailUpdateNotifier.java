package RailOptimization;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;

final class RailUpdateNotifier {

    private RailUpdateNotifier() {
    }

    @SuppressWarnings("null")
    static void notifyNeighborChanged(Level level, int x, int y, int z, Block sourceBlock, MutableBlockPos scratchPos) {
        scratchPos.set(x, y, z);
        level.updateNeighborsAt(scratchPos, sourceBlock);
    }

    private static void notifyRailEnd(PoweredRailBlock self, Level world, int endX, int endY, int endZ, Block block,
            int railX, int railY, int railZ, MutableBlockPos scratchPos) {
        notifyNeighborChanged(world, endX, endY, endZ, block, scratchPos);

        scratchPos.set(railX, railY, railZ);
        BlockState state = world.getBlockState(scratchPos);

        if (state.is(self) && RailLogic.isAscending(state.getValue(PoweredRailBlock.SHAPE))) {
            notifyNeighborChanged(world, endX, endY + 1, endZ, block, scratchPos);
        }
    }

    static void updateRails(PoweredRailBlock self, boolean eastWest, Level world, BlockPos pos,
            BlockState mainState, int firstDirectionCount, int secondDirectionCount) {
        Block block = mainState.getBlock();
        boolean secondDirectionEmpty = secondDirectionCount == 0;
        Direction[] directions = eastWest ? RailLogic.EAST_WEST_DIR : RailLogic.NORTH_SOUTH_DIR;

        for (int i = 0; i < directions.length; ++i) {
            int countAmt = i == 0 ? firstDirectionCount : secondDirectionCount;

            if (i == 1 && countAmt == 0) {
                continue;
            }

            updateRailSection(self, world, pos, block, directions[i], i, countAmt, secondDirectionEmpty, eastWest);
        }
    }

    private static void updateRailSection(PoweredRailBlock self, Level world, BlockPos pos, Block block, Direction dir,
            int directionIndex, int countAmt, boolean secondDirectionEmpty, boolean eastWest) {
        final int baseX = pos.getX();
        final int baseY = pos.getY();
        final int baseZ = pos.getZ();

        final int stepX = dir.getStepX();
        final int stepZ = dir.getStepZ();

        MutableBlockPos scratchPos = new MutableBlockPos();

        for (int c = countAmt; c >= directionIndex; c--) {
            int x = baseX + stepX * c;
            int y = baseY;
            int z = baseZ + stepZ * c;

            boolean hasEndPos = c == countAmt;
            boolean hasOppositePos = c == 0 && secondDirectionEmpty;

            int endX = x + stepX;
            int endY = y;
            int endZ = z + stepZ;

            int oppositeX = x - stepX;
            int oppositeY = y;
            int oppositeZ = z - stepZ;

            if (eastWest) {
                if (hasOppositePos) {
                    notifyNeighborChanged(world, oppositeX, oppositeY, oppositeZ, block, scratchPos);
                }

                if (hasEndPos) {
                    notifyRailEnd(self, world, endX, endY, endZ, block, x, y, z, scratchPos);
                }

                notifyRailAndSideNeighbors(world, x, y, z, block, true, scratchPos);
            } else {
                notifyRailAndSideNeighbors(world, x, y, z, block, false, scratchPos);

                if (hasEndPos) {
                    notifyRailEnd(self, world, endX, endY, endZ, block, x, y, z, scratchPos);
                }

                if (hasOppositePos) {
                    notifyNeighborChanged(world, oppositeX, oppositeY, oppositeZ, block, scratchPos);
                }
            }

            notifyLowerRailSideNeighbors(world, x, y - 1, z, block, eastWest, scratchPos);

            if (hasEndPos) {
                notifyNeighborChanged(world, endX, endY - 1, endZ, block, scratchPos);
            }

            if (hasOppositePos) {
                notifyNeighborChanged(world, oppositeX, oppositeY - 1, oppositeZ, block, scratchPos);
            }
        }
    }

    private static void notifyRailAndSideNeighbors(Level world, int x, int y, int z, Block block, boolean eastWest,
            MutableBlockPos scratchPos) {
        if (eastWest) {
            notifyNeighborChanged(world, x, y - 1, z, block, scratchPos);
            notifyNeighborChanged(world, x, y + 1, z, block, scratchPos);
            notifyNeighborChanged(world, x, y, z - 1, block, scratchPos);
            notifyNeighborChanged(world, x, y, z + 1, block, scratchPos);
        } else {
            notifyNeighborChanged(world, x - 1, y, z, block, scratchPos);
            notifyNeighborChanged(world, x + 1, y, z, block, scratchPos);
            notifyNeighborChanged(world, x, y - 1, z, block, scratchPos);
            notifyNeighborChanged(world, x, y + 1, z, block, scratchPos);
        }
    }

    private static void notifyLowerRailSideNeighbors(Level world, int x, int y, int z, Block block, boolean eastWest,
            MutableBlockPos scratchPos) {
        if (eastWest) {
            notifyNeighborChanged(world, x, y - 1, z, block, scratchPos);
            notifyNeighborChanged(world, x, y, z - 1, block, scratchPos);
            notifyNeighborChanged(world, x, y, z + 1, block, scratchPos);
        } else {
            notifyNeighborChanged(world, x - 1, y, z, block, scratchPos);
            notifyNeighborChanged(world, x + 1, y, z, block, scratchPos);
            notifyNeighborChanged(world, x, y - 1, z, block, scratchPos);
        }
    }
}
