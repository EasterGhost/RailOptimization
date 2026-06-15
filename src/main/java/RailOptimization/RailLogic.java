package RailOptimization;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

import java.util.HashMap;
import java.util.Map;

import static net.minecraft.world.level.block.Block.*;
import static net.minecraft.world.level.block.PoweredRailBlock.POWERED;
import static net.minecraft.world.level.block.PoweredRailBlock.SHAPE;

public class RailLogic {

    private static final Direction[] EAST_WEST_DIR = new Direction[]{Direction.WEST, Direction.EAST};
    private static final Direction[] NORTH_SOUTH_DIR = new Direction[]{Direction.SOUTH, Direction.NORTH};

    private static final int UPDATE_FORCE_PLACE = UPDATE_MOVE_BY_PISTON | UPDATE_KNOWN_SHAPE | UPDATE_CLIENTS;

    private static int railPowerLimit = 8;

    private static boolean isAscending(RailShape railShape) {
        return railShape == RailShape.ASCENDING_EAST ||
               railShape == RailShape.ASCENDING_WEST ||
               railShape == RailShape.ASCENDING_NORTH ||
               railShape == RailShape.ASCENDING_SOUTH;
    }

    private static void notifyNeighborChanged(Level world, BlockPos pos, Block block) {
        // 在1.21.2中，neighborChanged方法需要使用updateNeighborsAt替代
        world.updateNeighborsAt(pos, block);
    }

    public static void giveShapeUpdate(Level level, BlockState state, BlockPos pos) {
        // BlockState oldState = level.getBlockState(pos);
        // 在1.21.2中，直接通知方块状态更新
        level.updateNeighborsAt(pos, state.getBlock());
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
                                                      boolean bl, int distance, RailShape shape,
                                                      Map<BlockPos,Boolean> checkedPos) {
        BlockState blockState = world.getBlockState(pos);
        boolean speedCheck = Boolean.TRUE.equals(checkedPos.get(pos));
        if (speedCheck) {
            return world.hasNeighborSignal(pos) ||
                    findPoweredRailSignalFaster(self, world, pos, blockState, bl, distance + 1, checkedPos);
        } else {
            if (blockState.is(self)) {
                RailShape railShape = blockState.getValue(SHAPE);
                if (shape == RailShape.EAST_WEST && (
                        railShape == RailShape.NORTH_SOUTH ||
                                railShape == RailShape.ASCENDING_NORTH ||
                                railShape == RailShape.ASCENDING_SOUTH
                ) || shape == RailShape.NORTH_SOUTH && (
                        railShape == RailShape.EAST_WEST ||
                                railShape == RailShape.ASCENDING_EAST ||
                                railShape == RailShape.ASCENDING_WEST
                )) {
                    return false;
                } else if (blockState.getValue(POWERED)) {
                    return world.hasNeighborSignal(pos) ||
                            findPoweredRailSignalFaster(self, world, pos, blockState, bl, distance + 1, checkedPos);
                } else {
                    return false;
                }
            }
            return false;
        }
    }

    public static boolean findPoweredRailSignalFaster(PoweredRailBlock self, Level level,
                                                      BlockPos pos, BlockState state, boolean bl, int distance,
                                                      Map<BlockPos,Boolean> checkedPos) {
        if (distance >= railPowerLimit - 1) return false;
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        boolean bl2 = true;
        RailShape railShape = state.getValue(SHAPE);
        switch (railShape) {
            case NORTH_SOUTH -> {
                if (bl) ++k;
                else --k;
            }
            case EAST_WEST -> {
                if (bl) --i;
                else ++i;
            }
            case ASCENDING_EAST -> {
                if (bl) {
                    --i;
                } else {
                    ++i;
                    ++j;
                    bl2 = false;
                }
                railShape = RailShape.EAST_WEST;
            }
            case ASCENDING_WEST -> {
                if (bl) {
                    --i;
                    ++j;
                    bl2 = false;
                } else {
                    ++i;
                }
                railShape = RailShape.EAST_WEST;
            }
            case ASCENDING_NORTH -> {
                if (bl) {
                    ++k;
                } else {
                    --k;
                    ++j;
                    bl2 = false;
                }
                railShape = RailShape.NORTH_SOUTH;
            }
            case ASCENDING_SOUTH -> {
                if (bl) {
                    ++k;
                    ++j;
                    bl2 = false;
                } else {
                    --k;
                }
                railShape = RailShape.NORTH_SOUTH;
            }
            default -> {
                return false;
            }
        }
        return findPoweredRailSignalFaster(
                self, level, new BlockPos(i, j, k),
                bl, distance, railShape, checkedPos
        ) ||
                (bl2 && findPoweredRailSignalFaster(
                        self, level, new BlockPos(i, j - 1, k),
                        bl, distance, railShape, checkedPos
                ));
    }

