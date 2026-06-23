package RailOptimization.gametest;

import RailOptimization.RailLogic;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationSlopeGameTest extends RailOptimizationGameTestSupport {
    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_13", maxTicks = 140)
    public void ascendingRailPowersAndDepowersFromRedstoneSource(GameTestHelper helper) {
        BlockPos ramp = new BlockPos(2, RAIL_Y, 2);
        BlockPos[] rails = new BlockPos[]{ramp, ramp.east().above()};
        placeAscendingEastRailPair(helper, ramp);

        helper.startSequence()
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(ramp.west()), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(ramp.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    assertMatchingRailPower(helper, mirrorCopy(rails), rails);
                    helper.assertBlockProperty(ramp, PoweredRailBlock.POWERED, true);
                })
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(ramp.west()), Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(ramp.west(), Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    assertMatchingRailPower(helper, mirrorCopy(rails), rails);
                    helper.assertBlockProperty(ramp, PoweredRailBlock.POWERED, false);
                })
                .thenSucceed();
    }

    @GameTest(environment = "railoptimization-gametest:serial_14", maxTicks = 100)
    public void ascendingWestRailPowersFromRedstoneSource(GameTestHelper helper) {
        BlockPos ramp = new BlockPos(4, RAIL_Y, 2);
        BlockPos[] rails = new BlockPos[]{
                ramp,
                ramp.west().above()
        };
        RailShape[] shapes = new RailShape[]{
                RailShape.ASCENDING_WEST,
                RailShape.EAST_WEST
        };

        compareMirroredPathPower(
                helper,
                rails,
                shapes,
                ramp.north(),
                () -> {
                    assertMatchingRailPower(helper, mirrorCopy(rails), rails);
                    assertRailsPowered(helper, rails, true);
                }
        );
    }

    @GameTest(environment = "railoptimization-gametest:serial_15", maxTicks = 100)
    public void ascendingNorthRailPowersFromRedstoneSource(GameTestHelper helper) {
        BlockPos ramp = new BlockPos(3, RAIL_Y, 4);
        BlockPos[] rails = new BlockPos[]{
                ramp,
                ramp.north().above()
        };
        RailShape[] shapes = new RailShape[]{
                RailShape.ASCENDING_NORTH,
                RailShape.NORTH_SOUTH
        };

        compareMirroredPathPower(
                helper,
                rails,
                shapes,
                ramp.west(),
                () -> {
                    assertMatchingRailPower(helper, mirrorCopy(rails), rails);
                    assertRailsPowered(helper, rails, true);
                }
        );
    }

    @GameTest(environment = "railoptimization-gametest:serial_16", maxTicks = 100)
    public void ascendingSouthRailPowersFromRedstoneSource(GameTestHelper helper) {
        BlockPos ramp = new BlockPos(3, RAIL_Y, 2);
        BlockPos[] rails = new BlockPos[]{
                ramp,
                ramp.south().above()
        };
        RailShape[] shapes = new RailShape[]{
                RailShape.ASCENDING_SOUTH,
                RailShape.NORTH_SOUTH
        };

        compareMirroredPathPower(
                helper,
                rails,
                shapes,
                ramp.west(),
                () -> {
                    assertMatchingRailPower(helper, mirrorCopy(rails), rails);
                    assertRailsPowered(helper, rails, true);
                }
        );
    }

    @GameTest(environment = "railoptimization-gametest:serial_17", maxTicks = 100)
    public void ascendingEastRailPowersFromUpperEndSource(GameTestHelper helper) {
        BlockPos ramp = new BlockPos(2, RAIL_Y, 2);
        BlockPos upperRail = ramp.east().above();
        BlockPos[] rails = new BlockPos[]{
                ramp,
                upperRail
        };
        RailShape[] shapes = new RailShape[]{
                RailShape.ASCENDING_EAST,
                RailShape.EAST_WEST
        };

        compareMirroredPathPower(
                helper,
                rails,
                shapes,
                upperRail.north(),
                () -> {
                    assertMatchingRailPower(helper, mirrorCopy(rails), rails);
                    assertRailsPowered(helper, rails, true);
                }
        );
    }

    @GameTest(environment = "railoptimization-gametest:serial_18", maxTicks = 100)
    public void continuousAscendingEastRailsRespectPowerLimitBoundary(GameTestHelper helper) {
        BlockPos[] rails = new BlockPos[]{
                new BlockPos(1, RAIL_Y, 2),
                new BlockPos(2, RAIL_Y + 1, 2),
                new BlockPos(3, RAIL_Y + 2, 2),
                new BlockPos(4, RAIL_Y + 3, 2),
                new BlockPos(5, RAIL_Y + 4, 2),
                new BlockPos(6, RAIL_Y + 5, 2),
                new BlockPos(7, RAIL_Y + 6, 2),
                new BlockPos(8, RAIL_Y + 7, 2),
                new BlockPos(9, RAIL_Y + 8, 2),
                new BlockPos(10, RAIL_Y + 9, 2)
        };
        RailShape[] shapes = new RailShape[]{
                RailShape.ASCENDING_EAST,
                RailShape.ASCENDING_EAST,
                RailShape.ASCENDING_EAST,
                RailShape.ASCENDING_EAST,
                RailShape.ASCENDING_EAST,
                RailShape.ASCENDING_EAST,
                RailShape.ASCENDING_EAST,
                RailShape.ASCENDING_EAST,
                RailShape.ASCENDING_EAST,
                RailShape.ASCENDING_EAST
        };

        compareMirroredPathPower(
                helper,
                rails,
                shapes,
                rails[0].north(),
                () -> {
                    assertMatchingRailPower(helper, mirrorCopy(rails), rails);
                    assertRailsPowered(helper, rails, 0, 9, true);
                    assertRailsPowered(helper, rails, 9, rails.length, false);
                }
        );
    }

    @GameTest(environment = "railoptimization-gametest:serial_19", maxTicks = 100)
    public void continuousAscendingWestRailsRespectPowerLimitBoundary(GameTestHelper helper) {
        BlockPos[] rails = new BlockPos[]{
                new BlockPos(10, RAIL_Y, 2),
                new BlockPos(9, RAIL_Y + 1, 2),
                new BlockPos(8, RAIL_Y + 2, 2),
                new BlockPos(7, RAIL_Y + 3, 2),
                new BlockPos(6, RAIL_Y + 4, 2),
                new BlockPos(5, RAIL_Y + 5, 2),
                new BlockPos(4, RAIL_Y + 6, 2),
                new BlockPos(3, RAIL_Y + 7, 2),
                new BlockPos(2, RAIL_Y + 8, 2),
                new BlockPos(1, RAIL_Y + 9, 2)
        };
        RailShape[] shapes = new RailShape[]{
                RailShape.ASCENDING_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.ASCENDING_WEST
        };

        compareMirroredPathPower(
                helper,
                rails,
                shapes,
                rails[0].north(),
                () -> {
                    assertMatchingRailPower(helper, mirrorCopy(rails), rails);
                    assertRailsPowered(helper, rails, 0, 9, true);
                    assertRailsPowered(helper, rails, 9, rails.length, false);
                }
        );
    }

    @GameTest(environment = "railoptimization-gametest:serial_20", maxTicks = 100)
    public void continuousAscendingSouthRailsRespectPowerLimitBoundary(GameTestHelper helper) {
        BlockPos[] rails = new BlockPos[]{
                new BlockPos(2, RAIL_Y, 1),
                new BlockPos(2, RAIL_Y + 1, 2),
                new BlockPos(2, RAIL_Y + 2, 3),
                new BlockPos(2, RAIL_Y + 3, 4),
                new BlockPos(2, RAIL_Y + 4, 5),
                new BlockPos(2, RAIL_Y + 5, 6),
                new BlockPos(2, RAIL_Y + 6, 7),
                new BlockPos(2, RAIL_Y + 7, 8),
                new BlockPos(2, RAIL_Y + 8, 9),
                new BlockPos(2, RAIL_Y + 9, 10)
        };
        RailShape[] shapes = new RailShape[]{
                RailShape.ASCENDING_SOUTH,
                RailShape.ASCENDING_SOUTH,
                RailShape.ASCENDING_SOUTH,
                RailShape.ASCENDING_SOUTH,
                RailShape.ASCENDING_SOUTH,
                RailShape.ASCENDING_SOUTH,
                RailShape.ASCENDING_SOUTH,
                RailShape.ASCENDING_SOUTH,
                RailShape.ASCENDING_SOUTH,
                RailShape.ASCENDING_SOUTH
        };

        compareMirroredPathPower(
                helper,
                rails,
                shapes,
                rails[0].west(),
                () -> {
                    assertMatchingRailPower(helper, mirrorCopy(rails), rails);
                    assertRailsPowered(helper, rails, 0, 9, true);
                    assertRailsPowered(helper, rails, 9, rails.length, false);
                }
        );
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_21", maxTicks = 100)
    public void ascendingEastRailStopsAtMissingUpperRailGap(GameTestHelper helper) {
        BlockPos ramp = new BlockPos(2, RAIL_Y, 2);
        BlockPos disconnectedRail = ramp.east(2).above();
        BlockPos[] rails = new BlockPos[]{
                ramp,
                disconnectedRail
        };
        RailShape[] shapes = new RailShape[]{
                RailShape.ASCENDING_EAST,
                RailShape.EAST_WEST
        };

        compareMirroredPathPower(
                helper,
                rails,
                shapes,
                ramp.north(),
                () -> {
                    assertMatchingRailPower(helper, mirrorCopy(rails), rails);
                    helper.assertBlockProperty(rails[0], PoweredRailBlock.POWERED, true);
                    assertRailsPowered(helper, rails, 1, rails.length, false);
                }
        );
    }

    @GameTest(environment = "railoptimization-gametest:serial_22", maxTicks = 160)
    public void sawtoothDescendingRailsPropagateOnlyLeftToRightByAssertion(GameTestHelper helper) {
        BlockPos[] rails = sawtoothDescendingRails(2);
        RailShape[] shapes = sawtoothDescendingRailShapes();

        placeRailPath(helper, rails, shapes);

        helper.startSequence()
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(rails[0].west(), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    assertRailsPowered(helper, rails, true);
                })
                .thenExecute(() -> helper.setBlock(rails[0].west(), Blocks.AIR))
                .thenIdle(4)
                .thenExecute(() -> assertRailsPowered(helper, rails, false))
                .thenExecute(() -> helper.setBlock(rails[rails.length - 1].east(), Blocks.REDSTONE_BLOCK))
                .thenIdle(4)
                .thenExecute(() -> {
                    assertRailsPowered(helper, rails, 0, 4, false);
                    assertRailsPowered(helper, rails, 4, rails.length, true);
                })
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_23", maxTicks = 100)
    public void sawtoothDescendingRailsMatchOriginalDirectionalGaps(GameTestHelper helper) {
        BlockPos[] leftToRightRails = sawtoothDescendingRails(2);
        BlockPos[] rightToLeftRails = sawtoothDescendingRails(5);
        RailShape[] shapes = sawtoothDescendingRailShapes();

        placeRailPathPair(helper, leftToRightRails, shapes);
        placeRailPathPair(helper, rightToLeftRails, shapes);
        compareMirroredAndOptimizedPower(
                helper,
                () -> {
                    helper.setBlock(mirrorCopy(leftToRightRails[0].west()), Blocks.REDSTONE_BLOCK);
                    helper.setBlock(mirrorCopy(rightToLeftRails[rightToLeftRails.length - 1].east()),
                            Blocks.REDSTONE_BLOCK);
                },
                () -> {
                    helper.setBlock(leftToRightRails[0].west(), Blocks.REDSTONE_BLOCK);
                    helper.setBlock(rightToLeftRails[rightToLeftRails.length - 1].east(), Blocks.REDSTONE_BLOCK);
                },
                () -> {
                    assertMatchingRailPower(helper, mirrorCopy(leftToRightRails), leftToRightRails);
                    assertMatchingRailPower(helper, mirrorCopy(rightToLeftRails), rightToLeftRails);
                }
        );
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_24", maxTicks = 100)
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
        compareMirroredAndOptimizedPower(
                helper,
                () -> helper.setBlock(mirrorCopy(rails[0].north()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(rails[0].north(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailPower(helper, mirrorCopy(rails), rails);
                    assertRailsPowered(helper, rails, 0, 9, true);
                    assertRailsPowered(helper, rails, 9, rails.length, false);
                }
        );
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_25", maxTicks = 100)
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
        compareMirroredAndOptimizedPower(
                helper,
                () -> helper.setBlock(mirrorCopy(rails[0].north()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(rails[0].north(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailPower(helper, mirrorCopy(rails), rails);
                    assertRailsPowered(helper, rails, 0, 9, true);
                    assertRailsPowered(helper, rails, 9, rails.length, false);
                }
        );
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_26", maxTicks = 100)
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
        compareMirroredAndOptimizedPower(
                helper,
                () -> helper.setBlock(mirrorCopy(rails[0].north()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(rails[0].north(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailPower(helper, mirrorCopy(rails), rails);
                    assertRailsPowered(helper, rails, true);
                }
        );
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_27", maxTicks = 100)
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
        compareMirroredAndOptimizedPower(
                helper,
                () -> helper.setBlock(mirrorCopy(rails[0].north()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(rails[0].north(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailPower(helper, mirrorCopy(rails), rails);
                    assertRailsPowered(helper, rails, true);
                }
        );
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_28", maxTicks = 100)
    public void continuousDescendingThenContinuousAscendingRailsPowerWithinLimit(GameTestHelper helper) {
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
        compareMirroredAndOptimizedPower(
                helper,
                () -> helper.setBlock(mirrorCopy(rails[0].north()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(rails[0].north(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailPower(helper, mirrorCopy(rails), rails);
                    assertRailsPowered(helper, rails, 0, 9, true);
                    assertRailsPowered(helper, rails, 9, rails.length, false);
                }
        );
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_29", maxTicks = 100)
    public void northSouthContinuousDescendingThenContinuousAscendingRailsPowerWithinLimit(GameTestHelper helper) {
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
        compareMirroredAndOptimizedPower(
                helper,
                () -> helper.setBlock(mirrorCopy(rails[0].west()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(rails[0].west(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailPower(helper, mirrorCopy(rails), rails);
                    assertRailsPowered(helper, rails, 0, 9, true);
                    assertRailsPowered(helper, rails, 9, rails.length, false);
                }
        );
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_30", maxTicks = 100)
    public void flatThenContinuousDescendingThenContinuousAscendingThenFlatRailsPowerWithinLimit(GameTestHelper helper) {
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
        compareMirroredAndOptimizedPower(
                helper,
                () -> helper.setBlock(mirrorCopy(rails[0].north()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(rails[0].north(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailPower(helper, mirrorCopy(rails), rails);
                    assertRailsPowered(helper, rails, 0, 9, true);
                    assertRailsPowered(helper, rails, 9, rails.length, false);
                }
        );
    }

    private static BlockPos[] sawtoothDescendingRails(int z) {
        return new BlockPos[]{
                new BlockPos(1, RAIL_Y + 1, z),
                new BlockPos(2, RAIL_Y, z),
                new BlockPos(3, RAIL_Y + 1, z),
                new BlockPos(4, RAIL_Y, z),
                new BlockPos(5, RAIL_Y + 1, z),
                new BlockPos(6, RAIL_Y, z)
        };
    }

    private static RailShape[] sawtoothDescendingRailShapes() {
        return new RailShape[]{
                RailShape.EAST_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.EAST_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.EAST_WEST,
                RailShape.ASCENDING_WEST
        };
    }

    @SuppressWarnings("null")
private static void compareMirroredPathPower(GameTestHelper helper, BlockPos[] rails, RailShape[] shapes,
                                                 BlockPos sourcePos, Runnable assertions) {
        placeRailPathPair(helper, rails, shapes);
        compareMirroredAndOptimizedPower(
                helper,
                () -> helper.setBlock(mirrorCopy(sourcePos), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(sourcePos, Blocks.REDSTONE_BLOCK),
                assertions
        );
    }
}
