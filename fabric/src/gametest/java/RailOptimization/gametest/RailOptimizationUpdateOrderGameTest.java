package RailOptimization.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationUpdateOrderGameTest extends RailOptimizationGameTestSupport {
	private static final int UPDATE_TYPE_LINE_LENGTH = 5;

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_46", maxTicks = 120, padding = 40)
	public void straightRailUpdateOrderMatchesVanilla(GameTestHelper helper) {
		int length = 5;
		BlockPos start = new BlockPos(1, RAIL_Y, 3);
		BlockPos[] rails = eastWestRails(start, length);
		BlockPos[] probes = new BlockPos[]{
				rails[0].south(),
				rails[2].south(),
				rails[4].south(),
				rails[4].east()
		};

		placeRailLinePair(helper, start, Direction.EAST, length, RailShape.EAST_WEST);
		placeOrderRecorderPair(helper, probes, rails);

		helper.startSequence()
				.thenExecute(() -> {
					resetOrderRecorders(helper, mirrorCopy(probes));
					resetOrderRecorders(helper, probes);
					helper.setBlock(mirrorCopy(rails[0].north()), Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(4)
				.thenExecute(() -> helper.setBlock(rails[0].north(), Blocks.REDSTONE_BLOCK))
				.thenIdle(4)
				.thenExecute(() -> assertOrderRecordsMatch(helper, mirrorCopy(probes), probes))
				.thenSucceed();
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_47", maxTicks = 120, padding = 40)
	public void ascendingRailUpdateOrderMatchesVanilla(GameTestHelper helper) {
		BlockPos[] rails = continuousAscendingEastRails(5);
		RailShape[] shapes = new RailShape[]{
				RailShape.ASCENDING_EAST,
				RailShape.ASCENDING_EAST,
				RailShape.ASCENDING_EAST,
				RailShape.ASCENDING_EAST,
				RailShape.ASCENDING_EAST
		};
		BlockPos[] probes = new BlockPos[]{
				rails[0].south(),
				rails[2].south(),
				rails[4].south(),
				rails[2].above()
		};

		placeRailPathPair(helper, rails, shapes);
		placeOrderRecorderPair(helper, probes, rails);

		helper.startSequence()
				.thenExecute(() -> {
					resetOrderRecorders(helper, mirrorCopy(probes));
					resetOrderRecorders(helper, probes);
					helper.setBlock(mirrorCopy(rails[0].north()), Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(4)
				.thenExecute(() -> helper.setBlock(rails[0].north(), Blocks.REDSTONE_BLOCK))
				.thenIdle(4)
				.thenExecute(() -> assertOrderRecordsMatch(helper, mirrorCopy(probes), probes))
				.thenSucceed();
	}

	@GameTest(environment = "railoptimization-gametest:serial_144", maxTicks = 120, padding = 40)
	public void eastWestUpdateTypeOrderMatchesVanilla(GameTestHelper helper) {
		verifyStraightUpdateTypeOrder(
				helper, new BlockPos(1, RAIL_Y, 3), Direction.EAST, Direction.SOUTH,
				RailShape.EAST_WEST, true, "east-west");
	}

	@GameTest(environment = "railoptimization-gametest:serial_145", maxTicks = 120, padding = 40)
	public void northSouthUpdateTypeOrderMatchesVanilla(GameTestHelper helper) {
		verifyStraightUpdateTypeOrder(
				helper, new BlockPos(3, RAIL_Y, 1), Direction.SOUTH, Direction.EAST,
				RailShape.NORTH_SOUTH, false, "north-south");
	}

	@GameTest(environment = "railoptimization-gametest:serial_146", maxTicks = 120, padding = 40)
	public void eastWestCenteredSourceUpdateOrderMatchesVanilla(GameTestHelper helper) {
		verifyCenteredSourceUpdateOrder(
				helper, new BlockPos(1, RAIL_Y, 3), Direction.EAST, Direction.SOUTH,
				RailShape.EAST_WEST, "east-west centered source");
	}

	@GameTest(environment = "railoptimization-gametest:serial_147", maxTicks = 120, padding = 40)
	public void northSouthCenteredSourceUpdateOrderMatchesVanilla(GameTestHelper helper) {
		verifyCenteredSourceUpdateOrder(
				helper, new BlockPos(3, RAIL_Y, 1), Direction.SOUTH, Direction.EAST,
				RailShape.NORTH_SOUTH, "north-south centered source");
	}

	@SuppressWarnings("null")
	private static void verifyStraightUpdateTypeOrder(
			GameTestHelper helper, BlockPos start, Direction railDirection, Direction probeDirection,
			RailShape railShape, boolean neighborChangedFarToNear, String label) {
		BlockPos[] rails = straightRails(start, railDirection, UPDATE_TYPE_LINE_LENGTH);
		BlockPos[] probes = new BlockPos[UPDATE_TYPE_LINE_LENGTH];
		for (int index = 0; index < probes.length; index++) {
			probes[index] = rails[index].relative(probeDirection);
		}
		BlockPos source = rails[0].relative(probeDirection.getOpposite());

		placeRailLinePair(helper, start, railDirection, UPDATE_TYPE_LINE_LENGTH, railShape);
		placeOrderRecorderPair(helper, probes, rails);

		helper.startSequence()
				.thenExecute(() -> {
					resetOrderRecorders(helper, mirrorCopy(probes));
					resetOrderRecorders(helper, probes);
					helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(4)
				.thenExecute(() -> helper.setBlock(source, Blocks.REDSTONE_BLOCK))
				.thenIdle(4)
				.thenExecute(() -> assertUpdateTypeOrder(
						helper, mirrorCopy(probes), probes, neighborChangedFarToNear, label + " powering"))
				.thenExecute(() -> {
					resetOrderRecorders(helper, mirrorCopy(probes));
					resetOrderRecorders(helper, probes);
					helper.setBlock(mirrorCopy(source), Blocks.AIR);
				})
				.thenIdle(4)
				.thenExecute(() -> helper.setBlock(source, Blocks.AIR))
				.thenIdle(4)
				.thenExecute(() -> assertUpdateTypeOrder(
						helper, mirrorCopy(probes), probes, neighborChangedFarToNear, label + " depowering"))
				.thenSucceed();
	}

	@SuppressWarnings("null")
	private static void verifyCenteredSourceUpdateOrder(
			GameTestHelper helper, BlockPos start, Direction railDirection, Direction probeDirection,
			RailShape railShape, String label) {
		BlockPos[] rails = straightRails(start, railDirection, UPDATE_TYPE_LINE_LENGTH);
		BlockPos[] probes = new BlockPos[UPDATE_TYPE_LINE_LENGTH];
		for (int index = 0; index < probes.length; index++) {
			probes[index] = rails[index].relative(probeDirection);
		}
		int sourceIndex = UPDATE_TYPE_LINE_LENGTH / 2;
		BlockPos source = rails[sourceIndex].relative(probeDirection.getOpposite());

		placeRailLinePair(helper, start, railDirection, UPDATE_TYPE_LINE_LENGTH, railShape);
		placeOrderRecorderPair(helper, probes, rails);

		helper.startSequence()
				.thenExecute(() -> {
					resetOrderRecorders(helper, mirrorCopy(probes));
					resetOrderRecorders(helper, probes);
					helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(4)
				.thenExecute(() -> helper.setBlock(source, Blocks.REDSTONE_BLOCK))
				.thenIdle(4)
				.thenExecute(() -> assertCenteredSourceUpdateOrder(
						helper, mirrorCopy(probes), probes, sourceIndex, label + " powering"))
				.thenExecute(() -> {
					resetOrderRecorders(helper, mirrorCopy(probes));
					resetOrderRecorders(helper, probes);
					helper.setBlock(mirrorCopy(source), Blocks.AIR);
				})
				.thenIdle(4)
				.thenExecute(() -> helper.setBlock(source, Blocks.AIR))
				.thenIdle(4)
				.thenExecute(() -> assertCenteredSourceUpdateOrder(
						helper, mirrorCopy(probes), probes, sourceIndex, label + " depowering"))
				.thenSucceed();
	}

	@SuppressWarnings("null")
	private static BlockPos[] straightRails(BlockPos start, Direction direction, int length) {
		BlockPos[] rails = new BlockPos[length];
		for (int index = 0; index < length; index++) {
			rails[index] = start.relative(direction, index);
		}
		return rails;
	}

	private static void assertUpdateTypeOrder(
			GameTestHelper helper, BlockPos[] mirrorProbes, BlockPos[] probes,
			boolean neighborChangedFarToNear, String label) {
		RailOptimizationGameTestMod.OrderProbeSnapshot[] vanilla = snapshots(helper, mirrorProbes);
		RailOptimizationGameTestMod.OrderProbeSnapshot[] optimized = snapshots(helper, probes);
		int expectedNeighborComparison = neighborChangedFarToNear ? 1 : -1;

		for (int index = 0; index < probes.length; index++) {
			helper.assertTrue(vanilla[index].order() > 0 && optimized[index].order() > 0,
					Component.literal(label + " NC probe " + index + " was not updated"));
			helper.assertTrue(vanilla[index].shapeOrder() > 0 && optimized[index].shapeOrder() > 0,
					Component.literal(label + " PP probe " + index + " was not updated"));
			assertMainNeighborChangedBeforeShape(helper, label + " vanilla", vanilla[index], index);
			assertMainNeighborChangedBeforeShape(helper, label + " optimized", optimized[index], index);
		}

		for (int near = 0; near < probes.length; near++) {
			for (int far = near + 1; far < probes.length; far++) {
				assertPairOrder(helper, label + " vanilla NC", vanilla[near].order(), vanilla[far].order(), expectedNeighborComparison);
				assertPairOrder(helper, label + " optimized NC", optimized[near].order(), optimized[far].order(), expectedNeighborComparison);
				assertPairOrder(helper, label + " vanilla PP", vanilla[near].shapeOrder(), vanilla[far].shapeOrder(), 1);
				assertPairOrder(helper, label + " optimized PP", optimized[near].shapeOrder(), optimized[far].shapeOrder(), 1);
			}
		}
	}

	private static void assertPairOrder(
			GameTestHelper helper, String label, int nearOrder, int farOrder, int expectedComparison) {
		int comparison = Integer.compare(nearOrder, farOrder);
		helper.assertTrue(comparison == expectedComparison,
				Component.literal(label + " order mismatch: near=" + nearOrder + ", far=" + farOrder));
	}

	private static void assertCenteredSourceUpdateOrder(
			GameTestHelper helper, BlockPos[] mirrorProbes, BlockPos[] probes, int sourceIndex, String label) {
		RailOptimizationGameTestMod.OrderProbeSnapshot[] vanilla = snapshots(helper, mirrorProbes);
		RailOptimizationGameTestMod.OrderProbeSnapshot[] optimized = snapshots(helper, probes);
		for (int index = 0; index < probes.length; index++) {
			if (index != sourceIndex) {
				helper.assertTrue(vanilla[sourceIndex].shapeOrder() > vanilla[index].shapeOrder(),
						Component.literal(label + " vanilla source PP was not last: " + describeRecords(vanilla)));
				helper.assertTrue(optimized[sourceIndex].shapeOrder() > optimized[index].shapeOrder(),
						Component.literal(label + " optimized source PP was not last: vanilla=" + describeRecords(vanilla)
								+ ", optimized=" + describeRecords(optimized)));
			}
		}

		for (int index = 0; index < probes.length; index++) {
			helper.assertTrue(vanilla[index].order() > 0 && optimized[index].order() > 0,
					Component.literal(label + " NC probe " + index + " was not updated"));
			helper.assertTrue(vanilla[index].shapeOrder() > 0 && optimized[index].shapeOrder() > 0,
					Component.literal(label + " PP probe " + index + " was not updated"));
			assertMainNeighborChangedBeforeShape(helper, label + " vanilla", vanilla[index], index);
			assertMainNeighborChangedBeforeShape(helper, label + " optimized", optimized[index], index);
		}

		for (int first = 0; first < probes.length; first++) {
			for (int second = first + 1; second < probes.length; second++) {
				assertMatchingPairOrder(helper, label + " NC", vanilla[first].order(), vanilla[second].order(),
						optimized[first].order(), optimized[second].order(), vanilla, optimized);
				assertMatchingPairOrder(helper, label + " PP", vanilla[first].shapeOrder(), vanilla[second].shapeOrder(),
						optimized[first].shapeOrder(), optimized[second].shapeOrder(), vanilla, optimized);
			}
		}
	}

	private static void assertMainNeighborChangedBeforeShape(
			GameTestHelper helper, String label,
			RailOptimizationGameTestMod.OrderProbeSnapshot snapshot, int probeIndex) {
		helper.assertTrue(snapshot.neighborEventOrder() < snapshot.shapeEventOrder(),
				Component.literal(label + " probe " + probeIndex + " received PP before its main NC"));
	}

	private static void assertMatchingPairOrder(
			GameTestHelper helper, String label, int vanillaFirst, int vanillaSecond,
			int optimizedFirst, int optimizedSecond,
			RailOptimizationGameTestMod.OrderProbeSnapshot[] vanilla,
			RailOptimizationGameTestMod.OrderProbeSnapshot[] optimized) {
		int vanillaComparison = Integer.compare(vanillaFirst, vanillaSecond);
		int optimizedComparison = Integer.compare(optimizedFirst, optimizedSecond);
		helper.assertTrue(vanillaComparison == optimizedComparison,
				Component.literal(label + " relative order mismatch, vanilla=" + describeRecords(vanilla)
						+ ", optimized=" + describeRecords(optimized)));
	}

	private static BlockPos[] eastWestRails(BlockPos start, int length) {
		BlockPos[] rails = new BlockPos[length];
		for (int railIndex = 0; railIndex < length; railIndex++) {
			rails[railIndex] = start.relative(Direction.EAST, railIndex);
		}
		return rails;
	}

	private static BlockPos[] continuousAscendingEastRails(int length) {
		BlockPos[] rails = new BlockPos[length];
		for (int railIndex = 0; railIndex < length; railIndex++) {
			rails[railIndex] = new BlockPos(1 + railIndex, RAIL_Y + railIndex, 3);
		}
		return rails;
	}

	private static void placeOrderRecorderPair(GameTestHelper helper, BlockPos[] probes, BlockPos[] watchedRails) {
		BlockPos[] mirrorProbes = mirrorCopy(probes);
		BlockPos[] mirrorRails = mirrorCopy(watchedRails);
		for (int probeIndex = 0; probeIndex < probes.length; probeIndex++) {
			placeOrderRecorder(helper, probes[probeIndex], watchedRails);
			placeOrderRecorder(helper, mirrorProbes[probeIndex], mirrorRails);
		}
	}

	private static void assertOrderRecordsMatch(GameTestHelper helper, BlockPos[] mirrorProbes, BlockPos[] probes) {
		RailOptimizationGameTestMod.OrderProbeSnapshot[] mirrorRecords = snapshots(helper, mirrorProbes);
		RailOptimizationGameTestMod.OrderProbeSnapshot[] optimizedRecords = snapshots(helper, probes);

		for (int probeIndex = 0; probeIndex < probes.length; probeIndex++) {
			int mirrorOrder = mirrorRecords[probeIndex].order();
			int optimizedOrder = optimizedRecords[probeIndex].order();
			int mirrorSnapshot = mirrorRecords[probeIndex].snapshot();
			int optimizedSnapshot = optimizedRecords[probeIndex].snapshot();

			helper.assertTrue(mirrorOrder > 0, Component.literal("mirror probe " + probeIndex + " was not updated"));
			helper.assertTrue(optimizedOrder > 0,
					Component.literal("optimized probe " + probeIndex + " was not updated"));
			helper.assertTrue(mirrorSnapshot == optimizedSnapshot,
					Component.literal("probe " + probeIndex + " snapshot mismatch: vanilla="
							+ mirrorSnapshot + ", optimized=" + optimizedSnapshot));
		}

		for (int firstProbe = 0; firstProbe < probes.length; firstProbe++) {
			for (int secondProbe = firstProbe + 1; secondProbe < probes.length; secondProbe++) {
				int mirrorOrder = Integer.compare(
						mirrorRecords[firstProbe].order(), mirrorRecords[secondProbe].order());
				int optimizedOrder = Integer.compare(
						optimizedRecords[firstProbe].order(), optimizedRecords[secondProbe].order());
				helper.assertTrue(mirrorOrder == optimizedOrder,
						Component.literal("relative update order mismatch for probes "
								+ firstProbe + " and " + secondProbe
								+ ", vanilla=" + describeRecords(mirrorRecords)
								+ ", optimized=" + describeRecords(optimizedRecords)));
			}
		}
	}

	private static String describeRecords(RailOptimizationGameTestMod.OrderProbeSnapshot[] records) {
		StringBuilder builder = new StringBuilder("[");
		for (int probeIndex = 0; probeIndex < records.length; probeIndex++) {
			if (probeIndex > 0) {
				builder.append(", ");
			}
			builder.append(probeIndex)
					.append(":order=")
					.append(records[probeIndex].order())
					.append(",snapshot=")
					.append(records[probeIndex].snapshot())
					.append(",shapeOrder=")
					.append(records[probeIndex].shapeOrder())
					.append(",shapeSnapshot=")
					.append(records[probeIndex].shapeSnapshot())
					.append(",neighborEventOrder=")
					.append(records[probeIndex].neighborEventOrder())
					.append(",shapeEventOrder=")
					.append(records[probeIndex].shapeEventOrder());
		}
		return builder.append(']').toString();
	}

	private static RailOptimizationGameTestMod.OrderProbeSnapshot[] snapshots(
			GameTestHelper helper, BlockPos[] probes) {
		RailOptimizationGameTestMod.OrderProbeSnapshot[] snapshots =
				new RailOptimizationGameTestMod.OrderProbeSnapshot[probes.length];
		for (int probeIndex = 0; probeIndex < probes.length; probeIndex++) {
			snapshots[probeIndex] = orderProbeSnapshot(helper, probes[probeIndex]);
		}
		return snapshots;
	}
}
