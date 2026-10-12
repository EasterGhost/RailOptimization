package RailOptimization;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.redstone.NeighborUpdater;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class RailNotificationBatch implements CollectingNeighborUpdater.NeighborUpdates {
	private static final byte NC = 0;
	private static final byte PP = 1;
	private static final byte SINGLE_NC = 2;
	private static final Direction[] SHAPE_ORDER = { Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.DOWN, Direction.UP };
	private static final ThreadLocal<ArrayDeque<RailNotificationBatch>> POOL = ThreadLocal.withInitial(ArrayDeque::new);
	private final long[] positions;
	private final BlockState[] shapes;
	private final byte[] kinds;
	private final byte[] directionMasks;
	private final int[] neighborOffsets = new int[NeighborUpdater.UPDATE_ORDER.length];
	private final MutableBlockPos scratchPos = new MutableBlockPos();
	private long[] seenBits;
	private Block source;
	private int size;
	private int cost;
	private int current;
	private int directionIndex;
	private @Nullable BlockPos origin;
	private boolean eastWest;
	private int minColumn;
	private int columnStride;
	private int minY;
	private int perpendicular;

	private RailNotificationBatch(int railCount) {
		int capacity = railCount * 4 + 2;
		positions = new long[capacity];
		shapes = new BlockState[capacity];
		kinds = new byte[capacity];
		directionMasks = new byte[capacity];
		seenBits = new long[((railCount + 2) * 15 + 63) >>> 6];
	}

	static boolean tryNotify(Level level, RailShape shape, int firstCount, int secondCount, RailChangeList changes) {
		RailNeighborBatchAccess updater = (RailNeighborBatchAccess) level.neighborUpdater;
		int count = changes.size();
		if (!updater.railoptimization$canBatch(count * 9 + 2) || level.enabledFeatures().contains(FeatureFlags.REDSTONE_EXPERIMENTS)) {
			return false;
		}
		for (int index = 0; index < count; ++index) {
			Block block = changes.state(index).getBlock();
			if (block != Blocks.POWERED_RAIL && block != Blocks.ACTIVATOR_RAIL)
				return false;
		}
		RailNotificationBatch batch = POOL.get().pollFirst();
		if (batch == null || batch.positions.length < count * 4 + 2)
			batch = new RailNotificationBatch(count);
		batch.size = batch.cost = batch.current = batch.directionIndex = 0;
		batch.origin = null;
		batch.prepareColumns(shape, changes);
		batch.plan(level, shape, firstCount, secondCount, changes);
		updater.railoptimization$enqueueBatch(batch);
		return true;
	}

	private void plan(Level level, RailShape shape, int firstCount, int secondCount, RailChangeList changes) {
		int secondStart = firstCount + 1;
		int secondEnd = firstCount + secondCount;
		boolean endpoint = !changes.hasSlope() && firstCount == 0 && secondCount > 0;
		source = changes.state(0).getBlock();
		long sourcePosition = changes.position(0);
		if (RailPath.RAIL_AXIS[shape.ordinal()] == RailPath.AXIS_EAST_WEST) {
			for (int index = firstCount; index >= 1; --index)
				rail(level, changes, index);
			if (endpoint)
				single(sourcePosition, Direction.WEST);
			for (int index = secondEnd; index >= secondStart; --index)
				rail(level, changes, index);
			appendNeighbors(level, sourcePosition, endpoint ? Direction.WEST : null);
		} else {
			appendNeighbors(level, sourcePosition, endpoint ? Direction.SOUTH : null);
			branch(level, changes, secondStart, secondEnd);
			if (endpoint)
				single(sourcePosition, Direction.SOUTH);
			branch(level, changes, 1, firstCount);
		}
		shape(level, sourcePosition);
		support(level, changes, 0);
	}

	private void branch(Level level, RailChangeList changes, int start, int end) {
		for (int index = start; index <= end; ++index)
			appendNeighbors(level, changes.position(index), null);
		for (int index = end; index >= start; --index) {
			shape(level, changes.position(index));
			support(level, changes, index);
		}
	}

	private void rail(Level level, RailChangeList changes, int index) {
		appendNeighbors(level, changes.position(index), null);
		shape(level, changes.position(index));
		support(level, changes, index);
	}

	private void support(Level level, RailChangeList changes, int index) {
		long position = changes.position(index);
		appendNeighbors(level, BlockPos.asLong(BlockPos.getX(position), BlockPos.getY(position) - 1, BlockPos.getZ(position)), null);
		if (changes.isAscending(index)) {
			appendNeighbors(level, BlockPos.asLong(BlockPos.getX(position), BlockPos.getY(position) + 1, BlockPos.getZ(position)), null);
		}
	}

	private void appendNeighbors(Level level, long position, @Nullable Direction skipped) {
		++cost;
		int mask = 0;
		int originIndex = bitIndex(position);
		for (int index = 0; index < NeighborUpdater.UPDATE_ORDER.length; ++index) {
			Direction direction = NeighborUpdater.UPDATE_ORDER[index];
			if (direction == skipped)
				continue;
			if (!duplicate(originIndex + neighborOffsets[index]))
				mask |= 1 << index;
		}
		if (mask == 0)
			return;
		positions[size] = position;
		kinds[size] = NC;
		directionMasks[size++] = (byte) mask;
	}

	@SuppressWarnings("null")
	private void shape(Level level, long position) {
		positions[size] = position;
		scratchPos.set(BlockPos.getX(position), BlockPos.getY(position), BlockPos.getZ(position));
		shapes[size] = level.getBlockState(scratchPos);
		kinds[size++] = PP;
		cost += SHAPE_ORDER.length;
	}

	private void single(long position, Direction direction) {
		++cost;
		long target = BlockPos.asLong(BlockPos.getX(position) + direction.getStepX(), BlockPos.getY(position), BlockPos.getZ(position) + direction.getStepZ());
		if (duplicate(bitIndex(target)))
			return;
		positions[size] = target;
		kinds[size++] = SINGLE_NC;
	}

	public int taskCost() {
		return cost;
	}

	public BlockPos firstPosition() {
		return BlockPos.of(positions[0]);
	}

	@SuppressWarnings("null")
	@Override
	public boolean runNext(@NonNull Level level) {
		if (origin == null)
			origin = BlockPos.of(positions[current]);
		byte kind = kinds[current];
		if (kind == PP) {
			Direction direction = SHAPE_ORDER[directionIndex++];
			NeighborUpdater.executeShapeUpdate(level, direction.getOpposite(), origin.relative(direction), origin, shapes[current],
					Block.UPDATE_CLIENTS, Block.UPDATE_LIMIT - 1);
		} else {
			BlockPos target;
			if (kind == SINGLE_NC) {
				target = origin;
				directionIndex = NeighborUpdater.UPDATE_ORDER.length;
			} else {
				int mask = directionMasks[current];
				Direction direction = NeighborUpdater.UPDATE_ORDER[Integer.numberOfTrailingZeros(mask)];
				directionMasks[current] = (byte) (mask & (mask - 1));
				target = origin.relative(direction);
				directionIndex = directionMasks[current] == 0 ? NeighborUpdater.UPDATE_ORDER.length : 0;
			}
			NeighborUpdater.executeUpdate(level, level.getBlockState(target), target, source, null, false);
		}
		if (directionIndex == NeighborUpdater.UPDATE_ORDER.length) {
			++current;
			directionIndex = 0;
			origin = null;
		}
		boolean more = current < size;
		if (!more)
			release();
		return more;
	}

	private int bitIndex(long position) {
		int column = (eastWest ? BlockPos.getX(position) : BlockPos.getZ(position)) - minColumn;
		int lateral = (eastWest ? BlockPos.getZ(position) : BlockPos.getX(position)) - perpendicular + 1;
		return column * columnStride + (BlockPos.getY(position) - minY) * 3 + lateral;
	}

	private boolean duplicate(int bitIndex) {
		int word = bitIndex >>> 6;
		long bit = 1L << bitIndex;
		long previous = seenBits[word];
		seenBits[word] = previous | bit;
		return (previous & bit) != 0;
	}

	private void prepareColumns(RailShape shape, RailChangeList changes) {
		eastWest = RailPath.RAIL_AXIS[shape.ordinal()] == RailPath.AXIS_EAST_WEST;
		long origin = changes.position(0);
		perpendicular = eastWest ? BlockPos.getZ(origin) : BlockPos.getX(origin);
		int minimum = eastWest ? BlockPos.getX(origin) : BlockPos.getZ(origin);
		int maximum = minimum;
		minY = BlockPos.getY(origin);
		int maxY = minY;
		for (int index = 1; index < changes.size(); ++index) {
			long position = changes.position(index);
			int coordinate = eastWest ? BlockPos.getX(position) : BlockPos.getZ(position);
			minimum = Math.min(minimum, coordinate);
			maximum = Math.max(maximum, coordinate);
			minY = Math.min(minY, BlockPos.getY(position));
			maxY = Math.max(maxY, BlockPos.getY(position));
		}
		int columns = maximum - minimum + 3;
		minColumn = minimum - 1;
		columnStride = (maxY - minY + 5) * 3;
		minY -= 2;
		int xStride = eastWest ? columnStride : 1;
		int zStride = eastWest ? 1 : columnStride;
		for (int index = 0; index < neighborOffsets.length; ++index) {
			Direction direction = NeighborUpdater.UPDATE_ORDER[index];
			neighborOffsets[index] = direction.getStepX() * xStride + direction.getStepY() * 3 + direction.getStepZ() * zStride;
		}
		int words = (columns * columnStride + 63) >>> 6;
		if (seenBits.length < words)
			seenBits = new long[words];
		else
			Arrays.fill(seenBits, 0, words, 0L);
	}

	private void release() {
		Arrays.fill(shapes, 0, size, null);
		ArrayDeque<RailNotificationBatch> pool = POOL.get();
		if (pool.size() < 4)
			pool.addFirst(this);
	}

	@SuppressWarnings("null")
	@Override
	public void forEachUpdatedPos(@NonNull Consumer<BlockPos> output) {
		BlockPos source = BlockPos.of(positions[current]);
		if (kinds[current] == SINGLE_NC) {
			output.accept(source);
		} else if (kinds[current] == PP) {
			for (int index = directionIndex; index < SHAPE_ORDER.length; ++index)
				output.accept(source.relative(SHAPE_ORDER[index]));
		} else {
			for (int mask = directionMasks[current]; mask != 0; mask &= mask - 1) {
				output.accept(source.relative(NeighborUpdater.UPDATE_ORDER[Integer.numberOfTrailingZeros(mask)]));
			}
		}
	}
}
