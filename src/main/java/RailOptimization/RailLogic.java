package RailOptimization;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.RailShape;

public final class RailLogic {
    private static final BooleanProperty POWERED = PoweredRailBlock.POWERED;
    static final Direction[] EAST_WEST_DIR = new Direction[] { Direction.WEST, Direction.EAST };
    static final Direction[] NORTH_SOUTH_DIR = new Direction[] { Direction.SOUTH, Direction.NORTH };

    private static final int UPDATE_FORCE_PLACE = Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_CLIENTS;
    private static final boolean[] RAIL_ASCENDING = new boolean[RailShape.values().length];
    static final byte CHECKED_UNKNOWN = 0;
    static final byte CHECKED_BLOCKED = 1;
    static final byte CHECKED_POWERED = 2;

    private static int railPowerLimit = 8;
    private static boolean optimizationEnabled = true;
    private static boolean useTestVanillaPositions;
    private static final LongSet testVanillaPositions = new LongOpenHashSet();
    private static final Long2IntOpenHashMap testPowerLimits = new Long2IntOpenHashMap();

    static {
        RAIL_ASCENDING[RailShape.ASCENDING_EAST.ordinal()] = true;
        RAIL_ASCENDING[RailShape.ASCENDING_WEST.ordinal()] = true;
        RAIL_ASCENDING[RailShape.ASCENDING_NORTH.ordinal()] = true;
        RAIL_ASCENDING[RailShape.ASCENDING_SOUTH.ordinal()] = true;
    }

    private RailLogic() {
    }

    static boolean isAscending(RailShape railShape) {
        return RAIL_ASCENDING[railShape.ordinal()];
    }

    private static RailUpdateContext newUpdateContext() {
        return new RailUpdateContext(railPowerLimit);
    }

    public static void setRailPowerLimit(int powerLimit) {
        railPowerLimit = Math.max(1, powerLimit);
    }

    static int getRailPowerLimit() {
        return railPowerLimit;
    }

    public static boolean isOptimizationEnabled() {
        return optimizationEnabled;
    }

    public static void setOptimizationEnabled(boolean enabled) {
        if (useTestVanillaPositions) {
            return;
        }

        optimizationEnabled = enabled;
    }

    public static boolean shouldUseOptimization(BlockPos pos) {
        return optimizationEnabled && (!useTestVanillaPositions || !testVanillaPositions.contains(pos.asLong()));
    }

    static void enablePositionBasedTestMode() {
        optimizationEnabled = true;
        useTestVanillaPositions = true;
        testVanillaPositions.clear();
        testPowerLimits.clear();
    }

    static void forceVanillaAtForTesting(BlockPos pos) {
        if (useTestVanillaPositions) {
            testVanillaPositions.add(pos.asLong());
        }
    }

    static void forcePowerLimitAtForTesting(BlockPos pos, int powerLimit) {
        if (useTestVanillaPositions) {
            testPowerLimits.put(pos.asLong(), Math.max(1, powerLimit));
        }
    }

    public static void customUpdateState(PoweredRailBlock self, BlockState state, Level level, BlockPos pos) {
        int testPowerLimit = useTestVanillaPositions ? testPowerLimits.get(pos.asLong()) : 0;
        if (testPowerLimit == 0) {
            customUpdateStateWithCurrentPowerLimit(self, state, level, pos);
            return;
        }

        int configuredPowerLimit = railPowerLimit;
        railPowerLimit = testPowerLimit;
        try {
            customUpdateStateWithCurrentPowerLimit(self, state, level, pos);
        } finally {
            railPowerLimit = configuredPowerLimit;
        }
    }

    @SuppressWarnings("null")
    private static void customUpdateStateWithCurrentPowerLimit(
            PoweredRailBlock self, BlockState state, Level level, BlockPos pos) {
        RailShape railShape = state.getValue(PoweredRailBlock.SHAPE);
        boolean supportsFastSearch = RailSignalSearcher.supportsFastSearch(railShape);
        RailUpdateContext context = supportsFastSearch ? newUpdateContext() : null;
        boolean shouldBePowered = (supportsFastSearch ? context.hasNeighborSignal(level, pos)
                : level.hasNeighborSignal(pos)) ||
                (supportsFastSearch
                        ? RailSignalSearcher.findPoweredRailSignalFaster(self, level, pos, state, true, 0, context)
                                ||
                                RailSignalSearcher.findPoweredRailSignalFaster(self, level, pos, state, false, 0,
                                        context)
                        : ((PoweredRailBlockInvoker) self).invokeFindPoweredRailSignal(level, pos, state, true, 0) ||
                                ((PoweredRailBlockInvoker) self).invokeFindPoweredRailSignal(level, pos, state, false,
                                        0));

        if (shouldBePowered != state.getValue(POWERED)) {
            if (shouldBePowered) {
                powerLane(self, level, pos, state, railShape, context);
            } else {
                dePowerLane(self, level, pos, state, railShape, context);
            }
        }
    }

