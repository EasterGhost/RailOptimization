package RailOptimization.gametest;

import RailOptimization.RailLogic;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationGameTest extends RailOptimizationGameTestSupport {
    @GameTest(maxTicks = 100)
    public void straightRailLinePowersFromRedstoneSource(GameTestHelper helper) {
        placeRailLinePair(helper, NORTH_SOUTH_LINE_START, Direction.SOUTH, DEFAULT_LINE_LENGTH, RailShape.NORTH_SOUTH);

        compareVanillaAndOptimizedPower(
                helper,
                () -> helper.setBlock(vanillaCopy(REDSTONE_SOURCE_POS), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(REDSTONE_SOURCE_POS, Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailLinePower(
                            helper, vanillaCopy(NORTH_SOUTH_LINE_START), NORTH_SOUTH_LINE_START,
                            Direction.SOUTH, DEFAULT_LINE_LENGTH
                    );
                    assertRailLinePowered(helper, NORTH_SOUTH_LINE_START, Direction.SOUTH, DEFAULT_LINE_LENGTH, true);
                }
        );
    }

    @GameTest(maxTicks = 140)
    public void straightRailLineDepowersAfterSourceRemoval(GameTestHelper helper) {
        placeRailLinePair(helper, NORTH_SOUTH_LINE_START, Direction.SOUTH, DEFAULT_LINE_LENGTH, RailShape.NORTH_SOUTH);

        helper.startSequence()
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(vanillaCopy(REDSTONE_SOURCE_POS), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(REDSTONE_SOURCE_POS, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    assertMatchingRailLinePower(
                            helper, vanillaCopy(NORTH_SOUTH_LINE_START), NORTH_SOUTH_LINE_START,
                            Direction.SOUTH, DEFAULT_LINE_LENGTH
                    );
                    assertRailLinePowered(helper, NORTH_SOUTH_LINE_START, Direction.SOUTH, DEFAULT_LINE_LENGTH, true);
                })
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(vanillaCopy(REDSTONE_SOURCE_POS), Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(REDSTONE_SOURCE_POS, Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    assertMatchingRailLinePower(
                            helper, vanillaCopy(NORTH_SOUTH_LINE_START), NORTH_SOUTH_LINE_START,
                            Direction.SOUTH, DEFAULT_LINE_LENGTH
                    );
                    assertRailLinePowered(helper, NORTH_SOUTH_LINE_START, Direction.SOUTH, DEFAULT_LINE_LENGTH, false);
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 100)
    public void eastWestRailLinePowersFromRedstoneSource(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 2);
        placeRailLinePair(helper, start, Direction.EAST, DEFAULT_LINE_LENGTH, RailShape.EAST_WEST);

        compareVanillaAndOptimizedPower(
                helper,
                () -> helper.setBlock(vanillaCopy(start.north()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(start.north(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailLinePower(helper, vanillaCopy(start), start, Direction.EAST, DEFAULT_LINE_LENGTH);
                    assertRailLinePowered(helper, start, Direction.EAST, DEFAULT_LINE_LENGTH, true);
                }
        );
    }

    @GameTest(maxTicks = 100)
    public void eastWestRailLineRespectsPowerLimitBoundary(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 2);
        placeRailLinePair(helper, start, Direction.EAST, 10, RailShape.EAST_WEST);

        compareVanillaAndOptimizedPower(
                helper,
                () -> helper.setBlock(vanillaCopy(start.north()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(start.north(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailLinePower(helper, vanillaCopy(start), start, Direction.EAST, 10);
                    assertRailLinePowered(helper, start, Direction.EAST, 9, true);
                    helper.assertBlockProperty(start.relative(Direction.EAST, 9), PoweredRailBlock.POWERED, false);
                }
        );
    }

    @GameTest(maxTicks = 100)
    public void railLineStopsPoweringAcrossGap(GameTestHelper helper) {
        BlockPos start = NORTH_SOUTH_LINE_START;
        placeRailLinePair(helper, start, Direction.SOUTH, 4, RailShape.NORTH_SOUTH);
        placeRailLinePair(helper, start.relative(Direction.SOUTH, 5), Direction.SOUTH, 3, RailShape.NORTH_SOUTH);

        compareVanillaAndOptimizedPower(
                helper,
                () -> helper.setBlock(vanillaCopy(start.west()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(start.west(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailLinePower(helper, vanillaCopy(start), start, Direction.SOUTH, 4);
                    assertMatchingRailLinePower(
                            helper, vanillaCopy(start.relative(Direction.SOUTH, 5)),
                            start.relative(Direction.SOUTH, 5), Direction.SOUTH, 3
                    );
                    assertRailLinePowered(helper, start, Direction.SOUTH, 4, true);
                    assertRailLinePowered(helper, start.relative(Direction.SOUTH, 5), Direction.SOUTH, 3, false);
                }
        );
    }

    @GameTest(maxTicks = 100)
    public void railLineRespectsPowerLimitBoundary(GameTestHelper helper) {
        BlockPos start = NORTH_SOUTH_LINE_START;
        placeRailLinePair(helper, start, Direction.SOUTH, 10, RailShape.NORTH_SOUTH);

        compareVanillaAndOptimizedPower(
                helper,
                () -> helper.setBlock(vanillaCopy(start.west()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(start.west(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailLinePower(helper, vanillaCopy(start), start, Direction.SOUTH, 10);
                    assertRailLinePowered(helper, start, Direction.SOUTH, 9, true);
                    helper.assertBlockProperty(start.relative(Direction.SOUTH, 9), PoweredRailBlock.POWERED, false);
                }
        );
    }

    @GameTest(maxTicks = 100)
    public void adjacentMismatchedRailShapesStillReceivePower(GameTestHelper helper) {
        BlockPos start = NORTH_SOUTH_LINE_START;
        BlockPos[] rails = new BlockPos[]{
                start,
                start.relative(Direction.SOUTH),
                start.relative(Direction.SOUTH, 2),
                start.relative(Direction.SOUTH, 3),
                start.relative(Direction.SOUTH, 4)
        };
        RailShape[] shapes = new RailShape[]{
                RailShape.NORTH_SOUTH,
                RailShape.NORTH_SOUTH,
                RailShape.EAST_WEST,
                RailShape.NORTH_SOUTH,
                RailShape.NORTH_SOUTH
        };

        placeRailPathPair(helper, rails, shapes);
        compareVanillaAndOptimizedPower(
                helper,
                () -> helper.setBlock(vanillaCopy(start.west()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(start.west(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailPower(helper, vanillaCopy(rails), rails);
                    assertRailLinePowered(helper, start, Direction.SOUTH, 5, true);
                }
        );
    }
}
