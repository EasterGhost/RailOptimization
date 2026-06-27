package RailOptimization.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationMixedRailGameTest extends RailOptimizationGameTestSupport {
    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_41", maxTicks = 100, padding = 40)
    public void poweredRailSignalStopsAtActivatorRailBoundary(GameTestHelper helper) {
        BlockPos[] rails = flatEastWestRails(5);
        Block[] railBlocks = new Block[]{
                Blocks.POWERED_RAIL,
                Blocks.POWERED_RAIL,
                Blocks.ACTIVATOR_RAIL,
                Blocks.POWERED_RAIL,
                Blocks.POWERED_RAIL
        };

        placeMixedRailPathPair(helper, rails, railBlocks, RailShape.EAST_WEST);

        compareMirroredAndOptimizedPower(
                helper,
                () -> helper.setBlock(mirrorCopy(rails[0].north()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(rails[0].north(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailPower(helper, mirrorCopy(rails), rails);
                    assertRailsPowered(helper, rails, 0, 2, true);
                    assertRailsPowered(helper, rails, 2, rails.length, false);
                }
        );
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_42", maxTicks = 100, padding = 40)
    public void activatorRailSignalStopsAtPoweredRailBoundary(GameTestHelper helper) {
        BlockPos[] rails = flatEastWestRails(5);
        Block[] railBlocks = new Block[]{
                Blocks.ACTIVATOR_RAIL,
                Blocks.ACTIVATOR_RAIL,
                Blocks.POWERED_RAIL,
                Blocks.ACTIVATOR_RAIL,
                Blocks.ACTIVATOR_RAIL
        };

        placeMixedRailPathPair(helper, rails, railBlocks, RailShape.EAST_WEST);

        compareMirroredAndOptimizedPower(
                helper,
                () -> helper.setBlock(mirrorCopy(rails[0].north()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(rails[0].north(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailPower(helper, mirrorCopy(rails), rails);
                    assertRailsPowered(helper, rails, 0, 2, true);
                    assertRailsPowered(helper, rails, 2, rails.length, false);
                }
        );
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_43", maxTicks = 100, padding = 40)
    public void mixedRailBoundaryOnSlopeMatchesVanilla(GameTestHelper helper) {
        BlockPos[] rails = new BlockPos[]{
                new BlockPos(1, RAIL_Y, 3),
                new BlockPos(2, RAIL_Y + 1, 3),
                new BlockPos(3, RAIL_Y + 2, 3),
                new BlockPos(4, RAIL_Y + 3, 3),
                new BlockPos(5, RAIL_Y + 4, 3)
        };
        RailShape[] shapes = new RailShape[]{
                RailShape.ASCENDING_EAST,
                RailShape.ASCENDING_EAST,
                RailShape.ASCENDING_EAST,
                RailShape.ASCENDING_EAST,
                RailShape.EAST_WEST
        };
        Block[] railBlocks = new Block[]{
                Blocks.POWERED_RAIL,
                Blocks.POWERED_RAIL,
                Blocks.ACTIVATOR_RAIL,
                Blocks.ACTIVATOR_RAIL,
                Blocks.ACTIVATOR_RAIL
        };

        placeMixedRailPathPair(helper, rails, shapes, railBlocks);

        compareMirroredAndOptimizedPower(
                helper,
                () -> helper.setBlock(mirrorCopy(rails[0].north()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(rails[0].north(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailPower(helper, mirrorCopy(rails), rails);
                    assertRailsPowered(helper, rails, 0, 2, true);
                    assertRailsPowered(helper, rails, 2, rails.length, false);
                }
        );
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_44", maxTicks = 120, padding = 40)
    public void activatorRailPowersOnlyAfterBudUpdate(GameTestHelper helper) {
        BlockPos rail = new BlockPos(3, RAIL_Y, 3);
        BlockPos source = rail.west();
        BlockPos trigger = rail.above();

        placeRail(helper, rail, RailShape.NORTH_SOUTH, Blocks.ACTIVATOR_RAIL);
        placeRail(helper, mirrorCopy(rail), RailShape.NORTH_SOUTH, Blocks.ACTIVATOR_RAIL);

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
    @GameTest(environment = "railoptimization-gametest:serial_45", maxTicks = 120, padding = 40)
    public void mixedRailBoundaryPowersOnlyAfterBudUpdate(GameTestHelper helper) {
        BlockPos[] rails = flatEastWestRails(5);
        Block[] railBlocks = new Block[]{
                Blocks.POWERED_RAIL,
                Blocks.POWERED_RAIL,
                Blocks.ACTIVATOR_RAIL,
                Blocks.POWERED_RAIL,
                Blocks.POWERED_RAIL
        };
        BlockPos source = rails[0].north();
        BlockPos trigger = rails[0].above();

        placeMixedRailPathPair(helper, rails, railBlocks, RailShape.EAST_WEST);

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
                    assertRailsPowered(helper, rails, 0, 2, true);
                    assertRailsPowered(helper, rails, 2, rails.length, false);
                })
                .thenSucceed();
    }

    private static BlockPos[] flatEastWestRails(int length) {
        BlockPos[] rails = new BlockPos[length];
        for (int railIndex = 0; railIndex < length; railIndex++) {
            rails[railIndex] = new BlockPos(1 + railIndex, RAIL_Y, 3);
        }
        return rails;
    }

    private static void placeMixedRailPathPair(GameTestHelper helper, BlockPos[] rails, Block[] railBlocks,
                                               RailShape shape) {
        RailShape[] shapes = new RailShape[rails.length];
        for (int railIndex = 0; railIndex < shapes.length; railIndex++) {
            shapes[railIndex] = shape;
        }
        placeMixedRailPathPair(helper, rails, shapes, railBlocks);
    }

    private static void placeMixedRailPathPair(GameTestHelper helper, BlockPos[] rails, RailShape[] shapes,
                                               Block[] railBlocks) {
        if (rails.length != shapes.length || rails.length != railBlocks.length) {
            throw new IllegalArgumentException("rails, shapes and railBlocks must have the same length");
        }

        for (int railIndex = 0; railIndex < rails.length; railIndex++) {
            placeRail(helper, rails[railIndex], shapes[railIndex], railBlocks[railIndex]);
            placeRail(helper, mirrorCopy(rails[railIndex]), shapes[railIndex], railBlocks[railIndex]);
        }
    }

    private static void triggerMirrorAndOptimizedUpdate(GameTestHelper helper, BlockPos trigger) {
        helper.setBlock(mirrorCopy(trigger), Blocks.STONE);
        helper.setBlock(trigger, Blocks.STONE);
    }

    private static void setBlockWithoutUpdates(GameTestHelper helper, BlockPos pos, Block block) {
        setBlockWithoutUpdates(helper, pos, block.defaultBlockState());
    }

    private static void setBlockWithoutUpdates(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.getLevel().setBlock(helper.absolutePos(pos), state, Block.UPDATE_NONE);
    }
}
