package RailOptimization.gametest;

import RailOptimization.RailLogic;
import RailOptimization.RailLogicTestAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

abstract class RailOptimizationGameTestSupport {
    static final int RAIL_Y = 2;
    static final int RAIL_X = 2;
    static final int FIRST_RAIL_Z = 1;
    static final int LAST_RAIL_Z = 7;
    static final int DEFAULT_LINE_LENGTH = LAST_RAIL_Z - FIRST_RAIL_Z + 1;
    static final int MIRROR_COPY_Y_OFFSET = 20;
    static final BlockPos NORTH_SOUTH_LINE_START = new BlockPos(RAIL_X, RAIL_Y, FIRST_RAIL_Z);
    static final BlockPos REDSTONE_SOURCE_POS = NORTH_SOUTH_LINE_START.west();

    static void compareMirroredAndOptimizedPower(GameTestHelper helper, Runnable mirrorTrigger,
                                                 Runnable optimizedTrigger, Runnable assertions) {
        helper.startSequence()
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    mirrorTrigger.run();
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

    static BlockPos mirrorCopy(BlockPos pos) {
        return pos.above(MIRROR_COPY_Y_OFFSET);
    }

    static BlockPos[] mirrorCopy(BlockPos[] positions) {
        BlockPos[] copy = new BlockPos[positions.length];
        for (int i = 0; i < positions.length; i++) {
            copy[i] = mirrorCopy(positions[i]);
        }
        return copy;
    }

    static void placeRailLinePair(GameTestHelper helper, BlockPos start, Direction direction,
                                  int length, RailShape shape) {
        placeRailLine(helper, start, direction, length, shape);
        placeRailLine(helper, mirrorCopy(start), direction, length, shape);
    }

    @SuppressWarnings("null")
    static void placeRailLine(GameTestHelper helper, BlockPos start, Direction direction,
                              int length, RailShape shape) {
        for (int step = 0; step < length; step++) {
            placeRail(helper, start.relative(direction, step), shape);
        }
    }

    static void placeRailPathPair(GameTestHelper helper, BlockPos[] rails, RailShape[] shapes) {
        placeRailPath(helper, rails, shapes);
        placeRailPath(helper, mirrorCopy(rails), shapes);
    }

    static void placeRailPath(GameTestHelper helper, BlockPos[] rails, RailShape[] shapes) {
        if (rails.length != shapes.length) {
            throw new IllegalArgumentException("rails and shapes must have the same length");
        }

        for (int i = 0; i < rails.length; i++) {
            placeRail(helper, rails[i], shapes[i]);
        }
    }

    static void placeRail(GameTestHelper helper, BlockPos railPos, RailShape shape) {
        placeRail(helper, railPos, shape, Blocks.POWERED_RAIL);
    }

    @SuppressWarnings("null")
    static void placeRail(GameTestHelper helper, BlockPos railPos, RailShape shape, Block railBlock) {
        markVanillaForMirrorRail(helper, railPos);
        helper.setBlock(railPos.below(), Blocks.STONE);
        placeAscendingRailSupport(helper, railPos, shape);
        helper.setBlock(railPos, railBlock.defaultBlockState().setValue(PoweredRailBlock.SHAPE, shape));
    }

    static void markVanillaForMirrorRail(GameTestHelper helper, BlockPos railPos) {
        if (railPos.getY() >= RAIL_Y + MIRROR_COPY_Y_OFFSET) {
            RailLogicTestAccess.forceVanillaAt(helper.absolutePos(railPos));
        }
    }

    private static void placeAscendingRailSupport(GameTestHelper helper, BlockPos railPos, RailShape shape) {
        switch (shape) {
            case ASCENDING_EAST -> helper.setBlock(railPos.east(), Blocks.STONE);
            case ASCENDING_WEST -> helper.setBlock(railPos.west(), Blocks.STONE);
            case ASCENDING_NORTH -> helper.setBlock(railPos.north(), Blocks.STONE);
            case ASCENDING_SOUTH -> helper.setBlock(railPos.south(), Blocks.STONE);
            default -> {
            }
        }
    }

    @SuppressWarnings("null")
    static void placeObserverWatchingRail(GameTestHelper helper, BlockPos observerPos, Direction facing) {
        helper.setBlock(observerPos, Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, facing));
    }

    @SuppressWarnings("null")
    static void placeNeighborCounters(GameTestHelper helper, BlockPos[] counters) {
        BlockState counter = RailOptimizationGameTestMod.NEIGHBOR_COUNTER.defaultBlockState();
        for (BlockPos counterPos : counters) {
            helper.setBlock(counterPos, counter);
        }
    }

    @SuppressWarnings("null")
    static void placeCascadingNeighborCounter(GameTestHelper helper, BlockPos counterPos) {
        helper.setBlock(counterPos, RailOptimizationGameTestMod.CASCADING_NEIGHBOR_COUNTER.defaultBlockState());
    }

    @SuppressWarnings("null")
    static void placeOrderRecorder(GameTestHelper helper, BlockPos probePos, BlockPos[] watchedRails) {
        helper.setBlock(probePos, RailOptimizationGameTestMod.ORDER_RECORDER.defaultBlockState());
        BlockPos[] absoluteRails = new BlockPos[watchedRails.length];
        for (int railIndex = 0; railIndex < watchedRails.length; railIndex++) {
            absoluteRails[railIndex] = helper.absolutePos(watchedRails[railIndex]);
        }
        RailOptimizationGameTestMod.registerOrderProbe(helper.absolutePos(probePos), absoluteRails);
    }

