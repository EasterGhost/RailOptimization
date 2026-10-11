package RailOptimization.gametest.batch;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

public final class RailPendingStateTestCases {
	public static void pendingStatesSurviveReuseAndCoordinateCollisions(GameTestHelper helper) {
		for (boolean eastWest : new boolean[] { true, false }) {
			BlockState previous = Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE,
					eastWest ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH);
			for (int limit : new int[] { 1, 8, 64 }) {
				RailBatchTestAccess.Context context = new RailBatchTestAccess.Context(limit);
				for (int pass = 0; pass < 3; ++pass) {
					context.reset();
					context.batchWriter.begin();
					BlockPos origin = new BlockPos(-48 + pass * 160, 20 + pass * 32, -80 + pass * 160);
					int reach = pass == 2 ? 1 : limit;
					BlockPos[] positions = new BlockPos[reach * 2 + 1];
					positions[0] = origin;
					for (int distance = 1; distance <= reach; ++distance) {
						positions[distance] = offset(origin, eastWest, -distance, distance % 4, 0);
						positions[reach + distance] = offset(origin, eastWest, distance, distance % 4, 0);
					}
					for (int index = 0; index < positions.length; ++index) {
						BlockPos pos = positions[index];
						assertPending(helper, context, pos, null);
						context.batchWriter.queue(pos, previous, (index & 1) == 0);
						for (int queued = 0; queued <= index; ++queued) {
							BlockPos pending = positions[queued];
							assertPending(helper, context, pending, previous.setValue(PoweredRailBlock.POWERED, (queued & 1) == 0));
							assertPending(helper, context, pending.above(), null);
							assertPending(helper, context, offset(pending, eastWest, 0, 0, 1), null);
						}
					}
					assertPending(helper, context, offset(origin, eastWest, reach + 1, 0, 0), null);
				}
			}
			RailBatchTestAccess.Context context = new RailBatchTestAccess.Context(8);
			context.batchWriter.begin();
			BlockPos origin = new BlockPos(0, 20, 0);
			BlockPos[] collisions = { origin, origin.above(), offset(origin, eastWest, 0, 0, 1), offset(origin, eastWest, 10, 0, 0) };
			for (BlockPos pos : collisions)
				context.batchWriter.queue(pos, previous, true);
			for (BlockPos pos : collisions)
				assertPending(helper, context, pos, previous.setValue(PoweredRailBlock.POWERED, true));
			context.batchWriter.queue(origin, previous, false);
			assertPending(helper, context, origin, previous);
		}
		helper.succeed();
	}

	private static BlockPos offset(BlockPos origin, boolean eastWest, int along, int up, int sideways) {
		return origin.offset(eastWest ? along : sideways, up, eastWest ? sideways : along);
	}

	private static void assertPending(GameTestHelper helper, RailBatchTestAccess.Context context, BlockPos pos, BlockState expected) {
		helper.assertTrue(context.batchWriter.pendingState(pos) == expected, Component.literal("Pending state mismatch at " + pos));
	}
}
