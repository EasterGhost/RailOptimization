package RailOptimization.gametest;

import RailOptimization.RailLogic;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationUpdateBehaviorGameTest extends RailOptimizationGameTestSupport {
    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_68", maxTicks = 180, padding = 40)
    public void neighborCountersReceiveNorthSouthRailDepowerUpdatePositions(GameTestHelper helper) {
        BlockPos start = new BlockPos(3, RAIL_Y, 2);
        BlockPos source = start.west();
        BlockPos[] counters = new BlockPos[]{
                start.relative(Direction.SOUTH).west(),
                start.relative(Direction.SOUTH, 2).west(),
                start.relative(Direction.SOUTH, 3).west(),
                start.east(),
                start.relative(Direction.SOUTH).east(),
                start.relative(Direction.SOUTH, 2).east(),
                start.relative(Direction.SOUTH, 3).east(),
                start.above(),
                start.relative(Direction.SOUTH).above(),
                start.relative(Direction.SOUTH, 2).above(),
                start.relative(Direction.SOUTH, 3).above(),
                start.relative(Direction.SOUTH, 2).below().west(),
                start.relative(Direction.SOUTH, 2).below().east(),
                start.relative(Direction.SOUTH, 2).below().below(),
                source.west(),
                source.above(),
                source.north(),
                start.north(),
                start.north().below(),
                start.relative(Direction.SOUTH, 4),
                start.relative(Direction.SOUTH, 4).below()
        };

        placeRailLinePair(helper, start, Direction.SOUTH, 4, RailShape.NORTH_SOUTH);
        placeNeighborCounters(helper, mirrorCopy(counters));
        placeNeighborCounters(helper, counters);

        helper.startSequence()
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(source, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    resetNeighborCounters(helper, mirrorCopy(counters));
                    resetNeighborCounters(helper, counters);
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(source), Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(source, Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.SOUTH, 4);
                    assertRailLinePowered(helper, start, Direction.SOUTH, 4, false);
                    assertNeighborCountersCoveredAndNotExceedingVanilla(helper, counters, "after depower");
                })
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_69", maxTicks = 200, padding = 40)
    public void neighborCountersReceiveAscendingRailUpdatePositionsOnPowerAndDepower(GameTestHelper helper) {
        BlockPos ramp = new BlockPos(3, RAIL_Y, 3);
        BlockPos flatRail = ramp.east().above();
        BlockPos source = ramp.west();
        BlockPos[] counters = new BlockPos[]{
                ramp.north(),
                ramp.south(),
                ramp.above(),
                ramp.below().north(),
                ramp.below().south(),
                ramp.below().below(),
                flatRail.north(),
                flatRail.south(),
                flatRail.above(),
                flatRail.east(),
                flatRail.below().north(),
                source.north(),
                source.above(),
                source.below(),
                source.west()
        };

        placeAscendingEastRailPair(helper, ramp);
        placeNeighborCounters(helper, mirrorCopy(counters));
        placeNeighborCounters(helper, counters);

        helper.startSequence()
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(source, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailPower(helper, mirrorCopy(ramp), ramp);
                    assertMatchingRailPower(helper, mirrorCopy(flatRail), flatRail);
                    helper.assertBlockProperty(ramp, PoweredRailBlock.POWERED, true);
                    helper.assertBlockProperty(flatRail, PoweredRailBlock.POWERED, true);
                    assertNeighborCountersCoveredAndNotExceedingVanilla(helper, counters, "after power");
                    resetNeighborCounters(helper, mirrorCopy(counters));
                    resetNeighborCounters(helper, counters);
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(source), Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(source, Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailPower(helper, mirrorCopy(ramp), ramp);
                    assertMatchingRailPower(helper, mirrorCopy(flatRail), flatRail);
                    helper.assertBlockProperty(ramp, PoweredRailBlock.POWERED, false);
                    helper.assertBlockProperty(flatRail, PoweredRailBlock.POWERED, false);
                    assertNeighborCountersCoveredAndNotExceedingVanilla(helper, counters, "after depower");
                })
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_70", maxTicks = 240, padding = 40)
    public void neighborCountersReceiveBoundaryUpdatesWhenOneOfTwoSourcesRemoved(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 3);
        int length = 10;
        BlockPos end = start.relative(Direction.EAST, length - 1);
        BlockPos source = start.north();
        BlockPos[] counters = new BlockPos[]{
                start.west(),
                start.south(),
                start.above(),
                start.below().west(),
                start.below().south(),
                start.below().below(),
                source.west(),
                source.north(),
                source.above(),
                source.below()
        };

        placeRailLinePair(helper, start, Direction.EAST, length, RailShape.EAST_WEST);
        placeNeighborCounters(helper, mirrorCopy(counters));
        placeNeighborCounters(helper, counters);

        helper.startSequence()
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(start.north()), Blocks.REDSTONE_BLOCK);
                    helper.setBlock(mirrorCopy(end.north()), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(start.north(), Blocks.REDSTONE_BLOCK);
                    helper.setBlock(end.north(), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, length);
                    assertRailLinePowered(helper, start, Direction.EAST, length, true);
                    resetNeighborCounters(helper, mirrorCopy(counters));
                    resetNeighborCounters(helper, counters);
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(start.north()), Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(start.north(), Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, length);
                    helper.assertBlockProperty(start, PoweredRailBlock.POWERED, false);
                    assertRailLinePowered(helper, start.relative(Direction.EAST), Direction.EAST, length - 1, true);
                    assertNeighborCountersCoveredAndNotExceedingVanilla(helper, counters, "after one source removal");
                })
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_71", maxTicks = 160, padding = 40)
    public void straightRailDepowerUpdateOrderMatchesVanilla(GameTestHelper helper) {
        int length = 5;
        BlockPos start = new BlockPos(1, RAIL_Y, 3);
        BlockPos[] rails = eastWestRails(start, length);
        BlockPos[] probes = new BlockPos[]{
                rails[0].south(),
                rails[2].south(),
                rails[4].south(),
                rails[4].east()
        };

        placeRailLinePair(helper, start, Direction.EAST, length, RailShape.EAST_WEST);
        placeOrderRecorderPair(helper, probes, rails);

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(mirrorCopy(rails[0].north()), Blocks.REDSTONE_BLOCK);
                    helper.setBlock(rails[0].north(), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    resetOrderRecorders(helper, mirrorCopy(probes));
                    resetOrderRecorders(helper, probes);
                    helper.setBlock(mirrorCopy(rails[0].north()), Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> helper.setBlock(rails[0].north(), Blocks.AIR))
                .thenIdle(4)
                .thenExecute(() -> assertOrderRecordsMatch(helper, mirrorCopy(probes), probes))
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_72", maxTicks = 220, padding = 40)
    public void fullLaneCascadingNeighborCountsMatchVanillaOnPowerAndDepower(GameTestHelper helper) {
        int length = 5;
        BlockPos start = new BlockPos(1, RAIL_Y, 3);
        BlockPos source = start.north();
        BlockPos[] rails = eastWestRails(start, length);
        BlockPos[] cascadeSpots = new BlockPos[]{
                rails[0].south(),
                rails[2].south(),
                rails[4].south(),
                rails[4].east()
        };
        BlockPos[] counters = new BlockPos[]{
                rails[1].south(),
                rails[3].south(),
                rails[0].above(),
                rails[1].above(),
                rails[2].above(),
                rails[3].above(),
                rails[4].above(),
                rails[4].east().above(),
                rails[0].west()
        };

        placeRailLinePair(helper, start, Direction.EAST, length, RailShape.EAST_WEST);
        for (BlockPos spot : cascadeSpots) {
            placeCascadingNeighborCounter(helper, mirrorCopy(spot));
            placeCascadingNeighborCounter(helper, spot);
        }
        placeNeighborCounters(helper, mirrorCopy(counters));
        placeNeighborCounters(helper, counters);

        helper.startSequence()
                .thenExecute(() -> {
                    resetEndpointCascadeCounters(helper, cascadeSpots, counters);
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(source, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, length);
                    assertCascadeCountsMatch(helper, cascadeSpots, counters, "after power");
                    resetEndpointCascadeCounters(helper, cascadeSpots, counters);
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(source), Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(source, Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, length);
                    assertCascadeCountsMatch(helper, cascadeSpots, counters, "after depower");
                })
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_73", maxTicks = 220, padding = 40)
    public void observerFacingIndirectRailActivatesOnLanePowerStateChanges(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 3);
        int length = 5;
        BlockPos watchedRail = start.relative(Direction.EAST, 4);
        BlockPos source = start.north();
        BlockPos observer = watchedRail.south();

        placeRailLinePair(helper, start, Direction.EAST, length, RailShape.EAST_WEST);
        placeObserverWatchingRail(helper, observer, Direction.NORTH);
        placeObserverWatchingRail(helper, mirrorCopy(observer), Direction.NORTH);

        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> {
                    assertObserverPowered(helper, observer, false);
                    assertObserverPowered(helper, mirrorCopy(observer), false);
                })
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertBlockProperty(mirrorCopy(watchedRail), PoweredRailBlock.POWERED, true);
                    assertObserverPowered(helper, mirrorCopy(observer), true);
                })
                .thenIdle(4)
                .thenExecute(() -> assertObserverPowered(helper, mirrorCopy(observer), false))
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(source, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertBlockProperty(watchedRail, PoweredRailBlock.POWERED, true);
                    assertObserverPowered(helper, observer, true);
                })
                .thenIdle(4)
                .thenExecute(() -> assertObserverPowered(helper, observer, false))
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(source), Blocks.AIR);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertBlockProperty(mirrorCopy(watchedRail), PoweredRailBlock.POWERED, false);
                    assertObserverPowered(helper, mirrorCopy(observer), true);
                })
                .thenIdle(4)
                .thenExecute(() -> assertObserverPowered(helper, mirrorCopy(observer), false))
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(source, Blocks.AIR);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertBlockProperty(watchedRail, PoweredRailBlock.POWERED, false);
                    assertObserverPowered(helper, observer, true);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    assertObserverPowered(helper, observer, false);
                })
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_74", maxTicks = 120, padding = 40)
    public void neighborCountersReceiveMidLaneSourceUpdatePositions(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 3);
        int length = 7;
        BlockPos sourceRail = start.relative(Direction.EAST, 3);
        BlockPos[] counters = new BlockPos[]{
                start.south(),
                start.west(),
                start.above(),
                start.relative(Direction.EAST).south(),
                start.relative(Direction.EAST).above(),
                start.relative(Direction.EAST, 2).south(),
                start.relative(Direction.EAST, 2).above(),
                start.relative(Direction.EAST, 2).north(),
                sourceRail.south(),
                sourceRail.above(),
                start.relative(Direction.EAST, 4).south(),
                start.relative(Direction.EAST, 4).above(),
                start.relative(Direction.EAST, 4).north(),
                start.relative(Direction.EAST, 5).south(),
                start.relative(Direction.EAST, 5).above(),
                start.relative(Direction.EAST, 6).south(),
                start.relative(Direction.EAST, 6).above(),
                sourceRail.north().north()
        };

        placeRailLinePair(helper, start, Direction.EAST, length, RailShape.EAST_WEST);
        placeNeighborCounters(helper, mirrorCopy(counters));
        placeNeighborCounters(helper, counters);

        compareMirroredAndOptimizedPower(
                helper,
                () -> helper.setBlock(mirrorCopy(sourceRail.north()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(sourceRail.north(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, length);
                    assertRailLinePowered(helper, start, Direction.EAST, length, true);
                    assertNeighborCountersCoveredAndNotExceedingVanilla(helper, counters, "after power");
                }
        );
    }

    private static BlockPos[] eastWestRails(BlockPos start, int length) {
        BlockPos[] rails = new BlockPos[length];
        for (int railIndex = 0; railIndex < length; railIndex++) {
            rails[railIndex] = start.relative(Direction.EAST, railIndex);
        }
        return rails;
    }

    private static void placeOrderRecorderPair(GameTestHelper helper, BlockPos[] probes, BlockPos[] watchedRails) {
        BlockPos[] mirrorProbes = mirrorCopy(probes);
        BlockPos[] mirrorRails = mirrorCopy(watchedRails);
        for (int probeIndex = 0; probeIndex < probes.length; probeIndex++) {
            placeOrderRecorder(helper, probes[probeIndex], watchedRails);
            placeOrderRecorder(helper, mirrorProbes[probeIndex], mirrorRails);
        }
    }

    private static void assertOrderRecordsMatch(GameTestHelper helper, BlockPos[] mirrorProbes, BlockPos[] probes) {
        RailOptimizationGameTestMod.OrderProbeSnapshot[] mirrorRecords = snapshots(helper, mirrorProbes);
        RailOptimizationGameTestMod.OrderProbeSnapshot[] optimizedRecords = snapshots(helper, probes);

        for (int probeIndex = 0; probeIndex < probes.length; probeIndex++) {
            int mirrorOrder = mirrorRecords[probeIndex].order();
            int optimizedOrder = optimizedRecords[probeIndex].order();
            int mirrorSnapshot = mirrorRecords[probeIndex].snapshot();
            int optimizedSnapshot = optimizedRecords[probeIndex].snapshot();

            helper.assertTrue(mirrorOrder > 0, Component.literal("mirror probe " + probeIndex + " was not updated"));
            helper.assertTrue(optimizedOrder > 0,
                    Component.literal("optimized probe " + probeIndex + " was not updated"));
            helper.assertTrue(mirrorSnapshot == optimizedSnapshot,
                    Component.literal("probe " + probeIndex + " snapshot mismatch: vanilla="
                            + mirrorSnapshot + ", optimized=" + optimizedSnapshot));
        }

        for (int firstProbe = 0; firstProbe < probes.length; firstProbe++) {
            for (int secondProbe = firstProbe + 1; secondProbe < probes.length; secondProbe++) {
                int mirrorOrder = Integer.compare(
                        mirrorRecords[firstProbe].order(), mirrorRecords[secondProbe].order());
                int optimizedOrder = Integer.compare(
                        optimizedRecords[firstProbe].order(), optimizedRecords[secondProbe].order());
                helper.assertTrue(mirrorOrder == optimizedOrder,
                        Component.literal("relative update order mismatch for probes "
                                + firstProbe + " and " + secondProbe
                                + ", vanilla=" + describeRecords(mirrorRecords)
                                + ", optimized=" + describeRecords(optimizedRecords)));
            }
        }
    }

    private static String describeRecords(RailOptimizationGameTestMod.OrderProbeSnapshot[] records) {
        StringBuilder builder = new StringBuilder("[");
        for (int probeIndex = 0; probeIndex < records.length; probeIndex++) {
            if (probeIndex > 0) {
                builder.append(", ");
            }
            builder.append(probeIndex)
                    .append(":order=")
                    .append(records[probeIndex].order())
                    .append(",snapshot=")
                    .append(records[probeIndex].snapshot());
        }
        return builder.append(']').toString();
    }

    private static RailOptimizationGameTestMod.OrderProbeSnapshot[] snapshots(
            GameTestHelper helper, BlockPos[] probes) {
        RailOptimizationGameTestMod.OrderProbeSnapshot[] snapshots =
                new RailOptimizationGameTestMod.OrderProbeSnapshot[probes.length];
        for (int probeIndex = 0; probeIndex < probes.length; probeIndex++) {
            snapshots[probeIndex] = orderProbeSnapshot(helper, probes[probeIndex]);
        }
        return snapshots;
    }

    private static void resetEndpointCascadeCounters(
            GameTestHelper helper, BlockPos[] cascadeSpots, BlockPos[] counters) {
        resetNeighborCounters(helper, mirrorCopy(cascadeSpots));
        resetNeighborCounters(helper, cascadeSpots);
        resetNeighborCounters(helper, mirrorCopy(counters));
        resetNeighborCounters(helper, counters);
    }

    private static void assertCascadeCountsMatch(GameTestHelper helper, BlockPos[] cascadeSpots,
            BlockPos[] counters, String stage) {
        assertNeighborCountersCoveredAndNotExceedingVanilla(helper, cascadeSpots, stage + " cascade spots");
        assertNeighborCountersCoveredAndNotExceedingVanilla(helper, counters, stage + " plain counters");
    }

    @SuppressWarnings("null")
    private static void assertNeighborCountersCoveredAndNotExceedingVanilla(
            GameTestHelper helper, BlockPos[] counters, String stage) {
        for (BlockPos pos : counters) {
            int vanillaCount = helper.getBlockState(mirrorCopy(pos))
                    .getValue(RailOptimizationGameTestMod.NeighborCounterBlock.COUNT);
            int optimizedCount = helper.getBlockState(pos)
                    .getValue(RailOptimizationGameTestMod.NeighborCounterBlock.COUNT);
            helper.assertTrue(vanillaCount > 0,
                    Component.literal(stage + ": vanilla counter at " + pos + " was not updated"));
            helper.assertTrue(optimizedCount > 0,
                    Component.literal(stage + ": optimized counter at " + pos + " was not updated"));
            helper.assertTrue(optimizedCount <= vanillaCount,
                    Component.literal(stage + ": optimized notified more than vanilla at " + pos
                            + ", vanilla=" + vanillaCount + ", optimized=" + optimizedCount));
        }
    }
}