    public static void powerLane(PoweredRailBlock self, Level world, BlockPos pos,
            BlockState mainState, RailShape railShape) {
        powerLane(self, world, pos, mainState, railShape, newUpdateContext());
    }

    @SuppressWarnings("null")
    private static void powerLane(PoweredRailBlock self, Level world, BlockPos pos,
            BlockState mainState, RailShape railShape, RailUpdateContext context) {
        if (!RailSignalSearcher.supportsFastSearch(railShape)) {
            return;
        }

        context.beginPowering();
        RailSearchCache checkedPos = context.searchCache;
        RailPathScan railPath = scanRailPath(self, world, pos, mainState, context);
        if (!railPath.hasSlope()) {
            powerStraightLane(self, world, pos, mainState, railShape, context, railPath);
            return;
        }

        RailChangeList changedRails = new RailChangeList(railPowerLimit * 2 + 1);
        setRailPowerState(world, pos, mainState, true, changedRails);
        checkedPos.put(pos.asLong(), CHECKED_POWERED);
        int firstDirectionCount = setRailPositionsPower(self, world, context, true, changedRails, railPath);
        int secondDirectionCount = setRailPositionsPower(self, world, context, false, changedRails, railPath);

        updateChangedRails(self, world, pos, mainState, railShape, firstDirectionCount, secondDirectionCount,
                changedRails, context);
    }

    public static void dePowerLane(PoweredRailBlock self, Level world, BlockPos pos,
            BlockState mainState, RailShape railShape) {
        dePowerLane(self, world, pos, mainState, railShape, newUpdateContext());
    }

    @SuppressWarnings("null")
    private static void dePowerLane(PoweredRailBlock self, Level world, BlockPos pos,
            BlockState mainState, RailShape railShape, RailUpdateContext context) {
        if (!RailSignalSearcher.supportsFastSearch(railShape)) {
            return;
        }

        context.beginDepowering();
        RailPathScan railPath = scanRailPath(self, world, pos, mainState, context);
        if (!railPath.hasSlope()) {
            dePowerStraightLane(self, world, pos, mainState, railShape, context, railPath);
            return;
        }

        RailChangeList changedRails = new RailChangeList(railPowerLimit * 2 + 1);
        setRailPowerState(world, pos, mainState, false, changedRails);

        int firstDirectionCount = setRailPositionsDePower(self, world, true, context, changedRails, railPath);
        int secondDirectionCount = setRailPositionsDePower(self, world, false, context, changedRails, railPath);

        updateChangedRails(self, world, pos, mainState, railShape, firstDirectionCount, secondDirectionCount,
                changedRails, context);
    }

    private static RailPathScan scanRailPath(PoweredRailBlock self, Level world, BlockPos pos, BlockState state,
            RailUpdateContext context) {
        RailPathScan railPath = new RailPathScan(railPowerLimit - 1,
                isAscending(state.getValue(PoweredRailBlock.SHAPE)));
        MutableBlockPos cursor = context.railCursor;
        MutableBlockPos scratchPos = context.scratchPos;

        for (int directionIndex = 0; directionIndex < 2; ++directionIndex) {
            boolean forward = directionIndex == 0;
            cursor.set(pos.getX(), pos.getY(), pos.getZ());
            BlockState previousState = state;

            for (int i = 1; i < railPowerLimit; ++i) {
                BlockState nextState = RailSignalSearcher.findNextRailState(self, world, cursor, previousState,
                        forward, scratchPos);
                if (nextState == null) {
                    break;
                }

                railPath.add(forward, cursor.asLong(), nextState);
                previousState = nextState;
            }
        }

        return railPath;
    }

    @SuppressWarnings("null")
    private static void powerStraightLane(PoweredRailBlock self, Level world, BlockPos pos,
            BlockState mainState, RailShape railShape, RailUpdateContext context, RailPathScan railPath) {
        Direction[] directions = getRailDirections(railShape);
        if (directions == null)
            return;

        world.setBlock(pos, mainState.setValue(POWERED, true), UPDATE_FORCE_PLACE);
        context.searchCache.put(pos.asLong(), CHECKED_POWERED);
        int firstDirectionCount = setStraightRailPositionsPower(self, world, context, true, railPath);
        int secondDirectionCount = setStraightRailPositionsPower(self, world, context, false, railPath);

        RailUpdateNotifier.updateRails(railShape == RailShape.EAST_WEST, world, pos, mainState,
                firstDirectionCount,
                secondDirectionCount, context.scratchPos);
    }

