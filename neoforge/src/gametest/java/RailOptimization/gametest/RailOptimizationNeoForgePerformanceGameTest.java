package RailOptimization.gametest;

import RailOptimization.LevelEpochAccess;
import RailOptimization.RailLogic;
import com.mojang.logging.LogUtils;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import org.slf4j.Logger;

final class RailOptimizationNeoForgePerformanceGameTest {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int ROUNDS = 11;
	private static final long TARGET_NANOS = 30_000_000L;
	private static final long MIN_SAMPLE_NANOS = 20_000_000L;
	private static final com.sun.management.ThreadMXBean ALLOCATION_BEAN =
			(com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

	private RailOptimizationNeoForgePerformanceGameTest() {
	}

	static void registerTests(RegisterGameTestsEvent event) {
		Identifier id = Identifier.fromNamespaceAndPath(RailOptimizationNeoForgeGameTest.MOD_ID, "compatibility_benchmark");
		RailOptimizationNeoForgeGameTest.registerTest(event, event.registerEnvironment(id), id.getPath(),
				Identifier.fromNamespaceAndPath(RailOptimizationNeoForgeGameTest.MOD_ID, "benchmark_empty"), 400, helper -> {
					helper.startSequence().thenIdle(2).thenExecute(() -> runBenchmarks(helper)).thenSucceed();
				});
	}

	private static void runBenchmarks(GameTestHelper helper) {
		boolean previous = RailLogic.isOptimizationEnabled();
		try {
			benchmark(helper, "powered EW direct", Blocks.POWERED_RAIL, Direction.EAST, false, 5, 8, true);
			benchmark(helper, "activator EW direct", Blocks.ACTIVATOR_RAIL, Direction.EAST, false, 5, 8, true);
			benchmark(helper, "powered NS direct", Blocks.POWERED_RAIL, Direction.SOUTH, false, 5, 8, true);
			benchmark(helper, "powered EW conductor inside", Blocks.POWERED_RAIL, Direction.EAST, true, 5, 8, true);
			benchmark(helper, "powered EW conductor boundary", Blocks.POWERED_RAIL, Direction.EAST, true, 0, 8, true);
			benchmark(helper, "powered EW limit 64", Blocks.POWERED_RAIL, Direction.EAST, false, 5, 64, false);
		} finally {
			RailLogic.setOptimizationEnabled(previous);
			RailLogic.setRailPowerLimit(8);
		}
	}

	private static void benchmark(GameTestHelper helper, String label, Block railBlock, Direction axis, boolean conductor,
			int localX, int limit, boolean withReference) {
		RailLogic.setRailPowerLimit(limit);
		BlockPos anchor = new BlockPos(72, 2, 16);
		BlockPos absolute = helper.absolutePos(anchor);
		BlockPos center = anchor.offset(Math.floorMod(localX - absolute.getX(), 16), 0, Math.floorMod(5 - absolute.getZ(), 16));
		Fixture optimized = placeFixture(helper, center, railBlock, axis, conductor, limit);
		Fixture reference = withReference ? placeFixture(helper, center.above(20), railBlock, axis, conductor, limit) : null;
		try {
			verifyTransitions(helper, optimized, true);
			if (reference != null) {
				verifyTransitions(helper, reference, false);
			}
			int operations = 512;
			for (int warmup = 0; warmup < 4; warmup++) {
				if (reference != null) sample(helper, reference.lever(), operations, false);
				sample(helper, optimized.lever(), operations, true);
			}
			while (sample(helper, optimized.lever(), operations, true).nanos() < TARGET_NANOS) {
				operations *= 2;
				helper.assertTrue(operations <= 131072, Component.literal("benchmark failed to reach sample duration"));
			}
			long[] optimizedTimes = new long[ROUNDS];
			long[] referenceTimes = new long[ROUNDS];
			long[] allocations = new long[ROUNDS];
			for (int round = 0; round < ROUNDS; round++) {
				if (reference != null && (round & 1) == 0) referenceTimes[round] = sample(helper, reference.lever(), operations, false).nanos();
				Sample measured = sample(helper, optimized.lever(), operations, true);
				optimizedTimes[round] = measured.nanos();
				allocations[round] = measured.bytes();
				if (reference != null && (round & 1) != 0) referenceTimes[round] = sample(helper, reference.lever(), operations, false).nanos();
			}
			long optimizedMedian = median(optimizedTimes);
			helper.assertTrue(optimizedMedian >= MIN_SAMPLE_NANOS, Component.literal(label + " optimized sample too short"));
			if (reference != null) {
				long referenceMedian = median(referenceTimes);
				helper.assertTrue(referenceMedian >= MIN_SAMPLE_NANOS, Component.literal(label + " reference sample too short"));
				helper.assertTrue(optimizedMedian < referenceMedian * 1.05, Component.literal(label + " slower than native reference"));
				LOGGER.info(String.format(Locale.ROOT,
						"NeoForge benchmark [%s]: reference=%.2f ns/op (MAD=%.2f%%), optimized=%.2f ns/op (MAD=%.2f%%), allocated=%.2f bytes/op, operations=%d",
						label, (double) referenceMedian / operations, mad(referenceTimes), (double) optimizedMedian / operations,
						mad(optimizedTimes), (double) median(allocations) / operations, operations));
				assertPowered(helper, reference.rails(), false);
			} else {
				LOGGER.info(String.format(Locale.ROOT,
						"NeoForge benchmark [%s]: optimized=%.2f ns/op (MAD=%.2f%%), allocated=%.2f bytes/op, operations=%d",
						label, (double) optimizedMedian / operations, mad(optimizedTimes), (double) median(allocations) / operations, operations));
			}
			assertPowered(helper, optimized.rails(), false);
		} finally {
			clearFixture(helper, optimized);
			if (reference != null) clearFixture(helper, reference);
		}
	}

	private static Sample sample(GameTestHelper helper, BlockPos lever, int operations, boolean optimized) {
		RailLogic.setOptimizationEnabled(optimized);
		long epoch = ((LevelEpochAccess) helper.getLevel()).railoptimization$getBlockChangeEpoch().get();
		long thread = Thread.currentThread().threadId();
		long beforeBytes = ALLOCATION_BEAN.getThreadAllocatedBytes(thread);
		long start = System.nanoTime();
		for (int index = 0; index < operations; index++) helper.pullLever(lever);
		long elapsed = System.nanoTime() - start;
		long bytes = ALLOCATION_BEAN.getThreadAllocatedBytes(thread) - beforeBytes;
		long advance = ((LevelEpochAccess) helper.getLevel()).railoptimization$getBlockChangeEpoch().get() - epoch;
		helper.assertTrue(advance >= operations, Component.literal("lever benchmark did not invalidate memo for every operation"));
		helper.assertBlockProperty(lever, LeverBlock.POWERED, false);
		return new Sample(elapsed, bytes);
	}

	private static Fixture placeFixture(GameTestHelper helper, BlockPos center, Block railBlock, Direction axis, boolean conductor, int limit) {
		BlockPos[] rails = new BlockPos[limit * 2 + 1];
		RailShape shape = axis.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
		for (int index = 0; index < rails.length; index++) {
			BlockPos rail = center.relative(axis, index - limit);
			helper.assertTrue(helper.getBounds().contains(helper.absolutePos(rail).getCenter()), Component.literal("benchmark rail outside structure"));
			rails[index] = rail;
			helper.setBlock(rail.below(), Blocks.GLASS);
			helper.setBlock(rail, railBlock.defaultBlockState().setValue(PoweredRailBlock.SHAPE, shape));
		}
		BlockPos input = center.relative(axis.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.WEST);
		BlockPos lever = conductor ? input.above() : input;
		helper.setBlock(lever.below(), conductor ? Blocks.STONE : Blocks.GLASS);
		helper.setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
				.setValue(LeverBlock.FACING, Direction.NORTH).setValue(LeverBlock.POWERED, false));
		for (BlockPos rail : rails) helper.assertBlockProperty(rail, PoweredRailBlock.SHAPE, shape);
		return new Fixture(rails, lever);
	}

