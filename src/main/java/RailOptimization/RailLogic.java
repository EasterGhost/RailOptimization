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
import net.minecraft.world.level.block.state.properties.RailShape;

import static net.minecraft.world.level.block.PoweredRailBlock.POWERED;
import static net.minecraft.world.level.block.PoweredRailBlock.SHAPE;

public final class RailLogic {

    private static final Direction[] EAST_WEST_DIR = new Direction[] { Direction.WEST, Direction.EAST };
    private static final Direction[] NORTH_SOUTH_DIR = new Direction[] { Direction.SOUTH, Direction.NORTH };

    private static final int UPDATE_FORCE_PLACE = Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_CLIENTS;

    private static final byte CHECKED_UNKNOWN = 0;
    private static final byte CHECKED_BLOCKED = 1;
    private static final byte CHECKED_POWERED = 2;

    private static int railPowerLimit = 8;

    private RailLogic() {
    }

    private static boolean isAscending(RailShape railShape) {
        return railShape == RailShape.ASCENDING_EAST || railShape == RailShape.ASCENDING_WEST ||
                railShape == RailShape.ASCENDING_NORTH || railShape == RailShape.ASCENDING_SOUTH;
    }

    private static void notifyNeighborChanged(Level level, int x, int y, int z,
            Block sourceBlock, MutableBlockPos scratchPos) {
        scratchPos.set(x, y, z);
        level.updateNeighborsAt(scratchPos, sourceBlock);
    }

    private static Long2ByteOpenHashMap newCheckedMap() {
        Long2ByteOpenHashMap checkedPos = new Long2ByteOpenHashMap(Math.max(railPowerLimit * 2, 4));
        checkedPos.defaultReturnValue(CHECKED_UNKNOWN);
        return checkedPos;
    }

    public static void setRailPowerLimit(int powerLimit) {
        railPowerLimit = Math.max(1, powerLimit);
    }

    public static void customUpdateState(PoweredRailBlock self, BlockState state, Level level, BlockPos pos) {
        boolean shouldBePowered = level.hasNeighborSignal(pos) ||
                ((PoweredRailBlockInvoker) self).invokeFindPoweredRailSignal(level, pos, state, true, 0) ||
                ((PoweredRailBlockInvoker) self).invokeFindPoweredRailSignal(level, pos, state, false, 0);

        if (shouldBePowered != state.getValue(POWERED)) {
            RailShape railShape = state.getValue(SHAPE);

            if (isAscending(railShape)) {
                level.setBlock(pos, state.setValue(POWERED, shouldBePowered), 3);

                MutableBlockPos scratchPos = new MutableBlockPos();
                notifyNeighborChanged(level, pos.getX(), pos.getY() - 1, pos.getZ(), self, scratchPos);
                notifyNeighborChanged(level, pos.getX(), pos.getY() + 1, pos.getZ(), self, scratchPos);
            } else if (shouldBePowered) {
                powerLane(self, level, pos, state, railShape);
            } else {
                dePowerLane(self, level, pos, state, railShape);
            }
        }
    }

