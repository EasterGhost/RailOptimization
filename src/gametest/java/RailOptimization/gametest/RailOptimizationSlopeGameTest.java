package RailOptimization.gametest;

import RailOptimization.RailLogic;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationSlopeGameTest extends RailOptimizationGameTestSupport {
    @GameTest(maxTicks = 140)
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

    @GameTest(maxTicks = 100)
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

    @GameTest(maxTicks = 100)
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

    @GameTest(maxTicks = 100)
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

    @GameTest(maxTicks = 100)
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

    @GameTest(maxTicks = 100)
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

    @GameTest(maxTicks = 100)
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
    @GameTest(maxTicks = 100)
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
}
