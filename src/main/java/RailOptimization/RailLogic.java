package RailOptimization;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

import static net.minecraft.world.level.block.Block.UPDATE_CLIENTS;
import static net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE;
import static net.minecraft.world.level.block.Block.UPDATE_MOVE_BY_PISTON;
import static net.minecraft.world.level.block.PoweredRailBlock.POWERED;
import static net.minecraft.world.level.block.PoweredRailBlock.SHAPE;

public final class RailLogic {

    private static final Direction[] EAST_WEST_DIR = new Direction[]{Direction.WEST, Direction.EAST};
    private static final Direction[] NORTH_SOUTH_DIR = new Direction[]{Direction.SOUTH, Direction.NORTH};

    private static final int UPDATE_FORCE_PLACE = UPDATE_MOVE_BY_PISTON | UPDATE_KNOWN_SHAPE | UPDATE_CLIENTS;
    private static final byte CHECKED_UNKNOWN = 0;
    private static final byte CHECKED_BLOCKED = 1;
    private static final byte CHECKED_POWERED = 2;

    private static int railPowerLimit = 8;

    private RailLogic() {
    }

    private static boolean isAscending(RailShape railShape) {
        return railShape == RailShape.ASCENDING_EAST ||
               railShape == RailShape.ASCENDING_WEST ||
               railShape == RailShape.ASCENDING_NORTH ||
               railShape == RailShape.ASCENDING_SOUTH;
    }

    private static void notifyNeighborChanged(Level level, BlockPos pos, Block sourceBlock) {
        level.updateNeighborsAt(pos, sourceBlock);
    }

    public static void setRailPowerLimit(int powerLimit) {
        railPowerLimit = Math.max(1, powerLimit);
    }

