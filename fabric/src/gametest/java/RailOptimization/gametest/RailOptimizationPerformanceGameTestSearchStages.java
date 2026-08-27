package RailOptimization.gametest;

import RailOptimization.RailHotPathBenchmarkAccess;
import RailOptimization.RailLogic;
import RailOptimization.RailLogicTestAccess;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationPerformanceGameTestSearchStages extends RailOptimizationGameTestSupport {
	private static final int INITIAL_FAST_OPERATIONS = 1 << 16;
	private static final int INITIAL_WORLD_READ_OPERATIONS = 1 << 14;
	private static final int INITIAL_SEARCH_OPERATIONS = 1 << 10;

	@GameTest(environment = "railoptimization-gametest:serial_116", maxTicks = 200, padding = 40)
	public void epochAdvanceCostIsMeasured(GameTestHelper helper) {
		runAfterSetup(helper, "memo epoch advance", INITIAL_FAST_OPERATIONS,
				operations -> RailHotPathBenchmarkAccess.measureEpochAdvance(helper.getLevel(), operations));
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_117", maxTicks = 200, padding = 40)
	public void confirmedMemoHitCostIsMeasured(GameTestHelper helper) {
		BlockPos rail = chunkAlignedPos(helper, 8, 8);
		placeRail(helper, rail, RailShape.EAST_WEST);
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					var probe = RailHotPathBenchmarkAccess.memoProbe(
							helper.getLevel(), helper.absolutePos(rail), false);
					RailBenchmarkRunner.measureAndReportIsolated(
							helper, "confirmed memo hit", INITIAL_FAST_OPERATIONS, probe::measureHit);
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_118", maxTicks = 200, padding = 40)
	public void staleMemoEntryCostIsMeasured(GameTestHelper helper) {
		BlockPos rail = chunkAlignedPos(helper, 8, 8);
		placeRail(helper, rail, RailShape.EAST_WEST);
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					var probe = RailHotPathBenchmarkAccess.memoProbe(
							helper.getLevel(), helper.absolutePos(rail), false);
					RailBenchmarkRunner.measureAndReportIsolated(
							helper, "stale memo entry lookup", INITIAL_FAST_OPERATIONS,
							probe::measureStaleEntry);
				})
				.thenSucceed();
	}

	@GameTest(environment = "railoptimization-gametest:serial_119", maxTicks = 200, padding = 40)
	public void searchCacheHitCostIsMeasured(GameTestHelper helper) {
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					var probe = RailHotPathBenchmarkAccess.searchCacheProbe();
					RailBenchmarkRunner.measureAndReportIsolated(
							helper, "RailSearchCache direct-signal hit", INITIAL_FAST_OPERATIONS,
							probe::measureHit);
				})
				.thenSucceed();
	}

	@GameTest(environment = "railoptimization-gametest:serial_120", maxTicks = 200, padding = 40)
	public void searchCacheMissCostIsMeasured(GameTestHelper helper) {
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					var probe = RailHotPathBenchmarkAccess.searchCacheProbe();
					RailBenchmarkRunner.measureAndReportIsolated(
							helper, "RailSearchCache empty-slot miss", INITIAL_FAST_OPERATIONS,
							probe::measureMiss);
				})
				.thenSucceed();
	}

	@GameTest(environment = "railoptimization-gametest:serial_121", maxTicks = 200, padding = 40)
	public void pooledContextResetCostIsMeasured(GameTestHelper helper) {
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					var probe = RailHotPathBenchmarkAccess.contextResetProbe();
					RailBenchmarkRunner.measureAndReportIsolated(
							helper, "pooled RailUpdateContext reset", INITIAL_FAST_OPERATIONS,
							probe::measure);
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_122", maxTicks = 200, padding = 40)
	public void cachedChunkBlockStateReadCostIsMeasured(GameTestHelper helper) {
		BlockPos rail = chunkAlignedPos(helper, 8, 8);
		placeRail(helper, rail, RailShape.EAST_WEST);
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					var probe = RailHotPathBenchmarkAccess.blockReadProbe(
							helper.getLevel(), helper.absolutePos(rail));
					RailBenchmarkRunner.measureAndReportIsolated(
							helper, "cached-chunk rail BlockState read", INITIAL_WORLD_READ_OPERATIONS,
							probe::measure);
					RailBenchmarkRunner.measureAndReportIsolated(
							helper, "direct-section rail BlockState read", INITIAL_WORLD_READ_OPERATIONS,
							probe::measureSection);
				})
				.thenSucceed();
	}

	@GameTest(environment = "railoptimization-gametest:serial_123", maxTicks = 200, padding = 40)
	public void unpoweredNeighborSignalCheckCostIsMeasured(GameTestHelper helper) {
		BlockPos rail = chunkAlignedPos(helper, 8, 8);
		placeRail(helper, rail, RailShape.EAST_WEST);
		measureNeighborSignal(helper, rail, "same-chunk unpowered rail direct-signal scan");
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_124", maxTicks = 200, padding = 40)
	public void directlyPoweredNeighborSignalCheckCostIsMeasured(GameTestHelper helper) {
		BlockPos rail = chunkAlignedPos(helper, 8, 8);
		helper.setBlock(rail.below(), Blocks.REDSTONE_BLOCK);
		helper.setBlock(rail, Blocks.POWERED_RAIL.defaultBlockState()
				.setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST));
		measureNeighborSignal(helper, rail, "same-chunk directly-powered rail direct-signal scan");
	}

	@GameTest(environment = "railoptimization-gametest:serial_125", maxTicks = 200, padding = 60)
	public void coldMaximumDistanceSignalSearchCostIsMeasured(GameTestHelper helper) {
		measureColdSignalSearch(
				helper, chunkAlignedPos(helper, 2, 8),
				"same-chunk cold one-direction distance-limit rail search");
	}

	@GameTest(environment = "railoptimization-gametest:serial_133", maxTicks = 200, padding = 60)
	public void coldMaximumDistanceCrossChunkSignalSearchCostIsMeasured(GameTestHelper helper) {
		measureColdSignalSearch(
				helper, chunkAlignedPos(helper, 12, 8),
				"one-boundary cold one-direction distance-limit rail search");
	}

	@GameTest(environment = "railoptimization-gametest:serial_134", maxTicks = 200, padding = 40)
	public void unpoweredCrossChunkNeighborSignalCheckCostIsMeasured(GameTestHelper helper) {
		BlockPos rail = chunkAlignedPos(helper, 15, 8);
		placeRail(helper, rail, RailShape.EAST_WEST);
		measureNeighborSignal(helper, rail, "chunk-edge unpowered rail direct-signal scan");
	}

	@GameTest(environment = "railoptimization-gametest:serial_135", maxTicks = 200, padding = 60)
	public void coldDistanceLimitSignalHitCostIsMeasured(GameTestHelper helper) {
		BlockPos start = chunkAlignedPos(helper, 2, 8);
		BlockPos[] rails = placePoweredSearchFixture(helper, start, 8);
		helper.setBlock(rails[rails.length - 1].below(), Blocks.REDSTONE_BLOCK);
		measureColdSignalSearch(
				helper, rails, 8, true,
				"powerLimit=8 cold one-direction distance-limit signal hit");
	}

	@GameTest(environment = "railoptimization-gametest:serial_136", maxTicks = 200, padding = 100)
	public void coldExtendedMaximumDistanceSignalSearchCostIsMeasured(GameTestHelper helper) {
		BlockPos start = chunkAlignedPos(helper, 2, 8);
		measureColdSignalSearch(
				helper, placePoweredSearchFixture(helper, start, 64), 64, false,
				"powerLimit=64 cold one-direction distance-limit rail search");
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_137", maxTicks = 400, padding = 120)
	public void candidateRevalidationBenchmarks(GameTestHelper helper) {
		BlockPos base = chunkAlignedPos(helper, 2, 8);
		BlockPos[] missRails = placePoweredSearchFixture(helper, base, 8);
		BlockPos[] hitRails = placePoweredSearchFixture(helper, base.south(16), 8);
		helper.setBlock(hitRails[hitRails.length - 1].below(), Blocks.REDSTONE_BLOCK);
		BlockPos[] extendedMissRails = placePoweredSearchFixture(helper, base.south(32), 64);

		BlockPos sameChunkRail = chunkAlignedPos(helper, 8, 8).south(48);
		BlockPos chunkEdgeRail = chunkAlignedPos(helper, 15, 8).south(64);
		BlockPos directlyPoweredRail = chunkAlignedPos(helper, 8, 8).south(80);
		placeRail(helper, sameChunkRail, RailShape.EAST_WEST);
		placeRail(helper, chunkEdgeRail, RailShape.EAST_WEST);
		helper.setBlock(directlyPoweredRail.below(), Blocks.REDSTONE_BLOCK);
		helper.setBlock(directlyPoweredRail, Blocks.POWERED_RAIL.defaultBlockState()
				.setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST));

		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					int configuredPowerLimit = RailLogicTestAccess.currentPowerLimit();
					BlockPos[] absoluteMissRails = absolutePositions(helper, missRails);
					BlockPos[] absoluteHitRails = absolutePositions(helper, hitRails);
					BlockPos[] absoluteExtendedMissRails = absolutePositions(helper, extendedMissRails);
					try {
						RailHotPathBenchmarkAccess.forcePoweredState(helper.getLevel(), absoluteMissRails, true);
						RailHotPathBenchmarkAccess.forcePoweredState(helper.getLevel(), absoluteHitRails, true);
						RailHotPathBenchmarkAccess.forcePoweredState(helper.getLevel(), absoluteExtendedMissRails, true);

						RailLogic.setRailPowerLimit(8);
						var missProbe = RailHotPathBenchmarkAccess.coldSignalSearchProbe(
								helper.getLevel(), absoluteMissRails[0], false, 8, false);
						RailBenchmarkRunner.measureAndReportIsolated(
								helper, "revalidation powerLimit=8 full miss",
								INITIAL_SEARCH_OPERATIONS, missProbe::measure);

						var hitProbe = RailHotPathBenchmarkAccess.coldSignalSearchProbe(
								helper.getLevel(), absoluteHitRails[0], false, 8, true);
						RailBenchmarkRunner.measureAndReportIsolated(
								helper, "revalidation powerLimit=8 distance-limit hit",
								INITIAL_SEARCH_OPERATIONS, hitProbe::measure);

						RailLogic.setRailPowerLimit(64);
						var extendedMissProbe = RailHotPathBenchmarkAccess.coldSignalSearchProbe(
								helper.getLevel(), absoluteExtendedMissRails[0], false, 64, false);
						RailBenchmarkRunner.measureAndReportIsolated(
								helper, "revalidation powerLimit=64 full miss",
								INITIAL_SEARCH_OPERATIONS, extendedMissProbe::measure);

						var sameChunkProbe = RailHotPathBenchmarkAccess.neighborSignalProbe(
								helper.getLevel(), helper.absolutePos(sameChunkRail));
						RailBenchmarkRunner.measureAndReportIsolated(
								helper, "revalidation same-chunk direct-signal full scan",
								INITIAL_WORLD_READ_OPERATIONS, sameChunkProbe::measure);

						var chunkEdgeProbe = RailHotPathBenchmarkAccess.neighborSignalProbe(
								helper.getLevel(), helper.absolutePos(chunkEdgeRail));
						RailBenchmarkRunner.measureAndReportIsolated(
								helper, "revalidation chunk-edge direct-signal full scan",
								INITIAL_WORLD_READ_OPERATIONS, chunkEdgeProbe::measure);

						var directlyPoweredProbe = RailHotPathBenchmarkAccess.neighborSignalProbe(
								helper.getLevel(), helper.absolutePos(directlyPoweredRail));
						RailBenchmarkRunner.measureAndReportIsolated(
								helper, "revalidation directly-powered early hit",
								INITIAL_WORLD_READ_OPERATIONS, directlyPoweredProbe::measure);
					} finally {
						RailLogic.setRailPowerLimit(configuredPowerLimit);
					}
				})
				.thenSucceed();
	}

	@GameTest(environment = "railoptimization-gametest:serial_126", maxTicks = 200, padding = 80)
	public void preInvalidatedMemoEndToEndCostIsMeasured(GameTestHelper helper) {
		final int lineCount = 6;
		final int lineLength = 9;
		int startX = relativeCoordinateAtChunkLocal(helper, Direction.Axis.X, 0);
		int startZ = relativeCoordinateAtChunkLocal(helper, Direction.Axis.Z, 2);
		BlockPos[] targets = new BlockPos[lineCount];
		BlockPos[] allRails = new BlockPos[lineCount * lineLength];
		for (int line = 0; line < lineCount; line++) {
			BlockPos start = new BlockPos(startX, RAIL_Y, startZ + line * 2);
			placeRailLine(helper, start, Direction.EAST, lineLength, RailShape.EAST_WEST);
			helper.setBlock(start.below(), Blocks.REDSTONE_BLOCK);
			targets[line] = start.relative(Direction.EAST, lineLength - 1);
			for (int rail = 0; rail < lineLength; rail++) {
				allRails[line * lineLength + rail] = start.relative(Direction.EAST, rail);
			}
		}

		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					int configuredPowerLimit = RailLogicTestAccess.currentPowerLimit();
					RailLogic.setRailPowerLimit(8);
					try {
						RailHotPathBenchmarkAccess.forcePoweredState(
								helper.getLevel(), absolutePositions(helper, allRails), true);
						var probe = RailHotPathBenchmarkAccess.forcedMemoMissProbe(
								helper.getLevel(), absolutePositions(helper, targets));
						RailBenchmarkRunner.measureAndReportIsolated(
								helper, "pre-invalidated memo full unchanged rail decision",
								INITIAL_SEARCH_OPERATIONS, probe::measure);
					} finally {
						RailLogic.setRailPowerLimit(configuredPowerLimit);
					}
				})
				.thenSucceed();
	}

	private static void measureColdSignalSearch(GameTestHelper helper, BlockPos start, String label) {
		measureColdSignalSearch(helper, placePoweredSearchFixture(helper, start, 8), 8, false, label);
	}

	private static BlockPos[] placePoweredSearchFixture(GameTestHelper helper, BlockPos start, int powerLimit) {
		BlockPos[] rails = new BlockPos[powerLimit + 1];
		for (int index = 0; index < rails.length; index++) {
			rails[index] = start.relative(Direction.EAST, index);
		}
		placeRailLine(helper, start, Direction.EAST, rails.length, RailShape.EAST_WEST);
		return rails;
	}

	private static void measureColdSignalSearch(
			GameTestHelper helper, BlockPos[] rails, int powerLimit, boolean expectedFound, String label) {
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					int configuredPowerLimit = RailLogicTestAccess.currentPowerLimit();
					BlockPos[] absoluteRails = absolutePositions(helper, rails);
					RailLogic.setRailPowerLimit(powerLimit);
					try {
						RailHotPathBenchmarkAccess.forcePoweredState(
								helper.getLevel(), absoluteRails, true);
						var probe = RailHotPathBenchmarkAccess.coldSignalSearchProbe(
								helper.getLevel(), absoluteRails[0], false, powerLimit, expectedFound);
						RailBenchmarkRunner.measureAndReportIsolated(
								helper, label, INITIAL_SEARCH_OPERATIONS, probe::measure);
					} finally {
						RailLogic.setRailPowerLimit(configuredPowerLimit);
					}
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	private static void measureNeighborSignal(GameTestHelper helper, BlockPos rail, String label) {
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					var probe = RailHotPathBenchmarkAccess.neighborSignalProbe(
							helper.getLevel(), helper.absolutePos(rail));
					RailBenchmarkRunner.measureAndReportIsolated(
							helper, label, INITIAL_WORLD_READ_OPERATIONS, probe::measure);
				})
				.thenSucceed();
	}

	private static void runAfterSetup(
			GameTestHelper helper, String label, int initialOperations,
			java.util.function.IntToLongFunction measurement) {
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> RailBenchmarkRunner.measureAndReportIsolated(
						helper, label, initialOperations, measurement))
				.thenSucceed();
	}

	@SuppressWarnings("null")
	private static BlockPos[] absolutePositions(GameTestHelper helper, BlockPos[] relativePositions) {
		BlockPos[] absolutePositions = new BlockPos[relativePositions.length];
		for (int index = 0; index < relativePositions.length; index++) {
			absolutePositions[index] = helper.absolutePos(relativePositions[index]);
		}
		return absolutePositions;
	}

	private static BlockPos chunkAlignedPos(GameTestHelper helper, int localX, int localZ) {
		return new BlockPos(
				relativeCoordinateAtChunkLocal(helper, Direction.Axis.X, localX),
				RAIL_Y,
				relativeCoordinateAtChunkLocal(helper, Direction.Axis.Z, localZ));
	}

	private static int relativeCoordinateAtChunkLocal(
			GameTestHelper helper, Direction.Axis axis, int desiredLocalCoordinate) {
		BlockPos absoluteOrigin = helper.absolutePos(new BlockPos(0, RAIL_Y, 0));
		int absoluteCoordinate = axis == Direction.Axis.X ? absoluteOrigin.getX() : absoluteOrigin.getZ();
		int relativeCoordinate = Math.floorMod(desiredLocalCoordinate - absoluteCoordinate, 16);
		return relativeCoordinate < 3 ? relativeCoordinate + 16 : relativeCoordinate;
	}
}
