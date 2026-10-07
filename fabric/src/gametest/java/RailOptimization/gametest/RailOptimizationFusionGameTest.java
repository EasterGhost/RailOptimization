package RailOptimization.gametest;

import RailOptimization.LevelEpochAccess;
import RailOptimization.RailLogic;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationFusionGameTest extends RailOptimizationGameTestSupport {
	@GameTest(environment = "railoptimization-gametest:serial_189", maxTicks = 80, padding = 150)
	public void eastwardOuterSourcesMatchVanilla(GameTestHelper helper) {
		verifyDirection(helper, Direction.EAST);
	}

	@GameTest(environment = "railoptimization-gametest:serial_190", maxTicks = 80, padding = 150)
	public void westwardOuterSourcesMatchVanilla(GameTestHelper helper) {
		verifyDirection(helper, Direction.WEST);
	}

	@GameTest(environment = "railoptimization-gametest:serial_191", maxTicks = 80, padding = 150)
	public void southwardOuterSourcesMatchVanilla(GameTestHelper helper) {
		verifyDirection(helper, Direction.SOUTH);
	}

	@GameTest(environment = "railoptimization-gametest:serial_192", maxTicks = 80, padding = 150)
	public void northwardOuterSourcesMatchVanilla(GameTestHelper helper) {
		verifyDirection(helper, Direction.NORTH);
	}

	private static void verifyDirection(GameTestHelper helper, Direction direction) {
		RailLogic.setRailPowerLimit(8);
		RailLogic.setOptimizationEnabled(true);
		Direction side = direction.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
		for (int prefix : new int[]{1, 4, 7, 10}) {
			BlockPos start = new BlockPos(40, RAIL_Y, 48).relative(side, prefix * 4);
			BlockPos[] rails = new BlockPos[28];
			RailShape[] shapes = new RailShape[rails.length];
			for (int index = 0; index < rails.length; ++index) {
				int height = Math.max(0, Math.min(2, index - prefix - 1));
				rails[index] = start.relative(direction, index).above(height);
				shapes[index] = index == prefix + 1 || index == prefix + 2 ? ascendingShape(direction)
						: direction.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
			}
			placeRailPathPair(helper, rails, shapes);
			BlockPos first = rails[0].relative(side);
			placeLeverPair(helper, first);
			for (int sourceIndex : new int[]{9, 16, 17}) {
				BlockPos second = rails[sourceIndex].relative(side);
				placeLeverPair(helper, second);
				pullLeverPair(helper, first);
				verifyState(helper, rails, shapes, sourceIndex, true, false);
				pullLeverPair(helper, second);
				verifyState(helper, rails, shapes, sourceIndex, true, true);
				pullLeverPair(helper, first);
				verifyState(helper, rails, shapes, sourceIndex, false, true);
				pullLeverPair(helper, second);
				verifyState(helper, rails, shapes, sourceIndex, false, false);
			}
		}
		helper.succeed();
	}

	private static RailShape ascendingShape(Direction direction) {
		return switch (direction) {
			case EAST -> RailShape.ASCENDING_EAST;
			case WEST -> RailShape.ASCENDING_WEST;
			case NORTH -> RailShape.ASCENDING_NORTH;
			case SOUTH -> RailShape.ASCENDING_SOUTH;
			default -> throw new IllegalArgumentException("Horizontal direction required");
		};
	}

	private static void placeLeverPair(GameTestHelper helper, BlockPos lever) {
		for (BlockPos pos : new BlockPos[]{lever, mirrorCopy(lever)}) {
			helper.setBlock(pos.below(), Blocks.STONE);
			helper.setBlock(pos, Blocks.LEVER.defaultBlockState()
					.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
					.setValue(LeverBlock.POWERED, false));
		}
	}

	private static void pullLeverPair(GameTestHelper helper, BlockPos lever) {
		helper.pullLever(mirrorCopy(lever));
		helper.pullLever(lever);
	}

	private static void assertState(GameTestHelper helper, BlockPos[] rails, RailShape[] shapes, int sourceIndex, boolean first, boolean second) {
		assertMatchingRailPower(helper, mirrorCopy(rails), rails);
		for (int index = 0; index < rails.length; ++index) {
			boolean powered = first && index <= 8 || second && Math.abs(index - sourceIndex) <= 8;
			for (BlockPos pos : new BlockPos[]{rails[index], mirrorCopy(rails[index])}) {
				helper.assertBlockProperty(pos, PoweredRailBlock.POWERED, powered);
				helper.assertBlockProperty(pos, PoweredRailBlock.SHAPE, shapes[index]);
			}
		}
	}

	private static void verifyState(GameTestHelper helper, BlockPos[] rails, RailShape[] shapes, int sourceIndex, boolean first, boolean second) {
		assertState(helper, rails, shapes, sourceIndex, first, second);
		var epoch = ((LevelEpochAccess) helper.getLevel()).railoptimization$getBlockChangeEpoch();
		long before = epoch.get();
		for (BlockPos rail : rails) {
			helper.getLevel().neighborChanged(helper.absolutePos(mirrorCopy(rail)), Blocks.LEVER, null);
			helper.getLevel().neighborChanged(helper.absolutePos(rail), Blocks.LEVER, null);
		}
		helper.assertValueEqual(before, epoch.get(), "Unchanged rail updates must not write block state");
		assertState(helper, rails, shapes, sourceIndex, first, second);
	}
}
