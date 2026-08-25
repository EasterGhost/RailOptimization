package RailOptimization.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
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
	@GameTest(environment = "railoptimization-gametest:serial_111", maxTicks = 80, padding = 40)
	public void jukeboxPlaybackEndMatchesVanilla(GameTestHelper helper) {
		BlockPos rail = new BlockPos(3, RAIL_Y, 3);
		BlockPos jukebox = rail.north();
		BlockPos mirrorRail = mirrorCopy(rail);
		BlockPos mirrorJukebox = mirrorCopy(jukebox);

		placeRailLinePair(helper, rail, Direction.EAST, 1, RailShape.EAST_WEST);
		helper.setBlock(jukebox, Blocks.JUKEBOX);
		helper.setBlock(mirrorJukebox, Blocks.JUKEBOX);

		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					insertAndPlay(helper, mirrorJukebox);
					insertAndPlay(helper, jukebox);
				})
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertBlockProperty(mirrorRail, PoweredRailBlock.POWERED, true);
					helper.assertBlockProperty(rail, PoweredRailBlock.POWERED, true);

					stopWithoutRemovingRecord(helper, jukebox);
					stopWithoutRemovingRecord(helper, mirrorJukebox);
				})
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertBlockProperty(jukebox, JukeboxBlock.HAS_RECORD, true);
					helper.assertBlockProperty(mirrorJukebox, JukeboxBlock.HAS_RECORD, true);
					assertMatchingRailPower(helper, mirrorRail, rail);
					helper.assertBlockProperty(rail, PoweredRailBlock.POWERED, false);
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

	@SuppressWarnings("null")
	private static void insertAndPlay(GameTestHelper helper, BlockPos jukebox) {
		helper.getBlockEntity(jukebox, JukeboxBlockEntity.class)
				.setTheItem(new ItemStack(Items.MUSIC_DISC_13));
	}

	@SuppressWarnings("null")
	private static void stopWithoutRemovingRecord(GameTestHelper helper, BlockPos jukebox) {
		JukeboxBlockEntity blockEntity = helper.getBlockEntity(jukebox, JukeboxBlockEntity.class);
		blockEntity.getSongPlayer().stop(helper.getLevel(), helper.getBlockState(jukebox));
	}
}
