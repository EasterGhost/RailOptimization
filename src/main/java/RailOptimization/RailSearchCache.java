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
    private final int[] distances;
    private int size;

    RailSearchCache(int railPowerLimit) {
        int capacity = railPowerLimit >= MAX_CAPACITY / 8
                ? MAX_CAPACITY
                : Math.max(railPowerLimit * 8, MIN_CAPACITY);
        positions = new long[capacity];
        flags = new byte[capacity];
        states = new byte[capacity];
        distances = new int[capacity];
    }

    byte get(long position) {
        return get(position, LANE, -1);
    }

    byte get(long position, byte flags) {
        return get(position, flags, -1);
    }

    byte get(long position, byte flags, int distance) {
        for (int i = size - 1; i >= 0; --i) {
            if (positions[i] == position && this.flags[i] == flags && distances[i] == distance) {
                return states[i];
            }
        }
        return RailLogic.CHECKED_UNKNOWN;
    }

    void put(long position, byte state) {
        put(position, LANE, -1, state);
    }

    void put(long position, byte flags, byte state) {
        put(position, flags, -1, state);
    }

    void put(long position, byte flags, int distance, byte state) {
        for (int i = size - 1; i >= 0; --i) {
            if (positions[i] == position && this.flags[i] == flags && distances[i] == distance) {
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
        distances[size] = distance;
        ++size;
    }

    void retainSearchResults(byte state) {
        int retained = 0;
        for (int i = 0; i < size; ++i) {
            if ((flags[i] & SEARCH) != 0 && states[i] != state) {
                continue;
            }

            positions[retained] = positions[i];
            flags[retained] = flags[i];
            states[retained] = states[i];
            distances[retained] = distances[i];
            ++retained;
        }
        size = retained;
    }
}
