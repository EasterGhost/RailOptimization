package RailOptimization.gametest;

import RailOptimization.RailLogic;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationMonotonicSlopeGameTest extends RailOptimizationGameTestSupport {
	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_98", maxTicks = 140, padding = 40)
	public void monotonicAscendingChainDepowersFromBottomSource(GameTestHelper helper) {
		int chainLength = 9;
		BlockPos[] rails = new BlockPos[chainLength];
		RailShape[] shapes = new RailShape[chainLength];
		for (int k = 0; k < chainLength; k++) {
			rails[k] = new BlockPos(2 + k, RAIL_Y + k, 2);
			shapes[k] = RailShape.ASCENDING_EAST;
		}
		BlockPos source = new BlockPos(1, RAIL_Y, 2);
		placeRailPathPair(helper, rails, shapes);

		helper.startSequence()
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(false);
					helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(true);
					helper.setBlock(source, Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(true);
					assertMatchingRailPower(helper, mirrorCopy(rails), rails);
					assertRailsPowered(helper, rails, true);
				})
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(false);
					helper.setBlock(mirrorCopy(source), Blocks.AIR);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(true);
					helper.setBlock(source, Blocks.AIR);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(true);
					assertMatchingRailPower(helper, mirrorCopy(rails), rails);
					assertRailsPowered(helper, rails, false);
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_99", maxTicks = 140, padding = 40)
	public void monotonicAscendingChainBoundaryRailKeepsRestPowered(GameTestHelper helper) {
		int chainLength = 11;
		BlockPos[] rails = new BlockPos[chainLength];
		RailShape[] shapes = new RailShape[chainLength];
		for (int k = 0; k < chainLength; k++) {
			rails[k] = new BlockPos(2 + k, RAIL_Y + k, 2);
			shapes[k] = RailShape.ASCENDING_EAST;
		}
		BlockPos source = new BlockPos(1, RAIL_Y, 2);
		BlockPos boundarySignal = new BlockPos(2 + 9 + 1, RAIL_Y + 9, 2);
		placeRailPathPair(helper, rails, shapes);

		helper.startSequence()
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(false);
					helper.setBlock(mirrorCopy(boundarySignal), Blocks.REDSTONE_BLOCK);
					helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(true);
					helper.setBlock(boundarySignal, Blocks.REDSTONE_BLOCK);
					helper.setBlock(source, Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(true);
					assertMatchingRailPower(helper, mirrorCopy(rails), rails);
					assertRailsPowered(helper, rails, true);
				})
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(false);
					helper.setBlock(mirrorCopy(source), Blocks.AIR);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(true);
					helper.setBlock(source, Blocks.AIR);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(true);
					assertMatchingRailPower(helper, mirrorCopy(rails), rails);
					helper.assertBlockProperty(rails[0], PoweredRailBlock.POWERED, false);
					assertRailsPowered(helper, rails, 1, chainLength, true);
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_100", maxTicks = 140, padding = 40)
	public void mixedSlopeThenFlatChainDepowersWithComplexFallback(GameTestHelper helper) {
		BlockPos[] rails = new BlockPos[]{
				new BlockPos(2, RAIL_Y, 2),
				new BlockPos(3, RAIL_Y + 1, 2),
				new BlockPos(4, RAIL_Y + 1, 2),
				new BlockPos(5, RAIL_Y + 1, 2)
		};
		RailShape[] shapes = new RailShape[]{
				RailShape.ASCENDING_EAST,
				RailShape.EAST_WEST,
				RailShape.EAST_WEST,
				RailShape.EAST_WEST
		};
		BlockPos source = new BlockPos(1, RAIL_Y, 2);
		placeRailPathPair(helper, rails, shapes);

		helper.startSequence()
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(false);
					helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(true);
					helper.setBlock(source, Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(true);
					assertMatchingRailPower(helper, mirrorCopy(rails), rails);
					assertRailsPowered(helper, rails, true);
				})
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(false);
					helper.setBlock(mirrorCopy(source), Blocks.AIR);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(true);
					helper.setBlock(source, Blocks.AIR);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(true);
					assertMatchingRailPower(helper, mirrorCopy(rails), rails);
					assertRailsPowered(helper, rails, false);
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_101", maxTicks = 140, padding = 40)
	public void ascendingChainLongerThanLimitDepowersThroughCascade(GameTestHelper helper) {
		int chainLength = 12;
		BlockPos[] rails = new BlockPos[chainLength];
		RailShape[] shapes = new RailShape[chainLength];
		for (int k = 0; k < chainLength; k++) {
			rails[k] = new BlockPos(2 + k, RAIL_Y + k, 2);
			shapes[k] = RailShape.ASCENDING_EAST;
		}
		BlockPos source = new BlockPos(1, RAIL_Y, 2);
		placeRailPathPair(helper, rails, shapes);

		helper.startSequence()
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(false);
					helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(true);
					helper.setBlock(source, Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(true);
					assertMatchingRailPower(helper, mirrorCopy(rails), rails);
					assertRailsPowered(helper, rails, 0, 9, true);
					assertRailsPowered(helper, rails, 9, chainLength, false);
				})
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(false);
					helper.setBlock(mirrorCopy(source), Blocks.AIR);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(true);
					helper.setBlock(source, Blocks.AIR);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(true);
					assertMatchingRailPower(helper, mirrorCopy(rails), rails);
					assertRailsPowered(helper, rails, false);
				})
				.thenSucceed();
	}
}
