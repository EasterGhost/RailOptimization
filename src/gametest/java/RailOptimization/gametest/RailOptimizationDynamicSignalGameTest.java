package RailOptimization.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationDynamicSignalGameTest extends RailOptimizationGameTestSupport {
	@SuppressWarnings({"null", "removal"})
	@GameTest(environment = "railoptimization-gametest:serial_107", maxTicks = 160, padding = 40)
	public void trappedChestOutputChangeMatchesVanilla(GameTestHelper helper) {
		BlockPos rail = new BlockPos(3, RAIL_Y, 3);
		BlockPos chest = rail.north();
		BlockPos mirrorRail = mirrorCopy(rail);
		BlockPos mirrorChest = mirrorCopy(chest);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();

		placeRailLinePair(helper, rail, Direction.EAST, 1, RailShape.EAST_WEST);
		helper.setBlock(chest, Blocks.TRAPPED_CHEST);
		helper.setBlock(mirrorChest, Blocks.TRAPPED_CHEST);

		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> openChest(helper, player, mirrorChest))
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertBlockProperty(mirrorRail, PoweredRailBlock.POWERED, true);
					player.closeContainer();
				})
				.thenIdle(4)
				.thenExecute(() -> helper.assertBlockProperty(
						mirrorRail, PoweredRailBlock.POWERED, false))
				.thenExecute(() -> openChest(helper, player, chest))
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertBlockProperty(rail, PoweredRailBlock.POWERED, true);
					player.closeContainer();
				})
				.thenIdle(4)
				.thenExecute(() -> {
					helper.assertBlockProperty(rail, PoweredRailBlock.POWERED, false);
					assertMatchingRailPower(helper, mirrorRail, rail);
					helper.getLevel().getServer().getPlayerList().remove(player);
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	private static void openChest(GameTestHelper helper, ServerPlayer player, BlockPos chest) {
		BlockPos absoluteChest = helper.absolutePos(chest);
		player.snapTo(
				absoluteChest.getX() + 0.5,
				absoluteChest.getY(),
				absoluteChest.getZ() + 1.5);
		helper.useBlock(chest, player);
	}
}
