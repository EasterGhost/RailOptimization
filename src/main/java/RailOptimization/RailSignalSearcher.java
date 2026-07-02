package RailOptimization;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

final class RailSignalSearcher {
    private static final byte[] RAIL_AXIS = { 2, 1, 1, 1, 2, 2, 0, 0, 0, 0 };
    private static final long CACHE_FORWARD_MASK = 0x4000_0000_0000_0000L;
    private static final long CACHE_AXIS_MASK = 0x2000_0000_0000_0000L;

    private RailSignalSearcher() {
    }

    static boolean supportsFastSearch(RailShape railShape) {
        return RAIL_AXIS[railShape.ordinal()] != 0;
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
        long posKey = checkedPosKey(x, y, z, forward, expectedShape);
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

        if (isMismatchedRailAxis(expectedShape, actualShape) || !blockState.getValue(PoweredRailBlock.POWERED)) {
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

    private static boolean isMismatchedRailAxis(RailShape expected, RailShape actual) {
        return RAIL_AXIS[expected.ordinal()] != RAIL_AXIS[actual.ordinal()];
    }

    private static long checkedPosKey(int x, int y, int z, boolean forward, RailShape expectedShape) {
        long key = BlockPos.asLong(x, y, z);
        if (forward) {
            key ^= CACHE_FORWARD_MASK;
        }
        if (RAIL_AXIS[expectedShape.ordinal()] == 2) {
            key ^= CACHE_AXIS_MASK;
        }
        return key;
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

    static BlockState findNextRailState(PoweredRailBlock self, Level level, MutableBlockPos railPos, BlockState state,
            boolean forward, MutableBlockPos scratchPos) {
        boolean checkBelow = true;
        int x = railPos.getX();
        int y = railPos.getY();
        int z = railPos.getZ();
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
                return null;
            }
        }

        scratchPos.set(x, y, z);
        BlockState nextState = level.getBlockState(scratchPos);
        if (isSameRailWithAxis(self, nextState, railShape)) {
            railPos.set(scratchPos);
            return nextState;
        }

        if (!checkBelow) {
            return null;
        }

        scratchPos.set(x, y - 1, z);
        nextState = level.getBlockState(scratchPos);
        if (isSameRailWithAxis(self, nextState, railShape)) {
            railPos.set(scratchPos);
            return nextState;
        }

        return null;
    }

    private static boolean isSameRailWithAxis(PoweredRailBlock self, BlockState state, RailShape expectedShape) {
        return state.is(self) &&
                !isMismatchedRailAxis(expectedShape, state.getValue(PoweredRailBlock.SHAPE));
    }
}
