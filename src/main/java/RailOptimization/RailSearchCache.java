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
	private final byte[] searchGenerations;
	private final int mask;
	private byte searchGeneration;

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
		searchGenerations = new byte[capacity];
		mask = capacity - 1;
		Arrays.fill(flags, (byte) -1);
	}

	byte get(long position) {
		return get(position, LANE);
	}

	byte get(long position, byte entryFlags) {
		int index = findOrEmpty(position, entryFlags);
		return index >= 0 ? states[index] : RailLogic.CHECKED_UNKNOWN;
	}

	void put(long position, byte state) {
		put(position, LANE, state);
	}

	void put(long position, byte entryFlags, byte state) {
		int index = findOrEmpty(position, entryFlags);
		if (index >= 0) {
			states[index] = state;
			return;
		}
		if (index == -1) {
			return;
		}

		index = -index - 2;
		keys[index] = position;
		flags[index] = entryFlags;
		states[index] = state;
		searchCosts[index] = -1;
	}

	int getPoweredSearchCost(long position, byte entryFlags) {
		int index = findOrEmpty(position, entryFlags);
		return index >= 0 && searchGenerations[index] == searchGeneration ? searchCosts[index] : -1;
	}

	void putPoweredSearchCost(long position, byte entryFlags, int searchCost) {
		int index = findOrEmpty(position, entryFlags);
		if (index >= 0) {
			if (searchGenerations[index] == searchGeneration) {
				searchCosts[index] = Math.min(searchCosts[index], searchCost);
			} else {
				searchCosts[index] = searchCost;
				searchGenerations[index] = searchGeneration;
			}
			return;
		}
		if (index == -1) {
			return;
		}

		index = -index - 2;
		keys[index] = position;
		flags[index] = entryFlags;
		states[index] = RailLogic.CHECKED_POWERED;
		searchCosts[index] = searchCost;
		searchGenerations[index] = searchGeneration;
	}

	void advanceSearchGeneration() {
		if (++searchGeneration == 0) {
			clear();
			searchGeneration = 1;
		}
	}

	void clear() {
		Arrays.fill(flags, (byte) -1);
	}

	private int findOrEmpty(long position, byte entryFlags) {
		int index = (int) ((position * 0x9E3779B97F4A7C15L + entryFlags) & mask);
		byte slotFlags = flags[index];
		if (slotFlags == -1) {
			return -index - 2;
		}
		if (slotFlags == entryFlags && keys[index] == position) {
			return index;
		}
		int indexShift = ((int) (position >>> 32) & mask) | 1;
		for (int probes = keys.length - 1; probes > 0; --probes) {
			index = (index + indexShift) & mask;
			slotFlags = flags[index];
			if (slotFlags == -1) {
				return -index - 2;
			}
			if (slotFlags == entryFlags && keys[index] == position) {
				return index;
			}
		}
		return -1;
	}
}
