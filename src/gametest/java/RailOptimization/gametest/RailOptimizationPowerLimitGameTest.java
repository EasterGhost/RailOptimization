package RailOptimization.gametest;

import RailOptimization.RailLogicTestAccess;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationPowerLimitGameTest extends RailOptimizationGameTestSupport {
    @GameTest(environment = "railoptimization-gametest:serial_50", maxTicks = 140, padding = 40)
    public void powerLimitOnePowersOneConnectedRail(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 3);
        BlockPos source = start.north();
        int length = 4;

        placeRailLineWithPowerLimit(helper, start, length, 1);

        helper.startSequence()
                .thenExecute(() -> helper.setBlock(source, Blocks.REDSTONE_BLOCK))
                .thenIdle(4)
                .thenExecute(() -> {
                    assertRailLinePowered(helper, start, Direction.EAST, 2, true);
                    assertRailLinePowered(helper, start.east(2), Direction.EAST, length - 2, false);
                })
                .thenExecute(() -> helper.setBlock(source, Blocks.AIR))
                .thenIdle(4)
                .thenExecute(() -> assertRailLinePowered(helper, start, Direction.EAST, length, false))
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_51", maxTicks = 160, padding = 40)
    public void explicitDefaultPowerLimitMatchesVanilla(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 3);
        BlockPos source = start.north();
        int length = 12;

        placeRailLinePair(helper, start, Direction.EAST, length, RailShape.EAST_WEST);

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
                    helper.setBlock(source, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, length);
                    assertRailLinePowered(helper, start, Direction.EAST, 9, true);
                    assertRailLinePowered(helper, start.relative(Direction.EAST, 9), Direction.EAST, 3, false);
                })
                .thenExecute(() -> {
                    helper.setBlock(mirrorCopy(source), Blocks.AIR);
                    helper.setBlock(source, Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, length);
                    assertRailLinePowered(helper, start, Direction.EAST, length, false);
                })
                .thenSucceed();
    }

    @GameTest(environment = "railoptimization-gametest:serial_52", maxTicks = 140, padding = 40)
    public void largerPowerLimitExtendsPropagationBoundary(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 3);
        BlockPos source = start.north();
        int powerLimit = 12;
        int length = 15;

        placeRailLineWithPowerLimit(helper, start, length, powerLimit);

        helper.startSequence()
                .thenExecute(() -> helper.setBlock(source, Blocks.REDSTONE_BLOCK))
                .thenIdle(4)
                .thenExecute(() -> {
                    assertRailLinePowered(helper, start, Direction.EAST, powerLimit + 1, true);
                    assertRailLinePowered(helper, start.relative(Direction.EAST, powerLimit + 1),
                            Direction.EAST, length - powerLimit - 1, false);
                })
                .thenExecute(() -> helper.setBlock(source, Blocks.AIR))
                .thenIdle(4)
                .thenExecute(() -> assertRailLinePowered(helper, start, Direction.EAST, length, false))
                .thenSucceed();
    }

    private static void placeRailLineWithPowerLimit(
            GameTestHelper helper, BlockPos start, int length, int powerLimit) {
        placeRailLine(helper, start, Direction.EAST, length, RailShape.EAST_WEST);
        for (int railIndex = 0; railIndex < length; railIndex++) {
            RailLogicTestAccess.forcePowerLimitAt(
                    helper.absolutePos(start.relative(Direction.EAST, railIndex)), powerLimit);
        }
    }
}