    @SuppressWarnings("null")
    private static void dePowerStraightLane(PoweredRailBlock self, Level world, BlockPos pos,
            BlockState mainState, RailShape railShape, RailUpdateContext context, RailPathScan railPath) {
        Direction[] directions = getRailDirections(railShape);
        if (directions == null) {
            return;
        }

        world.setBlock(pos, mainState.setValue(POWERED, false), UPDATE_FORCE_PLACE);

        int firstDirectionCount = setStraightRailPositionsDePower(self, world, context, true, railPath);
        int secondDirectionCount = setStraightRailPositionsDePower(self, world, context, false, railPath);

        RailUpdateNotifier.updateRails(railShape == RailShape.EAST_WEST, world, pos, mainState,
                firstDirectionCount,
                secondDirectionCount, context.scratchPos);
    }

    private static Direction[] getRailDirections(RailShape railShape) {
        return switch (railShape) {
            case NORTH_SOUTH -> NORTH_SOUTH_DIR;
            case EAST_WEST -> EAST_WEST_DIR;
            default -> null;
        };
    }

    @SuppressWarnings("null")
    private static int setStraightRailPositionsPower(PoweredRailBlock self, Level world,
            RailUpdateContext context, boolean forward, RailPathScan railPath) {
        int count = 0;
        RailSearchCache checkedPos = context.searchCache;
        MutableBlockPos cursor = context.railCursor;

        for (int i = 0; i < railPath.count(forward); ++i) {
            long posKey = railPath.position(forward, i);
            byte checked = checkedPos.get(posKey);

            if (checked != CHECKED_UNKNOWN) {
                if (checked == CHECKED_BLOCKED)
                    break;
                count++;
                continue;
            }

            setCursor(cursor, posKey);
            BlockState state = railPath.state(forward, i);

            if (state.getValue(POWERED) || !(context.hasNeighborSignal(world, cursor) ||
                    RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, true, 0, context) ||
                    RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, false, 0, context))) {
                checkedPos.put(posKey, CHECKED_BLOCKED);
                break;
            }

