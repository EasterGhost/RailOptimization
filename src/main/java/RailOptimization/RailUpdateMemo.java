package RailOptimization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;

public final class RailUpdateMemo {
	private static final int CAPACITY = 256;
	private static final int MASK = CAPACITY - 1;

	private static final ThreadLocal<LaneWriteDepth> LANE_WRITE_DEPTH = ThreadLocal.withInitial(LaneWriteDepth::new);
	private static final ThreadLocal<List<RailUpdateMemo>> MEMOS = ThreadLocal.withInitial(ArrayList::new);
	private static final ThreadLocal<RailUpdateMemo> TOP = new ThreadLocal<>();
	private static volatile int blockChangeEpoch;

	private static final class LaneWriteDepth {
		int value;
	}

	private final long[] keys = new long[CAPACITY];
	private final byte[] used = new byte[CAPACITY];
	private final byte[] poweredFlags = new byte[CAPACITY];
	private final int[] powerLimits = new int[CAPACITY];
	private final int[] epochs = new int[CAPACITY];
	private int size;
	private int writeEpoch;

	RailUpdateMemo() {
	}

	public static void onBlockStateChanged() {
		if (LANE_WRITE_DEPTH.get().value != 0) {
			return;
		}
		++blockChangeEpoch;
	}

	static void beginLaneWrite() {
		LANE_WRITE_DEPTH.get().value++;
	}

	static void endLaneWrite() {
		LANE_WRITE_DEPTH.get().value--;
	}

	static void trackContext(RailUpdateMemo memo) {
		List<RailUpdateMemo> memos = MEMOS.get();
		for (int i = memos.size() - 1; i >= 0; --i) {
			if (memos.get(i).writeEpoch != blockChangeEpoch) {
				memos.remove(i);
			}
		}
		memos.add(memo);
		TOP.set(memo);
	}

	static boolean isConfirmed(long position, int powerLimit, boolean currentPowered) {
		RailUpdateMemo top = TOP.get();
		if (top != null) {
			int result = top.checkEntry(position, powerLimit, currentPowered);
			if (result != 0) {
				return result > 0;
			}
		}
		List<RailUpdateMemo> memos = MEMOS.get();
		for (int i = memos.size() - 1; i >= 0; --i) {
			RailUpdateMemo memo = memos.get(i);
			if (memo == top) {
				continue;
			}
			int result = memo.checkEntry(position, powerLimit, currentPowered);
			if (result != 0) {
				return result > 0;
			}
		}
		return false;
	}

	void beginWalk() {
		Arrays.fill(used, (byte) 0);
		size = 0;
	}

	void confirm(BlockPos pos, boolean powered, int powerLimit) {
		long position = pos.asLong();
		int index = (int) (position * 0x9E3779B97F4A7C15L) & MASK;
		for (int probes = CAPACITY; probes > 0; --probes) {
			if (used[index] == 0) {
				if (size < CAPACITY) {
					used[index] = 1;
					keys[index] = position;
					poweredFlags[index] = (byte) (powered ? 1 : 0);
					powerLimits[index] = powerLimit;
					epochs[index] = blockChangeEpoch;
					writeEpoch = blockChangeEpoch;
					++size;
				}
				return;
			}
			if (keys[index] == position) {
				poweredFlags[index] = (byte) (powered ? 1 : 0);
				powerLimits[index] = powerLimit;
				epochs[index] = blockChangeEpoch;
				writeEpoch = blockChangeEpoch;
				return;
			}
			index = (index + 1) & MASK;
		}
	}

	private int checkEntry(long position, int powerLimit, boolean currentPowered) {
		int index = (int) (position * 0x9E3779B97F4A7C15L) & MASK;
		if (used[index] == 0) {
			return 0;
		}
		if (keys[index] == position) {
			return checkMatch(index, powerLimit, currentPowered);
		}
		for (int probes = CAPACITY - 1; probes > 0; --probes) {
			index = (index + 1) & MASK;
			if (used[index] == 0) {
				return 0;
			}
			if (keys[index] == position) {
				return checkMatch(index, powerLimit, currentPowered);
			}
		}
		return 0;
	}

	private int checkMatch(int index, int powerLimit, boolean currentPowered) {
		if (powerLimits[index] == powerLimit
				&& epochs[index] == blockChangeEpoch
				&& (poweredFlags[index] == 1) == currentPowered) {
			return 1;
		}
		return -1;
	}
}
