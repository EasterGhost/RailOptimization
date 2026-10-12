package RailOptimization.gametest;

import java.util.function.Consumer;

import RailOptimization.RailLogic;
import RailOptimization.gametest.batch.RailBatchWriteTestCases;
import RailOptimization.gametest.batch.RailLithiumBatchTestCases;
import RailOptimization.gametest.batch.RailNotificationBatchTestCases;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.BuiltinTestFunctions;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

@Mod(RailOptimizationNeoForgeGameTest.MOD_ID)
public final class RailOptimizationNeoForgeGameTest {
	public static final String MOD_ID = "railoptimization_gametest";

	private static final Identifier TEST_ENVIRONMENT = id("default");
	private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
	private static final BlockPos FIRST_RAIL = new BlockPos(1, 2, 1);
	private static final BlockPos SOURCE = new BlockPos(1, 2, 2);
	private static final int RAIL_COUNT = 10;

	public RailOptimizationNeoForgeGameTest(IEventBus modEventBus) {
		RailOptimizationNeoForgeReviewGameTest.registerBlocks(modEventBus);
		RailOptimizationNeoForgeFollowupGameTest.registerBlocks(modEventBus);
		modEventBus.addListener(this::registerGameTests);
	}

	private void registerGameTests(RegisterGameTestsEvent event) {
		if (Boolean.getBoolean("railoptimization.gametest.benchmarkOnly")) {
			RailOptimizationNeoForgePerformanceGameTest.registerTests(event);
			return;
		}
		Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(TEST_ENVIRONMENT);
		registerTest(event, environment, "powered_rail_line", helper -> testRailLine(helper, Blocks.POWERED_RAIL));
		registerTest(event, environment, "activator_rail_line", helper -> testRailLine(helper, Blocks.ACTIVATOR_RAIL));
		RailOptimizationNeoForgeReviewGameTest.registerTests(event);
		RailOptimizationNeoForgeFollowupGameTest.registerTests(event);
		Holder<TestEnvironmentDefinition<?>> signalEnvironment = event.registerEnvironment(id("signal_boundaries"));
		registerTest(event, signalEnvironment, "signal_height_boundary_reads", RailSignalBoundaryTestCases::heightBoundaryReadsMatchVanilla);
		registerTest(event, signalEnvironment, "signal_height_edge_switches", RailSignalBoundaryTestCases::heightEdgeSignalsMatchVanilla);
		Holder<TestEnvironmentDefinition<?>> batchEnvironment = event.registerEnvironment(id("batch_write"));
		Identifier batchStructure = id("batch_write_empty");
		registerTest(event, batchEnvironment, "batch_real_lever", batchStructure, 100, RailBatchWriteTestCases::realLeverUsesMultiRailBatchesAndHonorsLimit);
		registerTest(event, batchEnvironment, "batch_extended_lever", id("batch_line_empty"), 100,
				RailBatchWriteTestCases::extendedRangeLeverUsesMultiRailBatchesAndHonorsLimit);
		registerTest(event, batchEnvironment, "batch_grouped_writes", batchStructure, 100,
				RailBatchWriteTestCases::groupedWritesAreDeferredAndPreserveSectionState);
		registerTest(event, batchEnvironment, "batch_palette_resize", batchStructure, 100, RailBatchWriteTestCases::paletteBatchHandlesResizesAndGlobalIds);
		registerTest(event, batchEnvironment, "batch_pending_states", batchStructure, 100,
				RailOptimization.gametest.batch.RailPendingStateTestCases::pendingStatesSurviveReuseAndCoordinateCollisions);
		registerTest(event, batchEnvironment, "batch_notification_plans", batchStructure, 100,
				RailNotificationBatchTestCases::bitmapPlansMatchHashAndSurviveReuse);
		registerTest(event, batchEnvironment, "batch_notification_queue", batchStructure, 100,
				RailNotificationBatchTestCases::nativeQueueHonorsBudgetAndDebugFallback);
		registerTest(event, batchEnvironment, "batch_platform_snapshots", batchStructure, 100,
				RailBatchWriteTestCases::platformSnapshotCaptureUsesNormalWrites);
		Holder<TestEnvironmentDefinition<?>> lithiumEnvironment = event.registerEnvironment(id("batch_lithium"));
		registerTest(event, lithiumEnvironment, "batch_lithium_tracking", batchStructure, 100,
				RailLithiumBatchTestCases::batchPreservesLithiumTrackingAndCounters);
		registerTest(event, lithiumEnvironment, "batch_lithium_entity_cache", batchStructure, 100,
				RailLithiumBatchTestCases::batchInvalidatesActiveLithiumEntityCacheWithoutDisablingIt);
		registerTest(event, lithiumEnvironment, "batch_lithium_hopper_cache", batchStructure, 100,
				RailLithiumBatchTestCases::batchInvalidatesAdjacentLithiumHopperCaches);
	}

	static void registerTest(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment, String name,
			Consumer<GameTestHelper> body) {
		registerTest(event, environment, name, EMPTY_STRUCTURE, 40, body);
	}

	static void registerTest(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment, String name,
			Identifier structure, int maxTicks, Consumer<GameTestHelper> body) {
		TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, structure, maxTicks, 0, true, Rotation.NONE, false, 1, 1, false, 16);
		event.registerTest(id(name), testData -> createTest(testData, body), data);
	}

	private static GameTestInstance createTest(TestData<Holder<TestEnvironmentDefinition<?>>> data, Consumer<GameTestHelper> body) {
		return new FunctionGameTestInstance(BuiltinTestFunctions.ALWAYS_PASS, data) {
			@Override
			public void run(GameTestHelper helper) {
				try {
					body.accept(helper);
				} catch (RuntimeException | Error failure) {
					com.mojang.logging.LogUtils.getLogger().error("NeoForge GameTest failed", failure);
					throw failure;
				}
			}
		};
	}

	private static void testRailLine(GameTestHelper helper, Block railBlock) {
		RailLogic.setOptimizationEnabled(true);
		RailLogic.setRailPowerLimit(8);

		BlockState railState = railBlock.defaultBlockState().setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST);
		for (int index = 0; index < RAIL_COUNT; index++) {
			BlockPos railPos = FIRST_RAIL.offset(index, 0, 0);
			helper.setBlock(railPos.below(), Blocks.SMOOTH_STONE);
			helper.setBlock(railPos, railState);
		}

		helper.startSequence()
				.thenExecute(() -> helper.setBlock(SOURCE, Blocks.REDSTONE_BLOCK))
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertBlockProperty(FIRST_RAIL.offset(8, 0, 0), PoweredRailBlock.POWERED, true);
					helper.assertBlockProperty(FIRST_RAIL.offset(9, 0, 0), PoweredRailBlock.POWERED, false);
					helper.setBlock(SOURCE, Blocks.AIR);
				})
				.thenIdle(2)
				.thenExecute(() -> {
					for (int index = 0; index < RAIL_COUNT; index++) {
						helper.assertBlockProperty(FIRST_RAIL.offset(index, 0, 0), PoweredRailBlock.POWERED, false);
					}
				})
				.thenSucceed();
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
