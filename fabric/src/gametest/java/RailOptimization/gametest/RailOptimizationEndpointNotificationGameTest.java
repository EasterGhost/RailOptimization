package RailOptimization.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationEndpointNotificationGameTest extends RailOptimizationGameTestSupport {
	private static final int LINE_LENGTH = 5;
	private static final int EXPECTED_OUTER_UPDATES = 2;

	@GameTest(environment = "railoptimization-gametest:serial_150", maxTicks = 120, padding = 40)
	public void eastWestOuterEndpointReceivesVanillaDuplicateUpdates(GameTestHelper helper) {
		verifyOuterEndpointNotificationCount(
				helper, new BlockPos(2, RAIL_Y, 3), Direction.EAST, Direction.NORTH,
				RailShape.EAST_WEST, "east-west");
	}

	@GameTest(environment = "railoptimization-gametest:serial_151", maxTicks = 120, padding = 40)
	public void northSouthOuterEndpointReceivesVanillaDuplicateUpdates(GameTestHelper helper) {
		verifyOuterEndpointNotificationCount(
				helper, new BlockPos(3, RAIL_Y, 2), Direction.SOUTH, Direction.WEST,
				RailShape.NORTH_SOUTH, "north-south");
	}

	@SuppressWarnings("null")
	private static void verifyOuterEndpointNotificationCount(
			GameTestHelper helper, BlockPos start, Direction railDirection,
			Direction sourceDirection, RailShape shape, String label) {
		BlockPos source = start.relative(sourceDirection);
		BlockPos outerEndpoint = start.relative(railDirection, LINE_LENGTH);
		BlockPos[] sameLevelCounter = new BlockPos[]{outerEndpoint};
		BlockPos[] lowerCounter = new BlockPos[]{outerEndpoint.below()};

		placeRailLinePair(helper, start, railDirection, LINE_LENGTH, shape);
		placeCounterPairs(helper, sameLevelCounter);

		helper.startSequence()
				.thenExecute(() -> resetCounterPairs(helper, sameLevelCounter))
				.thenExecute(() -> helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK))
				.thenIdle(4)
				.thenExecute(() -> helper.setBlock(source, Blocks.REDSTONE_BLOCK))
				.thenIdle(4)
				.thenExecute(() -> assertCounterPairsUpdatedTwice(
						helper, sameLevelCounter, label + " same-level powering"))
				.thenExecute(() -> resetCounterPairs(helper, sameLevelCounter))
				.thenExecute(() -> helper.setBlock(mirrorCopy(source), Blocks.AIR))
				.thenIdle(4)
				.thenExecute(() -> helper.setBlock(source, Blocks.AIR))
				.thenIdle(4)
				.thenExecute(() -> assertCounterPairsUpdatedTwice(
						helper, sameLevelCounter, label + " same-level depowering"))
				.thenExecute(() -> {
					removeCounterPairs(helper, sameLevelCounter);
					placeCounterPairs(helper, lowerCounter);
					resetCounterPairs(helper, lowerCounter);
				})
				.thenExecute(() -> helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK))
				.thenIdle(4)
				.thenExecute(() -> helper.setBlock(source, Blocks.REDSTONE_BLOCK))
				.thenIdle(4)
				.thenExecute(() -> assertCounterPairsUpdatedTwice(
						helper, lowerCounter, label + " lower powering"))
				.thenExecute(() -> resetCounterPairs(helper, lowerCounter))
				.thenExecute(() -> helper.setBlock(mirrorCopy(source), Blocks.AIR))
				.thenIdle(4)
				.thenExecute(() -> helper.setBlock(source, Blocks.AIR))
				.thenIdle(4)
				.thenExecute(() -> assertCounterPairsUpdatedTwice(
						helper, lowerCounter, label + " lower depowering"))
				.thenSucceed();
	}

	private static void placeCounterPairs(GameTestHelper helper, BlockPos[] counters) {
		placeNeighborCounters(helper, mirrorCopy(counters));
		placeNeighborCounters(helper, counters);
	}

	@SuppressWarnings("null")
	private static void removeCounterPairs(GameTestHelper helper, BlockPos[] counters) {
		for (BlockPos counter : counters) {
			helper.setBlock(mirrorCopy(counter), Blocks.AIR);
			helper.setBlock(counter, Blocks.AIR);
		}
	}

	private static void resetCounterPairs(GameTestHelper helper, BlockPos[] counters) {
		resetNeighborCounters(helper, mirrorCopy(counters));
		resetNeighborCounters(helper, counters);
	}

	@SuppressWarnings("null")
	private static void assertCounterPairsUpdatedTwice(
			GameTestHelper helper, BlockPos[] counters, String stage) {
		for (BlockPos counter : counters) {
			int vanillaCount = helper.getBlockState(mirrorCopy(counter))
					.getValue(RailOptimizationGameTestMod.NeighborCounterBlock.COUNT);
			int optimizedCount = helper.getBlockState(counter)
					.getValue(RailOptimizationGameTestMod.NeighborCounterBlock.COUNT);
			helper.assertTrue(
					vanillaCount == EXPECTED_OUTER_UPDATES && optimizedCount == EXPECTED_OUTER_UPDATES,
					Component.literal(stage + " outer counter mismatch at " + counter
							+ ": vanilla=" + vanillaCount + ", optimized=" + optimizedCount));
		}
	}
}
