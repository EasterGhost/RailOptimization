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

	private final long[] keys;
	private final byte[] flags;
	private final byte[] states;
	private final int[] searchCosts;
	private final int mask;
	private int size;

	RailSearchCache(int railPowerLimit) {
		int capacity = railPowerLimit >= MAX_CAPACITY / 8
				? MAX_CAPACITY
				: Math.max(railPowerLimit * 8, MIN_CAPACITY);
		capacity = Integer.highestOneBit(capacity - 1) << 1;
		capacity = Math.min(capacity, MAX_CAPACITY);
		keys = new long[capacity];
		flags = new byte[capacity];
		states = new byte[capacity];
		searchCosts = new int[capacity];
		mask = capacity - 1;
		Arrays.fill(flags, (byte) -1);
	}

	byte get(long position) {
		return get(position, LANE);
	}

	byte get(long position, byte entryFlags) {
		int index = find(position, entryFlags);
		return index >= 0 ? states[index] : RailLogic.CHECKED_UNKNOWN;
	}

	void put(long position, byte state) {
		put(position, LANE, state);
	}

	void put(long position, byte entryFlags, byte state) {
		int index = find(position, entryFlags);
		if (index >= 0) {
			states[index] = state;
			return;
		}

		insert(position, entryFlags, state, -1);
	}

	int getPoweredSearchCost(long position, byte entryFlags) {
		int index = find(position, entryFlags);
		return index >= 0 ? searchCosts[index] : -1;
	}

	void putPoweredSearchCost(long position, byte entryFlags, int searchCost) {
		int index = find(position, entryFlags);
		if (index >= 0) {
			searchCosts[index] = Math.min(searchCosts[index], searchCost);
			return;
		}

		insert(position, entryFlags, RailLogic.CHECKED_POWERED, searchCost);
	}

	void removeSearchResults() {
		for (int i = 0; i < keys.length; ++i) {
			if (flags[i] != -1 && (flags[i] & SEARCH) != 0) {
				flags[i] = -1;
				searchCosts[i] = -1;
				--size;
			}
		}
	}

	void clear() {
		Arrays.fill(flags, (byte) -1);
		size = 0;
	}

	private int find(long position, byte entryFlags) {
		int index = (int) ((position * 0x9E3779B97F4A7C15L + entryFlags) & mask);
		int indexShift = ((int) (position >>> 32) & mask) | 1;
		for (int probes = keys.length; probes > 0; --probes) {
			byte slotFlags = flags[index];
			if (slotFlags == -1) {
				return -1;
			}
			if (slotFlags == entryFlags && keys[index] == position) {
				return index;
			}
			index = (index + indexShift) & mask;
		}
		return -1;
	}

	private void insert(long position, byte entryFlags, byte state, int searchCost) {
		if (size == keys.length) {
			return;
		}

		int index = (int) ((position * 0x9E3779B97F4A7C15L + entryFlags) & mask);
		int indexShift = ((int) (position >>> 32) & mask) | 1;
		while (flags[index] != -1) {
			index = (index + indexShift) & mask;
		}

		keys[index] = position;
		flags[index] = entryFlags;
		states[index] = state;
		searchCosts[index] = searchCost;
		++size;
	}
}
