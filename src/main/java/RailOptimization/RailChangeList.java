package RailOptimization;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;

final class RailChangeList {
	private final long[] positions;
	private final boolean[] ascending;
	private int size;
	private boolean hasSlope;

	RailChangeList(int capacity) {
		this.positions = new long[capacity];
		this.ascending = new boolean[capacity];
	}

	void add(BlockPos pos, BlockState state) {
		positions[size] = pos.asLong();
		boolean isAscending = RailLogic.isAscending(state.getValue(PoweredRailBlock.SHAPE));
		ascending[size] = isAscending;
		hasSlope |= isAscending;
		size++;
	}

	void reset() {
		size = 0;
		hasSlope = false;
	}

	int size() {
		return size;
	}

	boolean hasSlope() {
		return hasSlope;
	}

	long position(int index) {
		return positions[index];
	}

	boolean isAscending(int index) {
		return ascending[index];
	}
}
