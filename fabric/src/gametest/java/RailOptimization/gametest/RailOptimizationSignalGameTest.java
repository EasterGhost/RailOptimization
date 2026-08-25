package RailOptimization.gametest;

import RailOptimization.RailLogicTestAccess;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationSignalGameTest extends RailOptimizationGameTestSupport {
	@GameTest(environment = "railoptimization-gametest:serial_63", maxTicks = 200, padding = 50)
	public void indirectlyPoweredThroughSolidBlockMatchesVanilla(GameTestHelper helper) {
		BlockPos rail = new BlockPos(4, RAIL_Y, 4);
		runIndirectPowerTest(helper, rail, rail.north(), RailShape.EAST_WEST);
	}

	@GameTest(environment = "railoptimization-gametest:serial_64", maxTicks = 200, padding = 50)
	public void indirectlyPoweredAcrossChunkBoundaryMatchesVanilla(GameTestHelper helper) {
		int originX = helper.absolutePos(BlockPos.ZERO).getX();
		int railX = 16 - Math.floorMod(originX, 16);
		BlockPos rail = new BlockPos(railX, RAIL_Y, 8);
		runIndirectPowerTest(helper, rail, rail.west(), RailShape.NORTH_SOUTH);
	}

	@SuppressWarnings("null")
	private static void runIndirectPowerTest(
			GameTestHelper helper, BlockPos rail, BlockPos conductor, RailShape shape) {
		BlockPos lever = conductor.above();
		placeRail(helper, rail, shape);
		placeRail(helper, mirrorCopy(rail), shape);
		helper.setBlock(conductor, Blocks.STONE);
		helper.setBlock(mirrorCopy(conductor), Blocks.STONE);
		placeFloorLever(helper, lever);
		placeFloorLever(helper, mirrorCopy(lever));

		helper.startSequence()
				.thenExecute(() -> toggleLeverPair(helper, lever))
				.thenIdle(4)
				.thenExecute(() -> assertPoweredStateMatches(helper, rail, true))
				.thenExecute(() -> toggleLeverPair(helper, lever))
				.thenIdle(4)
				.thenExecute(() -> assertPoweredStateMatches(helper, rail, false))
				.thenSucceed();
	}

	@SuppressWarnings("null")
	private static void assertPoweredStateMatches(
			GameTestHelper helper, BlockPos rail, boolean expected) {
		BlockPos absoluteRail = helper.absolutePos(rail);
		boolean vanillaSignal = helper.getLevel().hasNeighborSignal(absoluteRail);
		boolean optimizedSignal = RailLogicTestAccess.hasNeighborSignalFast(
				helper.getLevel(), absoluteRail);
		helper.assertTrue(vanillaSignal == optimizedSignal,
				Component.literal("neighbor signal mismatch: vanilla=" + vanillaSignal
						+ ", optimized=" + optimizedSignal));
		helper.assertTrue(optimizedSignal == expected,
				Component.literal("expected neighbor signal to be " + expected));
		assertMatchingRailPower(helper, mirrorCopy(rail), rail);
		assertRailsPowered(helper, new BlockPos[] { rail }, expected);
	}

	@SuppressWarnings("null")
	private static void toggleLeverPair(GameTestHelper helper, BlockPos lever) {
		helper.pullLever(mirrorCopy(lever));
		helper.pullLever(lever);
	}

	@SuppressWarnings("null")
	private static void placeFloorLever(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, Blocks.LEVER.defaultBlockState()
				.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
				.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
				.setValue(LeverBlock.POWERED, false));
	}
}
