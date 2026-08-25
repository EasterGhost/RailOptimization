package RailOptimization.gametest;

import RailOptimization.RailLogicTestAccess;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.slf4j.Logger;

public class RailOptimizationPerformanceGameTest extends RailOptimizationGameTestSupport {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int LINE_LENGTH = 17;
	private static final int CENTER_INDEX = LINE_LENGTH / 2;
	private static final int CONTROL_Z_OFFSET = 12;
	private static final int STATE_CHANGE_WARMUP_OPERATIONS = 1_000;
	private static final int UNCHANGED_UPDATE_WARMUP_OPERATIONS = 100_000;
	private static final int INDIRECT_UPDATE_WARMUP_OPERATIONS = 50_000;
	private static final int STATE_CHANGE_STABILIZATION_OPERATIONS = 200;
	private static final int EXTENDED_STABILIZATION_OPERATIONS = 5_000;
	private static final int UNCHANGED_UPDATE_STABILIZATION_OPERATIONS = 30_000;
	private static final int INDIRECT_UPDATE_STABILIZATION_OPERATIONS = 10_000;
	private static final int TOGGLES_PER_ROUND = 2_000;
	private static final int UNCHANGED_UPDATES_PER_ROUND = 300_000;
	private static final int INDIRECT_UPDATES_PER_ROUND = 200_000;
	private static final int EXTENDED_POWER_LIMIT = 64;
	private static final int EXTENDED_TOGGLES_PER_ROUND = 160;
	private static final double MAX_STATE_CHANGE_TIME_RATIO = 0.95;
	private static final double MAX_COMPLEX_STATE_CHANGE_TIME_RATIO = 1.05;
	private static final double MAX_SHALLOW_UNCHANGED_UPDATE_TIME_RATIO = 1.10;
	private static final double MAX_INDIRECT_UNCHANGED_UPDATE_TIME_RATIO = 1.20;

