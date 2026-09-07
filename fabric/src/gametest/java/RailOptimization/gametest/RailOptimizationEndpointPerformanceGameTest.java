package RailOptimization.gametest;

import RailOptimization.RailLogicTestAccess;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationEndpointPerformanceGameTest extends RailOptimizationGameTestSupport {
	private static final String STRUCTURE = "railoptimization-gametest:review_benchmark_empty";
	private static final int RAIL_COUNT = 9;

	@GameTest(environment = "railoptimization-gametest:serial_181", structure = STRUCTURE, maxTicks = 400, padding = 50)
	public void westEndpointTransitions(GameTestHelper helper) {
		measureEndpoint(helper, new BlockPos(3, RAIL_Y, 3), Direction.EAST, Direction.NORTH, RailShape.EAST_WEST, "west endpoint");
	}

	@GameTest(environment = "railoptimization-gametest:serial_182", structure = STRUCTURE, maxTicks = 400, padding = 50)
	public void southEndpointTransitions(GameTestHelper helper) {
		measureEndpoint(helper, new BlockPos(3, RAIL_Y, 11), Direction.NORTH, Direction.WEST, RailShape.NORTH_SOUTH, "south endpoint");
	}

	@SuppressWarnings("null")
	private static void measureEndpoint(GameTestHelper helper, BlockPos source, Direction direction, Direction leverSide, RailShape shape, String label) {
		BlockPos[] optimizedRails = placeFixture(helper, source, direction, leverSide, shape, false);
		BlockPos[] vanillaRails = placeFixture(helper, mirrorCopy(source), direction, leverSide, shape, true);
		BlockPos optimizedLever = source.relative(leverSide);
		BlockPos vanillaLever = mirrorCopy(optimizedLever);
		helper.startSequence().thenIdle(2).thenExecute(() -> {
			for (boolean powered : new boolean[]{true, false}) {
				RailBenchmarkRunner.pullLeverAndAssertMemoInvalidated(helper, vanillaLever);
				RailBenchmarkRunner.pullLeverAndAssertMemoInvalidated(helper, optimizedLever);
				assertRailsPowered(helper, vanillaRails, powered);
				assertRailsPowered(helper, optimizedRails, powered);
			}
			for (boolean powering : new boolean[]{true, false}) {
				RailBenchmarkRunner.measureAndReportDirectionalPair(helper, label + (powering ? " powering" : " depowering"),
						128, 1.05,
						operations -> RailBenchmarkRunner.measureLeverTransitions(helper, vanillaLever, operations, powering),
						operations -> RailBenchmarkRunner.measureLeverTransitions(helper, optimizedLever, operations, powering));
				assertRailsPowered(helper, vanillaRails, !powering);
				assertRailsPowered(helper, optimizedRails, !powering);
			}
			helper.pullLever(vanillaLever);
			helper.pullLever(optimizedLever);
			assertRailsPowered(helper, vanillaRails, false);
			assertRailsPowered(helper, optimizedRails, false);
		}).thenSucceed();
	}

	@SuppressWarnings("null")
	private static BlockPos[] placeFixture(GameTestHelper helper, BlockPos source, Direction direction, Direction leverSide, RailShape shape, boolean vanilla) {
		BlockPos[] rails = new BlockPos[RAIL_COUNT];
		for (int index = 0; index < rails.length; index++) {
			BlockPos rail = source.relative(direction, index);
			rails[index] = rail;
			helper.assertTrue(helper.getBounds().contains(helper.absolutePos(rail).getCenter()), Component.literal("benchmark rail outside structure"));
			if (vanilla) {
				RailLogicTestAccess.forceVanillaAt(helper.absolutePos(rail));
			} else {
				RailLogicTestAccess.forceOptimizedAt(helper.absolutePos(rail));
			}
			helper.setBlock(rail.below(), Blocks.GLASS);
			helper.setBlock(rail, Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, shape));
		}
		BlockPos lever = source.relative(leverSide);
		helper.setBlock(lever.below(), Blocks.GLASS);
		helper.setBlock(lever, Blocks.LEVER.defaultBlockState()
				.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
				.setValue(LeverBlock.FACING, leverSide).setValue(LeverBlock.POWERED, false));
		for (BlockPos rail : rails) {
			helper.assertBlockProperty(rail, PoweredRailBlock.SHAPE, shape);
		}
		return rails;
	}
}