    public static void powerLane(PoweredRailBlock self, Level world, BlockPos pos,
                                 BlockState mainState, RailShape railShape) {
        world.setBlock(pos, mainState.setValue(POWERED, true), UPDATE_FORCE_PLACE);
        Map<BlockPos,Boolean> checkedPos = new HashMap<>();
        checkedPos.put(pos, true);
        int[] count = new int[2];
        if (railShape == RailShape.NORTH_SOUTH) { //Order: +z, -z
            for(int i = 0; i < NORTH_SOUTH_DIR.length; ++i) {
                setRailPositionsPower(self, world, pos, checkedPos, count, i, NORTH_SOUTH_DIR[i]);
            }
            updateRails(self, false, world, pos, mainState, count);
        } else if (railShape == RailShape.EAST_WEST) { //Order: -x, +x
            for(int i = 0; i < EAST_WEST_DIR.length; ++i) {
                setRailPositionsPower(self, world, pos, checkedPos, count, i, EAST_WEST_DIR[i]);
            }
            updateRails(self, true, world, pos, mainState, count);
        }
    }

    public static void dePowerLane(PoweredRailBlock self, Level world, BlockPos pos,
                                   BlockState mainState, RailShape railShape) {
        world.setBlock(pos, mainState.setValue(POWERED, false), UPDATE_FORCE_PLACE);
        int[] count = new int[2];
        if (railShape == RailShape.NORTH_SOUTH) { //Order: +z, -z
            for(int i = 0; i < NORTH_SOUTH_DIR.length; ++i) {
                setRailPositionsDePower(self, world, pos, count, i, NORTH_SOUTH_DIR[i]);
            }
            updateRails(self, false, world, pos, mainState, count);
        } else if (railShape == RailShape.EAST_WEST) { //Order: -x, +x
            for(int i = 0; i < EAST_WEST_DIR.length; ++i) {
                setRailPositionsDePower(self, world, pos, count, i, EAST_WEST_DIR[i]);
            }
            updateRails(self, true, world, pos, mainState, count);
        }
    }

    private static void setRailPositionsPower(PoweredRailBlock self, Level world, BlockPos pos,
                                       Map<BlockPos, Boolean> checkedPos, int[] count, int i, Direction dir) {
        for (int z = 1; z < railPowerLimit; z++) {
            BlockPos newPos = pos.relative(dir, z);
            BlockState state = world.getBlockState(newPos);
            if (checkedPos.containsKey(newPos)) {
                if (!checkedPos.get(newPos)) break;
                count[i]++;
            } else if (!state.is(self) || state.getValue(POWERED) || !(
                    world.hasNeighborSignal(newPos) ||
                            findPoweredRailSignalFaster(self, world, newPos, state, true, 0, checkedPos) ||
                            findPoweredRailSignalFaster(self, world, newPos, state, false, 0, checkedPos)
            )) {
                checkedPos.put(newPos,false);
                break;
            } else {
                checkedPos.put(newPos,true);
                world.setBlock(newPos, state.setValue(POWERED, true), UPDATE_FORCE_PLACE);
                count[i]++;
            }
        }
    }

    private static void setRailPositionsDePower(PoweredRailBlock self, Level world, BlockPos pos,
                                                int[] count, int i, Direction dir) {
        for (int z = 1; z < railPowerLimit; z++) {
            BlockPos newPos = pos.relative(dir, z);
            BlockState state = world.getBlockState(newPos);
            if (!state.is(self) || !state.getValue(POWERED) || world.hasNeighborSignal(newPos) ||
                    ((PoweredRailBlockInvoker)self).invokeFindPoweredRailSignal(world, newPos, state, true, 0) ||
                    ((PoweredRailBlockInvoker)self).invokeFindPoweredRailSignal(world, newPos, state, false, 0)) break;
            world.setBlock(newPos, state.setValue(POWERED, false), UPDATE_FORCE_PLACE);
            count[i]++;
        }
    }

