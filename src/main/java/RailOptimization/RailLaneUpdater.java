package RailOptimization;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.RailShape;

final class RailLaneUpdater {
	private static final BooleanProperty POWERED = PoweredRailBlock.POWERED;
	private static final int UPDATE_FORCE_PLACE = Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_CLIENTS;

	private RailLaneUpdater() {
	}

	static void powerLane(PoweredRailBlock self, Level world, BlockPos pos, BlockState mainState,
			RailShape railShape, RailUpdateContext context, boolean directlyPowered) {
		if (!RailPath.supportsFastSearch(railShape)) {
			return;
		}

		context.memo.beginWalk();
		RailUpdateMemo.trackContext(context.memo);
		context.beginPowering();
		RailSearchCache checkedPos = context.searchCache;
		RailChangeList changedRails = context.changeList;
		int firstDirectionCount;
		int secondDirectionCount;
		RailUpdateMemo.beginLaneWrite();
		try {
			setRailPowerState(world, pos, mainState, true, changedRails, context);
			checkedPos.put(pos.asLong(), RailLogic.CHECKED_POWERED);
			firstDirectionCount = setRailPositionsPower(
					self, world, pos, mainState, context, true, directlyPowered, changedRails);
			secondDirectionCount = setRailPositionsPower(
					self, world, pos, mainState, context, false, directlyPowered, changedRails);
		} finally {
			RailUpdateMemo.endLaneWrite();
		}

		updateChangedRails(world, pos, mainState, railShape, firstDirectionCount, secondDirectionCount,
				changedRails, context);
	}

	static void dePowerLane(PoweredRailBlock self, Level world, BlockPos pos, BlockState mainState,
			RailShape railShape, RailUpdateContext context) {
		if (!RailPath.supportsFastSearch(railShape)) {
			return;
		}

		context.memo.beginWalk();
		RailUpdateMemo.trackContext(context.memo);
		context.beginDepowering();
		RailChangeList changedRails = context.changeList;
		int firstDirectionCount;
		int secondDirectionCount;
		RailUpdateMemo.beginLaneWrite();
		try {
			setRailPowerState(world, pos, mainState, false, changedRails, context);
			firstDirectionCount = setRailPositionsDePower(
					self, world, pos, mainState, true, context, changedRails);
			secondDirectionCount = setRailPositionsDePower(
					self, world, pos, mainState, false, context, changedRails);
		} finally {
			RailUpdateMemo.endLaneWrite();
		}

		updateChangedRails(world, pos, mainState, railShape, firstDirectionCount, secondDirectionCount, changedRails, context);
	}

	private static Direction[] getRailDirections(RailShape railShape) {
		return switch (railShape) {
			case NORTH_SOUTH -> RailPath.NORTH_SOUTH_DIRECTIONS;
			case EAST_WEST -> RailPath.EAST_WEST_DIRECTIONS;
			default -> null;
		};
	}

	private static int setRailPositionsPower(PoweredRailBlock self, Level world, BlockPos pos, BlockState sourceState, RailUpdateContext context,
			boolean forward, boolean directlyPowered, RailChangeList changedRails) {
		int count = 0;
		RailSearchCache checkedPos = context.searchCache;
		MutableBlockPos cursor = context.railCursor;
		cursor.set(pos.getX(), pos.getY(), pos.getZ());
		BlockState previousState = sourceState;
		RailShape sourceShape = RailPath.railShape(sourceState);
		RailShape directFlatShape = directlyPowered && getRailDirections(sourceShape) != null ? sourceShape : null;
		boolean directPath = directlyPowered;

		for (int i = 1; i <= RailLogic.getRailPowerLimit(); ++i) {
			long previousPos = cursor.asLong();
			int previousY = cursor.getY();
			BlockState state = RailPath.findNextRailState(
					self, world, cursor, previousState, forward, context);
			if (state == null) {
				break;
			}

			boolean continuesDirectFlatPath = directFlatShape != null && cursor.getY() == previousY && RailPath.railShape(state) == directFlatShape;
			if (!continuesDirectFlatPath) {
				directFlatShape = null;
			}
			boolean continuesDirectPath = directPath
					&& (continuesDirectFlatPath || RailPath.connectsBackTo(self, world, cursor, state, previousPos, previousState, context));
			if (!continuesDirectPath) {
				directPath = false;
			}

			long posKey = cursor.asLong();
			byte checked = checkedPos.get(posKey);

			if (checked != RailLogic.CHECKED_UNKNOWN) {
				if (checked == RailLogic.CHECKED_BLOCKED) {
					break;
				}
				previousState = state;
				count++;
				continue;
			}

			if (RailPath.isPowered(state) || (!continuesDirectPath && !(context.hasNeighborSignal(world, cursor) ||
					RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, true, 0, context) ||
					RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, false, 0, context)))) {
				checkedPos.put(posKey, RailLogic.CHECKED_BLOCKED);
				break;
			}