            checkedPos.put(posKey, CHECKED_POWERED);
            world.setBlock(cursor, state.setValue(POWERED, true), UPDATE_FORCE_PLACE);
            count++;
        }

        return count;
    }

    @SuppressWarnings("null")
    private static int setStraightRailPositionsDePower(PoweredRailBlock self, Level world,
            RailUpdateContext context, boolean forward, RailPathScan railPath) {
        int count = 0;
        RailSearchCache checkedPos = context.searchCache;
        MutableBlockPos cursor = context.railCursor;

        for (int i = 0; i < railPath.count(forward); ++i) {
            long posKey = railPath.position(forward, i);
            byte checked = checkedPos.get(posKey);

            if (checked == CHECKED_BLOCKED) {
                break;
            }

            setCursor(cursor, posKey);
            BlockState state = railPath.state(forward, i);

            if (!state.getValue(POWERED) ||
                    context.hasNeighborSignal(world, cursor) ||
                    RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, true, 0, context) ||
                    RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, false, 0, context)) {
                checkedPos.put(posKey, CHECKED_BLOCKED);
                break;
            }

            world.setBlock(cursor, state.setValue(POWERED, false), UPDATE_FORCE_PLACE);
            checkedPos.put(posKey, CHECKED_BLOCKED);
            count++;
        }

        return count;
    }

    @SuppressWarnings("null")
    private static int setRailPositionsPower(PoweredRailBlock self, Level world,
            RailUpdateContext context, boolean forward, RailChangeList changedRails, RailPathScan railPath) {
        int count = 0;
        RailSearchCache checkedPos = context.searchCache;
        MutableBlockPos cursor = context.railCursor;

        for (int i = 0; i < railPath.count(forward); ++i) {
            long posKey = railPath.position(forward, i);
            byte checked = checkedPos.get(posKey);

            if (checked != CHECKED_UNKNOWN) {
                if (checked == CHECKED_BLOCKED)
                    break;
                count++;
                continue;
            }

            setCursor(cursor, posKey);
            BlockState state = railPath.state(forward, i);
            if (state.getValue(POWERED) || !(context.hasNeighborSignal(world, cursor) ||
                    RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, true, 0, context) ||
                    RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, false, 0, context))) {
                checkedPos.put(posKey, CHECKED_BLOCKED);
                break;
            }

            checkedPos.put(posKey, CHECKED_POWERED);
            setRailPowerState(world, cursor, state, true, changedRails);
            count++;
        }

        return count;
    }

    @SuppressWarnings("null")
    private static int setRailPositionsDePower(PoweredRailBlock self, Level world,
            boolean forward, RailUpdateContext context, RailChangeList changedRails, RailPathScan railPath) {
        int count = 0;
        RailSearchCache checkedPos = context.searchCache;
        MutableBlockPos cursor = context.railCursor;

        for (int i = 0; i < railPath.count(forward); ++i) {
            long posKey = railPath.position(forward, i);
            byte checked = checkedPos.get(posKey);

            if (checked == CHECKED_BLOCKED) {
                break;
            }

            setCursor(cursor, posKey);
            BlockState state = railPath.state(forward, i);
            if (!state.getValue(POWERED) ||
                    context.hasNeighborSignal(world, cursor) ||
                    RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, true, 0, context) ||
                    RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, false, 0, context)) {
                checkedPos.put(posKey, CHECKED_BLOCKED);
                break;
            }

            setRailPowerState(world, cursor, state, false, changedRails);
            checkedPos.put(posKey, CHECKED_BLOCKED);
            count++;
        }

        return count;
    }

    private static void setCursor(MutableBlockPos cursor, long packedPos) {
        cursor.set(BlockPos.getX(packedPos), BlockPos.getY(packedPos), BlockPos.getZ(packedPos));
    }

    @SuppressWarnings("null")
    private static void setRailPowerState(Level world, BlockPos pos, BlockState state, boolean powered,
            RailChangeList changedRails) {
        world.setBlock(pos, state.setValue(POWERED, powered), UPDATE_FORCE_PLACE);
        changedRails.add(pos, state);
    }

    private static void updateChangedRails(PoweredRailBlock self, Level world, BlockPos pos, BlockState mainState,
            RailShape railShape, int firstDirectionCount, int secondDirectionCount, RailChangeList changedRails,
            RailUpdateContext context) {
        Direction[] directions = getRailDirections(railShape);
        if (directions != null && !changedRails.hasSlope()) {
            RailUpdateNotifier.updateRails(railShape == RailShape.EAST_WEST, world, pos, mainState,
                    firstDirectionCount,
                    secondDirectionCount, context.scratchPos);
            return;
        }

        Block block = mainState.getBlock();
        MutableBlockPos scratchPos = context.scratchPos;
        for (int i = changedRails.size() - 1; i >= 0; i--) {
            BlockPos railPos = changedRails.pos(i);
            BlockState state = changedRails.state(i);
            RailUpdateNotifier.notifyNeighborChanged(world, railPos.getX(), railPos.getY(), railPos.getZ(), block,
                    scratchPos);
            RailUpdateNotifier.notifyNeighborChanged(world, railPos.getX(), railPos.getY() - 1, railPos.getZ(), block,
                    scratchPos);

            if (isAscending(state.getValue(PoweredRailBlock.SHAPE))) {
                RailUpdateNotifier.notifyNeighborChanged(world, railPos.getX(), railPos.getY() + 1, railPos.getZ(),
                        block, scratchPos);
            }
        }
    }

    private static final class RailPathScan {
        private final long[] positions;
        private final BlockState[] states;
        private final int maxPerDirection;
        private int firstDirectionCount;
        private int secondDirectionCount;
        private boolean hasSlope;

        private RailPathScan(int maxPerDirection, boolean hasSlope) {
            this.maxPerDirection = maxPerDirection;
            this.positions = new long[maxPerDirection * 2];
            this.states = new BlockState[maxPerDirection * 2];
            this.hasSlope = hasSlope;
        }

        private void add(boolean forward, long position, BlockState state) {
            int count = count(forward);
            int index = index(forward, count);
            positions[index] = position;
            states[index] = state;
            if (forward) {
                ++firstDirectionCount;
            } else {
                ++secondDirectionCount;
            }
            hasSlope |= isAscending(state.getValue(PoweredRailBlock.SHAPE));
        }

        private int count(boolean forward) {
            return forward ? firstDirectionCount : secondDirectionCount;
        }

        private boolean hasSlope() {
            return hasSlope;
        }

        private long position(boolean forward, int offset) {
            return positions[index(forward, offset)];
        }

        private BlockState state(boolean forward, int offset) {
            return states[index(forward, offset)];
        }

        private int index(boolean forward, int offset) {
            return (forward ? 0 : maxPerDirection) + offset;
        }
    }

    private static final class RailChangeList {
        private final BlockPos[] positions;
        private final BlockState[] states;
        private int size;
        private boolean hasSlope;

        private RailChangeList(int capacity) {
            this.positions = new BlockPos[capacity];
            this.states = new BlockState[capacity];
        }

        private void add(BlockPos pos, BlockState state) {
            positions[size] = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
            states[size] = state;
            hasSlope |= isAscending(state.getValue(PoweredRailBlock.SHAPE));
            size++;
        }

        private int size() {
            return size;
        }

        private boolean hasSlope() {
            return hasSlope;
        }

        private BlockPos pos(int index) {
            return positions[index];
        }

        private BlockState state(int index) {
            return states[index];
        }
    }
}
