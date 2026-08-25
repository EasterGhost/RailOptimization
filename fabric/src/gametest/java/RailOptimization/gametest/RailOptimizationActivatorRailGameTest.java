package RailOptimization.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationActivatorRailGameTest extends RailOptimizationGameTestSupport {
	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_54", maxTicks = 160, padding = 40)
	public void activatorRailLinePowersAndDepowersLikeVanilla(GameTestHelper helper) {
		BlockPos start = new BlockPos(1, RAIL_Y, 3);
		BlockPos source = start.north();
		int length = 12;

		for (int railIndex = 0; railIndex < length; railIndex++) {
			BlockPos rail = start.relative(Direction.EAST, railIndex);
			placeRail(helper, rail, RailShape.EAST_WEST, Blocks.ACTIVATOR_RAIL);
			placeRail(helper, mirrorCopy(rail), RailShape.EAST_WEST, Blocks.ACTIVATOR_RAIL);
		}

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
}
