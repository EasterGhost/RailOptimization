package RailOptimization.gametest;

import com.mojang.logging.LogUtils;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.LongSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;

final class RailBenchmarkRunner {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int MEASUREMENT_ROUNDS = 11;
	private static final long MIN_MEDIAN_SAMPLE_NANOS = 20_000_000L;

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
		long startNanos = System.nanoTime();
		runLeverToggles(helper, lever, operations);
		return System.nanoTime() - startNanos;
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
		long[] samples = new long[MEASUREMENT_ROUNDS];
		for (int round = 0; round < MEASUREMENT_ROUNDS; round++) {
			samples[round] = sample.getAsLong();
		}
		return sampleStats(samples);
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
