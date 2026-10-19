package RailOptimization;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

final class RailChangeList {
	private final long[] positions;
	private final BlockState[] states;
	private final BlockState[] targets;
	private final boolean[] ascending;
	private int size;
	private boolean hasSlope;

	RailChangeList(int capacity) {
		this.positions = new long[capacity];
		this.states = new BlockState[capacity];
		this.targets = new BlockState[capacity];
		this.ascending = new boolean[capacity];
	}

	void add(BlockPos pos, BlockState state, BlockState target) {
		positions[size] = pos.asLong();
		states[size] = state;
		targets[size] = target;
		boolean isAscending = RailPath.isAscending(state);
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

	long[] positions() {
		return positions;
	}

	long position(int index) {
		return positions[index];
	}

	BlockState[] targets() {
		return targets;
	}

	BlockState target(int index) {
		return targets[index];
	}

	BlockState state(int index) {
		return states[index];
	}

	boolean isAscending(int index) {
		return ascending[index];
	}
}
