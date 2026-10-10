package RailOptimization;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailUpdateMemoGameTest {
	private static final long OLD_EPOCH_PERIOD = 1L << Integer.SIZE;
	private static final int EPOCH_WORKER_COUNT = 8;
	private static final int EPOCH_UPDATES_PER_WORKER = 100_000;

	@GameTest(environment = "railoptimization-gametest:serial_194", maxTicks = 20)
	public void memoEntriesMatchModelAcrossLimitsAndEpochs(GameTestHelper helper) {
		try {
			Method check = RailUpdateMemo.class.getDeclaredMethod("checkEntry", long.class, int.class, boolean.class);
			check.setAccessible(true);
			AtomicLong epoch = ((LevelEpochAccess) helper.getLevel()).railoptimization$getBlockChangeEpoch();
			long originalEpoch = epoch.get();
			try {
				for (int limit : new int[]{1, 8, 64}) {
					for (RailShape shape : new RailShape[]{RailShape.EAST_WEST, RailShape.NORTH_SOUTH}) {
						for (boolean powered : new boolean[]{false, true}) {
							epoch.set(0x12345678L);
							BlockPos origin = new BlockPos(-12_345_678, 64, 12_345_678);
							RailUpdateMemo candidate = new RailUpdateMemo();
							Map<Long, MemoEntry> reference = new HashMap<>();
							candidate.beginWalk(helper.getLevel());
							for (int delta = -limit; delta <= limit; ++delta) {
								BlockPos pos = memoPathPosition(origin, shape, delta);
								reference.put(pos.asLong(), new MemoEntry(epoch.get(), powered, limit));
								candidate.confirm(pos, powered, limit);
								assertMemoMatches(helper, check, reference, candidate, pos, limit, powered);
							}
							for (int delta = -limit; delta <= limit; ++delta) {
								BlockPos pos = memoPathPosition(origin, shape, delta);
								assertMemoMatches(helper, check, reference, candidate, pos, limit, powered);
								assertMemoMatches(helper, check, reference, candidate, pos.above(), limit, powered);
								assertMemoMatches(helper, check, reference, candidate, pos, limit, !powered);
								assertMemoMatches(helper, check, reference, candidate, pos, limit == 64 ? 8 : 64, powered);
							}
							BlockPos last = memoPathPosition(origin, shape, limit);
							BlockPos upper = last.above();
							reference.put(upper.asLong(), new MemoEntry(epoch.get(), !powered, limit));
							candidate.confirm(upper, !powered, limit);
							assertMemoMatches(helper, check, reference, candidate, last, limit, powered);
							assertMemoMatches(helper, check, reference, candidate, upper, limit, !powered);
							reference.put(origin.asLong(), new MemoEntry(epoch.get(), !powered, limit));
							candidate.confirm(origin, !powered, limit);
							assertMemoMatches(helper, check, reference, candidate, origin, limit, !powered);
							assertMemoMatches(helper, check, reference, candidate, origin, limit, powered);
							BlockPos otherLine = shape == RailShape.EAST_WEST ? origin.south() : origin.east();
							reference.put(otherLine.asLong(), new MemoEntry(epoch.get(), powered, limit));
							candidate.confirm(otherLine, powered, limit);
							assertMemoMatches(helper, check, reference, candidate, otherLine, limit, powered);
							int replacementLimit = limit == 64 ? 8 : 64;
							reference.put(origin.asLong(), new MemoEntry(epoch.get(), powered, replacementLimit));
							candidate.confirm(origin, powered, replacementLimit);
							assertMemoMatches(helper, check, reference, candidate, origin, replacementLimit, powered);
							assertMemoMatches(helper, check, reference, candidate, origin, limit, !powered);
							epoch.incrementAndGet();
							assertMemoMatches(helper, check, reference, candidate, last, limit, powered);
							assertMemoMatches(helper, check, reference, candidate, upper, limit, !powered);
							assertMemoMatches(helper, check, reference, candidate, origin, limit, !powered);
							reference.put(origin.asLong(), new MemoEntry(epoch.get(), powered, limit));
							candidate.confirm(origin, powered, limit);
							assertMemoMatches(helper, check, reference, candidate, origin, limit, powered);
							assertMemoMatches(helper, check, reference, candidate, last, limit, powered);
						}
					}
				}
			} finally {
				epoch.set(originalEpoch);
			}
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Unable to compare memo entries with the independent model", exception);
		}
		helper.succeed();
	}

	private static BlockPos memoPathPosition(BlockPos origin, RailShape shape, int delta) {
		return shape == RailShape.EAST_WEST ? origin.offset(delta, Math.floorMod(delta, 3), 0)
				: origin.offset(0, Math.floorMod(delta, 3), delta);
	}

	private static void assertMemoMatches(GameTestHelper helper, Method check, Map<Long, MemoEntry> reference, RailUpdateMemo candidate,
			BlockPos pos, int limit, boolean powered) throws ReflectiveOperationException {
		MemoEntry entry = reference.get(pos.asLong());
		long epoch = ((LevelEpochAccess) helper.getLevel()).railoptimization$getBlockChangeEpoch().get();
		int expected = entry == null ? 0 : entry.epoch == epoch && entry.limit == limit && entry.powered == powered ? 1 : -1;
		int actual = (int) check.invoke(candidate, pos.asLong(), limit, powered);
		helper.assertValueEqual(expected, actual, "memo at " + pos + ", limit=" + limit + ", powered=" + powered);
	}

	private record MemoEntry(long epoch, boolean powered, int limit) {
	}

	@GameTest(environment = "railoptimization-gametest:serial_110", maxTicks = 1)
	public void memoDoesNotAliasAfterOldEpochPeriod(GameTestHelper helper) {
		try {
			Method checkEntry = RailUpdateMemo.class.getDeclaredMethod(
					"checkEntry", long.class, int.class, boolean.class);
			checkEntry.setAccessible(true);

			AtomicLong epoch = ((LevelEpochAccess) helper.getLevel()).railoptimization$getBlockChangeEpoch();
			long originalEpoch = epoch.get();
			try {
				long recordedEpoch = 0x12345678L;
				long position = new BlockPos(12_345_678, 64, -12_345_678).asLong();
				RailUpdateMemo memo = new RailUpdateMemo();
				epoch.set(recordedEpoch);
				memo.beginWalk(helper.getLevel());
				memo.confirm(BlockPos.of(position), true, 8);

				helper.assertValueEqual(
						1,
						invokeCheck(checkEntry, memo, position),
						"memo match at the recorded epoch");
				epoch.set(recordedEpoch + OLD_EPOCH_PERIOD);
				helper.assertValueEqual(
						-1,
						invokeCheck(checkEntry, memo, position),
						"memo mismatch after the old 32-bit epoch period");
			} finally {
				epoch.set(originalEpoch);
			}
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Unable to inspect RailUpdateMemo", exception);
		}
		helper.succeed();
	}

	@GameTest(environment = "railoptimization-gametest:serial_152", maxTicks = 1)
	public void memoRecoversAfterStaleEntriesFillTable(GameTestHelper helper) {
		try {
			Method checkEntry = RailUpdateMemo.class.getDeclaredMethod(
					"checkEntry", long.class, int.class, boolean.class);
			checkEntry.setAccessible(true);
			var capacityField = RailUpdateMemo.class.getDeclaredField("CAPACITY");
			capacityField.setAccessible(true);
			int memoCapacity = capacityField.getInt(null);

			AtomicLong epoch = ((LevelEpochAccess) helper.getLevel()).railoptimization$getBlockChangeEpoch();
			long originalEpoch = epoch.get();
			try {
				epoch.set(1L);
				RailUpdateMemo memo = new RailUpdateMemo();
				memo.beginWalk(helper.getLevel());
				for (int index = 0; index < memoCapacity; ++index) {
					epoch.incrementAndGet();
					memo.confirm(new BlockPos(index, 64, 0), true, 8);
				}

				epoch.incrementAndGet();
				long recoveredPosition = new BlockPos(memoCapacity, 64, 0).asLong();
				memo.confirm(BlockPos.of(recoveredPosition), true, 8);
				helper.assertValueEqual(
						1,
						invokeCheck(checkEntry, memo, recoveredPosition),
						"memo entry inserted after stale entries filled the table");
			} finally {
				epoch.set(originalEpoch);
			}
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Unable to inspect RailUpdateMemo", exception);
		}
		helper.succeed();
	}

	@GameTest(environment = "railoptimization-gametest:serial_113", maxTicks = 1)
	public void blockChangeEpochAdvancesAtomically(GameTestHelper helper) {
		Thread[] workers = new Thread[EPOCH_WORKER_COUNT];
		CountDownLatch ready = new CountDownLatch(EPOCH_WORKER_COUNT);
		CountDownLatch start = new CountDownLatch(1);
		AtomicReference<Throwable> workerFailure = new AtomicReference<>();
		try {
			AtomicLong epoch = ((LevelEpochAccess) helper.getLevel()).railoptimization$getBlockChangeEpoch();
			long originalEpoch = epoch.get();
			try {
				epoch.set(0L);
				for (int workerIndex = 0; workerIndex < workers.length; ++workerIndex) {
					workers[workerIndex] = new Thread(() -> {
						ready.countDown();
						try {
							start.await();
							for (int update = 0; update < EPOCH_UPDATES_PER_WORKER; ++update) {
								RailUpdateMemo.onBlockStateChanged(helper.getLevel());
							}
						} catch (Throwable throwable) {
							workerFailure.compareAndSet(null, throwable);
						}
					}, "RailOptimization epoch test " + workerIndex);
					workers[workerIndex].start();
				}

				if (!ready.await(10, TimeUnit.SECONDS)) {
					throw new IllegalStateException("Timed out while starting epoch test workers");
				}
				start.countDown();
				for (Thread worker : workers) {
					worker.join();
				}
				if (workerFailure.get() != null) {
					throw new IllegalStateException("Epoch test worker failed", workerFailure.get());
				}

				long expectedEpoch = (long) EPOCH_WORKER_COUNT * EPOCH_UPDATES_PER_WORKER;
				helper.assertValueEqual(expectedEpoch, epoch.get(), "block change epoch after concurrent updates");
			} finally {
				start.countDown();
				try {
					for (Thread worker : workers) {
						if (worker != null && worker.isAlive()) {
							worker.interrupt();
							worker.join();
						}
					}
				} finally {
					epoch.set(originalEpoch);
				}
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Unable to test RailUpdateMemo concurrency", exception);
		}
		helper.succeed();
	}

	private static int invokeCheck(Method checkEntry, RailUpdateMemo memo, long position) throws ReflectiveOperationException {
		return (int) checkEntry.invoke(memo, position, 8, true);
	}
}
