package RailOptimization.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationEdgeCaseGameTest extends RailOptimizationGameTestSupport {
	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_80", maxTicks = 200, padding = 40)
	public void nestedLanePoweringDuringNotifyMatchesVanilla(GameTestHelper helper) {
		BlockPos startA = new BlockPos(2, RAIL_Y, 2);
		BlockPos sourceA = startA.north();
		BlockPos railA4 = startA.relative(Direction.EAST, 4);
		BlockPos observer = railA4.south();
		BlockPos startB = new BlockPos(2, RAIL_Y, 6);
		BlockPos[] dustLine = new BlockPos[] {
				new BlockPos(6, RAIL_Y, 4),
				new BlockPos(5, RAIL_Y, 4),
				new BlockPos(4, RAIL_Y, 4),
				new BlockPos(3, RAIL_Y, 4),
				new BlockPos(2, RAIL_Y, 4),
				new BlockPos(2, RAIL_Y, 5)
		};

		placeRailLinePair(helper, startA, Direction.EAST, 5, RailShape.EAST_WEST);
		placeRailLinePair(helper, startB, Direction.EAST, 5, RailShape.EAST_WEST);
		placeObserverWatchingRail(helper, observer, Direction.NORTH);
		placeObserverWatchingRail(helper, mirrorCopy(observer), Direction.NORTH);
		for (BlockPos dust : dustLine) {
			helper.setBlock(dust.below(), Blocks.STONE);
			helper.setBlock(mirrorCopy(dust).below(), Blocks.STONE);
			helper.setBlock(dust, Blocks.REDSTONE_WIRE.defaultBlockState());
			helper.setBlock(mirrorCopy(dust), Blocks.REDSTONE_WIRE.defaultBlockState());
		}

		helper.startSequence()
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(sourceA), Blocks.REDSTONE_BLOCK);
					helper.setBlock(sourceA, Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(6)
				.thenExecute(() -> {
					assertMatchingRailLinePower(helper, mirrorCopy(startA), startA, Direction.EAST, 5);
					assertMatchingRailLinePower(helper, mirrorCopy(startB), startB, Direction.EAST, 5);
					assertRailLinePowered(helper, startA, Direction.EAST, 5, true);
					assertRailLinePowered(helper, startB, Direction.EAST, 5, false);
				})
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(sourceA), Blocks.AIR);
					helper.setBlock(sourceA, Blocks.AIR);
				})
				.thenIdle(6)
				.thenExecute(() -> {
					assertMatchingRailLinePower(helper, mirrorCopy(startA), startA, Direction.EAST, 5);
					assertMatchingRailLinePower(helper, mirrorCopy(startB), startB, Direction.EAST, 5);
					assertRailLinePowered(helper, startA, Direction.EAST, 5, false);
					assertRailLinePowered(helper, startB, Direction.EAST, 5, false);
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_81", maxTicks = 200, padding = 40)
	public void pistonTriggeredByLanePowerMidNotifyMatchesVanilla(GameTestHelper helper) {
		BlockPos start = new BlockPos(2, RAIL_Y, 2);
		BlockPos source = start.north();
		BlockPos watchedRail = start.relative(Direction.EAST, 4);
		BlockPos observer = watchedRail.south();
		BlockPos piston = observer.south();
		BlockPos movedBlock = piston.above();

		placeRailLinePair(helper, start, Direction.EAST, 5, RailShape.EAST_WEST);
		placeObserverWatchingRail(helper, observer, Direction.NORTH);
		placeObserverWatchingRail(helper, mirrorCopy(observer), Direction.NORTH);
		helper.setBlock(piston, Blocks.PISTON.defaultBlockState()
				.setValue(PistonBaseBlock.FACING, Direction.UP));
		helper.setBlock(mirrorCopy(piston), Blocks.PISTON.defaultBlockState()
				.setValue(PistonBaseBlock.FACING, Direction.UP));
		helper.setBlock(movedBlock, Blocks.STONE);
		helper.setBlock(mirrorCopy(movedBlock), Blocks.STONE);

		helper.startSequence()
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
					helper.setBlock(source, Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(6)
				.thenExecute(() -> {
					assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, 5);
					assertRailLinePowered(helper, start, Direction.EAST, 5, true);
					helper.assertBlockPresent(Blocks.STONE, movedBlock.above());
					helper.assertBlockPresent(Blocks.STONE, mirrorCopy(movedBlock).above());
					helper.assertBlockNotPresent(Blocks.STONE, movedBlock);
					helper.assertBlockNotPresent(Blocks.STONE, mirrorCopy(movedBlock));
				})
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(source), Blocks.AIR);
					helper.setBlock(source, Blocks.AIR);
				})
				.thenIdle(6)
				.thenExecute(() -> {
					assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, 5);
					assertRailLinePowered(helper, start, Direction.EAST, 5, false);
					helper.assertBlockPresent(Blocks.STONE, movedBlock.above());
					helper.assertBlockPresent(Blocks.STONE, mirrorCopy(movedBlock).above());
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_82", maxTicks = 160, padding = 40)
	public void repeatedUnchangedUpdatesKeepLaneStateConsistent(GameTestHelper helper) {
		BlockPos start = new BlockPos(1, RAIL_Y, 3);
		BlockPos source = start.north();
		BlockPos midRail = start.relative(Direction.EAST, 2);

		placeRailLinePair(helper, start, Direction.EAST, 5, RailShape.EAST_WEST);

		helper.startSequence()
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
					helper.setBlock(source, Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, 5);
					assertRailLinePowered(helper, start, Direction.EAST, 5, true);
				})
				.thenExecute(() -> {
					for (int i = 0; i < 200; i++) {
						helper.getLevel().neighborChanged(helper.absolutePos(mirrorCopy(midRail)), Blocks.STONE, null);
						helper.getLevel().neighborChanged(helper.absolutePos(midRail), Blocks.STONE, null);
					}
				})
				.thenIdle(2)
				.thenExecute(() -> {
					assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, 5);
					assertRailLinePowered(helper, start, Direction.EAST, 5, true);
				})
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(source), Blocks.AIR);
					helper.setBlock(source, Blocks.AIR);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, 5);
					assertRailLinePowered(helper, start, Direction.EAST, 5, false);
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_83", maxTicks = 160, padding = 40)
	public void simultaneousTwoSourceRemovalMatchesVanilla(GameTestHelper helper) {
		BlockPos start = new BlockPos(1, RAIL_Y, 3);
		int length = 10;
		BlockPos firstSource = start.north();
		BlockPos secondSource = start.relative(Direction.EAST, length - 1).north();

		placeRailLinePair(helper, start, Direction.EAST, length, RailShape.EAST_WEST);

		helper.startSequence()
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(firstSource), Blocks.REDSTONE_BLOCK);
					helper.setBlock(firstSource, Blocks.REDSTONE_BLOCK);
					helper.setBlock(mirrorCopy(secondSource), Blocks.REDSTONE_BLOCK);
					helper.setBlock(secondSource, Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, length);
					assertRailLinePowered(helper, start, Direction.EAST, length, true);
				})
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(firstSource), Blocks.AIR);
					helper.setBlock(firstSource, Blocks.AIR);
					helper.setBlock(mirrorCopy(secondSource), Blocks.AIR);
					helper.setBlock(secondSource, Blocks.AIR);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, length);
					assertRailLinePowered(helper, start, Direction.EAST, length, false);
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_87", maxTicks = 160, padding = 40)
	public void curvedRailJunctionNextToPoweredLaneMatchesVanilla(GameTestHelper helper) {
		BlockPos junction = new BlockPos(3, RAIL_Y, 3);
		BlockPos junctionNorth = junction.north();
		BlockPos junctionSouth = junction.south();
		BlockPos junctionEast = junction.east();
		BlockPos laneStart = junction.east(2);
		BlockPos source = laneStart.north();

		placeShapedRail(helper, junction, RailShape.NORTH_SOUTH, Blocks.RAIL);
		placeShapedRail(helper, mirrorCopy(junction), RailShape.NORTH_SOUTH, Blocks.RAIL);
		placeShapedRail(helper, junctionNorth, RailShape.NORTH_SOUTH, Blocks.RAIL);
		placeShapedRail(helper, mirrorCopy(junctionNorth), RailShape.NORTH_SOUTH, Blocks.RAIL);
		placeShapedRail(helper, junctionSouth, RailShape.NORTH_SOUTH, Blocks.RAIL);
		placeShapedRail(helper, mirrorCopy(junctionSouth), RailShape.NORTH_SOUTH, Blocks.RAIL);
		placeShapedRail(helper, junctionEast, RailShape.EAST_WEST, Blocks.RAIL);
		placeShapedRail(helper, mirrorCopy(junctionEast), RailShape.EAST_WEST, Blocks.RAIL);
		placeRailLinePair(helper, laneStart, Direction.EAST, 3, RailShape.EAST_WEST);

		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
					helper.setBlock(source, Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					assertMatchingRailLinePower(helper, mirrorCopy(laneStart), laneStart, Direction.EAST, 3);
					assertRailLinePowered(helper, laneStart, Direction.EAST, 3, true);
					assertRailShapesMatch(helper, junction);
					assertRailShapesMatch(helper, junctionEast);
					assertRailShapesMatch(helper, junctionSouth);
				})
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(source), Blocks.AIR);
					helper.setBlock(source, Blocks.AIR);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					assertMatchingRailLinePower(helper, mirrorCopy(laneStart), laneStart, Direction.EAST, 3);
					assertRailLinePowered(helper, laneStart, Direction.EAST, 3, false);
					assertRailShapesMatch(helper, junction);
					assertRailShapesMatch(helper, junctionEast);
					assertRailShapesMatch(helper, junctionSouth);
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	private static void assertRailShapesMatch(GameTestHelper helper, BlockPos pos) {
		RailShape vanillaShape = helper.getBlockState(mirrorCopy(pos))
				.getValue(((BaseRailBlock) Blocks.RAIL).getShapeProperty());
		RailShape optimizedShape = helper.getBlockState(pos)
				.getValue(((BaseRailBlock) Blocks.RAIL).getShapeProperty());
		helper.assertTrue(vanillaShape == optimizedShape,
				Component.literal("rail shape mismatch at " + pos + ": vanilla="
						+ vanillaShape + ", optimized=" + optimizedShape));
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_102", maxTicks = 160, padding = 40)
	public void asymmetricDualSourceOverlapMatchesVanilla(GameTestHelper helper) {
		BlockPos start = new BlockPos(1, RAIL_Y, 1);

		placeRailLinePair(helper, start, Direction.EAST, 13, RailShape.EAST_WEST);

		BlockPos sourceLeft = start.west();
		BlockPos sourceRight = start.east(13);

		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(sourceLeft), Blocks.REDSTONE_BLOCK);
					helper.setBlock(sourceLeft, Blocks.REDSTONE_BLOCK);

					helper.setBlock(mirrorCopy(sourceRight), Blocks.REDSTONE_BLOCK);
					helper.setBlock(sourceRight, Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, 13);
					assertRailLinePowered(helper, start, Direction.EAST, 13, true);
				})
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(sourceLeft), Blocks.AIR);
					helper.setBlock(sourceLeft, Blocks.AIR);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, 13);
					assertRailLinePowered(helper, start, Direction.EAST, 4, false);
					assertRailLinePowered(helper, start.east(4), Direction.EAST, 9, true);
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_103", maxTicks = 160, padding = 40)
	public void zeroTickPulseFlipMatchesVanilla(GameTestHelper helper) {
		BlockPos start = new BlockPos(2, RAIL_Y, 2);
		placeRailLinePair(helper, start, Direction.EAST, 5, RailShape.EAST_WEST);

		BlockPos source = start.west();

		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
					helper.setBlock(source, Blocks.REDSTONE_BLOCK);

					helper.setBlock(mirrorCopy(source), Blocks.AIR);
					helper.setBlock(source, Blocks.AIR);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, 5);
					assertRailLinePowered(helper, start, Direction.EAST, 5, false);
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_104", maxTicks = 160, padding = 40)
	public void pistonShapeShiftingMatchesVanilla(GameTestHelper helper) {
		BlockPos start = new BlockPos(2, RAIL_Y, 2);
		placeRailLinePair(helper, start, Direction.EAST, 5, RailShape.EAST_WEST);

		BlockPos source = start.west();
		BlockPos pistonPos = start.east(2).north();

		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
					helper.setBlock(source, Blocks.REDSTONE_BLOCK);

					helper.setBlock(mirrorCopy(pistonPos),
							Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.SOUTH));
					helper.setBlock(pistonPos,
							Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.SOUTH));
				})
				.thenIdle(4)
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(pistonPos.above()), Blocks.REDSTONE_BLOCK);
					helper.setBlock(pistonPos.above(), Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(10)
				.thenExecute(() -> {
					assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, 2);
					assertMatchingRailLinePower(helper, mirrorCopy(start).east(3), start.east(3), Direction.EAST, 2);
					assertRailLinePowered(helper, start.east(3), Direction.EAST, 2, false);
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_105", maxTicks = 160, padding = 40)
	public void indirectPowerCascadeMatchesVanilla(GameTestHelper helper) {
		BlockPos railPos = new BlockPos(3, RAIL_Y, 3);
		BlockPos supportBlock = railPos.below();
		BlockPos torchPos = supportBlock.below();
		BlockPos baseBlock = torchPos.below();

		helper.setBlock(mirrorCopy(baseBlock), Blocks.STONE);
		helper.setBlock(baseBlock, Blocks.STONE);

		helper.setBlock(mirrorCopy(torchPos), Blocks.REDSTONE_TORCH);
		helper.setBlock(torchPos, Blocks.REDSTONE_TORCH);

		helper.setBlock(mirrorCopy(supportBlock), Blocks.STONE);
		helper.setBlock(supportBlock, Blocks.STONE);

		placeRailLinePair(helper, railPos, Direction.EAST, 3, RailShape.EAST_WEST);

		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					assertMatchingRailLinePower(helper, mirrorCopy(railPos), railPos, Direction.EAST, 3);
					assertRailLinePowered(helper, railPos, Direction.EAST, 3, true);
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_106", maxTicks = 160, padding = 40)
	public void brokenTopologicalLoopMatchesVanilla(GameTestHelper helper) {
		BlockPos center = new BlockPos(3, RAIL_Y, 3);

		placeShapedRail(helper, center.north().west(), RailShape.SOUTH_EAST, Blocks.RAIL);
		placeShapedRail(helper, mirrorCopy(center.north().west()), RailShape.SOUTH_EAST, Blocks.RAIL);

		placeShapedRail(helper, center.north().east(), RailShape.SOUTH_WEST, Blocks.RAIL);
		placeShapedRail(helper, mirrorCopy(center.north().east()), RailShape.SOUTH_WEST, Blocks.RAIL);

		placeShapedRail(helper, center.south().west(), RailShape.NORTH_EAST, Blocks.RAIL);
		placeShapedRail(helper, mirrorCopy(center.south().west()), RailShape.NORTH_EAST, Blocks.RAIL);

		placeShapedRail(helper, center.south().east(), RailShape.NORTH_WEST, Blocks.RAIL);
		placeShapedRail(helper, mirrorCopy(center.south().east()), RailShape.NORTH_WEST, Blocks.RAIL);

		placeShapedRail(helper, center.north(), RailShape.EAST_WEST, Blocks.POWERED_RAIL);
		placeShapedRail(helper, mirrorCopy(center.north()), RailShape.EAST_WEST, Blocks.POWERED_RAIL);

		placeShapedRail(helper, center.south(), RailShape.EAST_WEST, Blocks.POWERED_RAIL);
		placeShapedRail(helper, mirrorCopy(center.south()), RailShape.EAST_WEST, Blocks.POWERED_RAIL);

		placeShapedRail(helper, center.west(), RailShape.NORTH_SOUTH, Blocks.POWERED_RAIL);
		placeShapedRail(helper, mirrorCopy(center.west()), RailShape.NORTH_SOUTH, Blocks.POWERED_RAIL);

		placeShapedRail(helper, center.east(), RailShape.NORTH_SOUTH, Blocks.POWERED_RAIL);
		placeShapedRail(helper, mirrorCopy(center.east()), RailShape.NORTH_SOUTH, Blocks.POWERED_RAIL);

		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(center.north().above()), Blocks.REDSTONE_BLOCK);
					helper.setBlock(center.north().above(), Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					assertMatchingRailPower(helper, mirrorCopy(center.north()), center.north());
					helper.assertBlockProperty(center.north(), PoweredRailBlock.POWERED, true);

					helper.assertBlockProperty(center.south(), PoweredRailBlock.POWERED, false);
					helper.assertBlockProperty(center.west(), PoweredRailBlock.POWERED, false);
					helper.assertBlockProperty(center.east(), PoweredRailBlock.POWERED, false);
				})
				.thenSucceed();
	}
}
