package RailOptimization;

final class RailSearchCache {
    static final byte LANE = 0;
    static final byte SEARCH = 1;
    static final byte SEARCH_FORWARD = 1 << 1;
    static final byte SEARCH_NORTH_SOUTH = 1 << 2;
    static final byte DIRECT_SIGNAL = 1 << 3;

    private static final int MIN_CAPACITY = 8;
    private static final int MAX_CAPACITY = 256;

    private final long[] positions;
    private final byte[] flags;
    private final byte[] states;
    private final int[] searchCosts;
    private int size;

    RailSearchCache(int railPowerLimit) {
        int capacity = railPowerLimit >= MAX_CAPACITY / 8
                ? MAX_CAPACITY
                : Math.max(railPowerLimit * 8, MIN_CAPACITY);
        positions = new long[capacity];
        flags = new byte[capacity];
        states = new byte[capacity];
        searchCosts = new int[capacity];
    }

    byte get(long position) {
        return get(position, LANE);
    }

    byte get(long position, byte flags) {
        int index = find(position, flags);
        return index >= 0 ? states[index] : RailLogic.CHECKED_UNKNOWN;
    }

    void put(long position, byte state) {
        put(position, LANE, state);
    }

    void put(long position, byte flags, byte state) {
        int index = find(position, flags);
        if (index >= 0) {
            states[index] = state;
            return;
        }

        if (!append(position, flags, state)) {
            return;
        }
    }

    int getPoweredSearchCost(long position, byte flags) {
        int index = find(position, flags);
        return index >= 0 ? searchCosts[index] : -1;
    }

    void putPoweredSearchCost(long position, byte flags, int searchCost) {
        int index = find(position, flags);
        if (index >= 0) {
            searchCosts[index] = Math.min(searchCosts[index], searchCost);
            return;
        }

        if (append(position, flags, RailLogic.CHECKED_POWERED)) {
            searchCosts[size - 1] = searchCost;
        }
    }

    void removeSearchResults() {
        int retained = 0;
        for (int i = 0; i < size; ++i) {
            if ((flags[i] & SEARCH) != 0) {
                continue;
            }

            positions[retained] = positions[i];
            flags[retained] = flags[i];
            states[retained] = states[i];
            searchCosts[retained] = searchCosts[i];
            ++retained;
        }
        size = retained;
    }

    private int find(long position, byte flags) {
        for (int i = size - 1; i >= 0; --i) {
            if (positions[i] == position && this.flags[i] == flags) {
                return i;
            }
        }
        return -1;
    }

    private boolean append(long position, byte flags, byte state) {
        if (size == positions.length) {
            // Missing cache entries only cause repeated lookups; propagation remains unchanged.
            return false;
        }

        positions[size] = position;
        this.flags[size] = flags;
        states[size] = state;
        ++size;
        return true;
    }
}