	@GameTest(environment = "railoptimization-gametest:serial_58", maxTicks = 200, padding = 50)
	public void straightRailUpdatesDoNotRegressAgainstVanilla(GameTestHelper helper) {
		BlockPos optimizedStart = new BlockPos(2, RAIL_Y, 3);
		BlockPos[] optimizedRails = straightRails(optimizedStart);
		BlockPos optimizedCenter = optimizedStart.relative(Direction.EAST, CENTER_INDEX);
		BlockPos optimizedLever = optimizedCenter.north();
		BlockPos optimizedIndirectRail = optimizedStart;
		BlockPos vanillaCenter = controlCopy(optimizedCenter);
		BlockPos vanillaLever = controlCopy(optimizedLever);
		BlockPos vanillaIndirectRail = controlCopy(optimizedIndirectRail);

		placePerformanceRailLinePair(
				helper, optimizedStart, Direction.EAST, LINE_LENGTH, RailShape.EAST_WEST);
		placeLever(helper, optimizedLever);
		placeLever(helper, vanillaLever);

		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					verifyLeverPropagation(helper, vanillaLever, optimizedLever, optimizedRails);
					RailBenchmarkRunner.warmUpLeverToggles(
							helper, vanillaLever, optimizedLever, STATE_CHANGE_WARMUP_OPERATIONS);
					RailBenchmarkRunner.warmUpUnchangedUpdates(
							helper, vanillaCenter, optimizedCenter, UNCHANGED_UPDATE_WARMUP_OPERATIONS);
					setLeverPairPowered(helper, vanillaLever, optimizedLever, true);
					RailBenchmarkRunner.warmUpUnchangedUpdates(
							helper, vanillaCenter, optimizedCenter, UNCHANGED_UPDATE_WARMUP_OPERATIONS);
					RailBenchmarkRunner.warmUpUnchangedUpdates(
							helper, vanillaIndirectRail, optimizedIndirectRail,
							INDIRECT_UPDATE_WARMUP_OPERATIONS);
					setLeverPairPowered(helper, vanillaLever, optimizedLever, false);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					RailBenchmarkRunner.warmUpLeverToggles(
							helper, vanillaLever, optimizedLever, STATE_CHANGE_STABILIZATION_OPERATIONS);
					RailBenchmarkRunner.warmUpUnchangedUpdates(
							helper, vanillaCenter, optimizedCenter, UNCHANGED_UPDATE_STABILIZATION_OPERATIONS);
					setLeverPairPowered(helper, vanillaLever, optimizedLever, true);
					RailBenchmarkRunner.warmUpUnchangedUpdates(
							helper, vanillaCenter, optimizedCenter, UNCHANGED_UPDATE_STABILIZATION_OPERATIONS);
					RailBenchmarkRunner.warmUpUnchangedUpdates(
							helper, vanillaIndirectRail, optimizedIndirectRail,
							INDIRECT_UPDATE_STABILIZATION_OPERATIONS);
					setLeverPairPowered(helper, vanillaLever, optimizedLever, false);
				})
				.thenIdle(2)
				.thenExecute(() -> {
					RailBenchmarkRunner.BenchmarkResult stateChanges = RailBenchmarkRunner.benchmarkAlternating(
							() -> RailBenchmarkRunner.measureLeverToggles(helper, vanillaLever, TOGGLES_PER_ROUND),
							() -> RailBenchmarkRunner.measureLeverToggles(helper, optimizedLever, TOGGLES_PER_ROUND)
					);
					RailBenchmarkRunner.reportAndAssert(
							helper,
							"straight state-changing lever toggles",
							TOGGLES_PER_ROUND,
							MAX_STATE_CHANGE_TIME_RATIO,
							stateChanges
					);

					assertLeverOff(helper, vanillaLever);
					assertLeverOff(helper, optimizedLever);
					assertRailsMatchAndAreOff(helper, optimizedRails);

					RailBenchmarkRunner.BenchmarkResult unchangedUpdates = RailBenchmarkRunner.benchmarkAlternating(
							() -> RailBenchmarkRunner.measureUnchangedUpdates(helper, vanillaCenter, UNCHANGED_UPDATES_PER_ROUND),
							() -> RailBenchmarkRunner.measureUnchangedUpdates(helper, optimizedCenter, UNCHANGED_UPDATES_PER_ROUND)
					);
					RailBenchmarkRunner.reportAndAssert(
							helper,
							"unpowered unchanged neighbor updates",
							UNCHANGED_UPDATES_PER_ROUND,
							MAX_SHALLOW_UNCHANGED_UPDATE_TIME_RATIO,
							unchangedUpdates
					);

					setLeverPairPowered(helper, vanillaLever, optimizedLever, true);
					assertMatchingRailPower(helper, controlCopy(optimizedRails), optimizedRails);
					assertRailsPowered(helper, optimizedRails, true);

					RailBenchmarkRunner.BenchmarkResult directlyPoweredUpdates = RailBenchmarkRunner.benchmarkAlternating(
							() -> RailBenchmarkRunner.measureUnchangedUpdates(helper, vanillaCenter, UNCHANGED_UPDATES_PER_ROUND),
							() -> RailBenchmarkRunner.measureUnchangedUpdates(helper, optimizedCenter, UNCHANGED_UPDATES_PER_ROUND)
					);
					RailBenchmarkRunner.reportAndAssert(
							helper,
							"directly-powered unchanged neighbor updates",
							UNCHANGED_UPDATES_PER_ROUND,
							MAX_SHALLOW_UNCHANGED_UPDATE_TIME_RATIO,
							directlyPoweredUpdates
					);

					RailBenchmarkRunner.BenchmarkResult indirectlyPoweredUpdates = RailBenchmarkRunner.benchmarkAlternating(
							() -> RailBenchmarkRunner.measureUnchangedUpdates(
									helper, vanillaIndirectRail, INDIRECT_UPDATES_PER_ROUND),
							() -> RailBenchmarkRunner.measureUnchangedUpdates(
									helper, optimizedIndirectRail, INDIRECT_UPDATES_PER_ROUND)
					);
					RailBenchmarkRunner.reportAndAssert(
							helper,
							"distance-8 indirectly-powered unchanged neighbor updates",
							INDIRECT_UPDATES_PER_ROUND,
							MAX_INDIRECT_UNCHANGED_UPDATE_TIME_RATIO,
							indirectlyPoweredUpdates
					);

					setLeverPairPowered(helper, vanillaLever, optimizedLever, false);
					assertRailsMatchAndAreOff(helper, optimizedRails);
				})
				.thenSucceed();
	}

	@GameTest(environment = "railoptimization-gametest:serial_59", maxTicks = 200, padding = 50)
	public void mixedSlopeRailUpdatesDoNotRegressAgainstVanilla(GameTestHelper helper) {
		BlockPos[] optimizedRails = mixedSlopeRails();
		RailShape[] shapes = mixedSlopeShapes();
		BlockPos optimizedLever = optimizedRails[CENTER_INDEX].north();
		BlockPos vanillaLever = controlCopy(optimizedLever);

		placePerformanceRailPathPair(helper, optimizedRails, shapes);
		placeLever(helper, optimizedLever);
		placeLever(helper, vanillaLever);

		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					verifyLeverPropagation(helper, vanillaLever, optimizedLever, optimizedRails);
					RailBenchmarkRunner.warmUpLeverToggles(
							helper, vanillaLever, optimizedLever, STATE_CHANGE_WARMUP_OPERATIONS);
				})
				.thenIdle(4)
				.thenExecute(() -> RailBenchmarkRunner.warmUpLeverToggles(
						helper, vanillaLever, optimizedLever, STATE_CHANGE_STABILIZATION_OPERATIONS))
				.thenIdle(2)
				.thenExecute(() -> {
					RailBenchmarkRunner.BenchmarkResult stateChanges = RailBenchmarkRunner.benchmarkAlternating(
							() -> RailBenchmarkRunner.measureLeverToggles(helper, vanillaLever, TOGGLES_PER_ROUND),
							() -> RailBenchmarkRunner.measureLeverToggles(helper, optimizedLever, TOGGLES_PER_ROUND)
					);
					RailBenchmarkRunner.reportAndAssert(
							helper,
							"mixed-slope state-changing lever toggles",
							TOGGLES_PER_ROUND,
							MAX_COMPLEX_STATE_CHANGE_TIME_RATIO,
							stateChanges
					);

					assertLeverOff(helper, vanillaLever);
					assertLeverOff(helper, optimizedLever);
					assertRailsMatchAndAreOff(helper, optimizedRails);
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_61", maxTicks = 200, padding = 150)
	public void extendedRangeStraightRailUpdateCostIsMeasured(GameTestHelper helper) {
		int lineLength = EXTENDED_POWER_LIMIT * 2 + 1;
		BlockPos start = new BlockPos(2, RAIL_Y, 3);
		BlockPos[] rails = straightRails(start, lineLength);
		BlockPos center = rails[EXTENDED_POWER_LIMIT];
		BlockPos lever = center.north();

		placeRailLine(helper, start, Direction.EAST, lineLength, RailShape.EAST_WEST);
		for (BlockPos rail : rails) {
			RailLogicTestAccess.forcePowerLimitAt(helper.absolutePos(rail), EXTENDED_POWER_LIMIT);
		}
		placeLever(helper, lever);

		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> RailBenchmarkRunner.runLeverToggles(helper, lever, STATE_CHANGE_WARMUP_OPERATIONS))
				.thenIdle(4)
				.thenExecute(() -> RailBenchmarkRunner.runLeverToggles(
						helper, lever, EXTENDED_STABILIZATION_OPERATIONS))
				.thenIdle(2)
				.thenExecute(() -> {
					RailBenchmarkRunner.SampleStats stats = RailBenchmarkRunner.sample(
							() -> RailBenchmarkRunner.measureLeverToggles(helper, lever, EXTENDED_TOGGLES_PER_ROUND));
					long nanosPerOperation = stats.medianNanos() / EXTENDED_TOGGLES_PER_ROUND;
					LOGGER.info(
							"RailOptimization benchmark [powerLimit={} straight state changes]: "
									+ "optimized={} ns/op (MAD={}%), {} ns/changed-rail, {} allocated bytes/op",
							EXTENDED_POWER_LIMIT,
							nanosPerOperation,
							stats.relativeMedianAbsoluteDeviation(),
							nanosPerOperation / lineLength,
							RailBenchmarkRunner.allocatedBytesPerOperation(
									helper, lever, EXTENDED_TOGGLES_PER_ROUND)
					);

					assertLeverOff(helper, lever);
					assertRailsPowered(helper, rails, false);
				})
				.thenSucceed();
	}

	@GameTest(environment = "railoptimization-gametest:serial_114", maxTicks = 200, padding = 50)
	public void northSouthStraightRailUpdateCostIsMeasured(GameTestHelper helper) {
		measureNorthSouthStraightRailUpdateCost(helper, 8, TOGGLES_PER_ROUND);
	}

	@GameTest(environment = "railoptimization-gametest:serial_115", maxTicks = 200, padding = 150)
	public void extendedRangeNorthSouthStraightRailUpdateCostIsMeasured(GameTestHelper helper) {
		measureNorthSouthStraightRailUpdateCost(helper, EXTENDED_POWER_LIMIT, EXTENDED_TOGGLES_PER_ROUND);
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_65", maxTicks = 200, padding = 150)
	public void extendedRangeMixedSlopeUpdateCostIsMeasured(GameTestHelper helper) {
		int lineLength = EXTENDED_POWER_LIMIT * 2 + 1;
		BlockPos[] rails = extendedMixedSlopeRails(lineLength);
		RailShape[] shapes = extendedMixedSlopeShapes(lineLength);
		BlockPos center = rails[EXTENDED_POWER_LIMIT];
		BlockPos lever = center.north();

		placeRailPath(helper, rails, shapes);
		for (BlockPos rail : rails) {
			RailLogicTestAccess.forcePowerLimitAt(helper.absolutePos(rail), EXTENDED_POWER_LIMIT);
		}
		placeLever(helper, lever);

		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> RailBenchmarkRunner.runLeverToggles(helper, lever, STATE_CHANGE_WARMUP_OPERATIONS))
				.thenIdle(4)
				.thenExecute(() -> RailBenchmarkRunner.runLeverToggles(
						helper, lever, EXTENDED_STABILIZATION_OPERATIONS))
				.thenIdle(2)
				.thenExecute(() -> {
					RailBenchmarkRunner.SampleStats stats = RailBenchmarkRunner.sample(
							() -> RailBenchmarkRunner.measureLeverToggles(helper, lever, EXTENDED_TOGGLES_PER_ROUND));
					long nanosPerOperation = stats.medianNanos() / EXTENDED_TOGGLES_PER_ROUND;
					LOGGER.info(
							"RailOptimization benchmark [powerLimit={} mixed-slope state changes]: "
									+ "optimized={} ns/op (MAD={}%), {} ns/changed-rail, {} allocated bytes/op",
							EXTENDED_POWER_LIMIT,
							nanosPerOperation,
							stats.relativeMedianAbsoluteDeviation(),
							nanosPerOperation / lineLength,
							RailBenchmarkRunner.allocatedBytesPerOperation(
									helper, lever, EXTENDED_TOGGLES_PER_ROUND)
					);

					assertLeverOff(helper, lever);
					assertRailsPowered(helper, rails, false);
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	private static void verifyLeverPropagation(
			GameTestHelper helper, BlockPos vanillaLever, BlockPos optimizedLever,
			BlockPos[] optimizedRails) {
		helper.pullLever(vanillaLever);
		helper.pullLever(optimizedLever);
		assertMatchingRailPower(helper, controlCopy(optimizedRails), optimizedRails);
		assertRailsPowered(helper, optimizedRails, true);

		helper.pullLever(vanillaLever);
		helper.pullLever(optimizedLever);
		assertLeverOff(helper, vanillaLever);
		assertLeverOff(helper, optimizedLever);
		assertRailsMatchAndAreOff(helper, optimizedRails);
	}

	@SuppressWarnings("null")
	private static void setLeverPairPowered(
			GameTestHelper helper, BlockPos vanillaLever, BlockPos optimizedLever, boolean powered) {
		if (helper.getBlockState(vanillaLever).getValue(LeverBlock.POWERED) != powered) {
			helper.pullLever(vanillaLever);
		}
		if (helper.getBlockState(optimizedLever).getValue(LeverBlock.POWERED) != powered) {
			helper.pullLever(optimizedLever);
		}
	}

	@SuppressWarnings("null")
	private static void placeLever(GameTestHelper helper, BlockPos leverPos) {
		helper.setBlock(leverPos.below(), Blocks.STONE);
		helper.setBlock(leverPos, Blocks.LEVER.defaultBlockState()
				.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
				.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
				.setValue(LeverBlock.POWERED, false));
	}

	@SuppressWarnings("null")
	private static void assertLeverOff(GameTestHelper helper, BlockPos lever) {
		helper.assertBlockProperty(lever, LeverBlock.POWERED, false);
	}

	private static void assertRailsMatchAndAreOff(GameTestHelper helper, BlockPos[] optimizedRails) {
		assertMatchingRailPower(helper, controlCopy(optimizedRails), optimizedRails);
		assertRailsPowered(helper, optimizedRails, false);
	}

	@SuppressWarnings("null")
	private static void measureNorthSouthStraightRailUpdateCost(GameTestHelper helper, int powerLimit, int togglesPerRound) {
		int lineLength = powerLimit * 2 + 1;
		BlockPos start = new BlockPos(2, RAIL_Y, 3);
		BlockPos[] rails = straightRails(start, Direction.SOUTH, lineLength);
		BlockPos center = rails[powerLimit];
		BlockPos lever = center.east();

		placeRailLine(helper, start, Direction.SOUTH, lineLength, RailShape.NORTH_SOUTH);
		for (BlockPos rail : rails) {
			RailLogicTestAccess.forcePowerLimitAt(helper.absolutePos(rail), powerLimit);
		}
		placeLever(helper, lever);

		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> RailBenchmarkRunner.runLeverToggles(helper, lever, STATE_CHANGE_WARMUP_OPERATIONS))
				.thenIdle(4)
				.thenExecute(() -> RailBenchmarkRunner.runLeverToggles(
						helper, lever, powerLimit == EXTENDED_POWER_LIMIT
								? EXTENDED_STABILIZATION_OPERATIONS
								: STATE_CHANGE_STABILIZATION_OPERATIONS))
				.thenIdle(2)
				.thenExecute(() -> {
					RailBenchmarkRunner.SampleStats stats = RailBenchmarkRunner.sample(
							() -> RailBenchmarkRunner.measureLeverToggles(helper, lever, togglesPerRound));
					long nanosPerOperation = stats.medianNanos() / togglesPerRound;
					LOGGER.info(
							"RailOptimization benchmark [powerLimit={} north-south straight state changes]: "
									+ "optimized={} ns/op (MAD={}%), {} ns/changed-rail, {} allocated bytes/op",
							powerLimit,
							nanosPerOperation,
							stats.relativeMedianAbsoluteDeviation(),
							nanosPerOperation / lineLength,
							RailBenchmarkRunner.allocatedBytesPerOperation(helper, lever, togglesPerRound)
					);

					assertLeverOff(helper, lever);
					assertRailsPowered(helper, rails, false);
				})
				.thenSucceed();
	}

	private static BlockPos controlCopy(BlockPos pos) {
		return pos.relative(Direction.SOUTH, CONTROL_Z_OFFSET);
	}

	private static BlockPos[] controlCopy(BlockPos[] positions) {
		BlockPos[] copy = new BlockPos[positions.length];
		for (int index = 0; index < positions.length; index++) {
			copy[index] = controlCopy(positions[index]);
		}
		return copy;
	}

	private static BlockPos[] straightRails(BlockPos start, int length) {
		return straightRails(start, Direction.EAST, length);
	}

	@SuppressWarnings("null")
	private static BlockPos[] straightRails(BlockPos start, Direction direction, int length) {
		BlockPos[] rails = new BlockPos[length];
		for (int index = 0; index < length; index++) {
			rails[index] = start.relative(direction, index);
		}
		return rails;
	}

	@SuppressWarnings("null")
	private static void placePerformanceRailLinePair(
			GameTestHelper helper, BlockPos start, Direction direction, int length, RailShape shape) {
		placeRailLine(helper, start, direction, length, shape);
		for (int step = 0; step < length; step++) {
			BlockPos controlRail = controlCopy(start.relative(direction, step));
			markControlRail(helper, controlRail);
			placeRail(helper, controlRail, shape);
		}
	}

	private static void placePerformanceRailPathPair(
			GameTestHelper helper, BlockPos[] rails, RailShape[] shapes) {
		placeRailPath(helper, rails, shapes);
		for (int index = 0; index < rails.length; index++) {
			BlockPos controlRail = controlCopy(rails[index]);
			markControlRail(helper, controlRail);
			placeRail(helper, controlRail, shapes[index]);
		}
	}

	@SuppressWarnings("null")
	private static void markControlRail(GameTestHelper helper, BlockPos rail) {
		RailLogicTestAccess.forceVanillaAt(helper.absolutePos(rail));
	}

	private static BlockPos[] straightRails(BlockPos start) {
		BlockPos[] rails = new BlockPos[LINE_LENGTH];
		for (int index = 0; index < LINE_LENGTH; index++) {
			rails[index] = start.relative(Direction.EAST, index);
		}
		return rails;
	}

	private static BlockPos[] mixedSlopeRails() {
		int[] heightOffsets = new int[] { 3, 2, 1, 0, 0, 1, 2, 3, 3, 2, 1, 0, 0, 1, 2, 3, 3 };
		BlockPos[] rails = new BlockPos[heightOffsets.length];
		for (int index = 0; index < heightOffsets.length; index++) {
			rails[index] = new BlockPos(2 + index, RAIL_Y + heightOffsets[index], 3);
		}
		return rails;
	}

	private static RailShape[] mixedSlopeShapes() {
		return new RailShape[] {
				RailShape.EAST_WEST,
				RailShape.ASCENDING_WEST,
				RailShape.ASCENDING_WEST,
				RailShape.ASCENDING_WEST,
				RailShape.ASCENDING_EAST,
				RailShape.ASCENDING_EAST,
				RailShape.ASCENDING_EAST,
				RailShape.EAST_WEST,
				RailShape.EAST_WEST,
				RailShape.ASCENDING_WEST,
				RailShape.ASCENDING_WEST,
				RailShape.ASCENDING_WEST,
				RailShape.ASCENDING_EAST,
				RailShape.ASCENDING_EAST,
				RailShape.ASCENDING_EAST,
				RailShape.EAST_WEST,
				RailShape.EAST_WEST
		};
	}

	private static BlockPos[] extendedMixedSlopeRails(int length) {
		int[] heightOffsets = new int[] { 3, 2, 1, 0, 0, 1, 2, 3 };
		BlockPos[] rails = new BlockPos[length];
		for (int index = 0; index < length; index++) {
			rails[index] = new BlockPos(
					2 + index, RAIL_Y + heightOffsets[index & 7], 3);
		}
		return rails;
	}

	private static RailShape[] extendedMixedSlopeShapes(int length) {
		RailShape[] shapeCycle = new RailShape[] {
				RailShape.EAST_WEST,
				RailShape.ASCENDING_WEST,
				RailShape.ASCENDING_WEST,
				RailShape.ASCENDING_WEST,
				RailShape.ASCENDING_EAST,
				RailShape.ASCENDING_EAST,
				RailShape.ASCENDING_EAST,
				RailShape.EAST_WEST
		};
		RailShape[] shapes = new RailShape[length];
		for (int index = 0; index < length; index++) {
			shapes[index] = shapeCycle[index & 7];
		}
		return shapes;
	}
}
