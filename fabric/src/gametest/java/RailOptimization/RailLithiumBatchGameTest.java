package RailOptimization;

import RailOptimization.gametest.batch.RailLithiumBatchTestCases;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class RailLithiumBatchGameTest {
	@GameTest(environment = "railoptimization-gametest:serial_batch_lithium", structure = "railoptimization-gametest:batch_write_empty", maxTicks = 100, padding = 60)
	public void batchPreservesLithiumTrackingAndCounters(GameTestHelper helper) {
		RailLithiumBatchTestCases.batchPreservesLithiumTrackingAndCounters(helper);
	}

	@GameTest(environment = "railoptimization-gametest:serial_batch_lithium", structure = "railoptimization-gametest:batch_write_empty", maxTicks = 100, padding = 60)
	public void batchInvalidatesActiveLithiumEntityCacheWithoutDisablingIt(GameTestHelper helper) {
		RailLithiumBatchTestCases.batchInvalidatesActiveLithiumEntityCacheWithoutDisablingIt(helper);
	}

	@GameTest(environment = "railoptimization-gametest:serial_batch_lithium", structure = "railoptimization-gametest:batch_write_empty", maxTicks = 100, padding = 60)
	public void batchInvalidatesAdjacentLithiumHopperCaches(GameTestHelper helper) {
		RailLithiumBatchTestCases.batchInvalidatesAdjacentLithiumHopperCaches(helper);
	}
}