    @SuppressWarnings("null")
    static void resetOrderRecorders(GameTestHelper helper, BlockPos[] probes) {
        for (BlockPos probe : probes) {
            RailOptimizationGameTestMod.resetOrderProbe(helper.absolutePos(probe));
        }
    }

    @SuppressWarnings("null")
    static RailOptimizationGameTestMod.OrderProbeSnapshot orderProbeSnapshot(
            GameTestHelper helper, BlockPos probe) {
        return RailOptimizationGameTestMod.orderProbeSnapshot(helper.absolutePos(probe));
    }

    static void placeAscendingEastRailPair(GameTestHelper helper, BlockPos ramp) {
        placeAscendingEastRail(helper, ramp);
        placeAscendingEastRail(helper, mirrorCopy(ramp));
    }

    @SuppressWarnings("null")
    static void placeAscendingEastRail(GameTestHelper helper, BlockPos ramp) {
        markVanillaForMirrorRail(helper, ramp);
        markVanillaForMirrorRail(helper, ramp.east().above());
        helper.setBlock(ramp.below(), Blocks.STONE);
        helper.setBlock(ramp.east(), Blocks.STONE);
        helper.setBlock(ramp, Blocks.POWERED_RAIL.defaultBlockState()
                .setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_EAST));
        helper.setBlock(ramp.east().above(), Blocks.POWERED_RAIL.defaultBlockState()
                .setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST));
    }

    @SuppressWarnings("null")
    static void assertRailLinePowered(GameTestHelper helper, BlockPos start, Direction direction,
                                      int length, boolean powered) {
        for (int step = 0; step < length; step++) {
            helper.assertBlockProperty(start.relative(direction, step), PoweredRailBlock.POWERED, powered);
        }
    }

    @SuppressWarnings("null")
    static void assertNeighborCountersUpdated(GameTestHelper helper, BlockPos[] counters) {
        for (BlockPos counterPos : counters) {
            helper.assertBlockProperty(
                    counterPos,
                    RailOptimizationGameTestMod.NeighborCounterBlock.COUNT,
                    count -> count > 0,
                    Component.literal("expected neighbor counter to receive an update")
            );
        }
    }

    static void assertMatchingNeighborCounterCounts(GameTestHelper helper, BlockPos first, BlockPos second,
                                                    String stage) {
        int firstCount = neighborCounterCount(helper, first);
        int secondCount = neighborCounterCount(helper, second);
        helper.assertTrue(firstCount == secondCount,
                Component.literal(stage + ": counter mismatch at " + second
                        + ", vanilla=" + firstCount + ", optimized=" + secondCount));
    }

    static void assertMatchingNeighborCounterCounts(GameTestHelper helper, BlockPos[] positions, String stage) {
        for (BlockPos pos : positions) {
            assertMatchingNeighborCounterCounts(helper, mirrorCopy(pos), pos, stage);
        }
    }

    static void resetNeighborCounters(GameTestHelper helper, BlockPos[] positions) {
        for (BlockPos pos : positions) {
            resetNeighborCounter(helper, pos);
        }
    }

    static void resetNeighborCounter(GameTestHelper helper, BlockPos pos) {
        Block block = helper.getBlockState(pos).getBlock();
        helper.getLevel().setBlock(helper.absolutePos(pos), block.defaultBlockState(), Block.UPDATE_NONE);
    }

    private static int neighborCounterCount(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(RailOptimizationGameTestMod.NeighborCounterBlock.COUNT);
    }

    @SuppressWarnings("null")
    static void assertObserverPowered(GameTestHelper helper, BlockPos observerPos, boolean powered) {
        helper.assertBlockProperty(observerPos, ObserverBlock.POWERED, powered);
    }

    static void assertRailsPowered(GameTestHelper helper, BlockPos[] rails, boolean powered) {
        assertRailsPowered(helper, rails, 0, rails.length, powered);
    }

    @SuppressWarnings("null")
    static void assertRailsPowered(GameTestHelper helper, BlockPos[] rails,
                                   int startIndex, int endIndex, boolean powered) {
        for (int railIndex = startIndex; railIndex < endIndex; railIndex++) {
            helper.assertBlockProperty(rails[railIndex], PoweredRailBlock.POWERED, powered);
        }
    }

    @SuppressWarnings("null")
    static void assertMatchingRailLinePower(GameTestHelper helper, BlockPos firstStart, BlockPos secondStart,
                                            Direction direction, int length) {
        for (int step = 0; step < length; step++) {
            assertMatchingRailPower(
                    helper,
                    firstStart.relative(direction, step),
                    secondStart.relative(direction, step)
            );
        }
    }

    static void assertMatchingRailPower(GameTestHelper helper, BlockPos[] firstRails, BlockPos[] secondRails) {
        if (firstRails.length != secondRails.length) {
            throw new IllegalArgumentException("rail arrays must have the same length");
        }

        for (int i = 0; i < firstRails.length; i++) {
            assertMatchingRailPower(helper, firstRails[i], secondRails[i]);
        }
    }

    @SuppressWarnings("null")
    static void assertMatchingRailPower(GameTestHelper helper, BlockPos firstRail, BlockPos secondRail) {
        boolean firstPowered = helper.getBlockState(firstRail).getValue(PoweredRailBlock.POWERED);
        helper.assertBlockProperty(secondRail, PoweredRailBlock.POWERED, firstPowered);
    }
}