    public static void customUpdateState(PoweredRailBlock self, BlockState state, Level level, BlockPos pos) {
        boolean shouldBePowered = level.hasNeighborSignal(pos) ||
                ((PoweredRailBlockInvoker)self).invokeFindPoweredRailSignal(level, pos, state, true, 0) ||
                ((PoweredRailBlockInvoker)self).invokeFindPoweredRailSignal(level, pos, state, false, 0);
        if (shouldBePowered != state.getValue(POWERED)) {
            RailShape railShape = state.getValue(SHAPE);
            if (isAscending(railShape)) {
                level.setBlock(pos, state.setValue(POWERED, shouldBePowered), 3);
                level.updateNeighborsAt(pos.below(), self);
                level.updateNeighborsAt(pos.above(), self);
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
        BlockState blockState = world.getBlockState(pos);
        if (checkedPos.get(pos.asLong()) == CHECKED_POWERED) {
            return world.hasNeighborSignal(pos) ||
                    findPoweredRailSignalFaster(self, world, pos, blockState, forward, distance + 1, checkedPos);
        }

        if (!blockState.is(self)) {
            return false;
        }

        RailShape railShape = blockState.getValue(SHAPE);
        if (isMismatchedRailShape(shape, railShape) || !blockState.getValue(POWERED)) {
            return false;
        }

        boolean isPowered = world.hasNeighborSignal(pos) ||
                findPoweredRailSignalFaster(self, world, pos, blockState, forward, distance + 1, checkedPos);
        if (isPowered) {
            checkedPos.put(pos.asLong(), CHECKED_POWERED);
        }
        return isPowered;
    }

    private static boolean isMismatchedRailShape(RailShape expected, RailShape actual) {
        return expected == RailShape.EAST_WEST && (
                actual == RailShape.NORTH_SOUTH ||
                        actual == RailShape.ASCENDING_NORTH ||
                        actual == RailShape.ASCENDING_SOUTH
        ) || expected == RailShape.NORTH_SOUTH && (
                actual == RailShape.EAST_WEST ||
                        actual == RailShape.ASCENDING_EAST ||
                        actual == RailShape.ASCENDING_WEST
        );
    }

    public static boolean findPoweredRailSignalFaster(PoweredRailBlock self, Level level,
                                                      BlockPos pos, BlockState state, boolean forward, int distance,
                                                      Long2ByteMap checkedPos) {
        if (distance >= railPowerLimit - 1) return false;
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        boolean checkBelow = true;
        RailShape railShape = state.getValue(SHAPE);
        switch (railShape) {
            case NORTH_SOUTH -> {
                if (forward) ++z;
                else --z;
            }
            case EAST_WEST -> {
                if (forward) --x;
                else ++x;
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
        return findPoweredRailSignalFaster(
                self, level, new BlockPos(x, y, z),
                forward, distance, railShape, checkedPos
        ) ||
                (checkBelow && findPoweredRailSignalFaster(
                        self, level, new BlockPos(x, y - 1, z),
                        forward, distance, railShape, checkedPos
                ));
    }

    public static void powerLane(PoweredRailBlock self, Level world, BlockPos pos,
                                 BlockState mainState, RailShape railShape) {
        Direction[] directions = getRailDirections(railShape);
        if (directions == null) return;

        world.setBlock(pos, mainState.setValue(POWERED, true), UPDATE_FORCE_PLACE);
        Long2ByteOpenHashMap checkedPos = new Long2ByteOpenHashMap(railPowerLimit * 2);
        checkedPos.defaultReturnValue(CHECKED_UNKNOWN);
        checkedPos.put(pos.asLong(), CHECKED_POWERED);
        int firstDirectionCount = setRailPositionsPower(self, world, pos, checkedPos, directions[0]);
        int secondDirectionCount = setRailPositionsPower(self, world, pos, checkedPos, directions[1]);
        updateRails(self, railShape == RailShape.EAST_WEST, world, pos, mainState,
                firstDirectionCount, secondDirectionCount);
    }

    public static void dePowerLane(PoweredRailBlock self, Level world, BlockPos pos,
                                   BlockState mainState, RailShape railShape) {
        Direction[] directions = getRailDirections(railShape);
        if (directions == null) return;

        world.setBlock(pos, mainState.setValue(POWERED, false), UPDATE_FORCE_PLACE);
        int firstDirectionCount = setRailPositionsDePower(self, world, pos, directions[0]);
        int secondDirectionCount = setRailPositionsDePower(self, world, pos, directions[1]);
        updateRails(self, railShape == RailShape.EAST_WEST, world, pos, mainState,
                firstDirectionCount, secondDirectionCount);
    }

    private static Direction[] getRailDirections(RailShape railShape) {
        return switch (railShape) {
            case NORTH_SOUTH -> NORTH_SOUTH_DIR; // Order: +z, -z
            case EAST_WEST -> EAST_WEST_DIR; // Order: -x, +x
            default -> null;
        };
    }

    private static int setRailPositionsPower(PoweredRailBlock self, Level world, BlockPos pos,
                                             Long2ByteMap checkedPos, Direction dir) {
        int count = 0;
        for (int z = 1; z < railPowerLimit; z++) {
            BlockPos newPos = pos.relative(dir, z);
            BlockState state = world.getBlockState(newPos);
            long newPosKey = newPos.asLong();
            byte checked = checkedPos.get(newPosKey);
            if (checked != CHECKED_UNKNOWN) {
                if (checked == CHECKED_BLOCKED) break;
                count++;
            } else if (!state.is(self) || state.getValue(POWERED) || !(
                    world.hasNeighborSignal(newPos) ||
                            findPoweredRailSignalFaster(self, world, newPos, state, true, 0, checkedPos) ||
                            findPoweredRailSignalFaster(self, world, newPos, state, false, 0, checkedPos)
            )) {
                checkedPos.put(newPosKey, CHECKED_BLOCKED);
                break;
            } else {
                checkedPos.put(newPosKey, CHECKED_POWERED);
                world.setBlock(newPos, state.setValue(POWERED, true), UPDATE_FORCE_PLACE);
                count++;
            }
        }
        return count;
    }

    private static int setRailPositionsDePower(PoweredRailBlock self, Level world, BlockPos pos, Direction dir) {
        int count = 0;
        for (int z = 1; z < railPowerLimit; z++) {
            BlockPos newPos = pos.relative(dir, z);
            BlockState state = world.getBlockState(newPos);
            if (!state.is(self) || !state.getValue(POWERED) || world.hasNeighborSignal(newPos) ||
                    ((PoweredRailBlockInvoker)self).invokeFindPoweredRailSignal(world, newPos, state, true, 0) ||
                    ((PoweredRailBlockInvoker)self).invokeFindPoweredRailSignal(world, newPos, state, false, 0)) break;
            world.setBlock(newPos, state.setValue(POWERED, false), UPDATE_FORCE_PLACE);
            count++;
        }
        return count;
    }

    private static void notifyRailEnd(PoweredRailBlock self, Level world, BlockPos endPos,
                                      Block block, BlockPos railPos) {
        notifyNeighborChanged(world, endPos, block);
        BlockState state = world.getBlockState(railPos);
        if (state.is(self) && isAscending(state.getValue(SHAPE)))
            notifyNeighborChanged(world, endPos.above(), block);
    }

    private static void updateRails(PoweredRailBlock self, boolean eastWest, Level world,
                                    BlockPos pos, BlockState mainState,
                                    int firstDirectionCount, int secondDirectionCount) {
        Block block = mainState.getBlock();
        boolean secondDirectionEmpty = secondDirectionCount == 0;
        Direction[] directions = eastWest ? EAST_WEST_DIR : NORTH_SOUTH_DIR;
        for (int i = 0; i < directions.length; ++i) {
            int countAmt = i == 0 ? firstDirectionCount : secondDirectionCount;
            if (i == 1 && countAmt == 0) continue;
            updateRailSection(self, world, pos, block, directions[i], i, countAmt, secondDirectionEmpty, eastWest);
        }
    }

    private static void updateRailSection(PoweredRailBlock self, Level world, BlockPos pos, Block block,
                                          Direction dir, int directionIndex, int countAmt,
                                          boolean secondDirectionEmpty, boolean eastWest) {
        Direction oppositeDir = dir.getOpposite();
        for (int c = countAmt; c >= directionIndex; c--) {
            BlockPos p = pos.relative(dir, c);
            BlockPos endPos = c == countAmt ? p.relative(dir) : null;
            BlockPos oppositePos = c == 0 && secondDirectionEmpty ? p.relative(oppositeDir) : null;

            if (eastWest) {
                if (oppositePos != null) notifyNeighborChanged(world, oppositePos, block);
                if (endPos != null) notifyRailEnd(self, world, endPos, block, p);
                notifyRailAndSideNeighbors(world, p, block, true);
            } else {
                notifyRailAndSideNeighbors(world, p, block, false);
                if (endPos != null) notifyRailEnd(self, world, endPos, block, p);
                if (oppositePos != null) notifyNeighborChanged(world, oppositePos, block);
            }

            notifyLowerRailSideNeighbors(world, p.below(), block, eastWest);
            if (endPos != null) notifyNeighborChanged(world, endPos.below(), block);
            if (oppositePos != null) notifyNeighborChanged(world, oppositePos.below(), block);
        }
    }

    private static void notifyRailAndSideNeighbors(Level world, BlockPos pos, Block block, boolean eastWest) {
        if (eastWest) {
            notifyNeighborChanged(world, pos.below(), block);
            notifyNeighborChanged(world, pos.above(), block);
            notifyNeighborChanged(world, pos.north(), block);
            notifyNeighborChanged(world, pos.south(), block);
        } else {
            notifyNeighborChanged(world, pos.west(), block);
            notifyNeighborChanged(world, pos.east(), block);
            notifyNeighborChanged(world, pos.below(), block);
            notifyNeighborChanged(world, pos.above(), block);
        }
    }

    private static void notifyLowerRailSideNeighbors(Level world, BlockPos pos, Block block, boolean eastWest) {
        if (eastWest) {
            notifyNeighborChanged(world, pos.below(), block);
            notifyNeighborChanged(world, pos.north(), block);
            notifyNeighborChanged(world, pos.south(), block);
        } else {
            notifyNeighborChanged(world, pos.west(), block);
            notifyNeighborChanged(world, pos.east(), block);
            notifyNeighborChanged(world, pos.below(), block);
        }
    }
}
