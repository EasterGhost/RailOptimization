package RailOptimization.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationUpdateOrderGameTest extends RailOptimizationGameTestSupport {
    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_46", maxTicks = 120, padding = 40)
    public void straightRailUpdateOrderMatchesVanilla(GameTestHelper helper) {
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
                    resetOrderRecorders(helper, mirrorCopy(probes));
                    resetOrderRecorders(helper, probes);
                    helper.setBlock(mirrorCopy(rails[0].north()), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> helper.setBlock(rails[0].north(), Blocks.REDSTONE_BLOCK))
                .thenIdle(4)
                .thenExecute(() -> assertOrderRecordsMatch(helper, mirrorCopy(probes), probes))
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_47", maxTicks = 120, padding = 40)
    public void ascendingRailUpdateOrderMatchesVanilla(GameTestHelper helper) {
        BlockPos[] rails = continuousAscendingEastRails(5);
        RailShape[] shapes = new RailShape[]{
                RailShape.ASCENDING_EAST,
                RailShape.ASCENDING_EAST,
                RailShape.ASCENDING_EAST,
                RailShape.ASCENDING_EAST,
                RailShape.ASCENDING_EAST
        };
        BlockPos[] probes = new BlockPos[]{
                rails[0].south(),
                rails[2].south(),
                rails[4].south(),
                rails[2].above()
        };

        placeRailPathPair(helper, rails, shapes);
        placeOrderRecorderPair(helper, probes, rails);

        helper.startSequence()
                .thenExecute(() -> {
                    resetOrderRecorders(helper, mirrorCopy(probes));
                    resetOrderRecorders(helper, probes);
                    helper.setBlock(mirrorCopy(rails[0].north()), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> helper.setBlock(rails[0].north(), Blocks.REDSTONE_BLOCK))
                .thenIdle(4)
                .thenExecute(() -> assertOrderRecordsMatch(helper, mirrorCopy(probes), probes))
                .thenSucceed();
    }

    private static BlockPos[] eastWestRails(BlockPos start, int length) {
        BlockPos[] rails = new BlockPos[length];
        for (int railIndex = 0; railIndex < length; railIndex++) {
            rails[railIndex] = start.relative(Direction.EAST, railIndex);
        }
        return rails;
    }

    private static BlockPos[] continuousAscendingEastRails(int length) {
        BlockPos[] rails = new BlockPos[length];
        for (int railIndex = 0; railIndex < length; railIndex++) {
            rails[railIndex] = new BlockPos(1 + railIndex, RAIL_Y + railIndex, 3);
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
}
