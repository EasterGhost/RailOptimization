package RailOptimization;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

public class RailUpdateMemoGameTest {
	private static final long OLD_EPOCH_PERIOD = 1L << Integer.SIZE;
	private static final int EPOCH_WORKER_COUNT = 8;
	private static final int EPOCH_UPDATES_PER_WORKER = 100_000;

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
