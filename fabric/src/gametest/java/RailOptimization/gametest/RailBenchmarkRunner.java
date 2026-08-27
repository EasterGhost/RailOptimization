package RailOptimization.gametest;

import RailOptimization.LevelEpochAccess;
import com.mojang.logging.LogUtils;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.IntToLongFunction;
import java.util.function.LongSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import org.slf4j.Logger;

final class RailBenchmarkRunner {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int MEASUREMENT_ROUNDS = 11;
	private static final long MIN_MEDIAN_SAMPLE_NANOS = 20_000_000L;
	private static final long TARGET_SAMPLE_NANOS = 30_000_000L;
	private static final int ISOLATED_WARMUP_ROUNDS = 4;
	private static final ThreadLocal<EpochMeasurement> LAST_EPOCH_MEASUREMENT =
			ThreadLocal.withInitial(EpochMeasurement::new);

	private static final class EpochMeasurement {
		boolean recorded;
		long before;
		long after;
		long minimumAdvance;

		void clear() {
			recorded = false;
		}

		void record(long before, long after, long minimumAdvance) {
			this.recorded = true;
			this.before = before;
			this.after = after;
			this.minimumAdvance = minimumAdvance;
		}
	}

	private RailBenchmarkRunner() {
	}

	static void warmUpLeverToggles(
			GameTestHelper helper, BlockPos vanillaLever, BlockPos optimizedLever, int operations) {
		runLeverToggles(helper, vanillaLever, operations);
		runLeverToggles(helper, optimizedLever, operations);
		runLeverToggles(helper, optimizedLever, operations);
		runLeverToggles(helper, vanillaLever, operations);
	}

	static long measureLeverToggles(GameTestHelper helper, BlockPos lever, int operations) {
		verifyLeverTransitionsInvalidateMemo(helper, lever);
		long epochBefore = currentEpoch(helper);
		long startNanos = System.nanoTime();
		runLeverToggles(helper, lever, operations);
		long elapsedNanos = System.nanoTime() - startNanos;
		recordMeasuredEpochAdvance(helper, epochBefore, operations);
		return elapsedNanos;
	}

	@SuppressWarnings("null")
	static long measureLeverTransitions(
			GameTestHelper helper, BlockPos lever, int operations, boolean targetPowered) {
		setLeverPowered(helper, lever, !targetPowered);
		verifyLeverTransitionsInvalidateMemo(helper, lever);
		long epochBefore = currentEpoch(helper);
		long elapsedNanos = 0L;
		for (int operation = 0; operation < operations; operation++) {
			long startNanos = System.nanoTime();
			helper.pullLever(lever);
			elapsedNanos += System.nanoTime() - startNanos;
			helper.pullLever(lever);
		}
		boolean finalPowered = helper.getBlockState(lever).getValue(LeverBlock.POWERED);
		if (finalPowered == targetPowered) {
			throw new IllegalStateException("directional lever benchmark did not restore its initial state");
		}
		recordMeasuredEpochAdvance(helper, epochBefore, (long) operations * 2L);
		return elapsedNanos;
	}

	@SuppressWarnings("null")
	static void pullLeverAndAssertMemoInvalidated(
			GameTestHelper helper, BlockPos lever) {
		long before = ((LevelEpochAccess) helper.getLevel())
				.railoptimization$getBlockChangeEpoch().get();
		helper.pullLever(lever);
		long after = ((LevelEpochAccess) helper.getLevel())
				.railoptimization$getBlockChangeEpoch().get();
		if (after == before) {
			throw new IllegalStateException("lever transition did not invalidate the rail update memo epoch");
		}
	}

	@SuppressWarnings("null")
	static void runLeverToggles(GameTestHelper helper, BlockPos lever, int operations) {
		for (int operation = 0; operation < operations; operation++) {
			helper.pullLever(lever);
		}
	}

	static void warmUpUnchangedUpdates(
			GameTestHelper helper, BlockPos vanillaRail, BlockPos optimizedRail, int operations) {
		runUnchangedUpdates(helper, vanillaRail, operations);
		runUnchangedUpdates(helper, optimizedRail, operations);
		runUnchangedUpdates(helper, optimizedRail, operations);
		runUnchangedUpdates(helper, vanillaRail, operations);
	}

