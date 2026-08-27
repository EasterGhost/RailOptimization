package RailOptimization.gametest;

import RailOptimization.RailHotPathBenchmarkAccess;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationPerformanceGameTestWorldStages extends RailOptimizationGameTestSupport {
	private static final int RAIL_COUNT = 64;
	private static final int INITIAL_WRITE_OPERATIONS = 1 << 10;
	private static final int INITIAL_NOTIFICATION_OPERATIONS = 1 << 12;

	@GameTest(environment = "railoptimization-gametest:serial_127", maxTicks = 200, padding = 100)
	public void levelChunkStateWriteCostIsMeasured(GameTestHelper helper) {
		measureRailWriteStage(helper, "LevelChunk rail-state palette write", WriteStage.CHUNK);
	}

	@GameTest(environment = "railoptimization-gametest:serial_128", maxTicks = 200, padding = 100)
	public void blockStateNeighborShapePropagationCostIsMeasured(GameTestHelper helper) {
		measureRailWriteStage(helper, "six-direction BlockState neighbor-shape propagation", WriteStage.SHAPE);
	}

	@GameTest(environment = "railoptimization-gametest:serial_129", maxTicks = 200, padding = 100)
	public void clientUpdateRegistrationCostIsMeasured(GameTestHelper helper) {
		measureRailWriteStage(helper, "same-tick coalesced client-update registration", WriteStage.CLIENT_UPDATE);
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_130", maxTicks = 200, padding = 40)
	public void singleNeighborChangedDispatchCostIsMeasured(GameTestHelper helper) {
		BlockPos target = chunkAlignedPos(helper, 8, 8);
		helper.setBlock(target, Blocks.SMOOTH_STONE);
		measureNotificationStage(helper, target, "single inert neighborChanged dispatch", true);
	}

	@GameTest(environment = "railoptimization-gametest:serial_131", maxTicks = 200, padding = 40)
	public void sixNeighborDispatchCostIsMeasured(GameTestHelper helper) {
		BlockPos center = chunkAlignedPos(helper, 8, 8);
		for (Direction direction : Direction.values()) {
			helper.setBlock(center.relative(direction), Blocks.SMOOTH_STONE);
		}
		measureNotificationStage(helper, center, "six-neighbor updateNeighborsAt dispatch", false);
	}

	@GameTest(environment = "railoptimization-gametest:serial_132", maxTicks = 200, padding = 100)
	public void levelSetBlockCostIsMeasured(GameTestHelper helper) {
		measureRailWriteStage(helper, "complete Level.setBlock rail-state write", WriteStage.LEVEL);
	}

	@SuppressWarnings("null")
	private static void measureRailWriteStage(
			GameTestHelper helper, String label, WriteStage stage) {
		BlockPos start = chunkAlignedPos(helper, 0, 8);
		BlockPos[] rails = new BlockPos[RAIL_COUNT];
		for (int index = 0; index < rails.length; index++) {
			rails[index] = start.relative(Direction.EAST, index);
		}
		placeRailLine(helper, start, Direction.EAST, rails.length, RailShape.EAST_WEST);

		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					BlockPos[] absoluteRails = new BlockPos[rails.length];
					for (int index = 0; index < rails.length; index++) {
						absoluteRails[index] = helper.absolutePos(rails[index]);
					}
					var probe = RailHotPathBenchmarkAccess.worldWriteProbe(
							helper.getLevel(), absoluteRails);
					RailBenchmarkRunner.measureAndReportIsolated(
							helper, label, INITIAL_WRITE_OPERATIONS,
							operations -> stage.measure(probe, operations));
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	private static void measureNotificationStage(
			GameTestHelper helper, BlockPos pos, String label, boolean single) {
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					var probe = RailHotPathBenchmarkAccess.notificationProbe(
							helper.getLevel(), helper.absolutePos(pos));
					RailBenchmarkRunner.measureAndReportIsolated(
							helper, label, INITIAL_NOTIFICATION_OPERATIONS,
							single ? probe::measureSingleNeighborChanged : probe::measureSixNeighborUpdate);
				})
				.thenSucceed();
	}

	private enum WriteStage {
		CHUNK {
			@Override
			long measure(RailHotPathBenchmarkAccess.WorldWriteProbe probe, int operations) {
				return probe.measureChunkStateWrite(operations);
			}
		},
		SHAPE {
			@Override
			long measure(RailHotPathBenchmarkAccess.WorldWriteProbe probe, int operations) {
				return probe.measureShapeNotifications(operations);
			}
		},
		CLIENT_UPDATE {
			@Override
			long measure(RailHotPathBenchmarkAccess.WorldWriteProbe probe, int operations) {
				return probe.measureClientUpdateRegistration(operations);
			}
		},
		LEVEL {
			@Override
			long measure(RailHotPathBenchmarkAccess.WorldWriteProbe probe, int operations) {
				return probe.measureLevelSetBlock(operations);
			}
		};

		abstract long measure(RailHotPathBenchmarkAccess.WorldWriteProbe probe, int operations);
	}

	private static BlockPos chunkAlignedPos(GameTestHelper helper, int localX, int localZ) {
		BlockPos absoluteOrigin = helper.absolutePos(new BlockPos(0, RAIL_Y, 0));
		int relativeX = Math.floorMod(localX - absoluteOrigin.getX(), 16);
		int relativeZ = Math.floorMod(localZ - absoluteOrigin.getZ(), 16);
		return new BlockPos(relativeX < 3 ? relativeX + 16 : relativeX, RAIL_Y,
				relativeZ < 3 ? relativeZ + 16 : relativeZ);
	}
}
