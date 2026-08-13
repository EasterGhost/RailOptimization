package RailOptimization.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationNeighborUpdateGameTest extends RailOptimizationGameTestSupport {
    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_10", maxTicks = 100, padding = 40)
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

        compareMirroredAndOptimizedPower(
                helper,
                () -> helper.setBlock(mirrorCopy(start.west()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(start.west(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.SOUTH, 4);
                    assertNeighborCountersUpdated(helper, counters);
                }
        );
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_11", maxTicks = 100, padding = 40)
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

        compareMirroredAndOptimizedPower(
                helper,
                () -> helper.setBlock(mirrorCopy(start.north()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(start.north(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, 4);
                    assertNeighborCountersUpdated(helper, counters);
                }
        );
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_48", maxTicks = 160, padding = 40)
    public void endpointCascadeUpdatesMatchVanillaOnStraightRail(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 3);
        int length = 5;
        BlockPos source = start.north();
        BlockPos endpoint = start.relative(Direction.EAST, length);
        BlockPos[] cascadeNeighbors = new BlockPos[]{
                endpoint.east(),
                endpoint.above(),
                endpoint.below(),
                endpoint.north(),
                endpoint.south()
        };

        placeRailLinePair(helper, start, Direction.EAST, length, RailShape.EAST_WEST);
        placeCascadingNeighborCounter(helper, mirrorCopy(endpoint));
        placeCascadingNeighborCounter(helper, endpoint);
        placeNeighborCounters(helper, mirrorCopy(cascadeNeighbors));
        placeNeighborCounters(helper, cascadeNeighbors);

        helper.startSequence()
                .thenExecute(() -> {
                    resetEndpointCascadeCounters(helper, endpoint, cascadeNeighbors);
                    RailOptimization.RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailOptimization.RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(source, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> assertEndpointCascadeCountsMatch(helper, endpoint, cascadeNeighbors, "after power"))
                .thenExecute(() -> {
                    RailOptimization.RailLogic.setOptimizationEnabled(false);
                    helper.setBlock(mirrorCopy(source), Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailOptimization.RailLogic.setOptimizationEnabled(true);
                    helper.setBlock(source, Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> assertEndpointCascadeCountsMatch(
                        helper, endpoint, cascadeNeighbors, "after depower"))
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_49", maxTicks = 140, padding = 40)
    public void poweredRailDropsWhenPistonSupportExtends(GameTestHelper helper) {
        BlockPos stone = new BlockPos(2, 2, 2);
        BlockPos lever = stone.above();
        BlockPos piston = stone.east().below();
        BlockPos rail = stone.east();

        helper.setBlock(stone, Blocks.SMOOTH_STONE);
        helper.setBlock(lever, Blocks.LEVER.defaultBlockState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(LeverBlock.POWERED, false));
        helper.setBlock(piston, Blocks.PISTON.defaultBlockState()
                .setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(rail, Blocks.POWERED_RAIL.defaultBlockState()
                .setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST));

        helper.startSequence()
                .thenExecute(() -> helper.pullLever(lever))
                .thenIdle(8)
                .thenExecute(() -> {
                    helper.assertBlockProperty(piston, PistonBaseBlock.EXTENDED, true);
                    helper.assertBlockNotPresent(Blocks.POWERED_RAIL, rail);
                    helper.assertItemEntityPresent(Blocks.POWERED_RAIL.asItem(), rail, 1.5);
                })
                .thenSucceed();
    }

    @GameTest(environment = "railoptimization-gametest:serial_66", maxTicks = 140, padding = 40)
    public void unsupportedRailDropsWhenPoweringAfterTrapdoorOpens(GameTestHelper helper) {
        BlockPos rail = new BlockPos(3, RAIL_Y, 3);
        BlockPos source = rail.north();
        placeTrapdoorSupportedRailPair(helper, rail);

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> assertTrapdoorRailPairPresent(helper, rail, false, false))
                .thenExecute(() -> openTrapdoorPair(helper, rail.below()))
                .thenIdle(2)
                .thenExecute(() -> assertTrapdoorRailPairPresent(helper, rail, false, true))
                .thenExecute(() -> placeSourcePair(helper, source))
                .thenIdle(4)
                .thenExecute(() -> assertTrapdoorRailPairDropped(helper, rail))
                .thenSucceed();
    }

    @GameTest(environment = "railoptimization-gametest:serial_67", maxTicks = 160, padding = 40)
    public void unsupportedRailDropsWhenDepoweringAfterTrapdoorOpens(GameTestHelper helper) {
        BlockPos rail = new BlockPos(3, RAIL_Y, 3);
        BlockPos source = rail.north();
        placeTrapdoorSupportedRailPair(helper, rail);

        helper.startSequence()
                .thenExecute(() -> placeSourcePair(helper, source))
                .thenIdle(4)
                .thenExecute(() -> assertTrapdoorRailPairPresent(helper, rail, true, false))
                .thenExecute(() -> openTrapdoorPair(helper, rail.below()))
                .thenIdle(2)
                .thenExecute(() -> assertTrapdoorRailPairPresent(helper, rail, true, true))
                .thenExecute(() -> removeSourcePair(helper, source))
                .thenIdle(4)
                .thenExecute(() -> assertTrapdoorRailPairDropped(helper, rail))
                .thenSucceed();
    }

    @SuppressWarnings("null")
    private static void placeTrapdoorSupportedRailPair(GameTestHelper helper, BlockPos rail) {
        var trapdoorState = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, false)
                .setValue(TrapDoorBlock.HALF, Half.TOP)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        helper.setBlock(rail.below(), trapdoorState);
        helper.setBlock(mirrorCopy(rail).below(), trapdoorState);
        markVanillaForMirrorRail(helper, mirrorCopy(rail));
        helper.setBlock(rail, Blocks.POWERED_RAIL.defaultBlockState()
                .setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST));
        helper.setBlock(mirrorCopy(rail), Blocks.POWERED_RAIL.defaultBlockState()
                .setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST));
    }

    private static void openTrapdoorPair(GameTestHelper helper, BlockPos trapdoor) {
        helper.useBlock(trapdoor);
        helper.useBlock(mirrorCopy(trapdoor));
    }

    @SuppressWarnings("null")
    private static void assertTrapdoorRailPairPresent(
            GameTestHelper helper, BlockPos rail, boolean powered, boolean trapdoorOpen) {
        helper.assertBlockProperty(rail.below(), TrapDoorBlock.OPEN, trapdoorOpen);
        helper.assertBlockProperty(mirrorCopy(rail).below(), TrapDoorBlock.OPEN, trapdoorOpen);
        helper.assertBlockProperty(rail, PoweredRailBlock.POWERED, powered);
        helper.assertBlockProperty(mirrorCopy(rail), PoweredRailBlock.POWERED, powered);
    }

    private static void assertTrapdoorRailPairDropped(GameTestHelper helper, BlockPos rail) {
        helper.assertBlockNotPresent(Blocks.POWERED_RAIL, rail);
        helper.assertBlockNotPresent(Blocks.POWERED_RAIL, mirrorCopy(rail));
        helper.assertItemEntityPresent(Blocks.POWERED_RAIL.asItem(), rail, 1.5);
        helper.assertItemEntityPresent(Blocks.POWERED_RAIL.asItem(), mirrorCopy(rail), 1.5);
    }

    @SuppressWarnings("null")
    private static void placeSourcePair(GameTestHelper helper, BlockPos source) {
        helper.placeBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK, Direction.UP);
        helper.placeBlock(source, Blocks.REDSTONE_BLOCK, Direction.UP);
    }

    private static void removeSourcePair(GameTestHelper helper, BlockPos source) {
        helper.destroyBlock(mirrorCopy(source));
        helper.destroyBlock(source);
    }

    private static void assertEndpointCascadeCountsMatch(GameTestHelper helper, BlockPos endpoint,
                                                         BlockPos[] cascadeNeighbors, String stage) {
        assertMatchingNeighborCounterCounts(helper, mirrorCopy(endpoint), endpoint, stage + " endpoint");
        assertMatchingNeighborCounterCounts(helper, cascadeNeighbors, stage + " cascade neighbors");
    }

    private static void resetEndpointCascadeCounters(GameTestHelper helper, BlockPos endpoint,
                                                     BlockPos[] cascadeNeighbors) {
        resetNeighborCounter(helper, mirrorCopy(endpoint));
        resetNeighborCounter(helper, endpoint);
        resetNeighborCounters(helper, mirrorCopy(cascadeNeighbors));
        resetNeighborCounters(helper, cascadeNeighbors);
    }
}