    public static boolean findPoweredRailSignalFaster(PoweredRailBlock self, Level world, BlockPos pos,
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

        scratchPos.set(x, y, z);
        BlockState blockState = world.getBlockState(scratchPos);

        if (checked == CHECKED_POWERED) {
            return world.hasNeighborSignal(scratchPos) ||
                    findPoweredRailSignalFromState(self, world, x, y, z, blockState, forward, distance + 1, checkedPos,
                            scratchPos);
        }

        if (!blockState.is(self)) {
            return false;
        }

        RailShape actualShape = blockState.getValue(SHAPE);

        if (isMismatchedRailShape(expectedShape, actualShape) || !blockState.getValue(POWERED)) {
            return false;
        }

        boolean isPowered = world.hasNeighborSignal(scratchPos) ||
                findPoweredRailSignalFromState(self, world, x, y, z, blockState, forward, distance + 1, checkedPos,
                        scratchPos);

        if (isPowered) {
            checkedPos.put(posKey, CHECKED_POWERED);
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

    public static boolean findPoweredRailSignalFaster(PoweredRailBlock self, Level level,
            BlockPos pos, BlockState state, boolean forward, int distance,
            Long2ByteMap checkedPos) {
        MutableBlockPos scratchPos = new MutableBlockPos();
        return findPoweredRailSignalFromState(self, level, pos.getX(), pos.getY(), pos.getZ(), state, forward, distance,
                checkedPos, scratchPos);
    }

    private static boolean findPoweredRailSignalFromState(PoweredRailBlock self, Level level, int x, int y, int z,
            BlockState state, boolean forward, int distance, Long2ByteMap checkedPos, MutableBlockPos scratchPos) {
        if (distance >= railPowerLimit - 1) {
            return false;
        }

        boolean checkBelow = true;
        RailShape railShape = state.getValue(SHAPE);

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

    public static void powerLane(PoweredRailBlock self, Level world, BlockPos pos,
            BlockState mainState, RailShape railShape) {
        Direction[] directions = getRailDirections(railShape);
        if (directions == null)
            return;

        world.setBlock(pos, mainState.setValue(POWERED, true), UPDATE_FORCE_PLACE);
        Long2ByteOpenHashMap checkedPos = newCheckedMap();
        checkedPos.put(pos.asLong(), CHECKED_POWERED);
        int firstDirectionCount = setRailPositionsPower(self, world, pos, checkedPos, directions[0]);
        int secondDirectionCount = setRailPositionsPower(self, world, pos, checkedPos, directions[1]);

        updateRails(self, railShape == RailShape.EAST_WEST, world, pos, mainState, firstDirectionCount,
                secondDirectionCount);
    }

    public static void dePowerLane(PoweredRailBlock self, Level world, BlockPos pos,
            BlockState mainState, RailShape railShape) {
        Direction[] directions = getRailDirections(railShape);
        if (directions == null) {
            return;
        }

        world.setBlock(pos, mainState.setValue(POWERED, false), UPDATE_FORCE_PLACE);

        int firstDirectionCount = setRailPositionsDePower(self, world, pos, directions[0]);
        int secondDirectionCount = setRailPositionsDePower(self, world, pos, directions[1]);

        updateRails(self, railShape == RailShape.EAST_WEST, world, pos, mainState, firstDirectionCount,
                secondDirectionCount);
    }

    private static Direction[] getRailDirections(RailShape railShape) {
        return switch (railShape) {
            case NORTH_SOUTH -> NORTH_SOUTH_DIR;
            case EAST_WEST -> EAST_WEST_DIR;
            default -> null;
        };
    }

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

            if (!state.is(self) || state.getValue(POWERED) || !(world.hasNeighborSignal(cursor) ||
                    findPoweredRailSignalFaster(self, world, cursor, state, true, 0, checkedPos) ||
                    findPoweredRailSignalFaster(self, world, cursor, state, false, 0, checkedPos))) {
                checkedPos.put(posKey, CHECKED_BLOCKED);
                break;
            }

            checkedPos.put(posKey, CHECKED_POWERED);
            world.setBlock(cursor, state.setValue(POWERED, true), UPDATE_FORCE_PLACE);
            count++;
        }

        return count;
    }

    private static int setRailPositionsDePower(PoweredRailBlock self, Level world, BlockPos pos, Direction dir) {
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

            cursor.set(x, y, z);
            BlockState state = world.getBlockState(cursor);

            if (!state.is(self) ||
                    !state.getValue(POWERED) ||
                    world.hasNeighborSignal(cursor) ||
                    ((PoweredRailBlockInvoker) self).invokeFindPoweredRailSignal(world, cursor, state, true, 0) ||
                    ((PoweredRailBlockInvoker) self).invokeFindPoweredRailSignal(world, cursor, state, false, 0)) {
                break;
            }

            world.setBlock(cursor, state.setValue(POWERED, false), UPDATE_FORCE_PLACE);
            count++;
        }

        return count;
    }

    private static void notifyRailEnd(PoweredRailBlock self, Level world, int endX, int endY, int endZ, Block block,
            int railX, int railY, int railZ, MutableBlockPos scratchPos) {
        notifyNeighborChanged(world, endX, endY, endZ, block, scratchPos);

        scratchPos.set(railX, railY, railZ);
        BlockState state = world.getBlockState(scratchPos);

        if (state.is(self) && isAscending(state.getValue(SHAPE))) {
            notifyNeighborChanged(world, endX, endY + 1, endZ, block, scratchPos);
        }
    }

    private static void updateRails(PoweredRailBlock self, boolean eastWest, Level world, BlockPos pos,
            BlockState mainState, int firstDirectionCount, int secondDirectionCount) {
        Block block = mainState.getBlock();
        boolean secondDirectionEmpty = secondDirectionCount == 0;
        Direction[] directions = eastWest ? EAST_WEST_DIR : NORTH_SOUTH_DIR;

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
