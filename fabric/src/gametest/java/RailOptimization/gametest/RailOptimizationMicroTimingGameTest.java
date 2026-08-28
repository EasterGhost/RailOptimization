package RailOptimization.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationMicroTimingGameTest extends RailOptimizationGameTestSupport {
	private static final int SETTLE_TICKS = 4;

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_148", maxTicks = 180, padding = 40)
	public void observerPostPlacementOrderMatchesVanilla(GameTestHelper helper) {
		BlockPos firstRail = new BlockPos(3, RAIL_Y, 3);
		BlockPos lastRail = firstRail.south(2);
		BlockPos firstLever = firstRail.west();
		BlockPos lastLever = lastRail.west();
		BlockPos firstPiston = firstRail.east(2);
		BlockPos lastPiston = lastRail.east(2);

		placeObserverCompetitionPair(helper, firstRail);

		helper.startSequence()
				.thenIdle(6)
				.thenExecute(() -> helper.pullLever(mirrorCopy(firstLever)))
				.thenIdle(2)
				.thenExecute(() -> assertPistonWinner(
						helper, mirrorCopy(firstPiston), mirrorCopy(lastPiston), mirrorCopy(lastPiston),
						"vanilla first-source powering"))
				.thenIdle(SETTLE_TICKS)
				.thenExecute(() -> helper.pullLever(mirrorCopy(firstLever)))
				.thenIdle(2)
				.thenExecute(() -> assertPistonWinner(
						helper, mirrorCopy(firstPiston), mirrorCopy(lastPiston), mirrorCopy(lastPiston),
						"vanilla first-source depowering"))
				.thenIdle(SETTLE_TICKS)
				.thenExecute(() -> helper.pullLever(firstLever))
				.thenIdle(2)
				.thenExecute(() -> assertPistonWinner(
						helper, firstPiston, lastPiston, lastPiston, "optimized first-source powering"))
				.thenIdle(SETTLE_TICKS)
				.thenExecute(() -> helper.pullLever(firstLever))
				.thenIdle(2)
				.thenExecute(() -> assertPistonWinner(
						helper, firstPiston, lastPiston, lastPiston, "optimized first-source depowering"))
				.thenIdle(SETTLE_TICKS)
				.thenExecute(() -> helper.pullLever(mirrorCopy(lastLever)))
				.thenIdle(2)
				.thenExecute(() -> assertPistonWinner(
						helper, mirrorCopy(firstPiston), mirrorCopy(lastPiston), mirrorCopy(firstPiston),
						"vanilla last-source powering"))
				.thenIdle(SETTLE_TICKS)
				.thenExecute(() -> helper.pullLever(mirrorCopy(lastLever)))
				.thenIdle(2)
				.thenExecute(() -> assertPistonWinner(
						helper, mirrorCopy(firstPiston), mirrorCopy(lastPiston), mirrorCopy(firstPiston),
						"vanilla last-source depowering"))
				.thenIdle(SETTLE_TICKS)
				.thenExecute(() -> helper.pullLever(lastLever))
				.thenIdle(2)
				.thenExecute(() -> assertPistonWinner(
						helper, firstPiston, lastPiston, firstPiston, "optimized last-source powering"))
				.thenIdle(SETTLE_TICKS)
				.thenExecute(() -> helper.pullLever(lastLever))
				.thenIdle(2)
				.thenExecute(() -> assertPistonWinner(
						helper, firstPiston, lastPiston, firstPiston, "optimized last-source depowering"))
				.thenSucceed();
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_149", maxTicks = 180, padding = 40)
	public void neighborChangedBudOrderMatchesVanilla(GameTestHelper helper) {
		BlockPos firstRail = new BlockPos(3, RAIL_Y, 4);
		BlockPos lastRail = firstRail.east(2);
		BlockPos sourceLever = firstRail.north();
		BlockPos armLever = firstRail.east().south(2).above(2);
		BlockPos firstPiston = firstRail.south();
		BlockPos lastPiston = lastRail.south();

		placeBudOrderCircuitPair(helper, firstRail);

		helper.startSequence()
				.thenIdle(SETTLE_TICKS)
				.thenExecute(() -> helper.pullLever(mirrorCopy(armLever)))
				.thenIdle(SETTLE_TICKS)
				.thenExecute(() -> assertBothPistonsRetracted(
						helper, mirrorCopy(firstPiston), mirrorCopy(lastPiston), "vanilla armed"))
				.thenExecute(() -> helper.pullLever(mirrorCopy(sourceLever)))
				.thenIdle(SETTLE_TICKS)
				.thenExecute(() -> assertPistonWinner(
						helper, mirrorCopy(firstPiston), mirrorCopy(lastPiston), mirrorCopy(lastPiston),
						"vanilla powering"))
				.thenExecute(() -> helper.pullLever(armLever))
				.thenIdle(SETTLE_TICKS)
				.thenExecute(() -> assertBothPistonsRetracted(
						helper, firstPiston, lastPiston, "optimized armed"))
				.thenExecute(() -> helper.pullLever(sourceLever))
				.thenIdle(SETTLE_TICKS)
				.thenExecute(() -> assertBudOutcomesMatch(
						helper, firstPiston, lastPiston, "powering"))
				.thenExecute(() -> {
					helper.pullLever(mirrorCopy(armLever));
					helper.pullLever(armLever);
					wakePistons(helper, mirrorCopy(firstPiston), mirrorCopy(lastPiston));
					wakePistons(helper, firstPiston, lastPiston);
				})
				.thenIdle(SETTLE_TICKS)
				.thenExecute(() -> assertBothPistonsRetracted(
						helper, mirrorCopy(firstPiston), mirrorCopy(lastPiston), "vanilla disarmed"))
				.thenExecute(() -> assertBothPistonsRetracted(
						helper, firstPiston, lastPiston, "optimized disarmed"))
				.thenExecute(() -> {
					helper.pullLever(mirrorCopy(armLever));
					helper.pullLever(armLever);
				})
				.thenIdle(SETTLE_TICKS)
				.thenExecute(() -> assertBothPistonsRetracted(
						helper, mirrorCopy(firstPiston), mirrorCopy(lastPiston), "vanilla rearmed"))
				.thenExecute(() -> assertBothPistonsRetracted(
						helper, firstPiston, lastPiston, "optimized rearmed"))
				.thenExecute(() -> helper.pullLever(mirrorCopy(sourceLever)))
				.thenIdle(SETTLE_TICKS)
				.thenExecute(() -> assertPistonWinner(
						helper, mirrorCopy(firstPiston), mirrorCopy(lastPiston), mirrorCopy(lastPiston),
						"vanilla depowering"))
				.thenExecute(() -> helper.pullLever(sourceLever))
				.thenIdle(SETTLE_TICKS)
				.thenExecute(() -> assertBudOutcomesMatch(
						helper, firstPiston, lastPiston, "depowering"))
				.thenSucceed();
	}

	private static void placeObserverCompetitionPair(GameTestHelper helper, BlockPos firstRail) {
		placeObserverCompetition(helper, firstRail);
		placeObserverCompetition(helper, mirrorCopy(firstRail));
	}

	@SuppressWarnings("null")
	private static void placeObserverCompetition(GameTestHelper helper, BlockPos firstRail) {
		for (int offset = 0; offset < 3; offset++) {
			placeActivatorRailOnGlass(helper, firstRail.south(offset), RailShape.NORTH_SOUTH);
		}

		BlockPos lastRail = firstRail.south(2);
		placeFloorLever(helper, firstRail.west(), Direction.EAST, Blocks.WHITE_STAINED_GLASS);
		placeFloorLever(helper, lastRail.west(), Direction.EAST, Blocks.WHITE_STAINED_GLASS);
		helper.setBlock(firstRail.east(), Blocks.OBSERVER.defaultBlockState()
				.setValue(ObserverBlock.FACING, Direction.WEST));
		helper.setBlock(lastRail.east(), Blocks.OBSERVER.defaultBlockState()
				.setValue(ObserverBlock.FACING, Direction.WEST));
		helper.setBlock(firstRail.east(2), Blocks.PISTON.defaultBlockState()
				.setValue(PistonBaseBlock.FACING, Direction.SOUTH));
		helper.setBlock(lastRail.east(2), Blocks.PISTON.defaultBlockState()
				.setValue(PistonBaseBlock.FACING, Direction.NORTH));
	}

	private static void placeBudOrderCircuitPair(GameTestHelper helper, BlockPos firstRail) {
		placeBudOrderCircuit(helper, firstRail);
		placeBudOrderCircuit(helper, mirrorCopy(firstRail));
	}

	@SuppressWarnings("null")
	private static void placeBudOrderCircuit(GameTestHelper helper, BlockPos firstRail) {
		for (int offset = 0; offset < 3; offset++) {
			placeActivatorRailOnGlass(helper, firstRail.east(offset), RailShape.EAST_WEST);
		}

		BlockPos lastRail = firstRail.east(2);
		placeFloorLever(helper, firstRail.north(), Direction.NORTH, Blocks.WHITE_STAINED_GLASS);
		placeFloorLever(helper, lastRail.north(), Direction.NORTH, Blocks.WHITE_STAINED_GLASS);
		helper.setBlock(firstRail.south(), Blocks.PISTON.defaultBlockState()
				.setValue(PistonBaseBlock.FACING, Direction.EAST));
		helper.setBlock(lastRail.south(), Blocks.PISTON.defaultBlockState()
				.setValue(PistonBaseBlock.FACING, Direction.WEST));

		BlockPos qcRowStart = firstRail.south(2).above();
		for (int offset = 0; offset < 3; offset++) {
			helper.setBlock(qcRowStart.east(offset), Blocks.SMOOTH_STONE);
		}
		helper.setBlock(qcRowStart.above(), Blocks.REDSTONE_WIRE);
		helper.setBlock(qcRowStart.east(2).above(), Blocks.REDSTONE_WIRE);
		placeFloorLever(helper, qcRowStart.east().above(), Direction.NORTH, Blocks.SMOOTH_STONE);
	}

	@SuppressWarnings("null")
	private static void placeActivatorRailOnGlass(
			GameTestHelper helper, BlockPos railPos, RailShape shape) {
		markVanillaForMirrorRail(helper, railPos);
		helper.setBlock(railPos.below(), Blocks.WHITE_STAINED_GLASS);
		helper.setBlock(railPos, Blocks.ACTIVATOR_RAIL.defaultBlockState()
				.setValue(PoweredRailBlock.SHAPE, shape));
	}

	@SuppressWarnings("null")
	private static void placeFloorLever(
			GameTestHelper helper, BlockPos pos, Direction facing, Block support) {
		helper.setBlock(pos.below(), support);
		BlockState lever = Blocks.LEVER.defaultBlockState()
				.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
				.setValue(HorizontalDirectionalBlock.FACING, facing)
				.setValue(LeverBlock.POWERED, false);
		helper.setBlock(pos, lever);
	}

	@SuppressWarnings("null")
	private static void assertPistonWinner(
			GameTestHelper helper, BlockPos firstPiston, BlockPos lastPiston,
			BlockPos expectedWinner, String stage) {
		boolean firstExtended = helper.getBlockState(firstPiston).getValue(PistonBaseBlock.EXTENDED);
		boolean lastExtended = helper.getBlockState(lastPiston).getValue(PistonBaseBlock.EXTENDED);
		boolean expectFirst = expectedWinner.equals(firstPiston);
		helper.assertTrue(firstExtended == expectFirst && lastExtended != expectFirst,
				Component.literal(stage + ": expected piston " + expectedWinner + " to win, first="
						+ firstExtended + ", last=" + lastExtended));
	}

	@SuppressWarnings("null")
	private static void assertBudOutcomesMatch(
			GameTestHelper helper, BlockPos firstPiston, BlockPos lastPiston, String stage) {
		boolean vanillaFirst = helper.getBlockState(mirrorCopy(firstPiston)).getValue(PistonBaseBlock.EXTENDED);
		boolean vanillaLast = helper.getBlockState(mirrorCopy(lastPiston)).getValue(PistonBaseBlock.EXTENDED);
		boolean optimizedFirst = helper.getBlockState(firstPiston).getValue(PistonBaseBlock.EXTENDED);
		boolean optimizedLast = helper.getBlockState(lastPiston).getValue(PistonBaseBlock.EXTENDED);
		helper.assertTrue(vanillaFirst == optimizedFirst && vanillaLast == optimizedLast,
				Component.literal(stage + " BUD outcome mismatch: vanilla=" + vanillaFirst + "/" + vanillaLast
						+ ", optimized=" + optimizedFirst + "/" + optimizedLast));
		assertPistonWinner(helper, firstPiston, lastPiston, lastPiston, "optimized " + stage);
	}

	@SuppressWarnings("null")
	private static void assertBothPistonsRetracted(
			GameTestHelper helper, BlockPos firstPiston, BlockPos lastPiston, String stage) {
		boolean firstExtended = helper.getBlockState(firstPiston).getValue(PistonBaseBlock.EXTENDED);
		boolean lastExtended = helper.getBlockState(lastPiston).getValue(PistonBaseBlock.EXTENDED);
		helper.assertTrue(!firstExtended && !lastExtended,
				Component.literal(stage + ": expected both BUD pistons to be retracted, first="
						+ firstExtended + ", last=" + lastExtended));
	}

	@SuppressWarnings("null")
	private static void wakePistons(
			GameTestHelper helper, BlockPos firstPiston, BlockPos lastPiston) {
		helper.getLevel().updateNeighborsAt(helper.absolutePos(firstPiston).above(), Blocks.AIR);
		helper.getLevel().updateNeighborsAt(helper.absolutePos(lastPiston).above(), Blocks.AIR);
	}
}