	static long measureUnchangedUpdates(GameTestHelper helper, BlockPos rail, int operations) {
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

	static BenchmarkResult benchmarkAlternating(
			LongSupplier vanillaSample, LongSupplier optimizedSample) {
		LAST_EPOCH_MEASUREMENT.get().clear();
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

	static SampleStats sample(LongSupplier sample) {
		LAST_EPOCH_MEASUREMENT.get().clear();
		long[] samples = new long[MEASUREMENT_ROUNDS];
		for (int round = 0; round < MEASUREMENT_ROUNDS; round++) {
			samples[round] = sample.getAsLong();
		}
		return sampleStats(samples);
	}

	static void measureAndReportIsolated(
			GameTestHelper helper, String label, int initialOperations, IntToLongFunction measurement) {
		for (int round = 0; round < ISOLATED_WARMUP_ROUNDS; round++) {
			measurement.applyAsLong(initialOperations);
		}
		int operations = calibrateOperations(initialOperations, measurement);
		for (int round = 0; round < ISOLATED_WARMUP_ROUNDS; round++) {
			measurement.applyAsLong(operations);
		}
		int measuredOperations = calibrateOperations(operations, measurement);

		SampleStats stats = sample(() -> measurement.applyAsLong(measuredOperations));
		String nanosPerOperation = String.format(
				Locale.ROOT, "%.2f", stats.medianNanos() / (double) measuredOperations);
		String allocatedBytesPerOperation = allocatedBytesPerOperation(measurement, measuredOperations);
		LOGGER.info(
				"RailOptimization isolated benchmark [{}]: {} ns/op (MAD={}%), {} allocated bytes/op, {} operations/sample",
				label,
				nanosPerOperation,
				stats.relativeMedianAbsoluteDeviation(),
				allocatedBytesPerOperation,
				measuredOperations
		);
		helper.assertTrue(
				stats.medianNanos() >= MIN_MEDIAN_SAMPLE_NANOS,
				Component.literal(
						label + " sample is too short for reliable timing: "
								+ stats.medianNanos() / 1_000_000 + " ms"
				)
		);
	}

	static void measureAndReportDirectional(
			GameTestHelper helper, String label, int initialOperations, IntToLongFunction measurement) {
		for (int round = 0; round < ISOLATED_WARMUP_ROUNDS; round++) {
			measurement.applyAsLong(initialOperations);
		}
		int operations = calibrateOperations(initialOperations, measurement);
		for (int round = 0; round < ISOLATED_WARMUP_ROUNDS; round++) {
			measurement.applyAsLong(operations);
		}
		int measuredOperations = calibrateOperations(operations, measurement);

		SampleStats stats = sample(() -> measurement.applyAsLong(measuredOperations));
		LOGGER.info(
				"RailOptimization real-path benchmark [{}]: {} ns/target transition "
						+ "(MAD={}%), {} target transitions/sample",
				label,
				String.format(Locale.ROOT, "%.2f", stats.medianNanos() / (double) measuredOperations),
				stats.relativeMedianAbsoluteDeviation(),
				measuredOperations
		);
		reportLastMemoEpochMeasurement(helper, label);
		assertSampleDuration(helper, label, stats.medianNanos());
	}

	static void measureAndReportDirectionalPair(
			GameTestHelper helper, String label, int initialOperations,
			double maxOptimizedToVanillaRatio,
			IntToLongFunction vanillaMeasurement, IntToLongFunction optimizedMeasurement) {
		warmUpPair(initialOperations, vanillaMeasurement, optimizedMeasurement);
		IntToLongFunction shorterMeasurement = operations -> Math.min(
				vanillaMeasurement.applyAsLong(operations),
				optimizedMeasurement.applyAsLong(operations));
		int operations = calibrateOperations(initialOperations, shorterMeasurement);
		warmUpPair(operations, vanillaMeasurement, optimizedMeasurement);
		int measuredOperations = calibrateOperations(operations, shorterMeasurement);

		BenchmarkResult result = benchmarkAlternating(
				() -> vanillaMeasurement.applyAsLong(measuredOperations),
				() -> optimizedMeasurement.applyAsLong(measuredOperations));
		reportAndAssert(
				helper, label, measuredOperations,
				maxOptimizedToVanillaRatio, result);
	}

	private static void warmUpPair(
			int operations, IntToLongFunction vanillaMeasurement,
			IntToLongFunction optimizedMeasurement) {
		for (int round = 0; round < ISOLATED_WARMUP_ROUNDS; round++) {
			vanillaMeasurement.applyAsLong(operations);
			optimizedMeasurement.applyAsLong(operations);
			optimizedMeasurement.applyAsLong(operations);
			vanillaMeasurement.applyAsLong(operations);
		}
	}

	private static void verifyLeverTransitionsInvalidateMemo(
			GameTestHelper helper, BlockPos lever) {
		pullLeverAndAssertMemoInvalidated(helper, lever);
		pullLeverAndAssertMemoInvalidated(helper, lever);
	}

	private static long currentEpoch(GameTestHelper helper) {
		return ((LevelEpochAccess) helper.getLevel())
				.railoptimization$getBlockChangeEpoch().get();
	}

	private static void recordMeasuredEpochAdvance(
			GameTestHelper helper, long before, long minimumAdvance) {
		long after = currentEpoch(helper);
		long actualAdvance = after - before;
		if (actualAdvance < minimumAdvance) {
			throw new IllegalStateException(
					"measured rail transitions did not invalidate memo for every lever write: expected at least "
							+ minimumAdvance + ", observed " + actualAdvance);
		}
		LAST_EPOCH_MEASUREMENT.get().record(before, after, minimumAdvance);
	}

	private static int calibrateOperations(int initialOperations, IntToLongFunction measurement) {
		int operations = Integer.highestOneBit(Math.max(2, initialOperations - 1)) << 1;
		while (measurement.applyAsLong(operations) < TARGET_SAMPLE_NANOS) {
			if (operations >= 1 << 29) {
				return operations;
			}
			operations <<= 1;
		}
		return operations;
	}

	@SuppressWarnings("null")
	private static void setLeverPowered(
			GameTestHelper helper, BlockPos lever, boolean powered) {
		if (helper.getBlockState(lever).getValue(LeverBlock.POWERED) != powered) {
			helper.pullLever(lever);
		}
	}

	private static void assertSampleDuration(
			GameTestHelper helper, String label, long medianNanos) {
		helper.assertTrue(
				medianNanos >= MIN_MEDIAN_SAMPLE_NANOS,
				Component.literal(
						label + " sample is too short for reliable timing: "
								+ medianNanos / 1_000_000 + " ms"
				)
		);
	}

	private static String allocatedBytesPerOperation(IntToLongFunction measurement, int operations) {
		if (!(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean threadBean)
				|| !threadBean.isThreadAllocatedMemorySupported()) {
			return "unavailable";
		}
		if (!threadBean.isThreadAllocatedMemoryEnabled()) {
			threadBean.setThreadAllocatedMemoryEnabled(true);
		}
		long before = threadBean.getCurrentThreadAllocatedBytes();
		measurement.applyAsLong(operations);
		long allocatedBytes = threadBean.getCurrentThreadAllocatedBytes() - before;
		return String.format(Locale.ROOT, "%.2f", allocatedBytes / (double) operations);
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

	static void reportAndAssert(
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
		reportLastMemoEpochMeasurement(helper, label);
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

	static void reportLastMemoEpochMeasurement(
			GameTestHelper helper, String label) {
		EpochMeasurement measurement = LAST_EPOCH_MEASUREMENT.get();
		if (!measurement.recorded) {
			return;
		}
		long actualAdvance = measurement.after - measurement.before;
		LOGGER.info(
				"RailOptimization memo epoch [{}]: start={}, end={}, measured advance={}, required minimum={}",
				label,
				measurement.before,
				measurement.after,
				actualAdvance,
				measurement.minimumAdvance
		);
		helper.assertTrue(actualAdvance >= measurement.minimumAdvance,
				Component.literal(label + " did not invalidate memo for every measured lever write"));
	}

	static String allocatedBytesPerOperation(
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

	record BenchmarkResult(SampleStats vanilla, SampleStats optimized) {
		double speedup() {
			return (double) vanilla.medianNanos() / optimized.medianNanos();
		}
	}

	record SampleStats(long medianNanos, long medianAbsoluteDeviationNanos) {
		String relativeMedianAbsoluteDeviation() {
			return String.format(
					Locale.ROOT, "%.2f", medianAbsoluteDeviationNanos * 100.0 / medianNanos);
		}
	}
}
