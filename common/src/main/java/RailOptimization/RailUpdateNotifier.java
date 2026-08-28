package RailOptimization;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

final class RailUpdateNotifier {
	private static final int SHAPE_UPDATE_FLAGS = Block.UPDATE_CLIENTS;
	private static final int SHAPE_UPDATE_LIMIT = Block.UPDATE_LIMIT - 1;

	private RailUpdateNotifier() {
	}

	static void updateRails(
			Level world, Block sourceBlock, RailShape sourceShape,
			int firstDirectionCount, int secondDirectionCount,
			RailChangeList changedRails, MutableBlockPos scratchPos) {
		boolean flatPath = !changedRails.hasSlope()
				&& (sourceShape == RailShape.EAST_WEST || sourceShape == RailShape.NORTH_SOUTH);
		if (RailPath.RAIL_AXIS[sourceShape.ordinal()] == RailPath.AXIS_EAST_WEST) {
			updateEastWestRails(
					world, sourceBlock, firstDirectionCount, secondDirectionCount,
					changedRails, flatPath, scratchPos);
			return;
		}
		updateNorthSouthRails(
				world, sourceBlock, firstDirectionCount, secondDirectionCount,
				changedRails, flatPath, scratchPos);
	}

	private static void updateEastWestRails(
			Level world, Block sourceBlock, int firstDirectionCount, int secondDirectionCount,
			RailChangeList changedRails, boolean flatPath, MutableBlockPos scratchPos) {
		int firstStart = 1;
		int firstEnd = firstDirectionCount;
		int secondStart = firstEnd + 1;
		int secondEnd = firstEnd + secondDirectionCount;

		for (int index = firstEnd; index >= firstStart; --index) {
			notifyRail(
					world, sourceBlock, changedRails, index,
					flatPath && index == firstEnd ? Direction.WEST : null, scratchPos);
		}
		for (int index = secondEnd; index >= secondStart; --index) {
			notifyRail(
					world, sourceBlock, changedRails, index,
					flatPath && index == secondEnd ? Direction.EAST : null, scratchPos);
		}

		notifyMain(world, sourceBlock, changedRails, 0, scratchPos);
		if (flatPath && firstDirectionCount == 0) {
			notifyOuter(world, sourceBlock, changedRails, 0, Direction.WEST, 0, scratchPos);
		}
		if (flatPath && secondDirectionCount == 0) {
			notifyOuter(world, sourceBlock, changedRails, 0, Direction.EAST, 0, scratchPos);
		}
		notifyShape(world, changedRails, 0, scratchPos);
		notifySupport(world, sourceBlock, changedRails, 0, scratchPos);
		if (flatPath && firstDirectionCount == 0) {
			notifyOuter(world, sourceBlock, changedRails, 0, Direction.WEST, -1, scratchPos);
		}
		if (flatPath && secondDirectionCount == 0) {
			notifyOuter(world, sourceBlock, changedRails, 0, Direction.EAST, -1, scratchPos);
		}
	}

	private static void updateNorthSouthRails(
			Level world, Block sourceBlock, int firstDirectionCount, int secondDirectionCount,
			RailChangeList changedRails, boolean flatPath, MutableBlockPos scratchPos) {
		int firstStart = 1;
		int firstEnd = firstDirectionCount;
		int secondStart = firstEnd + 1;
		int secondEnd = firstEnd + secondDirectionCount;

		notifyMain(world, sourceBlock, changedRails, 0, scratchPos);
		if (flatPath && firstDirectionCount == 0) {
			notifyOuter(world, sourceBlock, changedRails, 0, Direction.SOUTH, 0, scratchPos);
		}
		if (flatPath && secondDirectionCount == 0) {
			notifyOuter(world, sourceBlock, changedRails, 0, Direction.NORTH, 0, scratchPos);
		}

		notifyNorthSouthBranch(
				world, sourceBlock, changedRails, secondStart, secondEnd,
				flatPath ? Direction.NORTH : null, scratchPos);
		notifyNorthSouthBranch(
				world, sourceBlock, changedRails, firstStart, firstEnd,
				flatPath ? Direction.SOUTH : null, scratchPos);

		notifyShape(world, changedRails, 0, scratchPos);
		notifySupport(world, sourceBlock, changedRails, 0, scratchPos);
		if (flatPath && firstDirectionCount == 0) {
			notifyOuter(world, sourceBlock, changedRails, 0, Direction.SOUTH, -1, scratchPos);
		}
		if (flatPath && secondDirectionCount == 0) {
			notifyOuter(world, sourceBlock, changedRails, 0, Direction.NORTH, -1, scratchPos);
		}
	}

