package RailOptimization.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationGameTest {
    private static final int RAIL_Y = 2;
    private static final int RAIL_X = 2;
    private static final int FIRST_RAIL_Z = 1;
    private static final int LAST_RAIL_Z = 7;
    private static final int DEFAULT_LINE_LENGTH = LAST_RAIL_Z - FIRST_RAIL_Z + 1;
    private static final BlockPos NORTH_SOUTH_LINE_START = new BlockPos(RAIL_X, RAIL_Y, FIRST_RAIL_Z);
    private static final BlockPos REDSTONE_SOURCE_POS = NORTH_SOUTH_LINE_START.west();

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
    public void straightRailLinePowersFromRedstoneSource(GameTestHelper helper) {
        placeRailLine(helper, NORTH_SOUTH_LINE_START, Direction.SOUTH, DEFAULT_LINE_LENGTH, RailShape.NORTH_SOUTH);
        helper.setBlock(REDSTONE_SOURCE_POS, Blocks.REDSTONE_BLOCK);

        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> assertRailLinePowered(
                        helper, NORTH_SOUTH_LINE_START, Direction.SOUTH, DEFAULT_LINE_LENGTH, true
                ))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void straightRailLineDepowersAfterSourceRemoval(GameTestHelper helper) {
        placeRailLine(helper, NORTH_SOUTH_LINE_START, Direction.SOUTH, DEFAULT_LINE_LENGTH, RailShape.NORTH_SOUTH);
        helper.setBlock(REDSTONE_SOURCE_POS, Blocks.REDSTONE_BLOCK);

        helper.startSequence()
                .thenWaitUntil(() -> assertRailLinePowered(
                        helper, NORTH_SOUTH_LINE_START, Direction.SOUTH, DEFAULT_LINE_LENGTH, true
                ))
                .thenExecute(() -> helper.setBlock(REDSTONE_SOURCE_POS, Blocks.AIR))
                .thenIdle(4)
                .thenExecute(() -> assertRailLinePowered(
                        helper, NORTH_SOUTH_LINE_START, Direction.SOUTH, DEFAULT_LINE_LENGTH, false
                ))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
    public void eastWestRailLinePowersFromRedstoneSource(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 2);
        placeRailLine(helper, start, Direction.EAST, DEFAULT_LINE_LENGTH, RailShape.EAST_WEST);
        helper.setBlock(start.north(), Blocks.REDSTONE_BLOCK);

        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> assertRailLinePowered(helper, start, Direction.EAST, DEFAULT_LINE_LENGTH, true))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
    public void railLineStopsPoweringAcrossGap(GameTestHelper helper) {
        BlockPos start = NORTH_SOUTH_LINE_START;
        placeRailLine(helper, start, Direction.SOUTH, 4, RailShape.NORTH_SOUTH);
        placeRailLine(helper, start.relative(Direction.SOUTH, 5), Direction.SOUTH, 3, RailShape.NORTH_SOUTH);
        helper.setBlock(start.west(), Blocks.REDSTONE_BLOCK);

        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> {
                    assertRailLinePowered(helper, start, Direction.SOUTH, 4, true);
                    assertRailLinePowered(helper, start.relative(Direction.SOUTH, 5), Direction.SOUTH, 3, false);
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
    public void railLineRespectsPowerLimitBoundary(GameTestHelper helper) {
        BlockPos start = NORTH_SOUTH_LINE_START;
        placeRailLine(helper, start, Direction.SOUTH, 10, RailShape.NORTH_SOUTH);
        helper.setBlock(start.west(), Blocks.REDSTONE_BLOCK);

        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> {
                    assertRailLinePowered(helper, start, Direction.SOUTH, 9, true);
                    helper.assertBlockProperty(start.relative(Direction.SOUTH, 9), PoweredRailBlock.POWERED, false);
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void ascendingRailPowersAndDepowersFromRedstoneSource(GameTestHelper helper) {
        BlockPos ramp = new BlockPos(2, RAIL_Y, 2);
        placeAscendingEastRail(helper, ramp);
        helper.setBlock(ramp.west(), Blocks.REDSTONE_BLOCK);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlockProperty(ramp, PoweredRailBlock.POWERED, true))
                .thenExecute(() -> helper.setBlock(ramp.west(), Blocks.AIR))
                .thenIdle(4)
                .thenExecute(() -> helper.assertBlockProperty(ramp, PoweredRailBlock.POWERED, false))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void descendingRailLinePowersAcrossMultipleSteps(GameTestHelper helper) {
        BlockPos[] rails = new BlockPos[]{
                new BlockPos(1, RAIL_Y + 3, 2),
                new BlockPos(2, RAIL_Y + 2, 2),
                new BlockPos(3, RAIL_Y + 2, 2),
                new BlockPos(4, RAIL_Y + 1, 2),
                new BlockPos(5, RAIL_Y + 1, 2),
                new BlockPos(6, RAIL_Y, 2),
                new BlockPos(7, RAIL_Y, 2)
        };

        placeRail(helper, rails[0], RailShape.EAST_WEST);
        placeRail(helper, rails[1], RailShape.ASCENDING_WEST);
        placeRail(helper, rails[2], RailShape.EAST_WEST);
        placeRail(helper, rails[3], RailShape.ASCENDING_WEST);
        placeRail(helper, rails[4], RailShape.EAST_WEST);
        placeRail(helper, rails[5], RailShape.ASCENDING_WEST);
        placeRail(helper, rails[6], RailShape.EAST_WEST);
        helper.setBlock(rails[0].north(), Blocks.REDSTONE_BLOCK);

        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> assertRailsPowered(helper, rails, true))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void descendingThenAscendingRailLinePowersAcrossMultipleSteps(GameTestHelper helper) {
        BlockPos[] rails = new BlockPos[]{
                new BlockPos(1, RAIL_Y + 2, 2),
                new BlockPos(2, RAIL_Y + 1, 2),
                new BlockPos(3, RAIL_Y + 1, 2),
                new BlockPos(4, RAIL_Y, 2),
                new BlockPos(5, RAIL_Y, 2),
                new BlockPos(6, RAIL_Y, 2),
                new BlockPos(7, RAIL_Y + 1, 2),
                new BlockPos(8, RAIL_Y + 1, 2),
                new BlockPos(9, RAIL_Y + 2, 2)
        };

        placeRail(helper, rails[0], RailShape.EAST_WEST);
        placeRail(helper, rails[1], RailShape.ASCENDING_WEST);
        placeRail(helper, rails[2], RailShape.EAST_WEST);
        placeRail(helper, rails[3], RailShape.ASCENDING_WEST);
        placeRail(helper, rails[4], RailShape.EAST_WEST);
        placeRail(helper, rails[5], RailShape.ASCENDING_EAST);
        placeRail(helper, rails[6], RailShape.EAST_WEST);
        placeRail(helper, rails[7], RailShape.ASCENDING_EAST);
        placeRail(helper, rails[8], RailShape.EAST_WEST);
        helper.setBlock(rails[0].north(), Blocks.REDSTONE_BLOCK);

        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> assertRailsPowered(helper, rails, true))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
    public void adjacentMismatchedRailShapesStillReceivePower(GameTestHelper helper) {
        BlockPos start = NORTH_SOUTH_LINE_START;
        placeRailLine(helper, start, Direction.SOUTH, 2, RailShape.NORTH_SOUTH);
        placeRail(helper, start.relative(Direction.SOUTH, 2), RailShape.EAST_WEST);
        placeRailLine(helper, start.relative(Direction.SOUTH, 3), Direction.SOUTH, 2, RailShape.NORTH_SOUTH);
        helper.setBlock(start.west(), Blocks.REDSTONE_BLOCK);

        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> assertRailLinePowered(helper, start, Direction.SOUTH, 5, true))
                .thenSucceed();
    }

    private static void placeRailLine(GameTestHelper helper, BlockPos start, Direction direction,
                                      int length, RailShape shape) {
        BlockState rail = Blocks.POWERED_RAIL.defaultBlockState()
                .setValue(PoweredRailBlock.SHAPE, shape);

        for (int step = 0; step < length; step++) {
            BlockPos railPos = start.relative(direction, step);
            helper.setBlock(railPos.below(), Blocks.STONE);
            helper.setBlock(railPos, rail);
        }
    }

    private static void placeRail(GameTestHelper helper, BlockPos railPos, RailShape shape) {
        helper.setBlock(railPos.below(), Blocks.STONE);
        helper.setBlock(railPos, Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, shape));
    }

    private static void placeAscendingEastRail(GameTestHelper helper, BlockPos ramp) {
        helper.setBlock(ramp.below(), Blocks.STONE);
        helper.setBlock(ramp.east(), Blocks.STONE);
        helper.setBlock(ramp, Blocks.POWERED_RAIL.defaultBlockState()
                .setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_EAST));
        helper.setBlock(ramp.east().above(), Blocks.POWERED_RAIL.defaultBlockState()
                .setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST));
    }

    private static void assertRailLinePowered(GameTestHelper helper, BlockPos start, Direction direction,
                                              int length, boolean powered) {
        for (int step = 0; step < length; step++) {
            helper.assertBlockProperty(start.relative(direction, step), PoweredRailBlock.POWERED, powered);
        }
    }

    private static void assertRailsPowered(GameTestHelper helper, BlockPos[] rails, boolean powered) {
        for (BlockPos rail : rails) {
            helper.assertBlockProperty(rail, PoweredRailBlock.POWERED, powered);
        }
    }
}
