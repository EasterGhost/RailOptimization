package RailOptimization;

import java.lang.reflect.Method;
import java.util.SplittableRandom;

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
	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_195", structure = "railoptimization-gametest:batch_write_empty", maxTicks = 20)
	public void directionalSearchMatchesVanillaSnapshots(GameTestHelper helper) {
		Level level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(10, 12, 10));
		// Include the two-block redstone query halo around the snapshot grid.
		helper.assertValueEqual(true, helper.getBounds().contains(origin.offset(-10, -11, -10).getCenter()), "snapshot query lower bound");
		helper.assertValueEqual(true, helper.getBounds().contains(origin.offset(10, 11, 10).getCenter()), "snapshot query upper bound");
		int originalLimit = RailLogicTestAccess.currentPowerLimit();
		int queries = 0;
		try {
			Method vanillaSearch = PoweredRailBlock.class.getDeclaredMethod("findPoweredRailSignal",
					Level.class, BlockPos.class, BlockState.class, boolean.class, int.class);
			vanillaSearch.setAccessible(true);
			RailLogic.setRailPowerLimit(8);
			SplittableRandom random = new SplittableRandom(0x5241494C);
			for (Block block : new Block[]{Blocks.POWERED_RAIL, Blocks.ACTIVATOR_RAIL}) {
				PoweredRailBlock rail = (PoweredRailBlock) block;
				Block other = block == Blocks.POWERED_RAIL ? Blocks.ACTIVATOR_RAIL : Blocks.POWERED_RAIL;
				for (boolean eastWest : new boolean[]{true, false}) {
					RailShape[] shapes = eastWest
							? new RailShape[]{RailShape.EAST_WEST, RailShape.ASCENDING_EAST, RailShape.ASCENDING_WEST}
							: new RailShape[]{RailShape.NORTH_SOUTH, RailShape.ASCENDING_NORTH, RailShape.ASCENDING_SOUTH};
					for (int board = 0; board < 32; ++board) {
						// Frozen query snapshots exclude vertically stacked rails.
						for (int column = -8; column <= 8; ++column) {
							boolean railBelow = false;
							for (int height = -9; height <= 9; ++height) {
								int choice = random.nextInt(20);
								BlockState cell = choice == 0 ? Blocks.REDSTONE_BLOCK.defaultBlockState()
										: choice == 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
								if (choice >= 6 && !railBelow) {
									PoweredRailBlock cellRail = (PoweredRailBlock) (choice < 9 ? other : block);
									RailShape shape = RailShape.values()[random.nextInt(6)];
									cell = cellRail.defaultBlockState().setValue(PoweredRailBlock.SHAPE, shape)
											.setValue(PoweredRailBlock.POWERED, random.nextInt(10) != 0);
								}
								railBelow = cell.getBlock() instanceof PoweredRailBlock;
								BlockPos cellPos = queryPosition(origin, eastWest, column, height);
								if (railBelow) level.setBlock(cellPos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
								level.setBlock(cellPos, cell, Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
							}
						}
						level.setBlock(origin.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
						level.setBlock(origin.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
						for (RailShape shape : shapes) {
							BlockState root = rail.defaultBlockState().setValue(PoweredRailBlock.SHAPE, shape);
							level.setBlock(origin, root, Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
							for (boolean forward : new boolean[]{true, false}) {
								for (int distance : new int[]{0, 3, 7}) {
									boolean expected = (boolean) vanillaSearch.invoke(rail, level, origin, root, forward, distance);
									RailUpdateContext context = new RailUpdateContext(8);
									for (int repeat = 0; repeat < 2; ++repeat) {
										boolean actual = RailSignalSearcher.findPoweredRailSignalFaster(rail, level, origin, root, forward, distance, context);
										helper.assertValueEqual(expected, actual, block + ": board=" + board + ", root=" + shape
												+ ", forward=" + forward + ", distance=" + distance + ", repeat=" + repeat);
										++queries;
									}
								}
							}
						}
					}
					clearQuerySnapshot(level, origin, eastWest);
					for (int limit : new int[]{1, 8, 64}) {
						RailLogic.setRailPowerLimit(limit);
						BlockState root = rail.defaultBlockState().setValue(PoweredRailBlock.SHAPE, shapes[0]);
						BlockState powered = root.setValue(PoweredRailBlock.SHAPE, shapes[2]).setValue(PoweredRailBlock.POWERED, true);
						BlockPos primary = queryPosition(origin, eastWest, eastWest ? 1 : -1, 0);
						level.setBlock(origin, root, Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
						level.setBlock(origin.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
						level.setBlock(primary, Blocks.AIR.defaultBlockState(), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
						level.setBlock(primary.below(), powered, Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
						level.setBlock(primary.below(2), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
						helper.assertValueEqual(false, level.hasNeighborSignal(primary), "primary candidate is not directly powered");
						helper.assertValueEqual(true, level.hasNeighborSignal(primary.below()), "lower candidate is directly powered");
						helper.assertValueEqual(true, RailSignalSearcher.findPoweredRailSignalFaster(rail, level, origin, root, false,
								limit - 1, new RailUpdateContext(limit)), "lower candidate at the final search depth, eastWest=" + eastWest + ", limit=" + limit);
					}
					clearQuerySnapshot(level, origin, eastWest);
					RailLogic.setRailPowerLimit(8);
				}
			}
			helper.assertValueEqual(4608, queries, "cold and warm vanilla query comparisons");
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Unable to compare vanilla rail search", exception);
		} finally {
			clearQuerySnapshot(level, origin, true);
			clearQuerySnapshot(level, origin, false);
			RailLogic.setRailPowerLimit(originalLimit);
		}
		helper.succeed();
	}

	private static BlockPos queryPosition(BlockPos origin, boolean eastWest, int column, int height) {
		return origin.offset(eastWest ? column : 0, height, eastWest ? 0 : column);
	}

	@SuppressWarnings("null")
	private static void clearQuerySnapshot(Level level, BlockPos origin, boolean eastWest) {
		for (int column = -8; column <= 8; ++column) {
			for (int height = -9; height <= 9; ++height) {
				level.setBlock(queryPosition(origin, eastWest, column, height), Blocks.AIR.defaultBlockState(), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
			}
		}
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_193", maxTicks = 20)
	public void knownBackDirectionMatchesTwoDirectionCheck(GameTestHelper helper) {
		assertAxisClassification(helper);
		Level level = helper.getLevel();
		BlockPos previousPos = helper.absolutePos(new BlockPos(4, 4, 4));
		RailUpdateContext context = new RailUpdateContext(8);
		int cases = 0;
		for (Block block : new Block[]{Blocks.POWERED_RAIL, Blocks.ACTIVATOR_RAIL}) {
			PoweredRailBlock rail = (PoweredRailBlock) block;
			Block otherRail = block == Blocks.POWERED_RAIL ? Blocks.ACTIVATOR_RAIL : Blocks.POWERED_RAIL;
			for (RailShape previousShape : PoweredRailBlock.SHAPE.getPossibleValues()) {
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

	private static void assertAxisClassification(GameTestHelper helper) {
		int cases = 0;
		for (RailShape expected : RailShape.values()) {
			for (RailShape actual : RailShape.values()) {
				boolean mismatched = expected == RailShape.EAST_WEST
						? actual == RailShape.NORTH_SOUTH || actual == RailShape.ASCENDING_NORTH || actual == RailShape.ASCENDING_SOUTH
						: actual == RailShape.EAST_WEST || actual == RailShape.ASCENDING_EAST || actual == RailShape.ASCENDING_WEST;
				helper.assertValueEqual(mismatched, RailPath.isMismatchedRailAxis(expected, actual.ordinal()), "axis classification: " + expected + " -> " + actual);
				++cases;
			}
		}
		helper.assertValueEqual(100, cases, "axis classification cases");
	}

	private static boolean referenceDirection(PoweredRailBlock self, Level level, BlockPos pos, BlockState state, boolean forward,
			long previousPos, BlockState previousState, RailUpdateContext context) {
		RailShape shape = RailPath.railShape(state);
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
