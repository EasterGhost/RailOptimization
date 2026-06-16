package RailOptimization.gametest;

import RailOptimization.RailLogic;
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
    private static final int VANILLA_COPY_Y_OFFSET = 20;
    private static final BlockPos NORTH_SOUTH_LINE_START = new BlockPos(RAIL_X, RAIL_Y, FIRST_RAIL_Z);
    private static final BlockPos REDSTONE_SOURCE_POS = NORTH_SOUTH_LINE_START.west();

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
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

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 140)
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

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
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

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
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

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void middlePoweredEastWestRailLinePowersBothDirectionsWithinLimit(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 3);
        BlockPos sourceRail = start.relative(Direction.EAST, 5);
        int length = 10;

        placeRailLinePair(helper, start, Direction.EAST, length, RailShape.EAST_WEST);

        compareVanillaAndOptimizedPower(
                helper,
                () -> helper.setBlock(vanillaCopy(sourceRail.north()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(sourceRail.north(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailLinePower(helper, vanillaCopy(start), start, Direction.EAST, length);
                    assertRailLinePowered(helper, start, Direction.EAST, length, true);
                }
        );
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 180)
    public void railLineWithTwoSourcesKeepsOriginalPowerAfterOneSourceRemoval(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 3);
        int length = 10;
        BlockPos end = start.relative(Direction.EAST, length - 1);

        placeRailLinePair(helper, start, Direction.EAST, length, RailShape.EAST_WEST);

        helper.startSequence()
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(vanillaCopy(start.north()), Blocks.REDSTONE_BLOCK);
                    helper.setBlock(vanillaCopy(end.north()), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(start.north(), Blocks.REDSTONE_BLOCK);
                    helper.setBlock(end.north(), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailLinePower(helper, vanillaCopy(start), start, Direction.EAST, length);
                    assertRailLinePowered(helper, start, Direction.EAST, length, true);
                })
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(vanillaCopy(start.north()), Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(start.north(), Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailLinePower(helper, vanillaCopy(start), start, Direction.EAST, length);
                    helper.assertBlockProperty(start, PoweredRailBlock.POWERED, false);
                    assertRailLinePowered(helper, start.relative(Direction.EAST), Direction.EAST, length - 1, true);
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
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

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
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

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 140)
    public void ascendingRailPowersAndDepowersFromRedstoneSource(GameTestHelper helper) {
        BlockPos ramp = new BlockPos(2, RAIL_Y, 2);
        BlockPos[] rails = new BlockPos[]{ramp, ramp.east().above()};
        placeAscendingEastRailPair(helper, ramp);

        helper.startSequence()
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(vanillaCopy(ramp.west()), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(ramp.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    assertMatchingRailPower(helper, vanillaCopy(rails), rails);
                    helper.assertBlockProperty(ramp, PoweredRailBlock.POWERED, true);
                })
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(vanillaCopy(ramp.west()), Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(ramp.west(), Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    assertMatchingRailPower(helper, vanillaCopy(rails), rails);
                    helper.assertBlockProperty(ramp, PoweredRailBlock.POWERED, false);
                })
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
                new BlockPos(7, RAIL_Y, 2),
                new BlockPos(8, RAIL_Y, 2),
                new BlockPos(9, RAIL_Y, 2),
                new BlockPos(10, RAIL_Y, 2)
        };
        RailShape[] shapes = new RailShape[]{
                RailShape.EAST_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.EAST_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.EAST_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.EAST_WEST,
                RailShape.EAST_WEST,
                RailShape.EAST_WEST,
                RailShape.EAST_WEST
        };

        placeRailPathPair(helper, rails, shapes);
        compareVanillaAndOptimizedPower(
                helper,
                () -> helper.setBlock(vanillaCopy(rails[0].north()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(rails[0].north(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailPower(helper, vanillaCopy(rails), rails);
                    assertRailsPowered(helper, rails, 0, 9, true);
                    assertRailsPowered(helper, rails, 9, rails.length, false);
                }
        );
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
                new BlockPos(9, RAIL_Y + 2, 2),
                new BlockPos(10, RAIL_Y + 2, 2),
                new BlockPos(11, RAIL_Y + 2, 2)
        };
        RailShape[] shapes = new RailShape[]{
                RailShape.EAST_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.EAST_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.EAST_WEST,
                RailShape.ASCENDING_EAST,
                RailShape.EAST_WEST,
                RailShape.ASCENDING_EAST,
                RailShape.EAST_WEST,
                RailShape.EAST_WEST,
                RailShape.EAST_WEST
        };

        placeRailPathPair(helper, rails, shapes);
        compareVanillaAndOptimizedPower(
                helper,
                () -> helper.setBlock(vanillaCopy(rails[0].north()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(rails[0].north(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailPower(helper, vanillaCopy(rails), rails);
                    assertRailsPowered(helper, rails, 0, 9, true);
                    assertRailsPowered(helper, rails, 9, rails.length, false);
                }
        );
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void consecutiveDescendingRailsPowerFollowingFlatRail(GameTestHelper helper) {
        BlockPos[] rails = new BlockPos[]{
                new BlockPos(1, RAIL_Y + 2, 2),
                new BlockPos(2, RAIL_Y + 1, 2),
                new BlockPos(3, RAIL_Y, 2),
                new BlockPos(4, RAIL_Y, 2),
                new BlockPos(5, RAIL_Y, 2)
        };
        RailShape[] shapes = new RailShape[]{
                RailShape.EAST_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.EAST_WEST,
                RailShape.EAST_WEST
        };

        placeRailPathPair(helper, rails, shapes);
        compareVanillaAndOptimizedPower(
                helper,
                () -> helper.setBlock(vanillaCopy(rails[0].north()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(rails[0].north(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailPower(helper, vanillaCopy(rails), rails);
                    assertRailsPowered(helper, rails, true);
                }
        );
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void consecutiveDescendingRailsPowerFollowingAscendingRail(GameTestHelper helper) {
        BlockPos[] rails = new BlockPos[]{
                new BlockPos(1, RAIL_Y + 2, 2),
                new BlockPos(2, RAIL_Y + 1, 2),
                new BlockPos(3, RAIL_Y, 2),
                new BlockPos(4, RAIL_Y, 2),
                new BlockPos(5, RAIL_Y + 1, 2),
                new BlockPos(6, RAIL_Y + 1, 2)
        };
        RailShape[] shapes = new RailShape[]{
                RailShape.EAST_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.ASCENDING_EAST,
                RailShape.EAST_WEST,
                RailShape.EAST_WEST
        };

        placeRailPathPair(helper, rails, shapes);
        compareVanillaAndOptimizedPower(
                helper,
                () -> helper.setBlock(vanillaCopy(rails[0].north()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(rails[0].north(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailPower(helper, vanillaCopy(rails), rails);
                    assertRailsPowered(helper, rails, true);
                }
        );
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void continuousDescendingThenContinuousAscendingRailsMatchVanilla(GameTestHelper helper) {
        BlockPos[] rails = new BlockPos[]{
                new BlockPos(1, RAIL_Y + 4, 2),
                new BlockPos(2, RAIL_Y + 3, 2),
                new BlockPos(3, RAIL_Y + 2, 2),
                new BlockPos(4, RAIL_Y + 1, 2),
                new BlockPos(5, RAIL_Y, 2),
                new BlockPos(6, RAIL_Y, 2),
                new BlockPos(7, RAIL_Y + 1, 2),
                new BlockPos(8, RAIL_Y + 2, 2),
                new BlockPos(9, RAIL_Y + 3, 2),
                new BlockPos(10, RAIL_Y + 4, 2)
        };
        RailShape[] shapes = new RailShape[]{
                RailShape.EAST_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.ASCENDING_EAST,
                RailShape.ASCENDING_EAST,
                RailShape.ASCENDING_EAST,
                RailShape.ASCENDING_EAST,
                RailShape.EAST_WEST
        };

        placeRailPathPair(helper, rails, shapes);
        compareVanillaAndOptimizedPower(
                helper,
                () -> helper.setBlock(vanillaCopy(rails[0].north()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(rails[0].north(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailPower(helper, vanillaCopy(rails), rails);
                    assertRailsPowered(helper, rails, 0, 9, true);
                    assertRailsPowered(helper, rails, 9, rails.length, false);
                }
        );
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void northSouthContinuousDescendingThenContinuousAscendingRailsMatchVanilla(GameTestHelper helper) {
        BlockPos[] rails = new BlockPos[]{
                new BlockPos(2, RAIL_Y + 4, 1),
                new BlockPos(2, RAIL_Y + 3, 2),
                new BlockPos(2, RAIL_Y + 2, 3),
                new BlockPos(2, RAIL_Y + 1, 4),
                new BlockPos(2, RAIL_Y, 5),
                new BlockPos(2, RAIL_Y, 6),
                new BlockPos(2, RAIL_Y + 1, 7),
                new BlockPos(2, RAIL_Y + 2, 8),
                new BlockPos(2, RAIL_Y + 3, 9),
                new BlockPos(2, RAIL_Y + 4, 10)
        };
        RailShape[] shapes = new RailShape[]{
                RailShape.NORTH_SOUTH,
                RailShape.ASCENDING_NORTH,
                RailShape.ASCENDING_NORTH,
                RailShape.ASCENDING_NORTH,
                RailShape.ASCENDING_NORTH,
                RailShape.ASCENDING_SOUTH,
                RailShape.ASCENDING_SOUTH,
                RailShape.ASCENDING_SOUTH,
                RailShape.ASCENDING_SOUTH,
                RailShape.NORTH_SOUTH
        };

        placeRailPathPair(helper, rails, shapes);
        compareVanillaAndOptimizedPower(
                helper,
                () -> helper.setBlock(vanillaCopy(rails[0].west()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(rails[0].west(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailPower(helper, vanillaCopy(rails), rails);
                    assertRailsPowered(helper, rails, 0, 9, true);
                    assertRailsPowered(helper, rails, 9, rails.length, false);
                }
        );
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void flatThenContinuousDescendingThenContinuousAscendingThenFlatRailsMatchVanilla(GameTestHelper helper) {
        BlockPos[] rails = new BlockPos[]{
                new BlockPos(1, RAIL_Y + 3, 2),
                new BlockPos(2, RAIL_Y + 3, 2),
                new BlockPos(3, RAIL_Y + 3, 2),
                new BlockPos(4, RAIL_Y + 2, 2),
                new BlockPos(5, RAIL_Y + 1, 2),
                new BlockPos(6, RAIL_Y, 2),
                new BlockPos(7, RAIL_Y, 2),
                new BlockPos(8, RAIL_Y + 1, 2),
                new BlockPos(9, RAIL_Y + 2, 2),
                new BlockPos(10, RAIL_Y + 2, 2)
        };
        RailShape[] shapes = new RailShape[]{
                RailShape.EAST_WEST,
                RailShape.EAST_WEST,
                RailShape.EAST_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.ASCENDING_EAST,
                RailShape.ASCENDING_EAST,
                RailShape.EAST_WEST,
                RailShape.EAST_WEST
        };

        placeRailPathPair(helper, rails, shapes);
        compareVanillaAndOptimizedPower(
                helper,
                () -> helper.setBlock(vanillaCopy(rails[0].north()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(rails[0].north(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailPower(helper, vanillaCopy(rails), rails);
                    assertRailsPowered(helper, rails, 0, 9, true);
                    assertRailsPowered(helper, rails, 9, rails.length, false);
                }
        );
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void neighborCountersReceiveNorthSouthRailUpdatePositions(GameTestHelper helper) {
        BlockPos start = new BlockPos(3, RAIL_Y, 2);
        BlockPos monitoredRail = start.relative(Direction.SOUTH, 2);
        BlockPos[] counters = new BlockPos[]{
                monitoredRail.west(),
                monitoredRail.east(),
                monitoredRail.above(),
                monitoredRail.below().west(),
                monitoredRail.below().east(),
                monitoredRail.below().below(),
                start.relative(Direction.SOUTH, 4),
                start.relative(Direction.SOUTH, 4).below(),
                start.north(),
                start.north().below()
        };

        placeRailLinePair(helper, start, Direction.SOUTH, 4, RailShape.NORTH_SOUTH);
        placeNeighborCounters(helper, counters);

        compareVanillaAndOptimizedPower(
                helper,
                () -> helper.setBlock(vanillaCopy(start.west()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(start.west(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailLinePower(helper, vanillaCopy(start), start, Direction.SOUTH, 4);
                    assertNeighborCountersUpdated(helper, counters);
                }
        );
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void neighborCountersReceiveEastWestRailUpdatePositions(GameTestHelper helper) {
        BlockPos start = new BlockPos(2, RAIL_Y, 3);
        BlockPos monitoredRail = start.relative(Direction.EAST, 2);
        BlockPos[] counters = new BlockPos[]{
                monitoredRail.north(),
                monitoredRail.south(),
                monitoredRail.above(),
                monitoredRail.below().north(),
                monitoredRail.below().south(),
                monitoredRail.below().below(),
                start.relative(Direction.EAST, 4),
                start.relative(Direction.EAST, 4).below()
        };

        placeRailLinePair(helper, start, Direction.EAST, 4, RailShape.EAST_WEST);
        placeNeighborCounters(helper, counters);

        compareVanillaAndOptimizedPower(
                helper,
                () -> helper.setBlock(vanillaCopy(start.north()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(start.north(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailLinePower(helper, vanillaCopy(start), start, Direction.EAST, 4);
                    assertNeighborCountersUpdated(helper, counters);
                }
        );
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void adjacentMismatchedRailShapesMatchVanilla(GameTestHelper helper) {
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
                    helper.assertBlockProperty(start, PoweredRailBlock.POWERED, true);
                }
        );
    }

    private static void compareVanillaAndOptimizedPower(GameTestHelper helper, Runnable vanillaTrigger,
                                                        Runnable optimizedTrigger, Runnable assertions) {
        helper.startSequence()
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    vanillaTrigger.run();
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    optimizedTrigger.run();
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    assertions.run();
                })
                .thenSucceed();
    }

    private static BlockPos vanillaCopy(BlockPos pos) {
        return pos.above(VANILLA_COPY_Y_OFFSET);
    }

    private static BlockPos[] vanillaCopy(BlockPos[] positions) {
        BlockPos[] copy = new BlockPos[positions.length];
        for (int i = 0; i < positions.length; i++) {
            copy[i] = vanillaCopy(positions[i]);
        }
        return copy;
    }

    private static void placeRailLinePair(GameTestHelper helper, BlockPos start, Direction direction,
                                          int length, RailShape shape) {
        placeRailLine(helper, start, direction, length, shape);
        placeRailLine(helper, vanillaCopy(start), direction, length, shape);
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

    private static void placeRailPathPair(GameTestHelper helper, BlockPos[] rails, RailShape[] shapes) {
        placeRailPath(helper, rails, shapes);
        placeRailPath(helper, vanillaCopy(rails), shapes);
    }

    private static void placeRailPath(GameTestHelper helper, BlockPos[] rails, RailShape[] shapes) {
        if (rails.length != shapes.length) {
            throw new IllegalArgumentException("rails and shapes must have the same length");
        }

        for (int i = 0; i < rails.length; i++) {
            placeRail(helper, rails[i], shapes[i]);
        }
    }

    private static void placeRail(GameTestHelper helper, BlockPos railPos, RailShape shape) {
        helper.setBlock(railPos.below(), Blocks.STONE);
        helper.setBlock(railPos, Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, shape));
    }

    private static void placeNeighborCounters(GameTestHelper helper, BlockPos[] counters) {
        BlockState counter = RailOptimizationGameTestMod.NEIGHBOR_COUNTER.defaultBlockState();
        for (BlockPos counterPos : counters) {
            helper.setBlock(counterPos, counter);
        }
    }

    private static void placeAscendingEastRailPair(GameTestHelper helper, BlockPos ramp) {
        placeAscendingEastRail(helper, ramp);
        placeAscendingEastRail(helper, vanillaCopy(ramp));
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

    private static void assertNeighborCountersUpdated(GameTestHelper helper, BlockPos[] counters) {
        for (BlockPos counterPos : counters) {
            helper.assertBlockProperty(
                    counterPos,
                    RailOptimizationGameTestMod.NeighborCounterBlock.COUNT,
                    count -> count > 0,
                    "expected neighbor counter to receive an update"
            );
        }
    }

    private static void assertRailsPowered(GameTestHelper helper, BlockPos[] rails, boolean powered) {
        assertRailsPowered(helper, rails, 0, rails.length, powered);
    }

    private static void assertRailsPowered(GameTestHelper helper, BlockPos[] rails,
                                           int startIndex, int endIndex, boolean powered) {
        for (int railIndex = startIndex; railIndex < endIndex; railIndex++) {
            helper.assertBlockProperty(rails[railIndex], PoweredRailBlock.POWERED, powered);
        }
    }

    private static void assertMatchingRailLinePower(GameTestHelper helper, BlockPos firstStart, BlockPos secondStart,
                                                    Direction direction, int length) {
        for (int step = 0; step < length; step++) {
            assertMatchingRailPower(
                    helper,
                    firstStart.relative(direction, step),
                    secondStart.relative(direction, step)
            );
        }
    }

    private static void assertMatchingRailPower(GameTestHelper helper, BlockPos[] firstRails, BlockPos[] secondRails) {
        if (firstRails.length != secondRails.length) {
            throw new IllegalArgumentException("rail arrays must have the same length");
        }

        for (int i = 0; i < firstRails.length; i++) {
            assertMatchingRailPower(helper, firstRails[i], secondRails[i]);
        }
    }

    private static void assertMatchingRailPower(GameTestHelper helper, BlockPos firstRail, BlockPos secondRail) {
        boolean firstPowered = helper.getBlockState(firstRail).getValue(PoweredRailBlock.POWERED);
        helper.assertBlockProperty(secondRail, PoweredRailBlock.POWERED, firstPowered);
    }
}