	private static void notifyNorthSouthBranch(
			Level world, Block sourceBlock, RailChangeList changedRails,
			int start, int end, Direction outwardDirection, MutableBlockPos scratchPos) {
		for (int index = start; index <= end; ++index) {
			notifyMain(world, sourceBlock, changedRails, index, scratchPos);
			if (outwardDirection != null && index == end) {
				notifyOuter(world, sourceBlock, changedRails, index, outwardDirection, 0, scratchPos);
			}
		}
		for (int index = end; index >= start; --index) {
			notifyShape(world, changedRails, index, scratchPos);
			notifySupport(world, sourceBlock, changedRails, index, scratchPos);
			if (outwardDirection != null && index == end) {
				notifyOuter(world, sourceBlock, changedRails, index, outwardDirection, -1, scratchPos);
			}
		}
	}

	private static void notifyRail(
			Level world, Block sourceBlock, RailChangeList changedRails,
			int index, Direction outwardDirection, MutableBlockPos scratchPos) {
		notifyMain(world, sourceBlock, changedRails, index, scratchPos);
		if (outwardDirection != null) {
			notifyOuter(world, sourceBlock, changedRails, index, outwardDirection, 0, scratchPos);
		}
		notifyShape(world, changedRails, index, scratchPos);
		notifySupport(world, sourceBlock, changedRails, index, scratchPos);
		if (outwardDirection != null) {
			notifyOuter(world, sourceBlock, changedRails, index, outwardDirection, -1, scratchPos);
		}
	}

	@SuppressWarnings("null")
	private static void notifyMain(
			Level world, Block sourceBlock, RailChangeList changedRails,
			int index, MutableBlockPos scratchPos) {
		setPosition(scratchPos, changedRails.position(index), 0);
		world.updateNeighborsAt(scratchPos, sourceBlock);
	}

	@SuppressWarnings("null")
	private static void notifyShape(
			Level world, RailChangeList changedRails, int index, MutableBlockPos scratchPos) {
		setPosition(scratchPos, changedRails.position(index), 0);
		BlockState oldState = changedRails.state(index);
		BlockState newState = world.getBlockState(scratchPos);
		oldState.updateIndirectNeighbourShapes(world, scratchPos, SHAPE_UPDATE_FLAGS, SHAPE_UPDATE_LIMIT);
		newState.updateNeighbourShapes(world, scratchPos, SHAPE_UPDATE_FLAGS, SHAPE_UPDATE_LIMIT);
		newState.updateIndirectNeighbourShapes(world, scratchPos, SHAPE_UPDATE_FLAGS, SHAPE_UPDATE_LIMIT);
	}

	@SuppressWarnings("null")
	private static void notifySupport(
			Level world, Block sourceBlock, RailChangeList changedRails,
			int index, MutableBlockPos scratchPos) {
		long position = changedRails.position(index);
		setPosition(scratchPos, position, -1);
		world.updateNeighborsAt(scratchPos, sourceBlock);
		if (changedRails.isAscending(index)) {
			setPosition(scratchPos, position, 1);
			world.updateNeighborsAt(scratchPos, sourceBlock);
		}
	}

	@SuppressWarnings("null")
	private static void notifyOuter(
			Level world, Block sourceBlock, RailChangeList changedRails,
			int index, Direction direction, int yOffset, MutableBlockPos scratchPos) {
		long position = changedRails.position(index);
		scratchPos.set(
				BlockPos.getX(position) + direction.getStepX(),
				BlockPos.getY(position) + yOffset,
				BlockPos.getZ(position) + direction.getStepZ());
		world.neighborChanged(scratchPos.immutable(), sourceBlock, null);
	}

	private static void setPosition(MutableBlockPos pos, long position, int yOffset) {
		pos.set(BlockPos.getX(position), BlockPos.getY(position) + yOffset, BlockPos.getZ(position));
	}
}
