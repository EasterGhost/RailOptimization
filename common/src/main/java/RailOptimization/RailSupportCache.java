package RailOptimization;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

public final class RailSupportCache {

	private static final int SUPPORTED = 1;
	private static final int REUSABLE = 3;
	private static final int CAPACITY = 256;
	private static final long HASH_MULTIPLIER = 0x9E3779B97F4A7C15L;
	private static final int HASH_SHIFT = Long.SIZE - Integer.numberOfTrailingZeros(CAPACITY);
	private static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(Cache::new);

	private RailSupportCache() {
	}

	public static boolean shouldBeRemoved(BlockPos pos, Level level, RailShape shape) {
		Cache cache = CACHE.get();
		if (cache.writeDepth != 0) return (supportFlags(pos, level, shape, false) & SUPPORTED) == 0;
		if (cache.level != level || cache.observedEpoch != cache.epoch.get()) refresh(cache, level);
		long position = pos.asLong();
		int index = (int) ((position * HASH_MULTIPLIER) >>> HASH_SHIFT);
		if (cache.generations[index] == cache.generation && cache.positions[index] == position
				&& cache.shapes[index] == shape.ordinal()) return false;
		return checkAndRecord(cache, pos, level, shape, position, index);
	}

	private static boolean checkAndRecord(Cache cache, BlockPos pos, Level level, RailShape shape, long position, int index) {
		int generation = cache.generation;
		long epoch = cache.observedEpoch;
		int flags = supportFlags(pos, level, shape, true);
		if (flags == REUSABLE && cache.level == level && cache.writeDepth == 0 && cache.generation == generation
				&& cache.epoch.get() == epoch) {
			cache.positions[index] = position;
			cache.shapes[index] = (byte) shape.ordinal();
			cache.generations[index] = generation;
		}
		return (flags & SUPPORTED) == 0;
	}

	private static int supportFlags(BlockPos pos, Level level, RailShape shape, boolean allowReuse) {
		int below = supportFlags(level, pos.below(), allowReuse);
		if (below == 0) return 0;
		BlockPos risingSupport = switch (shape) {
			case ASCENDING_EAST -> pos.east();
			case ASCENDING_WEST -> pos.west();
			case ASCENDING_NORTH -> pos.north();
			case ASCENDING_SOUTH -> pos.south();
			default -> null;
		};
		return risingSupport == null ? below : below & supportFlags(level, risingSupport, allowReuse);
	}

	@SuppressWarnings("null")
	private static int supportFlags(Level level, BlockPos pos, boolean allowReuse) {
		BlockState state = level.getBlockState(pos);
		if (!state.isFaceSturdy(level, pos, Direction.UP, SupportType.RIGID)) return 0;
		return allowReuse && !state.getBlock().hasDynamicShape() && !state.hasBlockEntity()
				&& !(state.getBlock() instanceof PoweredRailBlock) ? REUSABLE : SUPPORTED;
	}

	private static void refresh(Cache cache, Level level) {
		if (cache.level != level) {
			cache.level = level;
			cache.epoch = ((LevelEpochAccess) level).railoptimization$getBlockChangeEpoch();
		}
		cache.observedEpoch = cache.epoch.get();
		cache.invalidate();
	}

	static void beginLaneWrite() {
		Cache cache = CACHE.get();
		cache.invalidate();
		cache.writeDepth++;
	}

	static void endLaneWrite() {
		CACHE.get().writeDepth--;
	}

	private static final class Cache {
		final long[] positions = new long[CAPACITY];
		final int[] generations = new int[CAPACITY];
		final byte[] shapes = new byte[CAPACITY];
		Level level;
		AtomicLong epoch;
		long observedEpoch;
		int generation = 1;
		int writeDepth;

		void invalidate() {
			if (++generation == 0) {
				Arrays.fill(generations, 0);
				generation = 1;
			}
		}
	}
}
