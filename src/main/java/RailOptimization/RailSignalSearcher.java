package RailOptimization;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

final class RailSignalSearcher {

    private RailSignalSearcher() {
    }

    static boolean findPoweredRailSignalFaster(PoweredRailBlock self, Level world, BlockPos pos,
            boolean forward, int distance, RailShape shape,
            Long2ByteMap checkedPos) {
        MutableBlockPos scratchPos = new MutableBlockPos();
        return findPoweredRailSignalAt(self, world, pos.getX(), pos.getY(), pos.getZ(), forward, distance, shape,
                checkedPos, scratchPos);
    }

    private static boolean findPoweredRailSignalAt(PoweredRailBlock self, Level world, int x, int y, int z,
            boolean forward, int distance, RailShape expectedShape, Long2ByteMap checkedPos,
            MutableBlockPos scratchPos) {
        long posKey = BlockPos.asLong(x, y, z);
        byte checked = checkedPos.get(posKey);

        if (checked == RailLogic.CHECKED_BLOCKED) {
            return false;
        }

        scratchPos.set(x, y, z);
        BlockState blockState = world.getBlockState(scratchPos);

        if (checked == RailLogic.CHECKED_POWERED) {
            return world.hasNeighborSignal(scratchPos) ||
                    findPoweredRailSignalFromState(self, world, x, y, z, blockState, forward, distance + 1, checkedPos,
                            scratchPos);
        }

        if (!blockState.is(self)) {
            return false;
        }

        RailShape actualShape = blockState.getValue(PoweredRailBlock.SHAPE);

        if (isMismatchedRailShape(expectedShape, actualShape) || !blockState.getValue(PoweredRailBlock.POWERED)) {
            return false;
        }

        boolean isPowered = world.hasNeighborSignal(scratchPos) ||
                findPoweredRailSignalFromState(self, world, x, y, z, blockState, forward, distance + 1, checkedPos,
                        scratchPos);

        if (isPowered) {
            checkedPos.put(posKey, RailLogic.CHECKED_POWERED);
        }

        return isPowered;
    }

    private static boolean isMismatchedRailShape(RailShape expected, RailShape actual) {
        return expected == RailShape.EAST_WEST
                && (actual == RailShape.NORTH_SOUTH || actual == RailShape.ASCENDING_NORTH
                        || actual == RailShape.ASCENDING_SOUTH)
                || expected == RailShape.NORTH_SOUTH && (actual == RailShape.EAST_WEST
                        || actual == RailShape.ASCENDING_EAST || actual == RailShape.ASCENDING_WEST);
    }

    static boolean findPoweredRailSignalFaster(PoweredRailBlock self, Level level,
            BlockPos pos, BlockState state, boolean forward, int distance,
            Long2ByteMap checkedPos) {
        MutableBlockPos scratchPos = new MutableBlockPos();
        return findPoweredRailSignalFromState(self, level, pos.getX(), pos.getY(), pos.getZ(), state, forward, distance,
                checkedPos, scratchPos);
    }

    private static boolean findPoweredRailSignalFromState(PoweredRailBlock self, Level level, int x, int y, int z,
            BlockState state, boolean forward, int distance, Long2ByteMap checkedPos, MutableBlockPos scratchPos) {
        if (distance >= RailLogic.getRailPowerLimit()) {
            return false;
        }

        boolean checkBelow = true;
        RailShape railShape = state.getValue(PoweredRailBlock.SHAPE);

        switch (railShape) {
            case NORTH_SOUTH -> {
                if (forward)
                    ++z;
                else
                    --z;
            }
            case EAST_WEST -> {
                if (forward)
                    --x;
                else
                    ++x;
            }
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

        if (findPoweredRailSignalAt(self, level, x, y, z, forward, distance, railShape, checkedPos, scratchPos))
            return true;
        return checkBelow && findPoweredRailSignalAt(self, level, x, y - 1, z, forward, distance, railShape, checkedPos,
                scratchPos);
    }
}
