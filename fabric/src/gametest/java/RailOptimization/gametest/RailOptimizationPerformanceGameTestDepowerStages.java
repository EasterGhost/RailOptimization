package RailOptimization.gametest;

import RailOptimization.RailHotPathBenchmarkAccess;
import RailOptimization.RailLogic;
import RailOptimization.RailLogicTestAccess;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationPerformanceGameTestDepowerStages extends RailOptimizationGameTestSupport {
	private static final int POWER_LIMIT = 8;
	private static final int RAIL_COUNT = POWER_LIMIT * 2 + 1;
	private static final int INITIAL_OPERATIONS = 1 << 9;

	@GameTest(environment = "railoptimization-gametest:serial_143", maxTicks = 400, padding = 100)
	public void depowerPlanningStagesAreMeasuredSeparately(GameTestHelper helper) {
		int railY = relativeYAtSectionLocal(helper, 6);
		int sourceX = relativeCoordinateAtChunkLocal(helper, Direction.Axis.X, 8);
		int railZ = relativeCoordinateAtChunkLocal(helper, Direction.Axis.Z, 8);
		BlockPos[] straightRails = straightRails(
				new BlockPos(sourceX - POWER_LIMIT, railY, railZ));
		placeOptimizedRailPath(
				helper, straightRails, straightRailShapes());

		BlockPos[] mixedRails = mixedSlopeRails(
				sourceX - POWER_LIMIT, railY, railZ + 16);
		placeOptimizedRailPath(helper, mixedRails, mixedSlopeShapes());

		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					int configuredPowerLimit = RailLogicTestAccess.currentPowerLimit();
					RailLogic.setRailPowerLimit(POWER_LIMIT);
					try {
						BlockPos[] absoluteStraightRails = absolutePositions(helper, straightRails);
						BlockPos[] absoluteMixedRails = absolutePositions(helper, mixedRails);
						RailHotPathBenchmarkAccess.forcePoweredState(
								helper.getLevel(), absoluteStraightRails, true);
						RailHotPathBenchmarkAccess.forcePoweredState(
								helper.getLevel(), absoluteMixedRails, true);

						var straightProbe = RailHotPathBenchmarkAccess.depowerStageProbe(
								helper.getLevel(), absoluteStraightRails[POWER_LIMIT],
								POWER_LIMIT, POWER_LIMIT);
						measure(helper, "depower straight two-direction entry decision",
								straightProbe::measureDecision);
						measure(helper, "depower straight one-direction planning after decision",
								operations -> straightProbe.measureStraightPlan(operations, true));

						var mixedProbe = RailHotPathBenchmarkAccess.depowerStageProbe(
								helper.getLevel(), absoluteMixedRails[POWER_LIMIT],
								POWER_LIMIT, POWER_LIMIT);
						measure(helper, "depower mixed-slope two-direction entry decision",
								mixedProbe::measureDecision);
						measure(helper, "depower mixed-slope straight-path rejection after decision",
								operations -> mixedProbe.measureStraightRejection(operations, true));
						measure(helper, "depower mixed-slope connected planning after rejection",
								operations -> mixedProbe.measureConnectedPlan(operations, true));

						BlockPos straightLever = straightRails[POWER_LIMIT].north();
						BlockPos mixedLever = mixedRails[POWER_LIMIT].north();
						placeDirectLever(helper, straightLever);
						placeDirectLever(helper, mixedLever);
						measureRealDepower(
								helper, "aligned direct straight depowering",
								straightLever);
						measureRealDepower(
								helper, "aligned direct mixed-slope depowering",
								mixedLever);
					} finally {
						RailLogic.setRailPowerLimit(configuredPowerLimit);
					}
				})
				.thenSucceed();
	}

	private static void measureRealDepower(
			GameTestHelper helper, String label, BlockPos lever) {
		RailBenchmarkRunner.measureAndReportDirectional(
				helper, label, INITIAL_OPERATIONS,
				operations -> RailBenchmarkRunner.measureLeverTransitions(
						helper, lever, operations, false));
	}

	@SuppressWarnings("null")
	private static void placeDirectLever(GameTestHelper helper, BlockPos lever) {
		helper.setBlock(lever.below(), Blocks.SMOOTH_STONE);
		helper.setBlock(lever, Blocks.LEVER.defaultBlockState()
				.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
				.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
				.setValue(LeverBlock.POWERED, false));
	}

	private static void measure(
			GameTestHelper helper, String label,
			java.util.function.IntToLongFunction measurement) {
		RailBenchmarkRunner.measureAndReportIsolated(
				helper, label, INITIAL_OPERATIONS, measurement);
	}

	private static BlockPos[] straightRails(BlockPos start) {
		BlockPos[] rails = new BlockPos[RAIL_COUNT];
		for (int index = 0; index < rails.length; index++) {
			rails[index] = start.east(index);
		}
		return rails;
	}

	private static BlockPos[] mixedSlopeRails(int startX, int railY, int z) {
		int[] heightOffsets = new int[] { 3, 2, 1, 0, 0, 1, 2, 3 };
		BlockPos[] rails = new BlockPos[RAIL_COUNT];
		for (int index = 0; index < rails.length; index++) {
			rails[index] = new BlockPos(
					startX + index, railY + heightOffsets[index & 7], z);
		}
		return rails;
	}

	private static RailShape[] mixedSlopeShapes() {
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
		RailShape[] shapes = new RailShape[RAIL_COUNT];
		for (int index = 0; index < shapes.length; index++) {
			shapes[index] = shapeCycle[index & 7];
		}
		return shapes;
	}

	private static RailShape[] straightRailShapes() {
		RailShape[] shapes = new RailShape[RAIL_COUNT];
		java.util.Arrays.fill(shapes, RailShape.EAST_WEST);
		return shapes;
	}

	@SuppressWarnings("null")
	private static void placeOptimizedRailPath(
			GameTestHelper helper, BlockPos[] rails, RailShape[] shapes) {
		for (int index = 0; index < rails.length; index++) {
			BlockPos rail = rails[index];
			RailShape shape = shapes[index];
			helper.setBlock(rail.below(), Blocks.STONE);
			switch (shape) {
				case ASCENDING_EAST -> helper.setBlock(rail.east(), Blocks.STONE);
				case ASCENDING_WEST -> helper.setBlock(rail.west(), Blocks.STONE);
				case ASCENDING_NORTH -> helper.setBlock(rail.north(), Blocks.STONE);
				case ASCENDING_SOUTH -> helper.setBlock(rail.south(), Blocks.STONE);
				default -> {
				}
			}
			helper.setBlock(rail, Blocks.POWERED_RAIL.defaultBlockState()
					.setValue(PoweredRailBlock.SHAPE, shape));
		}
	}

	@SuppressWarnings("null")
	private static BlockPos[] absolutePositions(
			GameTestHelper helper, BlockPos[] relativePositions) {
		BlockPos[] absolutePositions = new BlockPos[relativePositions.length];
		for (int index = 0; index < relativePositions.length; index++) {
			absolutePositions[index] = helper.absolutePos(relativePositions[index]);
		}
		return absolutePositions;
	}

	private static int relativeYAtSectionLocal(
			GameTestHelper helper, int desiredLocalY) {
		int absoluteOriginY = helper.absolutePos(BlockPos.ZERO).getY();
		return Math.floorMod(desiredLocalY - absoluteOriginY, 16) + 16;
	}

	private static int relativeCoordinateAtChunkLocal(
			GameTestHelper helper, Direction.Axis axis, int desiredLocalCoordinate) {
		BlockPos absoluteOrigin = helper.absolutePos(BlockPos.ZERO);
		int absoluteCoordinate = axis == Direction.Axis.X
				? absoluteOrigin.getX()
				: absoluteOrigin.getZ();
		return Math.floorMod(desiredLocalCoordinate - absoluteCoordinate, 16) + 16;
	}
}
