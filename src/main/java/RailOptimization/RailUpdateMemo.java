package RailOptimization;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;

public final class RailUpdateMemo {
    private static final class Entry {
        final boolean powered;
        final int powerLimit;
        final int epoch;

        Entry(boolean powered, int powerLimit, int epoch) {
            this.powered = powered;
            this.powerLimit = powerLimit;
            this.epoch = epoch;
        }
    }

    private static final ThreadLocal<Integer> LANE_WRITE_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static volatile int blockChangeEpoch;
    private static final Map<Long, Entry> CONFIRMED = new HashMap<>();

    private RailUpdateMemo() {
    }

    public static void onBlockStateChanged() {
        if (LANE_WRITE_DEPTH.get() != 0) {
            return;
        }
        ++blockChangeEpoch;
    }

    static void beginLaneWrite() {
        LANE_WRITE_DEPTH.set(LANE_WRITE_DEPTH.get() + 1);
    }

    static void endLaneWrite() {
        LANE_WRITE_DEPTH.set(LANE_WRITE_DEPTH.get() - 1);
    }

    static void beginWalk() {
        CONFIRMED.clear();
    }

    static void confirm(BlockPos pos, boolean powered, int powerLimit) {
        CONFIRMED.put(pos.asLong(), new Entry(powered, powerLimit, blockChangeEpoch));
    }

    static boolean isConfirmed(long position, int powerLimit, boolean currentPowered) {
        Entry entry = CONFIRMED.get(position);
        return entry != null
                && entry.powerLimit == powerLimit
                && entry.epoch == blockChangeEpoch
                && entry.powered == currentPowered;
    }
}
