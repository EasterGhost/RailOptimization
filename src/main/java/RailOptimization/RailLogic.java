package RailOptimization;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
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

    private RailLogic() {
    }

    static boolean isAscending(RailShape railShape) {
        return RAIL_ASCENDING[railShape.ordinal()];
    }

    private static Long2ByteOpenHashMap newCheckedMap() {
        Long2ByteOpenHashMap checkedPos = new Long2ByteOpenHashMap(Math.max(railPowerLimit * 2, 4));
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
        optimizationEnabled = enabled;
    }

    @SuppressWarnings("null")
    public static void customUpdateState(PoweredRailBlock self, BlockState state, Level level, BlockPos pos) {
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
            if (isAscending(railShape)) {
                level.setBlock(pos, state.setValue(POWERED, shouldBePowered), 3);

                MutableBlockPos scratchPos = new MutableBlockPos();
                RailUpdateNotifier.notifyNeighborChanged(level, pos.getX(), pos.getY() - 1, pos.getZ(), self,
                        scratchPos);
                RailUpdateNotifier.notifyNeighborChanged(level, pos.getX(), pos.getY() + 1, pos.getZ(), self,
                        scratchPos);
            } else if (shouldBePowered) {
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
        Direction[] directions = getRailDirections(railShape);
        if (directions == null)
            return;

        world.setBlock(pos, mainState.setValue(POWERED, true), UPDATE_FORCE_PLACE);
        checkedPos.put(pos.asLong(), CHECKED_POWERED);
        int firstDirectionCount = setRailPositionsPower(self, world, pos, checkedPos, directions[0]);
        int secondDirectionCount = setRailPositionsPower(self, world, pos, checkedPos, directions[1]);

        RailUpdateNotifier.updateRails(self, railShape == RailShape.EAST_WEST, world, pos, mainState,
                firstDirectionCount,
                secondDirectionCount);
    }

    public static void dePowerLane(PoweredRailBlock self, Level world, BlockPos pos,
            BlockState mainState, RailShape railShape) {
        dePowerLane(self, world, pos, mainState, railShape, newCheckedMap());
    }

    @SuppressWarnings("null")
    private static void dePowerLane(PoweredRailBlock self, Level world, BlockPos pos,
            BlockState mainState, RailShape railShape, Long2ByteMap checkedPos) {
        Direction[] directions = getRailDirections(railShape);
        if (directions == null) {
            return;
        }

        world.setBlock(pos, mainState.setValue(POWERED, false), UPDATE_FORCE_PLACE);

        int firstDirectionCount = setRailPositionsDePower(self, world, pos, directions[0], checkedPos);
        int secondDirectionCount = setRailPositionsDePower(self, world, pos, directions[1], checkedPos);

        RailUpdateNotifier.updateRails(self, railShape == RailShape.EAST_WEST, world, pos, mainState,
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
    private static int setRailPositionsPower(PoweredRailBlock self, Level world, BlockPos pos,
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
    private static int setRailPositionsDePower(PoweredRailBlock self, Level world, BlockPos pos, Direction dir,
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
}
