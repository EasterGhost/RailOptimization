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

	static void updateRails(Level world, Block sourceBlock, RailShape sourceShape, int firstDirectionCount, int secondDirectionCount,
			RailChangeList changedRails, MutableBlockPos scratchPos) {
		boolean flatPath = !changedRails.hasSlope() && (sourceShape == RailShape.EAST_WEST || sourceShape == RailShape.NORTH_SOUTH);
		if (RailPath.RAIL_AXIS[sourceShape.ordinal()] == RailPath.AXIS_EAST_WEST) {
			updateEastWestRails(world, sourceBlock, firstDirectionCount, secondDirectionCount, changedRails, flatPath, scratchPos);
			return;
		}
		updateNorthSouthRails(world, sourceBlock, firstDirectionCount, secondDirectionCount, changedRails, flatPath, scratchPos);
	}

	@SuppressWarnings("null")
	private static void updateEastWestRails(Level world, Block sourceBlock, int firstDirectionCount, int secondDirectionCount, RailChangeList changedRails,
			boolean flatPath, MutableBlockPos scratchPos) {
		int firstStart = 1;
		int firstEnd = firstDirectionCount;
		int secondStart = firstEnd + 1;
		int secondEnd = firstEnd + secondDirectionCount;
		boolean earlyWestEndpoint = flatPath && firstDirectionCount == 0 && secondDirectionCount > 0;

		for (int index = firstEnd; index >= firstStart; --index) {
			notifyRail(world, sourceBlock, changedRails, index, scratchPos);
		}
		if (earlyWestEndpoint) {
			notifyOuter(world, sourceBlock, changedRails, 0, Direction.WEST, 0, scratchPos);
		}
		for (int index = secondEnd; index >= secondStart; --index) {
			notifyRail(world, sourceBlock, changedRails, index, scratchPos);
		}

		if (earlyWestEndpoint) {
			setPosition(scratchPos, changedRails.position(0), 0);
			world.updateNeighborsAtExceptFromFacing(scratchPos, sourceBlock, Direction.WEST);
		} else {
			notifyMain(world, sourceBlock, changedRails, 0, scratchPos);
		}
		notifyShape(world, changedRails, 0, scratchPos);
		notifySupport(world, sourceBlock, changedRails, 0, scratchPos);
	}

	@SuppressWarnings("null")
	private static void updateNorthSouthRails(Level world, Block sourceBlock, int firstDirectionCount, int secondDirectionCount, RailChangeList changedRails,
			boolean flatPath, MutableBlockPos scratchPos) {
		int firstStart = 1;
		int firstEnd = firstDirectionCount;
		int secondStart = firstEnd + 1;
		int secondEnd = firstEnd + secondDirectionCount;
		boolean deferredSouthEndpoint = flatPath && firstDirectionCount == 0 && secondDirectionCount > 0;

		if (deferredSouthEndpoint) {
			setPosition(scratchPos, changedRails.position(0), 0);
			world.updateNeighborsAtExceptFromFacing(scratchPos, sourceBlock, Direction.SOUTH);
		} else {
			notifyMain(world, sourceBlock, changedRails, 0, scratchPos);
		}

		notifyNorthSouthBranch(world, sourceBlock, changedRails, secondStart, secondEnd, scratchPos);
		if (deferredSouthEndpoint) {
			notifyDeferredSouthEndpoint(world, sourceBlock, changedRails.position(0), scratchPos);
		}
		notifyNorthSouthBranch(world, sourceBlock, changedRails, firstStart, firstEnd, scratchPos);

		notifyShape(world, changedRails, 0, scratchPos);
		notifySupport(world, sourceBlock, changedRails, 0, scratchPos);
	}

	@SuppressWarnings("null")
	private static void notifyDeferredSouthEndpoint(Level world, Block sourceBlock, long sourcePosition, MutableBlockPos scratchPos) {
		scratchPos.set(BlockPos.getX(sourcePosition), BlockPos.getY(sourcePosition), BlockPos.getZ(sourcePosition) + 1);
		BlockPos endpoint = scratchPos.immutable();
		world.neighborChanged(endpoint, sourceBlock, BlockPos.of(sourcePosition));
	}

	private static void notifyNorthSouthBranch(Level world, Block sourceBlock, RailChangeList changedRails, int start, int end,
			MutableBlockPos scratchPos) {
		for (int index = start; index <= end; ++index) {
			notifyMain(world, sourceBlock, changedRails, index, scratchPos);
		}
		for (int index = end; index >= start; --index) {
			notifyShape(world, changedRails, index, scratchPos);
			notifySupport(world, sourceBlock, changedRails, index, scratchPos);
		}
	}

	private static void notifyRail(Level world, Block sourceBlock, RailChangeList changedRails, int index,
			MutableBlockPos scratchPos) {
		notifyMain(world, sourceBlock, changedRails, index, scratchPos);
		notifyShape(world, changedRails, index, scratchPos);
		notifySupport(world, sourceBlock, changedRails, index, scratchPos);
	}

	@SuppressWarnings("null")
	private static void notifyMain(Level world, Block sourceBlock, RailChangeList changedRails, int index, MutableBlockPos scratchPos) {
		setPosition(scratchPos, changedRails.position(index), 0);
		world.updateNeighborsAt(scratchPos, sourceBlock);
	}

	@SuppressWarnings("null")
	private static void notifyShape(Level world, RailChangeList changedRails, int index, MutableBlockPos scratchPos) {
		setPosition(scratchPos, changedRails.position(index), 0);
		BlockState oldState = changedRails.state(index);
		BlockState newState = world.getBlockState(scratchPos);
		oldState.updateIndirectNeighbourShapes(world, scratchPos, SHAPE_UPDATE_FLAGS, SHAPE_UPDATE_LIMIT);
		newState.updateNeighbourShapes(world, scratchPos.immutable(), SHAPE_UPDATE_FLAGS, SHAPE_UPDATE_LIMIT);
		newState.updateIndirectNeighbourShapes(world, scratchPos, SHAPE_UPDATE_FLAGS, SHAPE_UPDATE_LIMIT);
	}

	@SuppressWarnings("null")
	private static void notifySupport(Level world, Block sourceBlock, RailChangeList changedRails, int index, MutableBlockPos scratchPos) {
		long position = changedRails.position(index);
		setPosition(scratchPos, position, -1);
		world.updateNeighborsAt(scratchPos, sourceBlock);
		if (changedRails.isAscending(index)) {
			setPosition(scratchPos, position, 1);
			world.updateNeighborsAt(scratchPos, sourceBlock);
		}
	}

	@SuppressWarnings("null")
	private static void notifyOuter(Level world, Block sourceBlock, RailChangeList changedRails, int index, Direction direction, int yOffset,
			MutableBlockPos scratchPos) {
		long position = changedRails.position(index);
		scratchPos.set(BlockPos.getX(position) + direction.getStepX(), BlockPos.getY(position) + yOffset, BlockPos.getZ(position) + direction.getStepZ());
		world.neighborChanged(scratchPos.immutable(), sourceBlock, BlockPos.of(position).offset(0, yOffset, 0));
	}

	private static void setPosition(MutableBlockPos pos, long position, int yOffset) {
		pos.set(BlockPos.getX(position), BlockPos.getY(position) + yOffset, BlockPos.getZ(position));
	}
}
