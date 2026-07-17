package RailOptimization;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

final class RailSignalSearcher {
    private static final byte AXIS_NONE = 0;
    private static final byte AXIS_EAST_WEST = 1;
    private static final byte AXIS_NORTH_SOUTH = 2;
    private static final byte[] RAIL_AXIS = new byte[RailShape.values().length];

    static {
        RAIL_AXIS[RailShape.EAST_WEST.ordinal()] = AXIS_EAST_WEST;
        RAIL_AXIS[RailShape.ASCENDING_EAST.ordinal()] = AXIS_EAST_WEST;
        RAIL_AXIS[RailShape.ASCENDING_WEST.ordinal()] = AXIS_EAST_WEST;
        RAIL_AXIS[RailShape.NORTH_SOUTH.ordinal()] = AXIS_NORTH_SOUTH;
        RAIL_AXIS[RailShape.ASCENDING_NORTH.ordinal()] = AXIS_NORTH_SOUTH;
        RAIL_AXIS[RailShape.ASCENDING_SOUTH.ordinal()] = AXIS_NORTH_SOUTH;
    }

    private RailSignalSearcher() {
    }

    static boolean supportsFastSearch(RailShape railShape) {
        return RAIL_AXIS[railShape.ordinal()] != AXIS_NONE;
    }

    private static boolean findPoweredRailSignalAt(PoweredRailBlock self, Level world, int x, int y, int z,
            boolean forward, int distance, RailShape expectedShape, RailUpdateContext context) {
        long posKey = BlockPos.asLong(x, y, z);
        byte cacheFlags = checkedPosFlags(forward, expectedShape);
        byte checked = context.getSearchResult(posKey, cacheFlags, distance);

        if (checked == RailLogic.CHECKED_BLOCKED) {
            return false;
        }

        if (checked == RailLogic.CHECKED_POWERED) {
            return true;
        }

        context.scratchPos.set(x, y, z);
        BlockState blockState = world.getBlockState(context.scratchPos);

        if (!blockState.is(self)) {
            context.cacheSearchResult(posKey, cacheFlags, distance, false);
            return false;
        }

        RailShape actualShape = blockState.getValue(PoweredRailBlock.SHAPE);

        if (isMismatchedRailAxis(expectedShape, actualShape) || !blockState.getValue(PoweredRailBlock.POWERED)) {
            context.cacheSearchResult(posKey, cacheFlags, distance, false);
            return false;
        }

        boolean isPowered = context.hasNeighborSignal(world, context.scratchPos) ||
                findPoweredRailSignalFromState(self, world, x, y, z, blockState, forward, distance + 1, context);

        context.cacheSearchResult(posKey, cacheFlags, distance, isPowered);

        return isPowered;
    }

    private static boolean isMismatchedRailAxis(RailShape expected, RailShape actual) {
        return RAIL_AXIS[expected.ordinal()] != RAIL_AXIS[actual.ordinal()];
    }

    private static byte checkedPosFlags(boolean forward, RailShape expectedShape) {
        byte flags = RailSearchCache.SEARCH;
        if (forward) {
            flags |= RailSearchCache.SEARCH_FORWARD;
        }
        if (RAIL_AXIS[expectedShape.ordinal()] == AXIS_NORTH_SOUTH) {
            flags |= RailSearchCache.SEARCH_NORTH_SOUTH;
        }
        return flags;
    }

    static boolean findPoweredRailSignalFaster(PoweredRailBlock self, Level level,
            BlockPos pos, BlockState state, boolean forward, int distance,
            RailUpdateContext context) {
        return findPoweredRailSignalFromState(self, level, pos.getX(), pos.getY(), pos.getZ(), state, forward, distance,
                context);
    }

    private static boolean findPoweredRailSignalFromState(PoweredRailBlock self, Level level, int x, int y, int z,
            BlockState state, boolean forward, int distance, RailUpdateContext context) {
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

        if (findPoweredRailSignalAt(self, level, x, y, z, forward, distance, railShape, context))
            return true;
        return checkBelow && findPoweredRailSignalAt(self, level, x, y - 1, z, forward, distance, railShape, context);
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
