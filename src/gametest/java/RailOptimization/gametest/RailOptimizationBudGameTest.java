package RailOptimization.gametest;

import RailOptimization.RailLogic;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationBudGameTest extends RailOptimizationGameTestSupport {
    @GameTest(environment = "railoptimization-gametest:serial_31", maxTicks = 120, padding = 40)
    public void flatRailPowersOnlyAfterBudUpdate(GameTestHelper helper) {
        BlockPos rail = new BlockPos(3, RAIL_Y, 3);
        BlockPos source = rail.west();
        BlockPos trigger = rail.above();

        placeRail(helper, rail, RailShape.NORTH_SOUTH);
        placeRail(helper, mirrorCopy(rail), RailShape.NORTH_SOUTH);

        helper.startSequence()
                .thenExecute(() -> {
                    setBlockWithoutUpdates(helper, mirrorCopy(source), Blocks.REDSTONE_BLOCK);
                    setBlockWithoutUpdates(helper, source, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailPower(helper, mirrorCopy(rail), rail);
                    helper.assertBlockProperty(rail, PoweredRailBlock.POWERED, false);
                })
                .thenExecute(() -> triggerMirrorAndOptimizedUpdate(helper, trigger))
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailPower(helper, mirrorCopy(rail), rail);
                    helper.assertBlockProperty(rail, PoweredRailBlock.POWERED, true);
                })
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_32", maxTicks = 160, padding = 40)
    public void flatRailDepowersOnlyAfterBudUpdate(GameTestHelper helper) {
        BlockPos rail = new BlockPos(3, RAIL_Y, 3);
        BlockPos source = rail.west();
        BlockPos trigger = rail.above();

        placeRail(helper, rail, RailShape.NORTH_SOUTH);
        placeRail(helper, mirrorCopy(rail), RailShape.NORTH_SOUTH);

        helper.startSequence()
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(source, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailPower(helper, mirrorCopy(rail), rail);
                    helper.assertBlockProperty(rail, PoweredRailBlock.POWERED, true);
                })
                .thenExecute(() -> {
                    setBlockWithoutUpdates(helper, mirrorCopy(source), Blocks.AIR);
                    setBlockWithoutUpdates(helper, source, Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailPower(helper, mirrorCopy(rail), rail);
                    helper.assertBlockProperty(rail, PoweredRailBlock.POWERED, true);
                })
                .thenExecute(() -> triggerMirrorAndOptimizedUpdate(helper, trigger))
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailPower(helper, mirrorCopy(rail), rail);
                    helper.assertBlockProperty(rail, PoweredRailBlock.POWERED, false);
                })
                .thenSucceed();
    }

    @GameTest(environment = "railoptimization-gametest:serial_33", maxTicks = 120, padding = 40)
    public void railLinePowersOnlyAfterBudUpdate(GameTestHelper helper) {
        BlockPos start = new BlockPos(2, RAIL_Y, 3);
        BlockPos source = start.north();
        BlockPos trigger = start.above();
        int length = 6;

        placeRailLinePair(helper, start, Direction.EAST, length, RailShape.EAST_WEST);

        helper.startSequence()
                .thenExecute(() -> {
                    setBlockWithoutUpdates(helper, mirrorCopy(source), Blocks.REDSTONE_BLOCK);
                    setBlockWithoutUpdates(helper, source, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, length);
                    assertRailLinePowered(helper, start, Direction.EAST, length, false);
                })
                .thenExecute(() -> triggerMirrorAndOptimizedUpdate(helper, trigger))
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, length);
                    assertRailLinePowered(helper, start, Direction.EAST, length, true);
                })
                .thenSucceed();
    }

    @GameTest(environment = "railoptimization-gametest:serial_34", maxTicks = 120, padding = 40)
    public void ascendingRailPowersOnlyAfterBudUpdate(GameTestHelper helper) {
        BlockPos ramp = new BlockPos(2, RAIL_Y, 2);
        BlockPos upperRail = ramp.east().above();
        BlockPos[] rails = new BlockPos[]{ramp, upperRail};
        BlockPos source = ramp.west();
        BlockPos trigger = ramp.above();

        placeAscendingEastRailPair(helper, ramp);

        helper.startSequence()
                .thenExecute(() -> {
                    setBlockWithoutUpdates(helper, mirrorCopy(source), Blocks.REDSTONE_BLOCK);
                    setBlockWithoutUpdates(helper, source, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailPower(helper, mirrorCopy(rails), rails);
                    assertRailsPowered(helper, rails, false);
                })
                .thenExecute(() -> triggerMirrorAndOptimizedUpdate(helper, trigger))
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailPower(helper, mirrorCopy(rails), rails);
                    helper.assertBlockProperty(ramp, PoweredRailBlock.POWERED, true);
                })
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_35", maxTicks = 160, padding = 40)
    public void secondSourceInsidePoweredFlatLineDoesNotWakeFarRails(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 3);
        BlockPos firstSource = start.north();
        BlockPos secondSource = start.relative(Direction.EAST, 4).north();
        int length = 11;

        placeRailLinePair(helper, start, Direction.EAST, length, RailShape.EAST_WEST);

        helper.startSequence()
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(firstSource), Blocks.REDSTONE_BLOCK);
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(firstSource, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> assertFlatLineReachedPowerLimit(helper, start, length))
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(secondSource), Blocks.REDSTONE_BLOCK);
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(secondSource, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> assertFlatLineReachedPowerLimit(helper, start, length))
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_36", maxTicks = 160, padding = 40)
    public void secondSourceInsidePoweredSlopedLineDoesNotWakeFarRails(GameTestHelper helper) {
        BlockPos[] rails = new BlockPos[]{
                new BlockPos(1, RAIL_Y + 4, 3),
                new BlockPos(2, RAIL_Y + 3, 3),
                new BlockPos(3, RAIL_Y + 2, 3),
                new BlockPos(4, RAIL_Y + 1, 3),
                new BlockPos(5, RAIL_Y, 3),
                new BlockPos(6, RAIL_Y, 3),
                new BlockPos(7, RAIL_Y + 1, 3),
                new BlockPos(8, RAIL_Y + 2, 3),
                new BlockPos(9, RAIL_Y + 3, 3),
                new BlockPos(10, RAIL_Y + 4, 3),
                new BlockPos(11, RAIL_Y + 4, 3)
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
                RailShape.EAST_WEST,
                RailShape.EAST_WEST
        };
        BlockPos firstSource = rails[0].north();
        BlockPos secondSource = rails[4].north();

        placeRailPathPair(helper, rails, shapes);

        helper.startSequence()
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(firstSource), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(firstSource, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> assertSlopedLineReachedPowerLimit(helper, rails, "after first source"))
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(secondSource), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(secondSource, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> assertSlopedLineReachedPowerLimit(helper, rails, "after second source"))
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_37", maxTicks = 160, padding = 40)
    public void railsPlacedPastSecondSourceOnFlatLineStayUnpowered(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 3);
        BlockPos firstSource = start.north();
        BlockPos secondSource = start.relative(Direction.EAST, 8).north();
        int initiallyPlacedLength = 11;
        int finalLength = 13;

        placeRailLinePair(helper, start, Direction.EAST, initiallyPlacedLength, RailShape.EAST_WEST);

        helper.startSequence()
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(firstSource), Blocks.REDSTONE_BLOCK);
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(firstSource, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> assertFlatLineReachedPowerLimit(helper, start, initiallyPlacedLength))
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(secondSource), Blocks.REDSTONE_BLOCK);
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(secondSource, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> placeRailLinePastSecondSource(
                        helper,
                        start.relative(Direction.EAST, initiallyPlacedLength),
                        finalLength - initiallyPlacedLength,
                        RailShape.EAST_WEST
                ))
                .thenIdle(4)
                .thenExecute(() -> assertFlatLineReachedPowerLimit(helper, start, finalLength))
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_38", maxTicks = 160, padding = 40)
    public void railsPlacedPastSecondSourceOnSlopedLineStayUnpowered(GameTestHelper helper) {
        BlockPos[] rails = new BlockPos[]{
                new BlockPos(1, RAIL_Y + 4, 3),
                new BlockPos(2, RAIL_Y + 3, 3),
                new BlockPos(3, RAIL_Y + 2, 3),
                new BlockPos(4, RAIL_Y + 1, 3),
                new BlockPos(5, RAIL_Y, 3),
                new BlockPos(6, RAIL_Y, 3),
                new BlockPos(7, RAIL_Y + 1, 3),
                new BlockPos(8, RAIL_Y + 2, 3),
                new BlockPos(9, RAIL_Y + 3, 3),
                new BlockPos(10, RAIL_Y + 4, 3),
                new BlockPos(11, RAIL_Y + 4, 3),
                new BlockPos(12, RAIL_Y + 4, 3),
                new BlockPos(13, RAIL_Y + 4, 3)
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
                RailShape.EAST_WEST,
                RailShape.EAST_WEST,
                RailShape.EAST_WEST,
                RailShape.EAST_WEST
        };
        BlockPos firstSource = rails[0].north();
        BlockPos secondSource = rails[8].north();
        int initiallyPlacedLength = 11;

        placeRailPathPair(helper, rails, shapes, initiallyPlacedLength);

        helper.startSequence()
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(firstSource), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(firstSource, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> assertSlopedLineReachedPowerLimit(
                        helper, rails, initiallyPlacedLength, "after first source"))
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(secondSource), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(secondSource, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> placeRailPathPastSecondSource(helper, rails, shapes, initiallyPlacedLength))
                .thenIdle(4)
                .thenExecute(() -> assertSlopedLineReachedPowerLimit(helper, rails, "after placing more rails"))
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_39", maxTicks = 160, padding = 40)
    public void secondSourceInsidePoweredAllAscendingLineDoesNotWakeFarRails(GameTestHelper helper) {
        BlockPos[] rails = continuousAscendingEastRails(11);
        RailShape[] shapes = repeatedRailShapes(RailShape.ASCENDING_EAST, rails.length);
        BlockPos firstSource = rails[0].north();
        BlockPos secondSource = rails[4].north();

        placeRailPathPair(helper, rails, shapes);

        helper.startSequence()
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(firstSource), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(firstSource, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> assertSlopedLineReachedPowerLimit(helper, rails, "after first source"))
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(secondSource), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(secondSource, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> assertSlopedLineReachedPowerLimit(helper, rails, "after second source"))
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_40", maxTicks = 160, padding = 40)
    public void railsPlacedPastSecondSourceOnAllAscendingLineMatchesVanilla(GameTestHelper helper) {
        BlockPos[] rails = continuousAscendingEastRails(13);
        RailShape[] shapes = repeatedRailShapes(RailShape.ASCENDING_EAST, rails.length);
        BlockPos firstSource = rails[0].north();
        BlockPos secondSource = rails[8].north();
        int initiallyPlacedLength = 11;

        placeRailPathPair(helper, rails, shapes, initiallyPlacedLength);

        helper.startSequence()
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(firstSource), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(firstSource, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> assertSlopedLineReachedPowerLimit(
                        helper, rails, initiallyPlacedLength, "after first source"))
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(secondSource), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(secondSource, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> placeRailPathPastSecondSource(helper, rails, shapes, initiallyPlacedLength))
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailPathPower(helper, rails, rails.length);
                    assertRailsPowered(helper, rails, 0, 9, true);
                    helper.assertBlockProperty(rails[9], PoweredRailBlock.POWERED, true);
                })
                .thenSucceed();
    }

    private static void placeRailPathPair(GameTestHelper helper, BlockPos[] rails, RailShape[] shapes, int length) {
        for (int railIndex = 0; railIndex < length; railIndex++) {
            placeRail(helper, rails[railIndex], shapes[railIndex]);
            placeRail(helper, mirrorCopy(rails[railIndex]), shapes[railIndex]);
        }
    }

    private static void placeRailLinePastSecondSource(GameTestHelper helper, BlockPos start, int length,
                                                     RailShape shape) {
        for (int railIndex = 0; railIndex < length; railIndex++) {
            placeRailPastSecondSource(helper, start.relative(Direction.EAST, railIndex), shape);
        }
    }

    private static void placeRailPathPastSecondSource(GameTestHelper helper, BlockPos[] rails,
                                                     RailShape[] shapes, int startIndex) {
        for (int railIndex = startIndex; railIndex < rails.length; railIndex++) {
            placeRailPastSecondSource(helper, rails[railIndex], shapes[railIndex]);
        }
    }

    private static void placeRailPastSecondSource(GameTestHelper helper, BlockPos rail, RailShape shape) {
        RailLogic.setOptimizationEnabled(false);
        placeSingleRailPastSecondSource(helper, mirrorCopy(rail), shape);

        RailLogic.setOptimizationEnabled(true);
        placeSingleRailPastSecondSource(helper, rail, shape);
    }

    @SuppressWarnings("null")
    private static void placeSingleRailPastSecondSource(GameTestHelper helper, BlockPos rail, RailShape shape) {
        markVanillaForMirrorRail(helper, rail);

        if (shape == RailShape.EAST_WEST) {
            helper.setBlock(rail.below(), Blocks.STONE);
            helper.placeBlock(rail, Blocks.POWERED_RAIL, Direction.EAST);
            return;
        }

        placeAscendingRailSupportWithoutUpdates(helper, rail, shape);
        helper.setBlock(rail, Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, shape));
    }

    private static void placeAscendingRailSupportWithoutUpdates(GameTestHelper helper, BlockPos rail, RailShape shape) {
        switch (shape) {
            case ASCENDING_EAST -> setBlockWithoutUpdates(helper, rail.east(), Blocks.STONE);
            case ASCENDING_WEST -> setBlockWithoutUpdates(helper, rail.west(), Blocks.STONE);
            case ASCENDING_NORTH -> setBlockWithoutUpdates(helper, rail.north(), Blocks.STONE);
            case ASCENDING_SOUTH -> setBlockWithoutUpdates(helper, rail.south(), Blocks.STONE);
            default -> {
            }
        }
    }

    private static BlockPos[] continuousAscendingEastRails(int length) {
        BlockPos[] rails = new BlockPos[length];
        for (int railIndex = 0; railIndex < length; railIndex++) {
            rails[railIndex] = new BlockPos(1 + railIndex, RAIL_Y + railIndex, 3);
        }
        return rails;
    }

    private static RailShape[] repeatedRailShapes(RailShape shape, int length) {
        RailShape[] shapes = new RailShape[length];
        for (int railIndex = 0; railIndex < length; railIndex++) {
            shapes[railIndex] = shape;
        }
        return shapes;
    }

    private static void assertMatchingRailPathPower(GameTestHelper helper, BlockPos[] rails, int length) {
        for (int railIndex = 0; railIndex < length; railIndex++) {
            assertMatchingRailPower(helper, mirrorCopy(rails[railIndex]), rails[railIndex]);
        }
    }

    private static void assertFlatLineReachedPowerLimit(GameTestHelper helper, BlockPos start, int length) {
        assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, length);
        assertRailLinePowered(helper, start, Direction.EAST, 9, true);
        for (int railIndex = 9; railIndex < length; railIndex++) {
            helper.assertBlockProperty(start.relative(Direction.EAST, railIndex), PoweredRailBlock.POWERED, false);
        }
    }

    private static void assertSlopedLineReachedPowerLimit(GameTestHelper helper, BlockPos[] rails, String stage) {
        assertSlopedLineReachedPowerLimit(helper, rails, rails.length, stage);
    }

    private static void assertSlopedLineReachedPowerLimit(GameTestHelper helper, BlockPos[] rails, int length,
                                                          String stage) {
        assertMatchingRailPathPower(helper, rails, length);
        assertRailsPowered(helper, rails, 0, 9, true);
        assertRailsPowered(helper, rails, 9, length, false, stage);
    }

    @SuppressWarnings("null")
    private static void assertRailsPowered(GameTestHelper helper, BlockPos[] rails, int startIndex, int endIndex,
                                           boolean powered, String stage) {
        for (int railIndex = startIndex; railIndex < endIndex; railIndex++) {
            helper.assertBlockProperty(
                    rails[railIndex],
                    PoweredRailBlock.POWERED,
                    value -> value == powered,
                    Component.literal(stage + ": rail " + railIndex + " should be powered=" + powered)
            );
        }
    }

    @SuppressWarnings("null")
    private static void triggerMirrorAndOptimizedUpdate(GameTestHelper helper, BlockPos trigger) {
        RailLogic.setOptimizationEnabled(false);
        helper.setBlock(mirrorCopy(trigger), Blocks.STONE);
        RailLogic.setOptimizationEnabled(true);
        helper.setBlock(trigger, Blocks.STONE);
    }

    private static void setBlockWithoutUpdates(GameTestHelper helper, BlockPos pos, Block block) {
        setBlockWithoutUpdates(helper, pos, block.defaultBlockState());
    }

    @SuppressWarnings("null")
    private static void setBlockWithoutUpdates(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.getLevel().setBlock(helper.absolutePos(pos), state, Block.UPDATE_NONE);
    }
}