			checkedPos.put(posKey, RailLogic.CHECKED_POWERED);
			setRailPowerState(world, cursor, state, true, changedRails, context);
			previousState = state;
			count++;
		}

		return count;
	}

	private static int setRailPositionsDePower(PoweredRailBlock self, Level world, BlockPos pos, BlockState sourceState, boolean forward,
			RailUpdateContext context, RailChangeList changedRails) {
		RailShape sourceShape = RailPath.railShape(sourceState);
		int straightCount = RailSignalSearcher.countStraightRailsToDepower(self, world, pos, sourceShape, forward,
				context);
		if (straightCount != RailSignalSearcher.COMPLEX_PATH) {
			return setStraightRailPositionsDePower(world, pos, sourceShape, forward, straightCount, context,
					changedRails);
		}
		int connectedCount = RailSignalSearcher.countConnectedRailsToDepower(self, world, pos, sourceState, forward,
				context);
		if (connectedCount != RailSignalSearcher.COMPLEX_PATH) {
			return setConnectedRailPositionsDePower(world, connectedCount, context, changedRails);
		}

		int count = 0;
		RailSearchCache checkedPos = context.searchCache;
		MutableBlockPos cursor = context.railCursor;
		cursor.set(pos.getX(), pos.getY(), pos.getZ());
		BlockState previousState = sourceState;

		for (int i = 1; i <= RailLogic.getRailPowerLimit(); ++i) {
			BlockState state = RailPath.findNextRailState(self, world, cursor, previousState, forward, context);
			if (state == null) {
				break;
			}

			long posKey = cursor.asLong();
			byte checked = checkedPos.get(posKey);

			if (checked == RailLogic.CHECKED_BLOCKED) {
				break;
			}

			if (!RailPath.isPowered(state) || context.hasNeighborSignal(world, cursor) ||
					RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, true, 0, context) ||
					RailSignalSearcher.findPoweredRailSignalFaster(self, world, cursor, state, false, 0, context)) {
				checkedPos.put(posKey, RailLogic.CHECKED_BLOCKED);
				break;
			}

			setRailPowerState(world, cursor, state, false, changedRails, context);
			checkedPos.put(posKey, RailLogic.CHECKED_BLOCKED);
			previousState = state;
			count++;
		}

		return count;
	}

	private static int setConnectedRailPositionsDePower(Level world, int count, RailUpdateContext context, RailChangeList changedRails) {
		MutableBlockPos cursor = context.railCursor;
		for (int index = 0; index < count; ++index) {
			long position = context.connectedRailPositions[index];
			cursor.set(BlockPos.getX(position), BlockPos.getY(position), BlockPos.getZ(position));
			setRailPowerState(world, cursor, context.straightRailStates[index], false, changedRails, context);
		}
		return count;
	}

	private static int setStraightRailPositionsDePower(Level world, BlockPos pos, RailShape railShape, boolean forward, int count, RailUpdateContext context,
			RailChangeList changedRails) {
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
			setRailPowerState(world, cursor, context.straightRailStates[index], false, changedRails, context);
		}
		return count;
	}

	@SuppressWarnings("null")
	private static void setRailPowerState(Level world, BlockPos pos, BlockState state, boolean powered, RailChangeList changedRails,
			RailUpdateContext context) {
		world.setBlock(pos, state.setValue(POWERED, powered), UPDATE_FORCE_PLACE);
		context.memo.confirm(pos, powered, RailLogic.getRailPowerLimit());
		changedRails.add(pos, state);
	}

	private static void updateChangedRails(Level world, BlockPos pos, BlockState mainState, RailShape railShape, int firstDirectionCount,
			int secondDirectionCount, RailChangeList changedRails, RailUpdateContext context) {
		Direction[] directions = getRailDirections(railShape);
		if (directions != null && !changedRails.hasSlope()) {
			RailUpdateNotifier.updateRails(railShape == RailShape.EAST_WEST, world, pos, mainState, firstDirectionCount, secondDirectionCount,
					context.scratchPos);
			return;
		}

		Block block = mainState.getBlock();
		MutableBlockPos scratchPos = context.scratchPos;
		for (int i = changedRails.size() - 1; i >= 0; i--) {
			long railPos = changedRails.position(i);
			int x = BlockPos.getX(railPos);
			int y = BlockPos.getY(railPos);
			int z = BlockPos.getZ(railPos);
			RailUpdateNotifier.notifyNeighborChanged(world, x, y, z, block, scratchPos);
			RailUpdateNotifier.notifyNeighborChanged(world, x, y - 1, z, block, scratchPos);

			if (changedRails.isAscending(i)) {
				RailUpdateNotifier.notifyNeighborChanged(world, x, y + 1, z, block, scratchPos);
			}
		}
	}
}
