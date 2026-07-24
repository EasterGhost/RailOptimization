package RailOptimization.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationStraightBaselineGameTest extends RailOptimizationGameTestSupport {
    @GameTest(environment = "railoptimization-gametest:serial_53", maxTicks = 200, padding = 50)
    public void longStraightRailSourceMovesMatchVanilla(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 3);
        int length = 33;
        BlockPos firstSource = start.relative(Direction.EAST, 8).north();
        BlockPos secondSource = start.relative(Direction.EAST, 20).north();

        placeRailLinePair(helper, start, Direction.EAST, length, RailShape.EAST_WEST);

        helper.startSequence()
                .thenExecute(() -> placeSourcePair(helper, firstSource))
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, length);
                    assertRailLinePowered(helper, start, Direction.EAST, 17, true);
                    assertRailLinePowered(helper, start.relative(Direction.EAST, 17),
                            Direction.EAST, length - 17, false);
                })
                .thenExecute(() -> {
                    removeSourcePair(helper, firstSource);
                    placeSourcePair(helper, secondSource);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, length);
                    assertRailLinePowered(helper, start, Direction.EAST, 12, false);
                    assertRailLinePowered(helper, start.relative(Direction.EAST, 12), Direction.EAST, 17, true);
                    assertRailLinePowered(helper, start.relative(Direction.EAST, 29),
                            Direction.EAST, length - 29, false);
                })
                .thenExecute(() -> removeSourcePair(helper, secondSource))
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, length);
                    assertRailLinePowered(helper, start, Direction.EAST, length, false);
                })
                .thenSucceed();
    }

    @GameTest(environment = "railoptimization-gametest:serial_60", maxTicks = 200, padding = 50)
    public void removingSourcePreservesRailsReachedByDistantSource(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 3);
        int length = 20;
        BlockPos firstSource = start.north();
        BlockPos secondSource = start.relative(Direction.EAST, 10).north();

        placeRailLinePair(helper, start, Direction.EAST, length, RailShape.EAST_WEST);

        helper.startSequence()
                .thenExecute(() -> {
                    placeSourcePair(helper, firstSource);
                    placeSourcePair(helper, secondSource);
                })
                .thenIdle(4)
                .thenExecute(() -> assertMatchingRailLinePower(
                        helper, mirrorCopy(start), start, Direction.EAST, length))
                .thenExecute(() -> removeSourcePair(helper, firstSource))
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, length);
                    assertRailLinePowered(helper, start, Direction.EAST, 2, false);
                    assertRailLinePowered(helper, start.relative(Direction.EAST, 2), Direction.EAST, 17, true);
                    assertRailLinePowered(helper, start.relative(Direction.EAST, 19), Direction.EAST, 1, false);
                })
                .thenExecute(() -> removeSourcePair(helper, secondSource))
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, length);
                    assertRailLinePowered(helper, start, Direction.EAST, length, false);
                })
                .thenSucceed();
    }

    @SuppressWarnings("null")
    private static void placeSourcePair(GameTestHelper helper, BlockPos source) {
        helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
        helper.setBlock(source, Blocks.REDSTONE_BLOCK);
    }

    @SuppressWarnings("null")
    private static void removeSourcePair(GameTestHelper helper, BlockPos source) {
        helper.setBlock(mirrorCopy(source), Blocks.AIR);
        helper.setBlock(source, Blocks.AIR);
    }
}
