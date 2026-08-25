package RailOptimization.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationSignalEdgeGameTest extends RailOptimizationGameTestSupport {
	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_84", maxTicks = 120, padding = 40)
	public void indirectPowerThroughConductorMatchesVanilla(GameTestHelper helper) {
		BlockPos rail = new BlockPos(3, RAIL_Y, 3);
		BlockPos conductor = rail.north();
		BlockPos source = conductor.north();

		placeRail(helper, rail, RailShape.EAST_WEST);
		placeRail(helper, mirrorCopy(rail), RailShape.EAST_WEST);
		helper.setBlock(conductor, Blocks.STONE);
		helper.setBlock(mirrorCopy(conductor), Blocks.STONE);

		BlockPos trigger = rail.south();
		helper.startSequence()
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
					helper.setBlock(source, Blocks.REDSTONE_BLOCK);
					triggerRailUpdate(helper, trigger);
				})
				.thenIdle(4)
				.thenExecute(() -> assertMatchingRailPower(helper, mirrorCopy(rail), rail))
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(source), Blocks.AIR);
					helper.setBlock(source, Blocks.AIR);
					triggerRailUpdate(helper, trigger);
				})
				.thenIdle(4)
				.thenExecute(() -> assertMatchingRailPower(helper, mirrorCopy(rail), rail))
				.thenSucceed();
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_85", maxTicks = 120, padding = 40)
	public void waterloggedPoweredRailPowerMatchesVanilla(GameTestHelper helper) {
		BlockPos rail = new BlockPos(3, RAIL_Y, 3);
		BlockPos source = rail.west();

		helper.setBlock(rail.below(), Blocks.STONE);
		helper.setBlock(mirrorCopy(rail).below(), Blocks.STONE);
		markVanillaForMirrorRail(helper, mirrorCopy(rail));
		var waterloggedRail = Blocks.POWERED_RAIL.defaultBlockState()
				.setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST)
				.setValue(PoweredRailBlock.WATERLOGGED, true);
		helper.setBlock(rail, waterloggedRail);
		helper.setBlock(mirrorCopy(rail), waterloggedRail);

		helper.startSequence()
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
					helper.setBlock(source, Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					assertMatchingRailPower(helper, mirrorCopy(rail), rail);
					helper.assertBlockProperty(rail, PoweredRailBlock.POWERED, true);
					helper.assertBlockProperty(rail, PoweredRailBlock.WATERLOGGED, true);
					helper.assertBlockProperty(mirrorCopy(rail), PoweredRailBlock.WATERLOGGED, true);
				})
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(source), Blocks.AIR);
					helper.setBlock(source, Blocks.AIR);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					assertMatchingRailPower(helper, mirrorCopy(rail), rail);
					helper.assertBlockProperty(rail, PoweredRailBlock.POWERED, false);
					helper.assertBlockProperty(rail, PoweredRailBlock.WATERLOGGED, true);
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	private static void triggerRailUpdate(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, Blocks.STONE);
		helper.setBlock(mirrorCopy(pos), Blocks.STONE);
		helper.setBlock(pos, Blocks.AIR);
		helper.setBlock(mirrorCopy(pos), Blocks.AIR);
	}
}
