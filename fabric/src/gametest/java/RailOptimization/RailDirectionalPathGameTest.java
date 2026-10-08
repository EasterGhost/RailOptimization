package RailOptimization;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailDirectionalPathGameTest {
	@GameTest(environment = "railoptimization-gametest:serial_193", maxTicks = 20)
	public void knownBackDirectionMatchesTwoDirectionCheck(GameTestHelper helper) {
		Level level = helper.getLevel();
		BlockPos previousPos = helper.absolutePos(new BlockPos(4, 4, 4));
		RailUpdateContext context = new RailUpdateContext(8);
		int cases = 0;
		for (Block block : new Block[]{Blocks.POWERED_RAIL, Blocks.ACTIVATOR_RAIL}) {
			PoweredRailBlock rail = (PoweredRailBlock) block;
			Block otherRail = block == Blocks.POWERED_RAIL ? Blocks.ACTIVATOR_RAIL : Blocks.POWERED_RAIL;
			for (RailShape previousShape : RailPath.RAIL_SHAPES) {
				if (!RailPath.supportsFastSearch(previousShape)) continue;
				BlockState previous = rail.defaultBlockState().setValue(PoweredRailBlock.SHAPE, previousShape);
				for (boolean forward : new boolean[]{true, false}) {
					int step = (previousShape.ordinal() << 1) | (forward ? 0 : 1);
					for (RailShape shape : RailPath.RAIL_SHAPES) {
						if (RailPath.RAIL_AXIS[shape.ordinal()] != RailPath.RAIL_AXIS[previousShape.ordinal()]) continue;
						BlockState state = rail.defaultBlockState().setValue(PoweredRailBlock.SHAPE, shape);
						RailShape axis = RailPath.STEP_FLAT[step];
						RailShape perpendicular = axis == RailShape.EAST_WEST ? RailShape.NORTH_SOUTH : RailShape.EAST_WEST;
						BlockState railObstruction = rail.defaultBlockState().setValue(PoweredRailBlock.SHAPE, axis);
						BlockState perpendicularRail = rail.defaultBlockState().setValue(PoweredRailBlock.SHAPE, perpendicular);
						BlockState otherRailObstruction = otherRail.defaultBlockState().setValue(PoweredRailBlock.SHAPE, axis);
						for (int height = -2; height <= 2; ++height) {
							BlockPos pos = previousPos.offset(RailPath.STEP_X[step], height, RailPath.STEP_Z[step]);
							for (BlockState obstruction : new BlockState[]{Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(),
									railObstruction, perpendicularRail, otherRailObstruction}) {
								for (boolean direction : new boolean[]{true, false}) {
									int reverseStep = (shape.ordinal() << 1) | (direction ? 0 : 1);
									BlockPos probe = pos.offset(RailPath.STEP_X[reverseStep], RailPath.STEP_Y[reverseStep], RailPath.STEP_Z[reverseStep]);
									level.setBlock(probe.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_KNOWN_SHAPE);
									level.setBlock(probe, obstruction, Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_KNOWN_SHAPE);
									helper.assertValueEqual(obstruction, level.getBlockState(probe), "reverse-candidate fixture state");
								}
								boolean expected = referenceDirection(rail, level, pos, state, true, previousPos.asLong(), previous, context)
										|| referenceDirection(rail, level, pos, state, false, previousPos.asLong(), previous, context);
								boolean actual = RailPath.connectsBackTo(rail, level, pos, state, forward, previousPos.getY(), previous, context);
								helper.assertValueEqual(expected, actual, block + ": " + previousShape + " -> " + shape + ", forward=" + forward
										+ ", height=" + height + ", obstruction=" + obstruction);
								++cases;
							}
						}
					}
				}
			}
		}
		helper.assertValueEqual(1800, cases, "directional geometry cases");
		helper.succeed();
	}

	private static boolean referenceDirection(PoweredRailBlock self, Level level, BlockPos pos, BlockState state, boolean forward,
			long previousPos, BlockState previousState, RailUpdateContext context) {
		RailShape shape = RailPath.railShape(state);
		if (!RailPath.supportsFastSearch(shape)) return false;
		int step = (shape.ordinal() << 1) | (forward ? 0 : 1);
		int x = pos.getX() + RailPath.STEP_X[step];
		int y = pos.getY() + RailPath.STEP_Y[step];
		int z = pos.getZ() + RailPath.STEP_Z[step];
		RailShape axis = RailPath.STEP_FLAT[step];
		if (BlockPos.asLong(x, y, z) == previousPos) {
			return RailPath.isSameRailWithAxis(self, previousState, axis);
		}
		if (RailPath.STEP_BELOW[step] == 0 || BlockPos.asLong(x, y - 1, z) != previousPos
				|| !RailPath.isSameRailWithAxis(self, previousState, axis)) {
			return false;
		}
		context.scratchPos.set(x, y, z);
		return !RailPath.isSameRailWithAxis(self, context.getBlockState(level, context.scratchPos), axis);
	}
}
