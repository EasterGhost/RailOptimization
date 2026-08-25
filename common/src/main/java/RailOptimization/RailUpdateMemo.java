package RailOptimization;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class RailUpdateMemo {
	private static final int CAPACITY = 256;
	private static final int MASK = CAPACITY - 1;
	private static final long HASH_MULTIPLIER = 0x9E3779B97F4A7C15L;
	private static final int HASH_SHIFT = Long.SIZE - Integer.numberOfTrailingZeros(CAPACITY);
	private static final long EPOCH_MASK = 0x00FFFFFFFFFFFFFFL;
	private static final int POWER_LIMIT_SHIFT = 56;
	private static final long POWERED_MASK = 1L << 62;
	private static final long OCCUPIED_MASK = 1L << 63;

	private static final ThreadLocal<LaneWriteDepth> LANE_WRITE_DEPTH = ThreadLocal.withInitial(LaneWriteDepth::new);
	private static final ThreadLocal<List<RailUpdateMemo>> MEMOS = ThreadLocal.withInitial(ArrayList::new);
	private static final ThreadLocal<RailUpdateMemo> TOP = new ThreadLocal<>();
	private static final VarHandle BLOCK_CHANGE_EPOCH_HANDLE;
	private static volatile long blockChangeEpoch;

	static {
		try {
			BLOCK_CHANGE_EPOCH_HANDLE = MethodHandles.lookup().findStaticVarHandle(RailUpdateMemo.class, "blockChangeEpoch", long.class);
		} catch (ReflectiveOperationException exception) {
			throw new ExceptionInInitializerError(exception);
		}
	}

	private static final class LaneWriteDepth {
		int value;
	}

	private final long[] keys = new long[CAPACITY];
	private final long[] meta = new long[CAPACITY];
	private Level ownerLevel;
	private int size;
	private long writeEpoch;

	RailUpdateMemo() {
	}

	public static void onBlockStateChanged() {
		if (LANE_WRITE_DEPTH.get().value != 0) {
			return;
		}
		BLOCK_CHANGE_EPOCH_HANDLE.getAndAdd(1L);
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
			RailUpdateMemo candidate = memos.get(i);
			if (candidate == memo || candidate.writeEpoch != blockChangeEpoch) {
				memos.remove(i);
			}
		}
		memos.add(memo);
		TOP.set(memo);
	}

	static boolean isConfirmed(Level level, long position, int powerLimit, boolean currentPowered) {
		RailUpdateMemo top = TOP.get();
		if (top != null && top.ownerLevel == level) {
			int result = top.checkEntry(position, powerLimit, currentPowered);
			if (result != 0) {
				return result > 0;
			}
		}
		List<RailUpdateMemo> memos = MEMOS.get();
		for (int i = memos.size() - 1; i >= 0; --i) {
			RailUpdateMemo memo = memos.get(i);
			if (memo == top || memo.ownerLevel != level) {
				continue;
			}
			int result = memo.checkEntry(position, powerLimit, currentPowered);
			if (result != 0) {
				return result > 0;
			}
		}
		return false;
	}

	void beginWalk(Level level) {
		ownerLevel = level;
		Arrays.fill(meta, 0L);
		size = 0;
	}

	void bindLevel(Level level) {
		if (ownerLevel != level) {
			beginWalk(level);
		}
	}

	void confirm(BlockPos pos, boolean powered, int powerLimit) {
		long position = pos.asLong();
		long entryMeta = (blockChangeEpoch & EPOCH_MASK)
				| ((long) (powerLimit - 1) << POWER_LIMIT_SHIFT)
				| (powered ? POWERED_MASK : 0)
				| OCCUPIED_MASK;
		int index = hashIndex(position);
		for (int probes = CAPACITY; probes > 0; --probes) {
			if (meta[index] == 0) {
				if (size < CAPACITY) {
					keys[index] = position;
					meta[index] = entryMeta;
					writeEpoch = blockChangeEpoch;
					++size;
				}
				return;
			}
			if (keys[index] == position) {
				meta[index] = entryMeta;
				writeEpoch = blockChangeEpoch;
				return;
			}
			index = (index + 1) & MASK;
		}
	}

	private int checkEntry(long position, int powerLimit, boolean currentPowered) {
		int index = hashIndex(position);
		if (meta[index] == 0) {
			return 0;
		}
		if (keys[index] == position) {
			return meta[index] == expectedMeta(powerLimit, currentPowered) ? 1 : -1;
		}
		for (int probes = CAPACITY - 1; probes > 0; --probes) {
			index = (index + 1) & MASK;
			if (meta[index] == 0) {
				return 0;
			}
			if (keys[index] == position) {
				return meta[index] == expectedMeta(powerLimit, currentPowered) ? 1 : -1;
			}
		}
		return 0;
	}

	private static long expectedMeta(int powerLimit, boolean currentPowered) {
		return (blockChangeEpoch & EPOCH_MASK)
				| ((long) (powerLimit - 1) << POWER_LIMIT_SHIFT)
				| (currentPowered ? POWERED_MASK : 0)
				| OCCUPIED_MASK;
	}

	private static int hashIndex(long position) {
		return (int) ((position * HASH_MULTIPLIER) >>> HASH_SHIFT);
	}
}
