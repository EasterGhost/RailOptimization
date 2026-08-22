package RailOptimization;

import java.util.Arrays;

final class RailSearchCache {
	static final byte LANE = 0;
	static final byte SEARCH = 1;
	static final byte SEARCH_FORWARD = 1 << 1;
	static final byte SEARCH_NORTH_SOUTH = 1 << 2;
	static final byte DIRECT_SIGNAL = 1 << 3;

	private static final int MIN_CAPACITY = 16;
	private static final int MAX_CAPACITY = 1024;

	private static final int META_SENTINEL = 1 << 31;
	private static final int META_FLAGS = 0xFF;
	private static final int META_GEN_SHIFT = 8;
	private static final int META_COST_SHIFT = 16;
	private static final int META_STATE_SHIFT = 24;

	private final long[] keys;
	private final int[] meta;
	private final int mask;
	private byte searchGeneration;

	RailSearchCache(int railPowerLimit) {
		int capacity = railPowerLimit >= MAX_CAPACITY / 8
				? MAX_CAPACITY
				: Math.max(railPowerLimit * 8, MIN_CAPACITY);
		capacity = Integer.highestOneBit(capacity - 1) << 1;
		capacity = Math.min(capacity, MAX_CAPACITY);
		keys = new long[capacity];
		meta = new int[capacity];
		mask = capacity - 1;
	}

	byte get(long position) {
		return get(position, LANE);
	}

	byte get(long position, byte entryFlags) {
		int index = findOrEmpty(position, entryFlags);
		return index >= 0 ? (byte) (meta[index] >>> META_STATE_SHIFT) : RailLogic.CHECKED_UNKNOWN;
	}

	void put(long position, byte state) {
		put(position, LANE, state);
	}

	void put(long position, byte entryFlags, byte state) {
		int index = findOrEmpty(position, entryFlags);
		if (index >= 0) {
			meta[index] = (meta[index] & ~(META_FLAGS << META_STATE_SHIFT)) | ((state & META_FLAGS) << META_STATE_SHIFT);
			return;
		}
		if (index == -1) {
			return;
		}

		index = -index - 2;
		keys[index] = position;
		meta[index] = META_SENTINEL
				| ((state & META_FLAGS) << META_STATE_SHIFT)
				| (META_FLAGS << META_COST_SHIFT)
				| ((searchGeneration & META_FLAGS) << META_GEN_SHIFT)
				| (entryFlags & META_FLAGS);
	}

	int getPoweredSearchCost(long position, byte entryFlags) {
		int index = findOrEmpty(position, entryFlags);
		if (index < 0 || ((meta[index] >>> META_GEN_SHIFT) & META_FLAGS) != searchGeneration) {
			return -1;
		}
		byte cost = (byte) (meta[index] >>> META_COST_SHIFT);
		return cost == -1 ? -1 : cost & META_FLAGS;
	}

	void putPoweredSearchCost(long position, byte entryFlags, int searchCost) {
		int index = findOrEmpty(position, entryFlags);
		if (index >= 0) {
			if (((meta[index] >>> META_GEN_SHIFT) & META_FLAGS) == searchGeneration) {
				byte oldCost = (byte) (meta[index] >>> META_COST_SHIFT);
				if (oldCost == -1 || searchCost < (oldCost & META_FLAGS)) {
					meta[index] = (meta[index] & ~(META_FLAGS << META_COST_SHIFT)) | ((searchCost & META_FLAGS) << META_COST_SHIFT);
				}
			} else {
				meta[index] = (meta[index] & ~((META_FLAGS << META_GEN_SHIFT) | (META_FLAGS << META_COST_SHIFT)))
						| ((searchGeneration & META_FLAGS) << META_GEN_SHIFT)
						| ((searchCost & META_FLAGS) << META_COST_SHIFT);
			}
			return;
		}
		if (index == -1) {
			return;
		}

		index = -index - 2;
		keys[index] = position;
		meta[index] = META_SENTINEL
				| ((RailLogic.CHECKED_POWERED & META_FLAGS) << META_STATE_SHIFT)
				| ((searchCost & META_FLAGS) << META_COST_SHIFT)
				| ((searchGeneration & META_FLAGS) << META_GEN_SHIFT)
				| (entryFlags & META_FLAGS);
	}

	void advanceSearchGeneration() {
		if (++searchGeneration == 0) {
			clear();
			searchGeneration = 1;
		}
	}

	void clear() {
		Arrays.fill(meta, 0);
	}

	private int findOrEmpty(long position, byte entryFlags) {
		int index = (int) ((position * 0x9E3779B97F4A7C15L + entryFlags) & mask);
		if (meta[index] == 0) {
			return -index - 2;
		}
		if ((meta[index] & META_FLAGS) == entryFlags && keys[index] == position) {
			return index;
		}
		int indexShift = ((int) (position >>> 32) & mask) | 1;
		for (int probes = keys.length - 1; probes > 0; --probes) {
			index = (index + indexShift) & mask;
			if (meta[index] == 0) {
				return -index - 2;
			}
			if ((meta[index] & META_FLAGS) == entryFlags && keys[index] == position) {
				return index;
			}
		}
		return -1;
	}
}
