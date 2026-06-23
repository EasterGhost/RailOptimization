package RailOptimization.gametest;

import RailOptimization.RailLogic;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationObserverGameTest extends RailOptimizationGameTestSupport {
    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_12", maxTicks = 180)
    public void observerFacingFlatRailActivatesOnPowerStateChanges(GameTestHelper helper) {
        BlockPos rail = new BlockPos(3, RAIL_Y, 3);
        BlockPos source = rail.west();
        BlockPos observer = rail.east();

        placeRail(helper, rail, RailShape.NORTH_SOUTH);
        placeRail(helper, mirrorCopy(rail), RailShape.NORTH_SOUTH);
        placeObserverWatchingRail(helper, observer, Direction.WEST);
        placeObserverWatchingRail(helper, mirrorCopy(observer), Direction.WEST);

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
                    helper.assertBlockProperty(mirrorCopy(rail), PoweredRailBlock.POWERED, true);
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
                    helper.assertBlockProperty(rail, PoweredRailBlock.POWERED, true);
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
                    helper.assertBlockProperty(mirrorCopy(rail), PoweredRailBlock.POWERED, false);
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
                    helper.assertBlockProperty(rail, PoweredRailBlock.POWERED, false);
                    assertObserverPowered(helper, observer, true);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    assertObserverPowered(helper, observer, false);
                })
                .thenSucceed();
    }
}
