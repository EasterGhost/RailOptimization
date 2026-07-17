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
    private int size;

    RailSearchCache(int railPowerLimit) {
        int capacity = railPowerLimit >= MAX_CAPACITY / 8
                ? MAX_CAPACITY
                : Math.max(railPowerLimit * 8, MIN_CAPACITY);
        positions = new long[capacity];
        flags = new byte[capacity];
        states = new byte[capacity];
    }

    byte get(long position) {
        return get(position, LANE);
    }

    byte get(long position, byte flags) {
        for (int i = size - 1; i >= 0; --i) {
            if (positions[i] == position && this.flags[i] == flags) {
                return states[i];
            }
        }
        return RailLogic.CHECKED_UNKNOWN;
    }

    void put(long position, byte state) {
        put(position, LANE, state);
    }

    void put(long position, byte flags, byte state) {
        for (int i = size - 1; i >= 0; --i) {
            if (positions[i] == position && this.flags[i] == flags) {
                states[i] = state;
                return;
            }
        }

        if (size == positions.length) {
            // Missing cache entries only cause repeated lookups; propagation remains unchanged.
            return;
        }

        positions[size] = position;
        this.flags[size] = flags;
        states[size] = state;
        ++size;
    }
}