	private static void verifyTransitions(GameTestHelper helper, Fixture fixture, boolean optimized) {
		RailLogic.setOptimizationEnabled(optimized);
		assertPowered(helper, fixture.rails(), false);
		helper.pullLever(fixture.lever());
		assertPowered(helper, fixture.rails(), true);
		helper.pullLever(fixture.lever());
		assertPowered(helper, fixture.rails(), false);
	}

	private static void assertPowered(GameTestHelper helper, BlockPos[] rails, boolean powered) {
		for (BlockPos rail : rails) helper.assertBlockProperty(rail, PoweredRailBlock.POWERED, powered);
	}

	private static void clearFixture(GameTestHelper helper, Fixture fixture) {
		helper.setBlock(fixture.lever(), Blocks.AIR);
		helper.setBlock(fixture.lever().below(), Blocks.AIR);
		for (BlockPos rail : fixture.rails()) {
			helper.setBlock(rail, Blocks.AIR);
			helper.setBlock(rail.below(), Blocks.AIR);
		}
	}

	private static long median(long[] values) {
		long[] sorted = values.clone();
		Arrays.sort(sorted);
		return sorted[sorted.length / 2];
	}

	private static double mad(long[] values) {
		long middle = median(values);
		long[] differences = Arrays.stream(values).map(value -> Math.abs(value - middle)).toArray();
		return 100.0 * median(differences) / middle;
	}

	private record Fixture(BlockPos[] rails, BlockPos lever) {
	}

	private record Sample(long nanos, long bytes) {
	}
}
