package RailOptimization.gametest;

import RailOptimization.RailLogicTestAccess;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationPerformanceGameTestRealUpdates extends RailOptimizationGameTestSupport {
	private static final int DEFAULT_POWER_LIMIT = 8;
	private static final int EXTENDED_POWER_LIMIT = 64;
	private static final int CONTROL_Z_OFFSET = 24;
	private static final int DEFAULT_INITIAL_OPERATIONS = 1 << 9;
	private static final int EXTENDED_INITIAL_OPERATIONS = 1 << 6;
	private static final double MAX_OPTIMIZED_TO_VANILLA_RATIO = 1.05;

	@GameTest(environment = "railoptimization-gametest:serial_138", maxTicks = 400, padding = 60)
	public void directStraightTransitionsAreMeasuredSeparately(GameTestHelper helper) {
		BlockPos start = new BlockPos(3, RAIL_Y, 4);
		BlockPos[] optimizedRails = straightRails(start, DEFAULT_POWER_LIMIT * 2 + 1);
		BlockPos[] vanillaRails = controlCopy(optimizedRails);
		placeStraightPair(helper, optimizedRails, vanillaRails);

		BlockPos optimizedLever = optimizedRails[DEFAULT_POWER_LIMIT].north();
		BlockPos vanillaLever = controlCopy(optimizedLever);
		placeDirectLever(helper, optimizedLever);
		placeDirectLever(helper, vanillaLever);

		measurePairedTransitions(
				helper, "direct straight", vanillaLever, optimizedLever,
				vanillaRails, optimizedRails);
	}

	@GameTest(environment = "railoptimization-gametest:serial_139", maxTicks = 400, padding = 60)
	public void indirectStraightTransitionsAreMeasuredSeparately(GameTestHelper helper) {
		BlockPos start = new BlockPos(3, RAIL_Y, 4);
		BlockPos[] optimizedRails = straightRails(start, DEFAULT_POWER_LIMIT * 2 + 1);
		BlockPos[] vanillaRails = controlCopy(optimizedRails);
		placeStraightPair(helper, optimizedRails, vanillaRails);

		BlockPos optimizedConductor = optimizedRails[DEFAULT_POWER_LIMIT].north();
		BlockPos vanillaConductor = controlCopy(optimizedConductor);
		BlockPos optimizedLever = placeIndirectLever(helper, optimizedConductor);
		BlockPos vanillaLever = placeIndirectLever(helper, vanillaConductor);

		measurePairedTransitions(
				helper, "indirect-conductor straight", vanillaLever, optimizedLever,
				vanillaRails, optimizedRails);
	}

	@GameTest(environment = "railoptimization-gametest:serial_140", maxTicks = 400, padding = 60)
	public void directMixedSlopeTransitionsAreMeasuredSeparately(GameTestHelper helper) {
		BlockPos[] optimizedRails = mixedSlopeRails(DEFAULT_POWER_LIMIT * 2 + 1);
		RailShape[] shapes = mixedSlopeShapes(optimizedRails.length);
		BlockPos[] vanillaRails = controlCopy(optimizedRails);
		placePathPair(helper, optimizedRails, vanillaRails, shapes);

		BlockPos optimizedLever = optimizedRails[DEFAULT_POWER_LIMIT].north();
		BlockPos vanillaLever = controlCopy(optimizedLever);
		placeDirectLever(helper, optimizedLever);
		placeDirectLever(helper, vanillaLever);

		measurePairedTransitions(
				helper, "direct mixed-slope", vanillaLever, optimizedLever,
				vanillaRails, optimizedRails);
	}

	@GameTest(environment = "railoptimization-gametest:serial_141", maxTicks = 400, padding = 160)
	public void extendedDirectStraightTransitionsAreMeasuredSeparately(GameTestHelper helper) {
		BlockPos start = new BlockPos(3, RAIL_Y, 4);
		BlockPos[] rails = straightRails(start, EXTENDED_POWER_LIMIT * 2 + 1);
		placeRailLine(helper, start, Direction.EAST, rails.length, RailShape.EAST_WEST);
		forcePowerLimit(helper, rails, EXTENDED_POWER_LIMIT);

		BlockPos lever = rails[EXTENDED_POWER_LIMIT].north();
		placeDirectLever(helper, lever);
		measureOptimizedTransitions(helper, "powerLimit=64 direct straight", lever, rails);
	}

	@GameTest(environment = "railoptimization-gametest:serial_142", maxTicks = 400, padding = 160)
	public void extendedDirectMixedSlopeTransitionsAreMeasuredSeparately(GameTestHelper helper) {
		BlockPos[] rails = mixedSlopeRails(EXTENDED_POWER_LIMIT * 2 + 1);
		placeRailPath(helper, rails, mixedSlopeShapes(rails.length));
		forcePowerLimit(helper, rails, EXTENDED_POWER_LIMIT);

		BlockPos lever = rails[EXTENDED_POWER_LIMIT].north();
		placeDirectLever(helper, lever);
		measureOptimizedTransitions(helper, "powerLimit=64 direct mixed-slope", lever, rails);
	}

	private static void measurePairedTransitions(
			GameTestHelper helper, String label,
			BlockPos vanillaLever, BlockPos optimizedLever,
			BlockPos[] vanillaRails, BlockPos[] optimizedRails) {
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					verifyPair(helper, vanillaLever, optimizedLever, vanillaRails, optimizedRails);
					RailBenchmarkRunner.measureAndReportDirectionalPair(
							helper, label + " powering", DEFAULT_INITIAL_OPERATIONS,
							MAX_OPTIMIZED_TO_VANILLA_RATIO,
							operations -> RailBenchmarkRunner.measureLeverTransitions(
									helper, vanillaLever, operations, true),
							operations -> RailBenchmarkRunner.measureLeverTransitions(
									helper, optimizedLever, operations, true));
					assertRailsPowered(helper, vanillaRails, false);
					assertRailsPowered(helper, optimizedRails, false);

					RailBenchmarkRunner.measureAndReportDirectionalPair(
							helper, label + " depowering", DEFAULT_INITIAL_OPERATIONS,
							MAX_OPTIMIZED_TO_VANILLA_RATIO,
							operations -> RailBenchmarkRunner.measureLeverTransitions(
									helper, vanillaLever, operations, false),
							operations -> RailBenchmarkRunner.measureLeverTransitions(
									helper, optimizedLever, operations, false));
					assertMatchingRailPower(helper, vanillaRails, optimizedRails);
					assertRailsPowered(helper, optimizedRails, true);
					setLeverPowered(helper, vanillaLever, false);
					setLeverPowered(helper, optimizedLever, false);
					assertRailsPowered(helper, vanillaRails, false);
					assertRailsPowered(helper, optimizedRails, false);
				})
				.thenSucceed();
	}

	private static void measureOptimizedTransitions(
			GameTestHelper helper, String label, BlockPos lever, BlockPos[] rails) {
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					verifySingle(helper, lever, rails);
					RailBenchmarkRunner.measureAndReportDirectional(
							helper, label + " powering", EXTENDED_INITIAL_OPERATIONS,
							operations -> RailBenchmarkRunner.measureLeverTransitions(
									helper, lever, operations, true));
					assertRailsPowered(helper, rails, false);

					RailBenchmarkRunner.measureAndReportDirectional(
							helper, label + " depowering", EXTENDED_INITIAL_OPERATIONS,
							operations -> RailBenchmarkRunner.measureLeverTransitions(
									helper, lever, operations, false));
					assertRailsPowered(helper, rails, true);
					setLeverPowered(helper, lever, false);
					assertRailsPowered(helper, rails, false);
				})
				.thenSucceed();
	}

	private static void verifyPair(
			GameTestHelper helper, BlockPos vanillaLever, BlockPos optimizedLever,
			BlockPos[] vanillaRails, BlockPos[] optimizedRails) {
		RailBenchmarkRunner.pullLeverAndAssertMemoInvalidated(helper, vanillaLever);
		RailBenchmarkRunner.pullLeverAndAssertMemoInvalidated(helper, optimizedLever);
		assertMatchingRailPower(helper, vanillaRails, optimizedRails);
		assertRailsPowered(helper, optimizedRails, true);
		RailBenchmarkRunner.pullLeverAndAssertMemoInvalidated(helper, vanillaLever);
		RailBenchmarkRunner.pullLeverAndAssertMemoInvalidated(helper, optimizedLever);
		assertMatchingRailPower(helper, vanillaRails, optimizedRails);
		assertRailsPowered(helper, optimizedRails, false);
	}

	private static void verifySingle(
			GameTestHelper helper, BlockPos lever, BlockPos[] rails) {
		RailBenchmarkRunner.pullLeverAndAssertMemoInvalidated(helper, lever);
		assertRailsPowered(helper, rails, true);
		RailBenchmarkRunner.pullLeverAndAssertMemoInvalidated(helper, lever);
		assertRailsPowered(helper, rails, false);
	}

	private static void placeStraightPair(
			GameTestHelper helper, BlockPos[] optimizedRails, BlockPos[] vanillaRails) {
		placeRailLine(
				helper, optimizedRails[0], Direction.EAST,
				optimizedRails.length, RailShape.EAST_WEST);
		for (BlockPos rail : vanillaRails) {
			markVanilla(helper, rail);
			placeRail(helper, rail, RailShape.EAST_WEST);
		}
	}

	private static void placePathPair(
			GameTestHelper helper, BlockPos[] optimizedRails,
			BlockPos[] vanillaRails, RailShape[] shapes) {
		placeRailPath(helper, optimizedRails, shapes);
		for (int index = 0; index < vanillaRails.length; index++) {
			markVanilla(helper, vanillaRails[index]);
			placeRail(helper, vanillaRails[index], shapes[index]);
		}
	}

	@SuppressWarnings("null")
	private static void placeDirectLever(GameTestHelper helper, BlockPos lever) {
		helper.setBlock(lever.below(), Blocks.SMOOTH_STONE);
		helper.setBlock(lever, Blocks.LEVER.defaultBlockState()
				.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
				.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
				.setValue(LeverBlock.POWERED, false));
	}

	@SuppressWarnings("null")
	private static BlockPos placeIndirectLever(
			GameTestHelper helper, BlockPos conductor) {
		helper.setBlock(conductor, Blocks.SMOOTH_STONE);
		BlockPos lever = conductor.above();
		placeDirectLever(helper, lever);
		return lever;
	}

	@SuppressWarnings("null")
	private static void setLeverPowered(
			GameTestHelper helper, BlockPos lever, boolean powered) {
		if (helper.getBlockState(lever).getValue(LeverBlock.POWERED) != powered) {
			helper.pullLever(lever);
		}
	}

	@SuppressWarnings("null")
	private static void forcePowerLimit(
			GameTestHelper helper, BlockPos[] rails, int powerLimit) {
		for (BlockPos rail : rails) {
			RailLogicTestAccess.forcePowerLimitAt(helper.absolutePos(rail), powerLimit);
		}
	}

	@SuppressWarnings("null")
	private static void markVanilla(GameTestHelper helper, BlockPos rail) {
		RailLogicTestAccess.forceVanillaAt(helper.absolutePos(rail));
	}

	private static BlockPos[] straightRails(BlockPos start, int length) {
		BlockPos[] rails = new BlockPos[length];
		for (int index = 0; index < length; index++) {
			rails[index] = start.east(index);
		}
		return rails;
	}

	private static BlockPos[] mixedSlopeRails(int length) {
		int[] heightOffsets = new int[] { 3, 2, 1, 0, 0, 1, 2, 3 };
		BlockPos[] rails = new BlockPos[length];
		for (int index = 0; index < rails.length; index++) {
			rails[index] = new BlockPos(
					3 + index, RAIL_Y + heightOffsets[index & 7], 4);
		}
		return rails;
	}

	private static RailShape[] mixedSlopeShapes(int length) {
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
		for (int index = 0; index < shapes.length; index++) {
			shapes[index] = shapeCycle[index & 7];
		}
		return shapes;
	}

	private static BlockPos controlCopy(BlockPos pos) {
		return pos.south(CONTROL_Z_OFFSET);
	}

	private static BlockPos[] controlCopy(BlockPos[] positions) {
		BlockPos[] copy = new BlockPos[positions.length];
		for (int index = 0; index < positions.length; index++) {
			copy[index] = controlCopy(positions[index]);
		}
		return copy;
	}
}
