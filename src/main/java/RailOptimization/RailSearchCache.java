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

	private static final int META_OCCUPIED = 1 << 7;
	private static final int META_ENTRY_FLAGS_MASK = META_OCCUPIED - 1;
	private static final int META_BYTE_MASK = 0xFF;
	private static final int META_GEN_SHIFT = 8;
	private static final int META_COST_SHIFT = 16;
	private static final int META_STATE_SHIFT = 24;

	private final long[] keys;
	private final int[] meta;
	private final int mask;
	private int searchGeneration;

	RailSearchCache(int railPowerLimit) {
		int capacity = railPowerLimit >= MAX_CAPACITY / 8 ? MAX_CAPACITY : Math.max(railPowerLimit * 8, MIN_CAPACITY);
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
			meta[index] = (meta[index] & ~(META_BYTE_MASK << META_STATE_SHIFT)) | ((state & META_BYTE_MASK) << META_STATE_SHIFT);
			return;
		}
		if (index == -1) {
			return;
		}

		index = -index - 2;
		keys[index] = position;
		meta[index] = META_OCCUPIED
				| ((state & META_BYTE_MASK) << META_STATE_SHIFT)
				| (META_BYTE_MASK << META_COST_SHIFT)
				| ((searchGeneration & META_BYTE_MASK) << META_GEN_SHIFT)
				| (entryFlags & META_ENTRY_FLAGS_MASK);
	}

	int getPoweredSearchCost(long position, byte entryFlags) {
		int index = findOrEmpty(position, entryFlags);
		if (index < 0 || ((meta[index] >>> META_GEN_SHIFT) & META_BYTE_MASK) != searchGeneration) {
			return -1;
		}
		byte cost = (byte) (meta[index] >>> META_COST_SHIFT);
		return cost == -1 ? -1 : cost & META_BYTE_MASK;
	}

	void putPoweredSearchCost(long position, byte entryFlags, int searchCost) {
		int index = findOrEmpty(position, entryFlags);
		if (index >= 0) {
			if (((meta[index] >>> META_GEN_SHIFT) & META_BYTE_MASK) == searchGeneration) {
				byte oldCost = (byte) (meta[index] >>> META_COST_SHIFT);
				if (oldCost == -1 || searchCost < (oldCost & META_BYTE_MASK)) {
					meta[index] = (meta[index] & ~(META_BYTE_MASK << META_COST_SHIFT)) | ((searchCost & META_BYTE_MASK) << META_COST_SHIFT);
				}
			} else {
				meta[index] = (meta[index]
						& ~((META_BYTE_MASK << META_GEN_SHIFT) | (META_BYTE_MASK << META_COST_SHIFT)))
						| ((searchGeneration & META_BYTE_MASK) << META_GEN_SHIFT)
						| ((searchCost & META_BYTE_MASK) << META_COST_SHIFT);
			}
			return;
		}
		if (index == -1) {
			return;
		}

		index = -index - 2;
		keys[index] = position;
		meta[index] = META_OCCUPIED
				| ((RailLogic.CHECKED_POWERED & META_BYTE_MASK) << META_STATE_SHIFT)
				| ((searchCost & META_BYTE_MASK) << META_COST_SHIFT)
				| ((searchGeneration & META_BYTE_MASK) << META_GEN_SHIFT)
				| (entryFlags & META_ENTRY_FLAGS_MASK);
	}

	void advanceSearchGeneration() {
		if (++searchGeneration > META_BYTE_MASK) {
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
		if ((meta[index] & META_ENTRY_FLAGS_MASK) == entryFlags && keys[index] == position) {
			return index;
		}
		int indexShift = ((int) (position >>> 32) & mask) | 1;
		for (int probes = keys.length - 1; probes > 0; --probes) {
			index = (index + indexShift) & mask;
			if (meta[index] == 0) {
				return -index - 2;
			}
			if ((meta[index] & META_ENTRY_FLAGS_MASK) == entryFlags && keys[index] == position) {
				return index;
			}
		}
		return -1;
	}
}
