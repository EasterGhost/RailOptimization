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

    private static void notifyShapeChanged(Level level, BlockPos pos, Block sourceBlock) {
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
                level.updateNeighborsAt(pos.above(), self); //isAscending
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

        return world.hasNeighborSignal(pos) ||
                findPoweredRailSignalFaster(self, world, pos, blockState, forward, distance + 1, checkedPos);
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

    private static void shapeUpdateEnd(PoweredRailBlock self, Level world, BlockPos pos, Block sourceBlock,
                                       int endPos, Direction direction, int currentPos, BlockPos blockPos) {
        if (currentPos == endPos) {
            BlockPos newPos = pos.relative(direction, currentPos+1);
            notifyShapeChanged(world, newPos, sourceBlock);
            BlockState state = world.getBlockState(blockPos);
            if (state.is(self) && isAscending(state.getValue(SHAPE)))
                notifyShapeChanged(world, newPos.above(), sourceBlock);
        }
    }

    private static void neighborUpdateEnd(PoweredRailBlock self, Level world, BlockPos pos, int endPos,
                                          Direction direction, Block block, int currentPos, BlockPos blockPos) {
        if (currentPos == endPos) {
            BlockPos newPos = pos.relative(direction, currentPos+1);
            notifyNeighborChanged(world, newPos, block);
            BlockState state = world.getBlockState(blockPos);
            if (state.is(self) && isAscending(state.getValue(SHAPE)))
                notifyNeighborChanged(world, newPos.above(), block);
        }
    }

    private static void updateRailsSectionEastWestShape(PoweredRailBlock self, Level world, BlockPos pos,
                                                        int c, Block sourceBlock, Direction dir,
                                                        boolean secondDirectionEmpty, int countAmt) {
        BlockPos pos1 = pos.relative(dir, c);
        if (c == 0 && secondDirectionEmpty)
            notifyShapeChanged(world, pos1.relative(dir.getOpposite()), sourceBlock);
        shapeUpdateEnd(self, world, pos, sourceBlock, countAmt, dir, c, pos1);
        notifyShapeChanged(world, pos1.below(), sourceBlock);
        notifyShapeChanged(world, pos1.above(), sourceBlock);
        notifyShapeChanged(world, pos1.north(), sourceBlock);
        notifyShapeChanged(world, pos1.south(), sourceBlock);
    }

    private static void updateRailsSectionNorthSouthShape(PoweredRailBlock self, Level world, BlockPos pos,
                                                          int c, Block sourceBlock, Direction dir,
                                                          boolean secondDirectionEmpty, int countAmt) {
        BlockPos pos1 = pos.relative(dir, c);
        notifyShapeChanged(world, pos1.west(), sourceBlock);
        notifyShapeChanged(world, pos1.east(), sourceBlock);
        notifyShapeChanged(world, pos1.below(), sourceBlock);
        notifyShapeChanged(world, pos1.above(), sourceBlock);
        shapeUpdateEnd(self, world, pos, sourceBlock, countAmt, dir, c, pos1);
        if (c == 0 && secondDirectionEmpty)
            notifyShapeChanged(world, pos1.relative(dir.getOpposite()), sourceBlock);
    }

    private static void updateRails(PoweredRailBlock self, boolean eastWest, Level world,
                                    BlockPos pos, BlockState mainState,
                                    int firstDirectionCount, int secondDirectionCount) {
        Block block = mainState.getBlock();
        boolean secondDirectionEmpty = secondDirectionCount == 0;
        if (eastWest) {
            for (int i = 0; i < EAST_WEST_DIR.length; ++i) {
                int countAmt = i == 0 ? firstDirectionCount : secondDirectionCount;
                if (i == 1 && countAmt == 0) continue;
                Direction dir = EAST_WEST_DIR[i];
                for (int c = countAmt; c >= i; c--) {
                    BlockPos p = pos.relative(dir, c);
                    if (c == 0 && secondDirectionEmpty) notifyNeighborChanged(world, p.relative(dir.getOpposite()), block);
                    neighborUpdateEnd(self, world, pos, countAmt, dir, block, c, p);
                    notifyNeighborChanged(world, p.below(), block);
                    notifyNeighborChanged(world, p.above(), block);
                    notifyNeighborChanged(world, p.north(), block);
                    notifyNeighborChanged(world, p.south(), block);
                    BlockPos pos2 = p.below();
                    notifyNeighborChanged(world, pos2.below(), block);
                    notifyNeighborChanged(world, pos2.north(), block);
                    notifyNeighborChanged(world, pos2.south(), block);
                    if (c == countAmt) notifyNeighborChanged(world, pos.relative(dir, c + 1).below(), block);
                    if (c == 0 && secondDirectionEmpty) notifyNeighborChanged(world, p.relative(dir.getOpposite()).below(), block);
                }
                for (int c = countAmt; c >= i; c--)
                    updateRailsSectionEastWestShape(self, world, pos, c, block, dir, secondDirectionEmpty, countAmt);
            }
        } else {
            for(int i = 0; i < NORTH_SOUTH_DIR.length; ++i) {
                int countAmt = i == 0 ? firstDirectionCount : secondDirectionCount;
                if (i == 1 && countAmt == 0) continue;
                Direction dir = NORTH_SOUTH_DIR[i];
                for (int c = countAmt; c >= i; c--) {
                    BlockPos p = pos.relative(dir,c);
                    notifyNeighborChanged(world, p.west(), block);
                    notifyNeighborChanged(world, p.east(), block);
                    notifyNeighborChanged(world, p.below(), block);
                    notifyNeighborChanged(world, p.above(), block);
                    neighborUpdateEnd(self, world, pos, countAmt, dir, block, c, p);
                    if (c == 0 && secondDirectionEmpty) notifyNeighborChanged(world, p.relative(dir.getOpposite()), block);
                    BlockPos pos2 = p.below();
                    notifyNeighborChanged(world, pos2.west(), block);
                    notifyNeighborChanged(world, pos2.east(), block);
                    notifyNeighborChanged(world, pos2.below(), block);
                    if (c == countAmt) notifyNeighborChanged(world, pos.relative(dir,c + 1).below(), block);
                    if (c == 0 && secondDirectionEmpty) notifyNeighborChanged(world, p.relative(dir.getOpposite()).below(), block);
                }
                for (int c = countAmt; c >= i; c--)
                    updateRailsSectionNorthSouthShape(self, world, pos, c, block, dir, secondDirectionEmpty, countAmt);
            }
        }
    }
}
