package RailOptimization.gametest;

import RailOptimization.RailLogicTestAccess;
import com.mojang.logging.LogUtils;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.LongSupplier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
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
	private static final int UNCHANGED_UPDATE_STABILIZATION_OPERATIONS = 30_000;
	private static final int INDIRECT_UPDATE_STABILIZATION_OPERATIONS = 10_000;
	private static final int MEASUREMENT_ROUNDS = 11;
	private static final int TOGGLES_PER_ROUND = 2_000;
	private static final int UNCHANGED_UPDATES_PER_ROUND = 300_000;
	private static final int INDIRECT_UPDATES_PER_ROUND = 200_000;
	private static final int EXTENDED_POWER_LIMIT = 64;
	private static final int EXTENDED_TOGGLES_PER_ROUND = 80;
	private static final long MIN_MEDIAN_SAMPLE_NANOS = 20_000_000L;
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
					warmUpLeverToggles(
							helper, vanillaLever, optimizedLever, STATE_CHANGE_WARMUP_OPERATIONS);
					warmUpUnchangedUpdates(
							helper, vanillaCenter, optimizedCenter, UNCHANGED_UPDATE_WARMUP_OPERATIONS);
					setLeverPairPowered(helper, vanillaLever, optimizedLever, true);
					warmUpUnchangedUpdates(
							helper, vanillaCenter, optimizedCenter, UNCHANGED_UPDATE_WARMUP_OPERATIONS);
					warmUpUnchangedUpdates(
							helper, vanillaIndirectRail, optimizedIndirectRail,
							INDIRECT_UPDATE_WARMUP_OPERATIONS);
					setLeverPairPowered(helper, vanillaLever, optimizedLever, false);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					warmUpLeverToggles(
							helper, vanillaLever, optimizedLever, STATE_CHANGE_STABILIZATION_OPERATIONS);
					warmUpUnchangedUpdates(
							helper, vanillaCenter, optimizedCenter, UNCHANGED_UPDATE_STABILIZATION_OPERATIONS);
					setLeverPairPowered(helper, vanillaLever, optimizedLever, true);
					warmUpUnchangedUpdates(
							helper, vanillaCenter, optimizedCenter, UNCHANGED_UPDATE_STABILIZATION_OPERATIONS);
					warmUpUnchangedUpdates(
							helper, vanillaIndirectRail, optimizedIndirectRail,
							INDIRECT_UPDATE_STABILIZATION_OPERATIONS);
					setLeverPairPowered(helper, vanillaLever, optimizedLever, false);
				})
				.thenIdle(2)
				.thenExecute(() -> {
					BenchmarkResult stateChanges = benchmarkAlternating(
							() -> measureLeverToggles(helper, vanillaLever, TOGGLES_PER_ROUND),
							() -> measureLeverToggles(helper, optimizedLever, TOGGLES_PER_ROUND)
					);
					reportAndAssert(
							helper,
							"straight state-changing lever toggles",
							TOGGLES_PER_ROUND,
							MAX_STATE_CHANGE_TIME_RATIO,
							stateChanges
					);

					assertLeverOff(helper, vanillaLever);
					assertLeverOff(helper, optimizedLever);
					assertRailsMatchAndAreOff(helper, optimizedRails);

					BenchmarkResult unchangedUpdates = benchmarkAlternating(
							() -> measureUnchangedUpdates(helper, vanillaCenter, UNCHANGED_UPDATES_PER_ROUND),
							() -> measureUnchangedUpdates(helper, optimizedCenter, UNCHANGED_UPDATES_PER_ROUND)
					);
					reportAndAssert(
							helper,
							"unpowered unchanged neighbor updates",
							UNCHANGED_UPDATES_PER_ROUND,
							MAX_SHALLOW_UNCHANGED_UPDATE_TIME_RATIO,
							unchangedUpdates
					);

					setLeverPairPowered(helper, vanillaLever, optimizedLever, true);
					assertMatchingRailPower(helper, controlCopy(optimizedRails), optimizedRails);
					assertRailsPowered(helper, optimizedRails, true);

					BenchmarkResult directlyPoweredUpdates = benchmarkAlternating(
							() -> measureUnchangedUpdates(helper, vanillaCenter, UNCHANGED_UPDATES_PER_ROUND),
							() -> measureUnchangedUpdates(helper, optimizedCenter, UNCHANGED_UPDATES_PER_ROUND)
					);
					reportAndAssert(
							helper,
							"directly-powered unchanged neighbor updates",
							UNCHANGED_UPDATES_PER_ROUND,
							MAX_SHALLOW_UNCHANGED_UPDATE_TIME_RATIO,
							directlyPoweredUpdates
					);

					BenchmarkResult indirectlyPoweredUpdates = benchmarkAlternating(
							() -> measureUnchangedUpdates(
									helper, vanillaIndirectRail, INDIRECT_UPDATES_PER_ROUND),
							() -> measureUnchangedUpdates(
									helper, optimizedIndirectRail, INDIRECT_UPDATES_PER_ROUND)
					);
					reportAndAssert(
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
					warmUpLeverToggles(
							helper, vanillaLever, optimizedLever, STATE_CHANGE_WARMUP_OPERATIONS);
				})
				.thenIdle(4)
				.thenExecute(() -> warmUpLeverToggles(
						helper, vanillaLever, optimizedLever, STATE_CHANGE_STABILIZATION_OPERATIONS))
				.thenIdle(2)
				.thenExecute(() -> {
					BenchmarkResult stateChanges = benchmarkAlternating(
							() -> measureLeverToggles(helper, vanillaLever, TOGGLES_PER_ROUND),
							() -> measureLeverToggles(helper, optimizedLever, TOGGLES_PER_ROUND)
					);
					reportAndAssert(
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
				.thenExecute(() -> runLeverToggles(helper, lever, STATE_CHANGE_WARMUP_OPERATIONS))
				.thenIdle(4)
				.thenExecute(() -> runLeverToggles(
						helper, lever, STATE_CHANGE_STABILIZATION_OPERATIONS))
				.thenIdle(2)
				.thenExecute(() -> {
					long[] samples = new long[MEASUREMENT_ROUNDS];
					for (int round = 0; round < MEASUREMENT_ROUNDS; round++) {
						samples[round] = measureLeverToggles(
								helper, lever, EXTENDED_TOGGLES_PER_ROUND);
					}

					SampleStats stats = sampleStats(samples);
					long nanosPerOperation = stats.medianNanos() / EXTENDED_TOGGLES_PER_ROUND;
					LOGGER.info(
							"RailOptimization benchmark [powerLimit={} straight state changes]: "
									+ "optimized={} ns/op (MAD={}%), {} ns/changed-rail, {} allocated bytes/op",
							EXTENDED_POWER_LIMIT,
							nanosPerOperation,
							stats.relativeMedianAbsoluteDeviation(),
							nanosPerOperation / lineLength,
							allocatedBytesPerOperation(
									helper, lever, EXTENDED_TOGGLES_PER_ROUND)
					);

					assertLeverOff(helper, lever);
					assertRailsPowered(helper, rails, false);
				})
				.thenSucceed();
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
				.thenExecute(() -> runLeverToggles(helper, lever, STATE_CHANGE_WARMUP_OPERATIONS))
				.thenIdle(4)
				.thenExecute(() -> runLeverToggles(
						helper, lever, STATE_CHANGE_STABILIZATION_OPERATIONS))
				.thenIdle(2)
				.thenExecute(() -> {
					long[] samples = new long[MEASUREMENT_ROUNDS];
					for (int round = 0; round < MEASUREMENT_ROUNDS; round++) {
						samples[round] = measureLeverToggles(
								helper, lever, EXTENDED_TOGGLES_PER_ROUND);
					}

					SampleStats stats = sampleStats(samples);
					long nanosPerOperation = stats.medianNanos() / EXTENDED_TOGGLES_PER_ROUND;
					LOGGER.info(
							"RailOptimization benchmark [powerLimit={} mixed-slope state changes]: "
									+ "optimized={} ns/op (MAD={}%), {} ns/changed-rail, {} allocated bytes/op",
							EXTENDED_POWER_LIMIT,
							nanosPerOperation,
							stats.relativeMedianAbsoluteDeviation(),
							nanosPerOperation / lineLength,
							allocatedBytesPerOperation(
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

	private static void warmUpLeverToggles(
			GameTestHelper helper, BlockPos vanillaLever, BlockPos optimizedLever, int operations) {
		runLeverToggles(helper, vanillaLever, operations);
		runLeverToggles(helper, optimizedLever, operations);
		runLeverToggles(helper, optimizedLever, operations);
		runLeverToggles(helper, vanillaLever, operations);
	}

	private static long measureLeverToggles(GameTestHelper helper, BlockPos lever, int operations) {
		long startNanos = System.nanoTime();
		runLeverToggles(helper, lever, operations);
		return System.nanoTime() - startNanos;
	}

	@SuppressWarnings("null")
	private static void runLeverToggles(GameTestHelper helper, BlockPos lever, int operations) {
		for (int operation = 0; operation < operations; operation++) {
			helper.pullLever(lever);
		}
	}

	private static void warmUpUnchangedUpdates(
			GameTestHelper helper, BlockPos vanillaRail, BlockPos optimizedRail, int operations) {
		runUnchangedUpdates(helper, vanillaRail, operations);
		runUnchangedUpdates(helper, optimizedRail, operations);
		runUnchangedUpdates(helper, optimizedRail, operations);
		runUnchangedUpdates(helper, vanillaRail, operations);
	}

	private static long measureUnchangedUpdates(GameTestHelper helper, BlockPos rail, int operations) {
		long startNanos = System.nanoTime();
		runUnchangedUpdates(helper, rail, operations);
		return System.nanoTime() - startNanos;
	}

	@SuppressWarnings("null")
	private static void runUnchangedUpdates(GameTestHelper helper, BlockPos rail, int operations) {
		BlockPos absoluteRail = helper.absolutePos(rail);
		for (int operation = 0; operation < operations; operation++) {
			helper.getLevel().neighborChanged(absoluteRail, Blocks.STONE, null);
		}
	}

	private static BenchmarkResult benchmarkAlternating(
			LongSupplier vanillaSample, LongSupplier optimizedSample) {
		long[] vanillaSamples = new long[MEASUREMENT_ROUNDS];
		long[] optimizedSamples = new long[MEASUREMENT_ROUNDS];

		for (int round = 0; round < MEASUREMENT_ROUNDS; round++) {
			if ((round & 1) == 0) {
				vanillaSamples[round] = vanillaSample.getAsLong();
				optimizedSamples[round] = optimizedSample.getAsLong();
			} else {
				optimizedSamples[round] = optimizedSample.getAsLong();
				vanillaSamples[round] = vanillaSample.getAsLong();
			}
		}

		return new BenchmarkResult(sampleStats(vanillaSamples), sampleStats(optimizedSamples));
	}

	private static SampleStats sampleStats(long[] samples) {
		Arrays.sort(samples);
		long median = samples[samples.length / 2];
		long[] deviations = new long[samples.length];
		for (int i = 0; i < samples.length; i++) {
			deviations[i] = Math.abs(samples[i] - median);
		}
		Arrays.sort(deviations);
		return new SampleStats(median, deviations[deviations.length / 2]);
	}

	private static void reportAndAssert(
			GameTestHelper helper, String label, int operationsPerRound,
			double maxOptimizedToVanillaRatio, BenchmarkResult result) {
		long vanillaNanosPerOperation = result.vanilla().medianNanos() / operationsPerRound;
		long optimizedNanosPerOperation = result.optimized().medianNanos() / operationsPerRound;
		String speedup = String.format(Locale.ROOT, "%.2f", result.speedup());
		String vanillaNoise = result.vanilla().relativeMedianAbsoluteDeviation();
		String optimizedNoise = result.optimized().relativeMedianAbsoluteDeviation();

		LOGGER.info(
				"RailOptimization benchmark [{}]: vanilla={} ns/op (MAD={}%), "
						+ "optimized={} ns/op (MAD={}%), speedup={}x",
				label,
				vanillaNanosPerOperation,
				vanillaNoise,
				optimizedNanosPerOperation,
				optimizedNoise,
				speedup
		);
		helper.assertTrue(
				Math.min(result.vanilla().medianNanos(), result.optimized().medianNanos())
						>= MIN_MEDIAN_SAMPLE_NANOS,
				Component.literal(
						label + " sample is too short for reliable timing: vanilla="
								+ result.vanilla().medianNanos() / 1_000_000 + " ms, optimized="
								+ result.optimized().medianNanos() / 1_000_000 + " ms"
				)
		);
		helper.assertTrue(
				result.optimized().medianNanos()
						<= result.vanilla().medianNanos() * maxOptimizedToVanillaRatio,
				Component.literal(
						label + " regressed: vanilla=" + vanillaNanosPerOperation
								+ " ns/op, optimized=" + optimizedNanosPerOperation
								+ " ns/op, speedup=" + speedup + "x"
				)
		);
	}

	@SuppressWarnings("null")
	private static void assertLeverOff(GameTestHelper helper, BlockPos lever) {
		helper.assertBlockProperty(lever, LeverBlock.POWERED, false);
	}

	private static void assertRailsMatchAndAreOff(GameTestHelper helper, BlockPos[] optimizedRails) {
		assertMatchingRailPower(helper, controlCopy(optimizedRails), optimizedRails);
		assertRailsPowered(helper, optimizedRails, false);
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
		BlockPos[] rails = new BlockPos[length];
		for (int index = 0; index < length; index++) {
			rails[index] = start.relative(Direction.EAST, index);
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

	private static String allocatedBytesPerOperation(
			GameTestHelper helper, BlockPos lever, int operations) {
		if (!(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean threadBean)
				|| !threadBean.isThreadAllocatedMemorySupported()) {
			return "unavailable";
		}
		if (!threadBean.isThreadAllocatedMemoryEnabled()) {
			threadBean.setThreadAllocatedMemoryEnabled(true);
		}
		long before = threadBean.getCurrentThreadAllocatedBytes();
		runLeverToggles(helper, lever, operations);
		long allocatedBytes = threadBean.getCurrentThreadAllocatedBytes() - before;
		return Long.toString(allocatedBytes / operations);
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

	private record BenchmarkResult(SampleStats vanilla, SampleStats optimized) {
		double speedup() {
			return (double) vanilla.medianNanos() / optimized.medianNanos();
		}
	}

	private record SampleStats(long medianNanos, long medianAbsoluteDeviationNanos) {
		String relativeMedianAbsoluteDeviation() {
			return String.format(
					Locale.ROOT, "%.2f", medianAbsoluteDeviationNanos * 100.0 / medianNanos);
		}
	}
}
