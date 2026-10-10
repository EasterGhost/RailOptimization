package RailOptimization;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

final class RailBatchWriter {
	private static final int FLAGS = Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	private final RailChangeList changes;
	private final long[] positions;
	private final BlockState[] targets;
	private final long[] groupKeys;
	private final int[] groups;
	private final int[] first;
	private final int[] next;
	private final int[] paletteIds;
	private final LevelChunk[] chunks;
	private final LevelChunkSection[] sections;
	private final boolean[] clientUpdates;
	private final MutableBlockPos cursor = new MutableBlockPos();
	private boolean planning;
	private int minX;
	private int maxX;
	private int minZ;
	private int maxZ;

	RailBatchWriter(RailChangeList changes) {
		this.changes = changes;
		positions = changes.positions();
		int capacity = positions.length;
		targets = new BlockState[capacity];
		groupKeys = new long[capacity];
		groups = new int[capacity];
		first = new int[capacity];
		next = new int[capacity];
		paletteIds = new int[capacity];
		chunks = new LevelChunk[capacity];
		sections = new LevelChunkSection[capacity];
		clientUpdates = new boolean[capacity];
	}

	void reset() {
		planning = false;
	}

	void begin() {
		planning = true;
	}

	boolean planning() {
		return planning;
	}

	BlockState pendingState(long position) {
		int x = BlockPos.getX(position);
		int z = BlockPos.getZ(position);
		if (x < minX || x > maxX || z < minZ || z > maxZ) {
			return null;
		}
		for (int index = changes.size() - 1; index >= 0; --index) {
			if (positions[index] == position) {
				return targets[index];
			}
		}
		return null;
	}

	void queue(BlockPos pos, BlockState previous, boolean powered) {
		int index = changes.size();
		if (index == 0) {
			minX = maxX = pos.getX();
			minZ = maxZ = pos.getZ();
		} else {
			minX = Math.min(minX, pos.getX());
			maxX = Math.max(maxX, pos.getX());
			minZ = Math.min(minZ, pos.getZ());
			maxZ = Math.max(maxZ, pos.getZ());
		}
		targets[index] = previous.setValue(PoweredRailBlock.POWERED, powered);
		changes.add(pos, previous);
	}

	void write(Level level, RailUpdateContext context) {
		planning = false;
		if (level.isDebug() || !RailLithiumCompatibility.supportsBatchWrites()) {
			writeFallback(level, context);
			return;
		}
		int groupCount = 0;
		try {
			LevelChunk previousChunk = null;
			int previousChunkX = 0;
			int previousChunkZ = 0;
			for (int index = 0; index < changes.size(); ++index) {
				cursor.set(BlockPos.getX(positions[index]), BlockPos.getY(positions[index]), BlockPos.getZ(positions[index]));
				BlockState previous = changes.state(index);
				if (!level.isInValidBounds(cursor) || (previous.getBlock() != Blocks.POWERED_RAIL && previous.getBlock() != Blocks.ACTIVATOR_RAIL)) {
					writeFallback(level, context);
					return;
				}
				long key = SectionPos.asLong(cursor.getX() >> 4, cursor.getY() >> 4, cursor.getZ() >> 4);
				int group = 0;
				while (group < groupCount && groupKeys[group] != key) {
					++group;
				}
				if (group == groupCount) {
					int chunkX = cursor.getX() >> 4;
					int chunkZ = cursor.getZ() >> 4;
					if (previousChunk == null || previousChunkX != chunkX || previousChunkZ != chunkZ) {
						previousChunk = level.getChunk(chunkX, chunkZ);
						previousChunkX = chunkX;
						previousChunkZ = chunkZ;
					}
					groupKeys[group] = key;
					chunks[group] = previousChunk;
					sections[group] = previousChunk.getSection(previousChunk.getSectionIndex(cursor.getY()));
					FullChunkStatus status = previousChunk.getFullStatus();
					clientUpdates[group] = status != null && status.isOrAfter(FullChunkStatus.BLOCK_TICKING);
					first[group] = -1;
					++groupCount;
				}
				groups[index] = group;
				next[index] = first[group];
				first[group] = index;
			}

			for (int group = 0; group < groupCount; ++group) {
				writeSection(sections[group], first[group]);
				chunks[group].markUnsaved();
			}
			// Client and compatibility notifications retain the walk's original order.
			for (int index = 0; index < changes.size(); ++index) {
				cursor.set(BlockPos.getX(positions[index]), BlockPos.getY(positions[index]), BlockPos.getZ(positions[index]));
				BlockState previous = changes.state(index);
				BlockState target = targets[index];
				int group = groups[index];
				RailLithiumCompatibility.afterSectionWrite(sections[group], cursor, previous, target);
				if (clientUpdates[group]) {
					level.sendBlockUpdated(cursor, previous, target, FLAGS);
				}
				RailLithiumCompatibility.invalidateAdjacentHoppers(level, chunks[group], cursor, context.scratchPos);
				level.updatePOIOnBlockStateChange(cursor, previous, target);
				context.memo.confirm(cursor, RailPath.isPowered(target), context.railPowerLimit);
			}
		} finally {
			Arrays.fill(chunks, 0, groupCount, null);
			Arrays.fill(sections, 0, groupCount, null);
		}
	}

	@SuppressWarnings("unchecked")
	private void writeSection(LevelChunkSection section, int firstIndex) {
		((RailPaletteAccess<BlockState>) (Object) section.getStates()).railoptimization$setBatch(positions, targets, next, firstIndex, paletteIds);
	}

	private void writeFallback(Level level, RailUpdateContext context) {
		for (int index = 0; index < changes.size(); ++index) {
			cursor.set(BlockPos.getX(positions[index]), BlockPos.getY(positions[index]), BlockPos.getZ(positions[index]));
			level.setBlock(cursor, targets[index], FLAGS);
			context.memo.confirm(cursor, RailPath.isPowered(targets[index]), context.railPowerLimit);
		}
	}
}
