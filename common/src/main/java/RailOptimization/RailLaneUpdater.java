package RailOptimization;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

final class RailLaneUpdater {

	private RailLaneUpdater() {
	}

	static void powerLane(PoweredRailBlock self, Level world, BlockPos pos, BlockState mainState, RailShape railShape, RailUpdateContext context,
			boolean directlyPowered) {
		context.memo.beginWalk(world);
		RailUpdateMemo.trackContext(context.memo);
		context.batchWriter.begin();
		RailChangeList changedRails = context.changeList;
		int firstDirectionCount;
		int secondDirectionCount;
		RailUpdateMemo.beginLaneWrite();
		try {
			context.batchWriter.queue(pos, mainState, true);
			firstDirectionCount = setRailPositionsPower(self, world, pos, mainState, context, true, directlyPowered);
			secondDirectionCount = setRailPositionsPower(self, world, pos, mainState, context, false, directlyPowered);
			context.batchWriter.write(world, context);
		} finally {
			RailUpdateMemo.endLaneWrite();
		}

		RailUpdateNotifier.updateRails(world, mainState.getBlock(), railShape, firstDirectionCount, secondDirectionCount, changedRails, context.scratchPos);
	}

	static void dePowerLane(PoweredRailBlock self, Level world, BlockPos pos, BlockState mainState, RailShape railShape, RailUpdateContext context,
			int firstPlan, int secondPlan) {
		context.memo.beginWalk(world);
		RailUpdateMemo.trackContext(context.memo);
		context.beginDepowering();
		RailChangeList changedRails = context.changeList;
		int firstDirectionCount;
		int secondDirectionCount;
		RailUpdateMemo.beginLaneWrite();
		try {
			context.batchWriter.queue(pos, mainState, false);
			firstDirectionCount = firstPlan < 0 ? setRailPositionsDePower(self, world, pos, mainState, true, context)
					: queueConnectedRailsToDepower(firstPlan, context, 0);
			secondDirectionCount = secondPlan < 0 ? setRailPositionsDePower(self, world, pos, mainState, false, context)
					: queueConnectedRailsToDepower(secondPlan, context, context.railPowerLimit);
			context.batchWriter.write(world, context);
		} finally {
			RailUpdateMemo.endLaneWrite();
		}

		RailUpdateNotifier.updateRails(world, mainState.getBlock(), railShape, firstDirectionCount, secondDirectionCount, changedRails, context.scratchPos);
	}

	private static int setRailPositionsPower(PoweredRailBlock self, Level world, BlockPos pos, BlockState sourceState, RailUpdateContext context,
			boolean forward, boolean directlyPowered) {
		int count = 0;
		MutableBlockPos cursor = context.railCursor;
		cursor.set(pos.getX(), pos.getY(), pos.getZ());
		BlockState previousState = sourceState;
		RailShape sourceShape = RailPath.railShape(sourceState);
		RailShape directFlatShape = directlyPowered && (sourceShape == RailShape.EAST_WEST || sourceShape == RailShape.NORTH_SOUTH) ? sourceShape : null;
		boolean directPath = directlyPowered;

		for (int i = 1; i <= context.railPowerLimit; ++i) {
			int previousY = cursor.getY();
			BlockState state = RailPath.findNextRailState(self, world, cursor, previousState, forward, context);
			if (state == null || RailPath.isPowered(state)) {
				break;
			}

			boolean continuesDirectFlatPath = directFlatShape != null && cursor.getY() == previousY && RailPath.railShape(state) == directFlatShape;
			if (!continuesDirectFlatPath) {
				directFlatShape = null;
			}
			boolean continuesDirectPath = directPath
					&& (continuesDirectFlatPath || RailPath.connectsBackTo(self, world, cursor, state, forward, previousY, previousState, context));
			if (!continuesDirectPath) {
				directPath = false;
			}

			if (!continuesDirectPath && !(context.hasNeighborSignal(world, cursor) ||
					RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, true, 0, context) ||
					RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, false, 0, context))) {
				break;
			}

			context.batchWriter.queue(cursor, state, true);
			previousState = state;
			count++;
		}

		return count;
	}

	private static int setRailPositionsDePower(PoweredRailBlock self, Level world, BlockPos pos, BlockState sourceState, boolean forward,
			RailUpdateContext context) {
		RailShape sourceShape = RailPath.railShape(sourceState);
		int straightCount = RailSignalSearcher.countStraightRailsToDepower(self, world, pos, sourceShape, forward, context);
		if (straightCount != RailSignalSearcher.COMPLEX_PATH) {
			return queueStraightRailsToDepower(pos, sourceShape, forward, straightCount, context);
		}
		int connectedCount = RailSignalSearcher.countConnectedRailsToDepower(self, world, pos, sourceState, forward, context);
		if (connectedCount != RailSignalSearcher.COMPLEX_PATH) {
			return queueConnectedRailsToDepower(connectedCount, context, 0);
		}

		int count = 0;
		MutableBlockPos cursor = context.railCursor;
		cursor.set(pos.getX(), pos.getY(), pos.getZ());
		BlockState previousState = sourceState;

		for (int i = 1; i <= context.railPowerLimit; ++i) {
			BlockState state = RailPath.findNextRailState(self, world, cursor, previousState, forward, context);
			if (state == null) {
				break;
			}

			if (!RailPath.isPowered(state) || context.hasNeighborSignal(world, cursor) ||
					RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, true, 0, context) ||
					RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, false, 0, context)) {
				break;
			}

			context.batchWriter.queue(cursor, state, false);
			previousState = state;
			count++;
		}

		return count;
	}

	private static int queueConnectedRailsToDepower(int count, RailUpdateContext context, int offset) {
		MutableBlockPos cursor = context.railCursor;
		for (int index = 0; index < count; ++index) {
			long position = context.connectedRailPositions[offset + index];
			cursor.set(BlockPos.getX(position), BlockPos.getY(position), BlockPos.getZ(position));
			context.batchWriter.queue(cursor, context.straightRailStates[offset + index], false);
		}
		return count;
	}

	private static int queueStraightRailsToDepower(BlockPos pos, RailShape railShape, boolean forward, int count, RailUpdateContext context) {
		int stepIndex = (railShape.ordinal() << 1) | (forward ? 0 : 1);
		int stepX = RailPath.STEP_X[stepIndex];
		int stepY = RailPath.STEP_Y[stepIndex];
		int stepZ = RailPath.STEP_Z[stepIndex];
		MutableBlockPos cursor = context.railCursor;
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();

		for (int index = 0; index < count; ++index) {
			x += stepX;
			y += stepY;
			z += stepZ;
			cursor.set(x, y, z);
			context.batchWriter.queue(cursor, context.straightRailStates[index], false);
		}
		return count;
	}
}
