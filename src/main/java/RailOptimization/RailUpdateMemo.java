package RailOptimization;

import java.util.Arrays;
import net.minecraft.core.BlockPos;

public final class RailUpdateMemo {
    private static final int CAPACITY = 256;
    private static final int MASK = CAPACITY - 1;

    private static final ThreadLocal<Integer> LANE_WRITE_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static volatile int blockChangeEpoch;
    private static final long[] keys = new long[CAPACITY];
    private static final byte[] used = new byte[CAPACITY];
    private static final byte[] poweredFlags = new byte[CAPACITY];
    private static final int[] powerLimits = new int[CAPACITY];
    private static final int[] epochs = new int[CAPACITY];
    private static int size;

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
        Arrays.fill(used, (byte) 0);
        size = 0;
    }

    static void confirm(BlockPos pos, boolean powered, int powerLimit) {
        long position = pos.asLong();
        int index = (int) (position * 0x9E3779B97F4A7C15L) & MASK;
        for (;;) {
            if (used[index] == 0) {
                if (size < CAPACITY) {
                    used[index] = 1;
                    keys[index] = position;
                    poweredFlags[index] = (byte) (powered ? 1 : 0);
                    powerLimits[index] = powerLimit;
                    epochs[index] = blockChangeEpoch;
                    ++size;
                }
                return;
            }
            if (keys[index] == position) {
                poweredFlags[index] = (byte) (powered ? 1 : 0);
                powerLimits[index] = powerLimit;
                epochs[index] = blockChangeEpoch;
                return;
            }
            index = (index + 1) & MASK;
        }
    }

    static boolean isConfirmed(long position, int powerLimit, boolean currentPowered) {
        int index = (int) (position * 0x9E3779B97F4A7C15L) & MASK;
        for (;;) {
            if (used[index] == 0) {
                return false;
            }
            if (keys[index] == position) {
                return powerLimits[index] == powerLimit
                        && epochs[index] == blockChangeEpoch
                        && (poweredFlags[index] == 1) == currentPowered;
            }
            index = (index + 1) & MASK;
        }
    }
}
