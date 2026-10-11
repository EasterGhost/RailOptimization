package RailOptimization;

import RailOptimization.gametest.batch.RailBatchWriteTestCases;
import RailOptimization.gametest.batch.RailPendingStateTestCases;
import RailOptimization.gametest.batch.RailNotificationBatchTestCases;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class RailBatchWriteGameTest {
	@GameTest(environment = "railoptimization-gametest:serial_batch_write", structure = "railoptimization-gametest:batch_write_empty", maxTicks = 100, padding = 60)
	public void realLeverUsesMultiRailBatchesAndHonorsLimit(GameTestHelper helper) {
		RailBatchWriteTestCases.realLeverUsesMultiRailBatchesAndHonorsLimit(helper);
	}

	@GameTest(environment = "railoptimization-gametest:serial_batch_write", structure = "railoptimization-gametest:batch_line_empty", maxTicks = 100, padding = 60)
	public void extendedRangeLeverUsesMultiRailBatchesAndHonorsLimit(GameTestHelper helper) {
		RailBatchWriteTestCases.extendedRangeLeverUsesMultiRailBatchesAndHonorsLimit(helper);
	}

	@GameTest(environment = "railoptimization-gametest:serial_batch_write", structure = "railoptimization-gametest:batch_write_empty", maxTicks = 100, padding = 60)
	public void groupedWritesAreDeferredAndPreserveSectionState(GameTestHelper helper) {
		RailBatchWriteTestCases.groupedWritesAreDeferredAndPreserveSectionState(helper);
	}

	@GameTest(environment = "railoptimization-gametest:serial_batch_write", structure = "railoptimization-gametest:batch_write_empty", maxTicks = 100, padding = 60)
	public void paletteBatchHandlesResizesAndGlobalIds(GameTestHelper helper) {
		RailBatchWriteTestCases.paletteBatchHandlesResizesAndGlobalIds(helper);
	}

	@GameTest(environment = "railoptimization-gametest:serial_batch_write", structure = "railoptimization-gametest:batch_write_empty", maxTicks = 100, padding = 60)
	public void pendingStatesSurviveReuseAndCoordinateCollisions(GameTestHelper helper) {
		RailPendingStateTestCases.pendingStatesSurviveReuseAndCoordinateCollisions(helper);
	}

	@GameTest(environment = "railoptimization-gametest:serial_batch_write", structure = "railoptimization-gametest:batch_write_empty", maxTicks = 100, padding = 60)
	public void bitmapPlansMatchHashAndSurviveReuse(GameTestHelper helper) {
		RailNotificationBatchTestCases.bitmapPlansMatchHashAndSurviveReuse(helper);
	}

	@GameTest(environment = "railoptimization-gametest:serial_batch_write", structure = "railoptimization-gametest:batch_write_empty", maxTicks = 100, padding = 60)
	public void nativeQueueHonorsBudgetAndDebugFallback(GameTestHelper helper) {
		RailNotificationBatchTestCases.nativeQueueHonorsBudgetAndDebugFallback(helper);
	}
}
