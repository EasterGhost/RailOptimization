package RailOptimization;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
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
    private static final boolean[] RAIL_ASCENDING = { false, false, true, true, true, true, false, false, false, false };
    static final byte CHECKED_UNKNOWN = 0;
    static final byte CHECKED_BLOCKED = 1;
    static final byte CHECKED_POWERED = 2;

    private static int railPowerLimit = 8;
    private static boolean optimizationEnabled = true;
    private static boolean useTestVanillaPositions;
    private static final LongSet testVanillaPositions = new LongOpenHashSet();
    private static final Long2IntOpenHashMap testPowerLimits = new Long2IntOpenHashMap();

    private RailLogic() {
    }

    static boolean isAscending(RailShape railShape) {
        return RAIL_ASCENDING[railShape.ordinal()];
    }

    private static Long2ByteOpenHashMap newCheckedMap() {
        Long2ByteOpenHashMap checkedPos = new Long2ByteOpenHashMap(Math.max(railPowerLimit * 8, 8));
        checkedPos.defaultReturnValue(CHECKED_UNKNOWN);
        return checkedPos;
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
        Long2ByteOpenHashMap checkedPos = supportsFastSearch ? newCheckedMap() : null;
        boolean shouldBePowered = level.hasNeighborSignal(pos) ||
                (supportsFastSearch
                        ? RailSignalSearcher.findPoweredRailSignalFaster(self, level, pos, state, true, 0, checkedPos)
                                ||
                                RailSignalSearcher.findPoweredRailSignalFaster(self, level, pos, state, false, 0,
                                        checkedPos)
                        : ((PoweredRailBlockInvoker) self).invokeFindPoweredRailSignal(level, pos, state, true, 0) ||
                                ((PoweredRailBlockInvoker) self).invokeFindPoweredRailSignal(level, pos, state, false,
                                        0));

        if (shouldBePowered != state.getValue(POWERED)) {
            if (shouldBePowered) {
                powerLane(self, level, pos, state, railShape, checkedPos);
            } else {
                dePowerLane(self, level, pos, state, railShape, checkedPos);
            }
        }
    }

    public static void powerLane(PoweredRailBlock self, Level world, BlockPos pos,
            BlockState mainState, RailShape railShape) {
        powerLane(self, world, pos, mainState, railShape, newCheckedMap());
    }

    @SuppressWarnings("null")
    private static void powerLane(PoweredRailBlock self, Level world, BlockPos pos,
            BlockState mainState, RailShape railShape, Long2ByteOpenHashMap checkedPos) {
        if (!RailSignalSearcher.supportsFastSearch(railShape)) {
            return;
        }

        if (!hasSlopeInReach(self, world, pos, mainState)) {
            powerStraightLane(self, world, pos, mainState, railShape, checkedPos);
            return;
        }

        RailChangeList changedRails = new RailChangeList(railPowerLimit * 2 + 1);
        setRailPowerState(world, pos, mainState, true, changedRails);
        checkedPos.put(pos.asLong(), CHECKED_POWERED);
        int firstDirectionCount = setRailPositionsPower(self, world, pos, mainState.setValue(POWERED, true),
                checkedPos, true, changedRails);
        int secondDirectionCount = setRailPositionsPower(self, world, pos, mainState.setValue(POWERED, true),
                checkedPos, false, changedRails);

        updateChangedRails(self, world, pos, mainState, railShape, firstDirectionCount, secondDirectionCount,
                changedRails);
    }

    public static void dePowerLane(PoweredRailBlock self, Level world, BlockPos pos,
            BlockState mainState, RailShape railShape) {
        dePowerLane(self, world, pos, mainState, railShape, newCheckedMap());
    }

    @SuppressWarnings("null")
    private static void dePowerLane(PoweredRailBlock self, Level world, BlockPos pos,
            BlockState mainState, RailShape railShape, Long2ByteMap checkedPos) {
        if (!RailSignalSearcher.supportsFastSearch(railShape)) {
            return;
        }

        if (!hasSlopeInReach(self, world, pos, mainState)) {
            dePowerStraightLane(self, world, pos, mainState, railShape, checkedPos);
            return;
        }

        RailChangeList changedRails = new RailChangeList(railPowerLimit * 2 + 1);
        setRailPowerState(world, pos, mainState, false, changedRails);

        int firstDirectionCount = setRailPositionsDePower(self, world, pos, mainState.setValue(POWERED, false),
                true, checkedPos, changedRails);
        int secondDirectionCount = setRailPositionsDePower(self, world, pos, mainState.setValue(POWERED, false),
                false, checkedPos, changedRails);

        updateChangedRails(self, world, pos, mainState, railShape, firstDirectionCount, secondDirectionCount,
                changedRails);
    }

    private static boolean hasSlopeInReach(PoweredRailBlock self, Level world, BlockPos pos, BlockState state) {
        if (isAscending(state.getValue(PoweredRailBlock.SHAPE))) {
            return true;
        }

        return hasSlopeInReach(self, world, pos, state, true) ||
                hasSlopeInReach(self, world, pos, state, false);
    }

    private static boolean hasSlopeInReach(PoweredRailBlock self, Level world, BlockPos pos, BlockState state,
            boolean forward) {
        MutableBlockPos cursor = new MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
        MutableBlockPos scratchPos = new MutableBlockPos();
        BlockState previousState = state;

        for (int i = 1; i < railPowerLimit; i++) {
            BlockState nextState = RailSignalSearcher.findNextRailState(self, world, cursor, previousState, forward,
                    scratchPos);
            if (nextState == null) {
                return false;
            }

            if (isAscending(nextState.getValue(PoweredRailBlock.SHAPE))) {
                return true;
            }

            previousState = nextState;
        }

        return false;
    }

    @SuppressWarnings("null")
    private static void powerStraightLane(PoweredRailBlock self, Level world, BlockPos pos,
            BlockState mainState, RailShape railShape, Long2ByteMap checkedPos) {
        Direction[] directions = getRailDirections(railShape);
        if (directions == null)
            return;

        world.setBlock(pos, mainState.setValue(POWERED, true), UPDATE_FORCE_PLACE);
        checkedPos.put(pos.asLong(), CHECKED_POWERED);
        int firstDirectionCount = setStraightRailPositionsPower(self, world, pos, checkedPos, directions[0]);
        int secondDirectionCount = setStraightRailPositionsPower(self, world, pos, checkedPos, directions[1]);

        RailUpdateNotifier.updateRails(railShape == RailShape.EAST_WEST, world, pos, mainState,
                firstDirectionCount,
                secondDirectionCount);
    }

    @SuppressWarnings("null")
    private static void dePowerStraightLane(PoweredRailBlock self, Level world, BlockPos pos,
            BlockState mainState, RailShape railShape, Long2ByteMap checkedPos) {
        Direction[] directions = getRailDirections(railShape);
        if (directions == null) {
            return;
        }

        world.setBlock(pos, mainState.setValue(POWERED, false), UPDATE_FORCE_PLACE);

        int firstDirectionCount = setStraightRailPositionsDePower(self, world, pos, directions[0], checkedPos);
        int secondDirectionCount = setStraightRailPositionsDePower(self, world, pos, directions[1], checkedPos);

        RailUpdateNotifier.updateRails(railShape == RailShape.EAST_WEST, world, pos, mainState,
                firstDirectionCount,
                secondDirectionCount);
    }

    private static Direction[] getRailDirections(RailShape railShape) {
        return switch (railShape) {
            case NORTH_SOUTH -> NORTH_SOUTH_DIR;
            case EAST_WEST -> EAST_WEST_DIR;
            default -> null;
        };
    }

    private static boolean isRailAlignedWithDirection(BlockState state, Direction direction) {
        Direction[] railDirections = getRailDirections(state.getValue(PoweredRailBlock.SHAPE));
        Direction[] expectedDirections = direction.getAxis() == Direction.Axis.X ? EAST_WEST_DIR : NORTH_SOUTH_DIR;
        return railDirections == expectedDirections;
    }

    @SuppressWarnings("null")
    private static int setStraightRailPositionsPower(PoweredRailBlock self, Level world, BlockPos pos,
            Long2ByteMap checkedPos, Direction dir) {
        int count = 0;

        final int baseX = pos.getX();
        final int baseY = pos.getY();
        final int baseZ = pos.getZ();

        final int stepX = dir.getStepX();
        final int stepZ = dir.getStepZ();

        MutableBlockPos cursor = new MutableBlockPos();

        for (int i = 1; i < railPowerLimit; i++) {
            int x = baseX + stepX * i;
            int y = baseY;
            int z = baseZ + stepZ * i;

            long posKey = BlockPos.asLong(x, y, z);
            byte checked = checkedPos.get(posKey);

            if (checked != CHECKED_UNKNOWN) {
                if (checked == CHECKED_BLOCKED)
                    break;
                count++;
                continue;
            }

            cursor.set(x, y, z);
            BlockState state = world.getBlockState(cursor);

            if (!state.is(self) ||
                    !isRailAlignedWithDirection(state, dir) ||
                    state.getValue(POWERED) || !(world.hasNeighborSignal(cursor) ||
                    RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, true, 0, checkedPos) ||
                    RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, false, 0, checkedPos))) {
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
    private static int setStraightRailPositionsDePower(PoweredRailBlock self, Level world, BlockPos pos, Direction dir,
            Long2ByteMap checkedPos) {
        int count = 0;

        final int baseX = pos.getX();
        final int baseY = pos.getY();
        final int baseZ = pos.getZ();

        final int stepX = dir.getStepX();
        final int stepZ = dir.getStepZ();

        MutableBlockPos cursor = new MutableBlockPos();

        for (int i = 1; i < railPowerLimit; i++) {
            int x = baseX + stepX * i;
            int y = baseY;
            int z = baseZ + stepZ * i;

            long posKey = BlockPos.asLong(x, y, z);
            byte checked = checkedPos.get(posKey);

            if (checked == CHECKED_BLOCKED) {
                break;
            }

            cursor.set(x, y, z);
            BlockState state = world.getBlockState(cursor);

            if (!state.is(self) ||
                    !isRailAlignedWithDirection(state, dir) ||
                    !state.getValue(POWERED) ||
                    world.hasNeighborSignal(cursor) ||
                    RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, true, 0, checkedPos) ||
                    RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, false, 0, checkedPos)) {
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
    private static int setRailPositionsPower(PoweredRailBlock self, Level world, BlockPos pos,
            BlockState sourceState, Long2ByteMap checkedPos, boolean forward, RailChangeList changedRails) {
        int count = 0;

        MutableBlockPos cursor = new MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
        MutableBlockPos scratchPos = new MutableBlockPos();
        BlockState previousState = sourceState;

        for (int i = 1; i < railPowerLimit; i++) {
            BlockState state = RailSignalSearcher.findNextRailState(self, world, cursor, previousState, forward,
                    scratchPos);
            if (state == null) {
                break;
            }

            long posKey = cursor.asLong();
            byte checked = checkedPos.get(posKey);

            if (checked != CHECKED_UNKNOWN) {
                if (checked == CHECKED_BLOCKED)
                    break;
                count++;
                continue;
            }

            if (state.getValue(POWERED) || !(world.hasNeighborSignal(cursor) ||
                    RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, true, 0, checkedPos) ||
                    RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, false, 0, checkedPos))) {
                checkedPos.put(posKey, CHECKED_BLOCKED);
                break;
            }

            checkedPos.put(posKey, CHECKED_POWERED);
            previousState = state.setValue(POWERED, true);
            setRailPowerState(world, cursor, state, true, changedRails);
            count++;
        }

        return count;
    }

    @SuppressWarnings("null")
    private static int setRailPositionsDePower(PoweredRailBlock self, Level world, BlockPos pos,
            BlockState sourceState, boolean forward, Long2ByteMap checkedPos, RailChangeList changedRails) {
        int count = 0;

        MutableBlockPos cursor = new MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
        MutableBlockPos scratchPos = new MutableBlockPos();
        BlockState previousState = sourceState;

        for (int i = 1; i < railPowerLimit; i++) {
            BlockState state = RailSignalSearcher.findNextRailState(self, world, cursor, previousState, forward,
                    scratchPos);
            if (state == null) {
                break;
            }

            long posKey = cursor.asLong();
            byte checked = checkedPos.get(posKey);

            if (checked == CHECKED_BLOCKED) {
                break;
            }

            if (!state.getValue(POWERED) ||
                    world.hasNeighborSignal(cursor) ||
                    RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, true, 0, checkedPos) ||
                    RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, false, 0, checkedPos)) {
                checkedPos.put(posKey, CHECKED_BLOCKED);
                break;
            }

            previousState = state.setValue(POWERED, false);
            setRailPowerState(world, cursor, state, false, changedRails);
            checkedPos.put(posKey, CHECKED_BLOCKED);
            count++;
        }

        return count;
    }

    @SuppressWarnings("null")
    private static void setRailPowerState(Level world, BlockPos pos, BlockState state, boolean powered,
            RailChangeList changedRails) {
        world.setBlock(pos, state.setValue(POWERED, powered), UPDATE_FORCE_PLACE);
        changedRails.add(pos, state);
    }

    private static void updateChangedRails(PoweredRailBlock self, Level world, BlockPos pos, BlockState mainState,
            RailShape railShape, int firstDirectionCount, int secondDirectionCount, RailChangeList changedRails) {
        Direction[] directions = getRailDirections(railShape);
        if (directions != null && !changedRails.hasSlope()) {
            RailUpdateNotifier.updateRails(railShape == RailShape.EAST_WEST, world, pos, mainState,
                    firstDirectionCount,
                    secondDirectionCount);
            return;
        }

        Block block = mainState.getBlock();
        MutableBlockPos scratchPos = new MutableBlockPos();
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
