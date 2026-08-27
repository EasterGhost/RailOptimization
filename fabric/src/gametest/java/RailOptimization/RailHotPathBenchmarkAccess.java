package RailOptimization;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class RailHotPathBenchmarkAccess {
	private static final int UPDATE_FORCE_PLACE = Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_CLIENTS;
	private static final int DEFAULT_POWER_LIMIT = 8;
	private static final int CONTEXT_POOL_SIZE = 512;
	private RailHotPathBenchmarkAccess() {
	}

	public static long measureEpochAdvance(Level level, int operations) {
		long startNanos = System.nanoTime();
		for (int operation = 0; operation < operations; operation++) {
			RailUpdateMemo.onBlockStateChanged(level);
		}
		return System.nanoTime() - startNanos;
	}

	public static MemoProbe memoProbe(Level level, BlockPos pos, boolean powered) {
		return new MemoProbe(level, pos.asLong(), powered);
	}

	public static SearchCacheProbe searchCacheProbe() {
		return new SearchCacheProbe();
	}

	public static ContextResetProbe contextResetProbe() {
		return new ContextResetProbe();
	}

	public static BlockReadProbe blockReadProbe(Level level, BlockPos pos) {
		return new BlockReadProbe(level, pos);
	}

	public static DepowerStageProbe depowerStageProbe(
			Level level, BlockPos pos, int powerLimit, int expectedRailCount) {
		return new DepowerStageProbe(level, pos, powerLimit, expectedRailCount);
	}

	public static NeighborSignalProbe neighborSignalProbe(Level level, BlockPos pos) {
		return new NeighborSignalProbe(level, pos);
	}

	public static ColdSignalSearchProbe coldSignalSearchProbe(Level level, BlockPos pos, boolean forward) {
		return new ColdSignalSearchProbe(level, pos, forward, DEFAULT_POWER_LIMIT, false);
	}

	public static ColdSignalSearchProbe coldSignalSearchProbe(
			Level level, BlockPos pos, boolean forward, int powerLimit, boolean expectedFound) {
		return new ColdSignalSearchProbe(level, pos, forward, powerLimit, expectedFound);
	}

	public static ForcedMemoMissProbe forcedMemoMissProbe(Level level, BlockPos[] positions) {
		return new ForcedMemoMissProbe(level, positions);
	}

	public static WorldWriteProbe worldWriteProbe(Level level, BlockPos[] positions) {
		return new WorldWriteProbe(level, positions);
	}

	public static NotificationProbe notificationProbe(Level level, BlockPos pos) {
		return new NotificationProbe(level, pos);
	}

	@SuppressWarnings("null")
	public static void forcePoweredState(Level level, BlockPos[] positions, boolean powered) {
		RailUpdateMemo.beginLaneWrite();
		try {
			for (BlockPos pos : positions) {
				LevelChunk chunk = level.getChunkAt(pos);
				BlockState state = chunk.getBlockState(pos);
				chunk.setBlockState(pos, state.setValue(PoweredRailBlock.POWERED, powered), UPDATE_FORCE_PLACE);
			}
		} finally {
			RailUpdateMemo.endLaneWrite();
		}
	}

	public static final class MemoProbe {
		private final RailUpdateMemo memo = new RailUpdateMemo();
		private final Level level;
		private final long position;
		private final boolean powered;

		private MemoProbe(Level level, long position, boolean powered) {
			this.level = level;
			this.position = position;
			this.powered = powered;
		}

		public long measureHit(int operations) {
			prepare(false);
			return measure(operations, true);
		}

		public long measureStaleEntry(int operations) {
			prepare(true);
			return measure(operations, false);
		}

		private void prepare(boolean invalidate) {
			memo.beginWalk(level);
			RailUpdateMemo.trackContext(memo);
			memo.confirm(BlockPos.of(position), powered, DEFAULT_POWER_LIMIT);
			if (invalidate) {
				RailUpdateMemo.onBlockStateChanged(level);
			}
		}

		private long measure(int operations, boolean expectedHit) {
			int confirmed = 0;
			long startNanos = System.nanoTime();
			for (int operation = 0; operation < operations; operation++) {
				if (RailUpdateMemo.isConfirmed(level, position, DEFAULT_POWER_LIMIT, powered)) {
					confirmed++;
				}
			}
			long elapsedNanos = System.nanoTime() - startNanos;
			int expectedConfirmed = expectedHit ? operations : 0;
			require(confirmed == expectedConfirmed,
					"memo fixture changed during measurement: expected "
							+ expectedConfirmed + " confirmations, got " + confirmed);
			return elapsedNanos;
		}
	}

	public static final class SearchCacheProbe {
		private static final int ENTRY_COUNT = 32;
		private final RailSearchCache cache = new RailSearchCache(DEFAULT_POWER_LIMIT);
		private final long[] hits = new long[ENTRY_COUNT];
		private final long[] misses = new long[ENTRY_COUNT];

		private SearchCacheProbe() {
			for (int index = 0; index < ENTRY_COUNT; index++) {
				hits[index] = BlockPos.asLong(index, 64, index * 3);
				misses[index] = BlockPos.asLong(index + 128, 65, index * 3 + 1);
				cache.put(hits[index], RailSearchCache.DIRECT_SIGNAL, RailLogic.CHECKED_BLOCKED);
			}
		}

		public long measureHit(int operations) {
			return measure(operations, hits);
		}

		public long measureMiss(int operations) {
			return measure(operations, misses);
		}

		private long measure(int operations, long[] positions) {
			int result = 0;
			long startNanos = System.nanoTime();
			for (int operation = 0; operation < operations; operation++) {
				result += cache.get(positions[operation & (ENTRY_COUNT - 1)], RailSearchCache.DIRECT_SIGNAL);
			}
			long elapsedNanos = System.nanoTime() - startNanos;
			int expected = positions == hits ? operations * RailLogic.CHECKED_BLOCKED : 0;
			require(result == expected,
					"search-cache fixture returned " + result + " instead of " + expected);
			return elapsedNanos;
		}
	}

	public static final class ContextResetProbe {
		private final RailUpdateContext[] contexts = new RailUpdateContext[CONTEXT_POOL_SIZE];

		private ContextResetProbe() {
			for (int index = 0; index < contexts.length; index++) {
				contexts[index] = new RailUpdateContext(DEFAULT_POWER_LIMIT);
			}
		}

		public long measure(int operations) {
			int remaining = operations;
			long elapsedNanos = 0L;
			while (remaining > 0) {
				int count = Math.min(remaining, contexts.length);
				for (int index = 0; index < count; index++) {
					contexts[index].searchCache.put(index, RailSearchCache.DIRECT_SIGNAL, RailLogic.CHECKED_BLOCKED);
				}
				long startNanos = System.nanoTime();
				for (int index = 0; index < count; index++) {
					contexts[index].reset();
				}
				elapsedNanos += System.nanoTime() - startNanos;
				remaining -= count;
			}
			require(contexts[0].searchCache.get(0, RailSearchCache.DIRECT_SIGNAL)
					== RailLogic.CHECKED_UNKNOWN, "context reset left a search-cache entry behind");
			return elapsedNanos;
		}
	}

	public static final class BlockReadProbe {
		private final RailUpdateContext context = new RailUpdateContext(DEFAULT_POWER_LIMIT);
		private final Level level;
		private final BlockPos pos;
		private final Block expectedBlock;
		private final LevelChunkSection section;
		private final int localX;
		private final int localY;
		private final int localZ;

		@SuppressWarnings("null")
		private BlockReadProbe(Level level, BlockPos pos) {
			this.level = level;
			this.pos = pos;
			expectedBlock = level.getBlockState(pos).getBlock();
			context.getBlockState(level, pos);
			LevelChunk chunk = level.getChunkAt(pos);
			section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
			localX = pos.getX() & 15;
			localY = pos.getY() & 15;
			localZ = pos.getZ() & 15;
		}

		public long measure(int operations) {
			int matches = 0;
			long startNanos = System.nanoTime();
			for (int operation = 0; operation < operations; operation++) {
				if (context.getBlockState(level, pos).is(expectedBlock)) {
					matches++;
				}
			}
			long elapsedNanos = System.nanoTime() - startNanos;
			require(matches == operations,
					"cached block-state read fixture returned " + matches + " matches for " + operations + " reads");
			return elapsedNanos;
		}

		public long measureSection(int operations) {
			int matches = 0;
			long startNanos = System.nanoTime();
			for (int operation = 0; operation < operations; operation++) {
				if (section.getBlockState(localX, localY, localZ).is(expectedBlock)) {
					matches++;
				}
			}
			long elapsedNanos = System.nanoTime() - startNanos;
			require(matches == operations,
					"direct section block-state read fixture returned " + matches
							+ " matches for " + operations + " reads");
			return elapsedNanos;
		}
	}

	public static final class DepowerStageProbe {
		private final RailUpdateContext[] contexts = new RailUpdateContext[CONTEXT_POOL_SIZE];
		private final Level level;
		private final BlockPos pos;
		private final BlockState state;
		private final PoweredRailBlock railBlock;
		private final RailShape railShape;
		private final int expectedRailCount;

		@SuppressWarnings("null")
		private DepowerStageProbe(
				Level level, BlockPos pos, int powerLimit, int expectedRailCount) {
			this.level = level;
			this.pos = pos;
			this.expectedRailCount = expectedRailCount;
			state = level.getBlockState(pos);
			railBlock = (PoweredRailBlock) state.getBlock();
			railShape = RailPath.railShape(state);
			require(RailPath.isPowered(state), "depower-stage source rail must start powered");
			for (int index = 0; index < contexts.length; index++) {
				contexts[index] = new RailUpdateContext(powerLimit);
			}
		}

		public long measureDecision(int operations) {
			int remaining = operations;
			int unpoweredDecisions = 0;
			long elapsedNanos = 0L;
			while (remaining > 0) {
				int count = Math.min(remaining, contexts.length);
				for (int index = 0; index < count; index++) {
					contexts[index].reset();
				}
				long startNanos = System.nanoTime();
				for (int index = 0; index < count; index++) {
					if (!shouldRemainPowered(contexts[index])) {
						unpoweredDecisions++;
					}
				}
				elapsedNanos += System.nanoTime() - startNanos;
				remaining -= count;
			}
			require(unpoweredDecisions == operations,
					"depower-stage decision kept " + (operations - unpoweredDecisions)
							+ " source rails powered");
			return elapsedNanos;
		}

		public long measureStraightPlan(int operations, boolean forward) {
			return measurePlan(operations, context -> RailSignalSearcher.countStraightRailsToDepower(
					railBlock, level, pos, railShape, forward, context), expectedRailCount);
		}

		public long measureStraightRejection(int operations, boolean forward) {
			return measurePlan(operations, context -> RailSignalSearcher.countStraightRailsToDepower(
					railBlock, level, pos, railShape, forward, context), RailSignalSearcher.COMPLEX_PATH);
		}

		public long measureConnectedPlan(int operations, boolean forward) {
			return measurePlan(operations, context -> {
				int straightCount = RailSignalSearcher.countStraightRailsToDepower(
						railBlock, level, pos, railShape, forward, context);
				require(straightCount == RailSignalSearcher.COMPLEX_PATH,
						"connected depower fixture unexpectedly used the straight path");
				return RailSignalSearcher.countConnectedRailsToDepower(
						railBlock, level, pos, state, forward, context);
			}, expectedRailCount);
		}

		private long measurePlan(int operations, PlanMeasurement measurement, int expectedResult) {
			int remaining = operations;
			long resultSum = 0L;
			long elapsedNanos = 0L;
			while (remaining > 0) {
				int count = Math.min(remaining, contexts.length);
				for (int index = 0; index < count; index++) {
					prepareDepowering(contexts[index]);
				}
				long startNanos = System.nanoTime();
				for (int index = 0; index < count; index++) {
					resultSum += measurement.measure(contexts[index]);
				}
				elapsedNanos += System.nanoTime() - startNanos;
				remaining -= count;
			}
			require(resultSum == (long) expectedResult * operations,
					"depower-stage planner returned an aggregate of " + resultSum
							+ " instead of " + (long) expectedResult * operations);
			return elapsedNanos;
		}

		private void prepareDepowering(RailUpdateContext context) {
			context.reset();
			require(!shouldRemainPowered(context),
					"depower-stage fixture unexpectedly remained powered");
			context.beginDepowering();
		}

		private boolean shouldRemainPowered(RailUpdateContext context) {
			return context.hasNeighborSignal(level, pos)
					|| RailSignalSearcher.findPoweredRailSignalFaster(
							railBlock, level, pos, state, true, 0, context)
					|| RailSignalSearcher.findPoweredRailSignalFaster(
							railBlock, level, pos, state, false, 0, context);
		}

		@FunctionalInterface
		private interface PlanMeasurement {
			int measure(RailUpdateContext context);
		}
	}

	public static final class NeighborSignalProbe {
		private final Level level;
		private final BlockPos pos;
		private final MutableBlockPos scratchPos = new MutableBlockPos();
		private final LevelChunk chunk;
		private final int chunkX;
		private final int chunkZ;
		private final boolean expectedPowered;

		private NeighborSignalProbe(Level level, BlockPos pos) {
			this.level = level;
			this.pos = pos;
			chunkX = pos.getX() >> 4;
			chunkZ = pos.getZ() >> 4;
			chunk = level.getChunk(chunkX, chunkZ);
			expectedPowered = level.hasNeighborSignal(pos);
		}

		public long measure(int operations) {
			int powered = 0;
			long startNanos = System.nanoTime();
			for (int operation = 0; operation < operations; operation++) {
				if (RailNeighborSignalChecker.hasNeighborSignalFast(
						level, pos, scratchPos, chunk, chunkX, chunkZ)) {
					powered++;
				}
			}
			long elapsedNanos = System.nanoTime() - startNanos;
			int expected = expectedPowered ? operations : 0;
			require(powered == expected,
					"direct-signal fixture returned " + powered + " powered results instead of " + expected);
			return elapsedNanos;
		}
	}

	public static final class ColdSignalSearchProbe {
		private final RailUpdateContext[] contexts = new RailUpdateContext[CONTEXT_POOL_SIZE];
		private final PoweredRailBlock railBlock;
		private final Level level;
		private final BlockPos pos;
		private final BlockState state;
		private final boolean forward;
		private final boolean expectedFound;

		@SuppressWarnings("null")
		private ColdSignalSearchProbe(
				Level level, BlockPos pos, boolean forward, int powerLimit, boolean expectedFound) {
			this.level = level;
			this.pos = pos;
			this.forward = forward;
			this.expectedFound = expectedFound;
			state = level.getBlockState(pos);
			railBlock = (PoweredRailBlock) state.getBlock();
			int stepIndex = (RailPath.railShape(state).ordinal() << 1) | (forward ? 0 : 1);
			for (int distance = 1; distance <= powerLimit; distance++) {
				BlockPos expectedRail = pos.offset(
						RailPath.STEP_X[stepIndex] * distance,
						RailPath.STEP_Y[stepIndex] * distance,
						RailPath.STEP_Z[stepIndex] * distance);
				BlockState expectedState = level.getBlockState(expectedRail);
				require(expectedState.is(railBlock) && RailPath.isPowered(expectedState),
						"cold-search fixture does not contain a powered rail at step " + distance);
			}
			for (int index = 0; index < contexts.length; index++) {
				contexts[index] = new RailUpdateContext(powerLimit);
			}
		}

		public long measure(int operations) {
			int remaining = operations;
			int found = 0;
			long elapsedNanos = 0L;
			while (remaining > 0) {
				int count = Math.min(remaining, contexts.length);
				for (int index = 0; index < count; index++) {
					contexts[index].reset();
				}
				long startNanos = System.nanoTime();
				for (int index = 0; index < count; index++) {
					if (RailSignalSearcher.findPoweredRailSignalFaster(
							railBlock, level, pos, state, forward, 0, contexts[index])) {
						found++;
					}
				}
				elapsedNanos += System.nanoTime() - startNanos;
				remaining -= count;
			}
			int expected = expectedFound ? operations : 0;
			require(found == expected,
					"cold signal search found a signal " + found + " times instead of " + expected);
			return elapsedNanos;
		}
	}

	public static final class ForcedMemoMissProbe {
		private final RailUpdateMemo memo = new RailUpdateMemo();
		private final Level level;
		private final BlockPos[] positions;
		private final BlockState[] states;
		private final PoweredRailBlock railBlock = (PoweredRailBlock) Blocks.POWERED_RAIL;

		@SuppressWarnings("null")
		private ForcedMemoMissProbe(Level level, BlockPos[] positions) {
			this.level = level;
			this.positions = positions.clone();
			states = new BlockState[positions.length];
			for (int index = 0; index < positions.length; index++) {
				states[index] = level.getBlockState(positions[index]);
			}
		}

		public long measure(int operations) {
			int remaining = operations;
			int handled = 0;
			long elapsedNanos = 0L;
			while (remaining > 0) {
				int count = Math.min(remaining, positions.length);
				memo.beginWalk(level);
				RailUpdateMemo.trackContext(memo);
				for (int index = 0; index < count; index++) {
					memo.confirm(positions[index], true, DEFAULT_POWER_LIMIT);
				}
				RailUpdateMemo.onBlockStateChanged(level);

				long startNanos = System.nanoTime();
				for (int index = 0; index < count; index++) {
					if (RailLogic.tryCustomUpdateState(
							railBlock, states[index], level, positions[index], Blocks.STONE)) {
						handled++;
					}
				}
				elapsedNanos += System.nanoTime() - startNanos;
				remaining -= count;
			}
			require(handled == operations,
					"forced memo-miss fixture handled " + handled + " of " + operations + " updates");
			return elapsedNanos;
		}
	}

	public static final class WorldWriteProbe {
		private final Level level;
		private final BlockPos[] positions;
		private final LevelChunk[] chunks;
		private final BlockState[] unpoweredStates;
		private final BlockState[] poweredStates;

		@SuppressWarnings("null")
		private WorldWriteProbe(Level level, BlockPos[] positions) {
			this.level = level;
			this.positions = positions.clone();
			chunks = new LevelChunk[positions.length];
			unpoweredStates = new BlockState[positions.length];
			poweredStates = new BlockState[positions.length];
			for (int index = 0; index < positions.length; index++) {
				BlockPos pos = positions[index];
				chunks[index] = level.getChunkAt(pos);
				BlockState state = chunks[index].getBlockState(pos);
				unpoweredStates[index] = state.setValue(PoweredRailBlock.POWERED, false);
				poweredStates[index] = state.setValue(PoweredRailBlock.POWERED, true);
			}
		}

		public long measureChunkStateWrite(int operations) {
			prepareUnpowered();
			RailUpdateMemo.beginLaneWrite();
			try {
				long startNanos = System.nanoTime();
				int changes = runAlternating(operations, true);
				long elapsedNanos = System.nanoTime() - startNanos;
				require(changes == operations,
						"LevelChunk fixture changed " + changes + " of " + operations + " rail states");
				return elapsedNanos;
			} finally {
				RailUpdateMemo.endLaneWrite();
			}
		}

		public long measureLevelSetBlock(int operations) {
			prepareUnpowered();
			RailUpdateMemo.beginLaneWrite();
			try {
				long startNanos = System.nanoTime();
				int changes = runAlternating(operations, false);
				long elapsedNanos = System.nanoTime() - startNanos;
				require(changes == operations,
						"Level fixture changed " + changes + " of " + operations + " rail states");
				return elapsedNanos;
			} finally {
				RailUpdateMemo.endLaneWrite();
			}
		}

		@SuppressWarnings("null")
		public long measureShapeNotifications(int operations) {
			long startNanos = System.nanoTime();
			for (int operation = 0; operation < operations; operation++) {
				int index = operation & (positions.length - 1);
				poweredStates[index].updateNeighbourShapes(
						level, positions[index], UPDATE_FORCE_PLACE, 511);
			}
			long elapsedNanos = System.nanoTime() - startNanos;
			return elapsedNanos;
		}

		@SuppressWarnings("null")
		public long measureClientUpdateRegistration(int operations) {
			long startNanos = System.nanoTime();
			for (int operation = 0; operation < operations; operation++) {
				int index = operation & (positions.length - 1);
				level.sendBlockUpdated(
						positions[index], unpoweredStates[index], poweredStates[index], UPDATE_FORCE_PLACE);
			}
			long elapsedNanos = System.nanoTime() - startNanos;
			return elapsedNanos;
		}

		@SuppressWarnings("null")
		private int runAlternating(int operations, boolean directChunkWrite) {
			int remaining = operations;
			int changes = 0;
			boolean powered = true;
			while (remaining > 0) {
				int count = Math.min(remaining, positions.length);
				BlockState[] targetStates = powered ? poweredStates : unpoweredStates;
				for (int index = 0; index < count; index++) {
					if (directChunkWrite) {
						if (chunks[index].setBlockState(
								positions[index], targetStates[index], UPDATE_FORCE_PLACE) != null) {
							changes++;
						}
					} else if (level.setBlock(positions[index], targetStates[index], UPDATE_FORCE_PLACE)) {
						changes++;
					}
				}
				remaining -= count;
				powered = !powered;
			}
			return changes;
		}

		@SuppressWarnings("null")
		private void prepareUnpowered() {
			RailUpdateMemo.beginLaneWrite();
			try {
				for (int index = 0; index < positions.length; index++) {
					chunks[index].setBlockState(
							positions[index], unpoweredStates[index], UPDATE_FORCE_PLACE);
				}
			} finally {
				RailUpdateMemo.endLaneWrite();
			}
		}
	}

	public static final class NotificationProbe {
		private final Level level;
		private final BlockPos pos;

		private NotificationProbe(Level level, BlockPos pos) {
			this.level = level;
			this.pos = pos;
		}

		@SuppressWarnings("null")
		public long measureSingleNeighborChanged(int operations) {
			long startNanos = System.nanoTime();
			for (int operation = 0; operation < operations; operation++) {
				level.neighborChanged(pos, Blocks.POWERED_RAIL, null);
			}
			long elapsedNanos = System.nanoTime() - startNanos;
			return elapsedNanos;
		}

		@SuppressWarnings("null")
		public long measureSixNeighborUpdate(int operations) {
			long startNanos = System.nanoTime();
			for (int operation = 0; operation < operations; operation++) {
				level.updateNeighborsAt(pos, Blocks.POWERED_RAIL);
			}
			long elapsedNanos = System.nanoTime() - startNanos;
			return elapsedNanos;
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}
}