    private static void shapeUpdateEnd(PoweredRailBlock self, Level world, BlockPos pos, BlockState mainState,
                                       int endPos, Direction direction, int currentPos, BlockPos blockPos) {
        if (currentPos == endPos) {
            BlockPos newPos = pos.relative(direction, currentPos+1);
            RailLogic.giveShapeUpdate(world, mainState, newPos);
            BlockState state = world.getBlockState(blockPos);
            if (state.is(self) && isAscending(state.getValue(SHAPE)))
                RailLogic.giveShapeUpdate(world, mainState, newPos.above());
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
                                                        int c, BlockState mainState, Direction dir,
                                                        int[] count, int countAmt) {
        BlockPos pos1 = pos.relative(dir, c);
        if (c == 0 && count[1] == 0)
            giveShapeUpdate(world, mainState, pos1.relative(dir.getOpposite()));
        shapeUpdateEnd(self, world, pos, mainState, countAmt, dir, c, pos1);
        giveShapeUpdate(world, mainState, pos1.below());
        giveShapeUpdate(world, mainState, pos1.above());
        giveShapeUpdate(world, mainState, pos1.north());
        giveShapeUpdate(world, mainState, pos1.south());
    }

    private static void updateRailsSectionNorthSouthShape(PoweredRailBlock self, Level world, BlockPos pos,
                                                          int c, BlockState mainState, Direction dir,
                                                          int[] count, int countAmt) {
        BlockPos pos1 = pos.relative(dir, c);
        giveShapeUpdate(world, mainState, pos1.west());
        giveShapeUpdate(world, mainState, pos1.east());
        giveShapeUpdate(world, mainState, pos1.below());
        giveShapeUpdate(world, mainState, pos1.above());
        shapeUpdateEnd(self, world, pos, mainState, countAmt, dir, c, pos1);
        if (c == 0 && count[1] == 0)
            giveShapeUpdate(world, mainState, pos1.relative(dir.getOpposite()));
    }

    private static void updateRails(PoweredRailBlock self, boolean eastWest, Level world,
                                    BlockPos pos, BlockState mainState, int[] count) {
        if (eastWest) {
            for (int i = 0; i < EAST_WEST_DIR.length; ++i) {
                int countAmt = count[i];
                if (i == 1 && countAmt == 0) continue;
                Direction dir = EAST_WEST_DIR[i];
                Block block = mainState.getBlock();
                for (int c = countAmt; c >= i; c--) {
                    BlockPos p = pos.relative(dir, c);
                    if (c == 0 && count[1] == 0) notifyNeighborChanged(world, p.relative(dir.getOpposite()), block);
                    neighborUpdateEnd(self, world, pos, countAmt, dir, block, c, p);
                    notifyNeighborChanged(world, p.below(), block);
                    notifyNeighborChanged(world, p.above(), block);
                    notifyNeighborChanged(world, p.north(), block);
                    notifyNeighborChanged(world, p.south(), block);
                    BlockPos pos2 = pos.relative(dir, c).below();
                    notifyNeighborChanged(world, pos2.below(), block);
                    notifyNeighborChanged(world, pos2.north(), block);
                    notifyNeighborChanged(world, pos2.south(), block);
                    if (c == countAmt) notifyNeighborChanged(world, pos.relative(dir, c + 1).below(), block);
                    if (c == 0 && count[1] == 0) notifyNeighborChanged(world, p.relative(dir.getOpposite()).below(), block);
                }
                for (int c = countAmt; c >= i; c--)
                    updateRailsSectionEastWestShape(self, world, pos, c, mainState, dir, count, countAmt);
            }
        } else {
            for(int i = 0; i < NORTH_SOUTH_DIR.length; ++i) {
                int countAmt = count[i];
                if (i == 1 && countAmt == 0) continue;
                Direction dir = NORTH_SOUTH_DIR[i];
                Block block = mainState.getBlock();
                for (int c = countAmt; c >= i; c--) {
                    BlockPos p = pos.relative(dir,c);
                    notifyNeighborChanged(world, p.west(), block);
                    notifyNeighborChanged(world, p.east(), block);
                    notifyNeighborChanged(world, p.below(), block);
                    notifyNeighborChanged(world, p.above(), block);
                    neighborUpdateEnd(self, world, pos, countAmt, dir, block, c, p);
                    if (c == 0 && count[1] == 0) notifyNeighborChanged(world, p.relative(dir.getOpposite()), block);
                    BlockPos pos2 = pos.relative(dir,c).below();
                    notifyNeighborChanged(world, pos2.west(), block);
                    notifyNeighborChanged(world, pos2.east(), block);
                    notifyNeighborChanged(world, pos2.below(), block);
                    if (c == countAmt) notifyNeighborChanged(world, pos.relative(dir,c + 1).below(), block);
                    if (c == 0 && count[1] == 0) notifyNeighborChanged(world, p.relative(dir.getOpposite()).below(), block);
                }
                for (int c = countAmt; c >= i; c--)
                    updateRailsSectionNorthSouthShape(self, world, pos, c, mainState, dir, count, countAmt);
            }
        }
    }
}
