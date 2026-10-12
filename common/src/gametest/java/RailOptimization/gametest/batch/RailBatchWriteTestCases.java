package RailOptimization.gametest.batch;

import RailOptimization.RailLogic;
import RailOptimization.LevelEpochAccess;
import RailOptimization.RailPaletteAccess;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.slf4j.LoggerFactory;

public final class RailBatchWriteTestCases {
	private static final Heightmap.Types[] HEIGHTMAPS = { Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE };

	public static void realLeverUsesMultiRailBatchesAndHonorsLimit(GameTestHelper helper) {
		realLeverBatch(helper, 8);
	}

	public static void extendedRangeLeverUsesMultiRailBatchesAndHonorsLimit(GameTestHelper helper) {
		realLeverBatch(helper, 64);
	}

	@SuppressWarnings("null")
	private static void realLeverBatch(GameTestHelper helper, int limit) {
		BlockPos[] rails = new BlockPos[limit * 2 + 3];
		for (int i = 0; i < rails.length; i++) {
			rails[i] = new BlockPos(2 + i, 3, 4);
			helper.assertTrue(helper.getBounds().contains(helper.absolutePos(rails[i]).getCenter()), Component.literal("Batch rail outside structure"));
			helper.setBlock(rails[i].below(), Blocks.SMOOTH_STONE);
			helper.setBlock(rails[i], Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST));
		}
		BlockPos lever = rails[limit + 1].north();
		helper.setBlock(lever.below(), Blocks.SMOOTH_STONE);
		helper.setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
				.setValue(LeverBlock.FACING, Direction.NORTH));
		helper.startSequence().thenIdle(2).thenExecute(() -> {
			boolean previousEnabled = RailLogic.isOptimizationEnabled();
			int previousLimit = RailBatchTestAccess.powerLimit();
			RailLogic.setOptimizationEnabled(true);
			RailLogic.setRailPowerLimit(limit);
			try {
				long multiBefore = RailBatchWriteAudit.multiRailBatches;
				long writesBefore = RailBatchWriteAudit.writes;
				RailBatchWriteAudit.enabled = true;
				for (int toggle = 0; toggle < 4; toggle++) {
					helper.pullLever(lever);
					for (int i = 0; i < rails.length; i++)
						helper.assertBlockProperty(rails[i], PoweredRailBlock.POWERED,
								(toggle & 1) == 0 && i > 0 && i < rails.length - 1);
				}
				helper.assertTrue(RailBatchWriteAudit.multiRailBatches - multiBefore == 4 && RailBatchWriteAudit.writes - writesBefore == 4L * (limit * 2 + 1),
						Component.literal("Real lever did not execute four actual multi-rail batches"));
				LoggerFactory.getLogger(RailBatchWriteTestCases.class).info(
						"Real lever batch audit: powerLimit={}, multi-rail batches={}, writes={}, range endpoints remain off",
						limit, RailBatchWriteAudit.multiRailBatches - multiBefore, RailBatchWriteAudit.writes - writesBefore);
			} finally {
				RailBatchWriteAudit.enabled = false;
				RailLogic.setOptimizationEnabled(previousEnabled);
				RailLogic.setRailPowerLimit(previousLimit);
			}
		}).thenSucceed();
	}

	@SuppressWarnings("null")
	public static void groupedWritesAreDeferredAndPreserveSectionState(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(new BlockPos(16, 4, 4));
		origin = origin.offset(Math.floorMod(15 - origin.getX(), 16), Math.floorMod(15 - origin.getY(), 16), 0);
		List<BlockState> variants = new ArrayList<>();
		for (PoweredRailBlock block : new PoweredRailBlock[] { (PoweredRailBlock) Blocks.POWERED_RAIL, (PoweredRailBlock) Blocks.ACTIVATOR_RAIL }) {
			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
				if (!state.getValue(PoweredRailBlock.POWERED)) {
					BlockState target = state.setValue(PoweredRailBlock.POWERED, true);
					helper.assertTrue(state.isAir() == target.isAir() && state.isRandomlyTicking() == target.isRandomlyTicking()
							&& state.getFluidState() == target.getFluidState() && !LightEngine.hasDifferentLightProperties(state, target),
							Component.literal("Rail POWERED changed Section or lighting predicates"));
					for (Heightmap.Types type : HEIGHTMAPS)
						helper.assertTrue(type.isOpaque().test(state) == type.isOpaque().test(target),
								Component.literal("Rail POWERED changed heightmap predicate"));
					variants.add(state);
				}
			}
		}
		BlockPos[] rails = new BlockPos[17];
		for (int i = 0; i < rails.length; i++)
			rails[i] = origin.offset(i, i % 2, 0);
		RailBatchTestAccess.Context context = new RailBatchTestAccess.Context(8);
		long writesBefore = RailBatchWriteAudit.writes;
		long multiBefore = RailBatchWriteAudit.multiRailBatches;
		long groupsBefore = RailBatchWriteAudit.sectionGroups;
		for (int variantOffset : new int[] { 0, 17 }) {
			for (int i = 0; i < rails.length; i++) {
				helper.getLevel().setBlock(rails[i], variants.get((i + variantOffset) % variants.size()),
						Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE);
			}
			for (boolean powered : new boolean[] { true, false }) {
				context.reset();
				context.memo.beginWalk(helper.getLevel());
				context.batchWriter.begin();
				IdentityHashMap<LevelChunkSection, short[]> counts = new IdentityHashMap<>();
				IdentityHashMap<LevelChunk, Boolean> chunks = new IdentityHashMap<>();
				int[][] heights = new int[rails.length][HEIGHTMAPS.length];
				long clientUpdatesBefore = RailBatchWriteAudit.clientUpdates;
				int expectedClientUpdates = 0;
				for (int i = 0; i < rails.length; i++) {
					BlockPos rail = rails[i];
					LevelChunk chunk = helper.getLevel().getChunkAt(rail);
					FullChunkStatus status = chunk.getFullStatus();
					if (status != null && status.isOrAfter(FullChunkStatus.BLOCK_TICKING))
						++expectedClientUpdates;
					LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(rail.getY()));
					counts.putIfAbsent(section, counts(section));
					if (chunks.put(chunk, true) == null)
						chunk.tryMarkSaved();
					for (int h = 0; h < HEIGHTMAPS.length; h++)
						heights[i][h] = chunk.getHeight(HEIGHTMAPS[h], rail.getX() & 15, rail.getZ() & 15);
					BlockState previous = helper.getLevel().getBlockState(rail);
					context.batchWriter.queue(rail, previous, powered);
				}
				for (int i = 0; i < rails.length; i++) {
					helper.assertTrue(helper.getLevel().getBlockState(rails[i]) == context.changeList.state(i),
							Component.literal("Writer did not defer its writes"));
					helper.assertTrue(context.getBlockState(helper.getLevel(), rails[i]).getValue(PoweredRailBlock.POWERED) == powered,
							Component.literal("Planning view lost a pending POWERED state"));
				}
				long epoch = ((LevelEpochAccess) helper.getLevel()).railoptimization$getBlockChangeEpoch().get();
				RailBatchTestAccess.beginLaneWrite();
				RailBatchWriteAudit.enabled = true;
				try {
					context.batchWriter.write(helper.getLevel(), context);
				} finally {
					RailBatchWriteAudit.enabled = false;
					RailBatchTestAccess.endLaneWrite();
				}
				helper.assertTrue(((LevelEpochAccess) helper.getLevel()).railoptimization$getBlockChangeEpoch().get() == epoch,
						Component.literal("Own batch writes must not invalidate their memo epoch"));
				helper.assertTrue(RailBatchWriteAudit.clientUpdates - clientUpdatesBefore == expectedClientUpdates,
						Component.literal("Batch client updates differ from Level.setBlock eligibility"));
				for (int i = 0; i < rails.length; i++) {
					BlockPos rail = rails[i];
					LevelChunk chunk = helper.getLevel().getChunkAt(rail);
					helper.assertTrue(helper.getLevel().getBlockState(rail) == context.changeList.state(i).setValue(PoweredRailBlock.POWERED, powered),
							Component.literal("Batch changed something besides POWERED"));
					for (int h = 0; h < HEIGHTMAPS.length; h++)
						helper.assertTrue(heights[i][h] == chunk.getHeight(HEIGHTMAPS[h], rail.getX() & 15, rail.getZ() & 15),
								Component.literal("Batch changed heightmap"));
				}
				for (LevelChunk chunk : chunks.keySet())
					helper.assertTrue(chunk.isUnsaved(), Component.literal("Batch chunk was not marked unsaved"));
				for (var entry : counts.entrySet()) {
					helper.assertTrue(Arrays.equals(entry.getValue(), counts(entry.getKey())), Component.literal("Batch changed Section counters"));
					FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
					try {
						entry.getKey().write(buffer);
						LevelChunkSection decoded = new LevelChunkSection(helper.getLevel().palettedContainerFactory());
						decoded.read(buffer);
						for (int y = 0; y < 16; y++)
							for (int z = 0; z < 16; z++)
								for (int x = 0; x < 16; x++) {
									helper.assertTrue(decoded.getBlockState(x, y, z) == entry.getKey().getBlockState(x, y, z),
											Component.literal("Batch Section serialization mismatch"));
								}
					} finally {
						buffer.release();
					}
				}
			}
		}
		helper.assertTrue(RailBatchWriteAudit.writes - writesBefore == 68 && RailBatchWriteAudit.multiRailBatches - multiBefore == 4
				&& RailBatchWriteAudit.sectionGroups - groupsBefore >= 8, Component.literal("Actual grouped writer was not exercised"));
		LoggerFactory.getLogger(RailBatchWriteTestCases.class).info(
				"Rail batch audit: writes={}, multi-rail batches={}, Section groups={}, variants={}; deferred planning/counts/heights/dirty/serialization verified",
				RailBatchWriteAudit.writes - writesBefore, RailBatchWriteAudit.multiRailBatches - multiBefore, RailBatchWriteAudit.sectionGroups - groupsBefore,
				variants.size());
		helper.succeed();
	}

	@SuppressWarnings("unchecked")
	public static void paletteBatchHandlesResizesAndGlobalIds(GameTestHelper helper) {
		List<BlockState> fillers = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			if (block != Blocks.POWERED_RAIL && block != Blocks.ACTIVATOR_RAIL) {
				fillers.addAll(block.getStateDefinition().getPossibleStates());
			}
			if (fillers.size() >= 300)
				break;
		}
		List<BlockState> targets = new ArrayList<>();
		for (Block block : new Block[] { Blocks.POWERED_RAIL, Blocks.ACTIVATOR_RAIL }) {
			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
				if (state.getValue(PoweredRailBlock.POWERED))
					targets.add(state);
			}
		}
		var strategy = helper.getLevel().palettedContainerFactory().blockStatesStrategy();
		for (int fillerCount : new int[] { 0, 9, 15, 31, 255, 300 }) {
			BlockState original = Blocks.POWERED_RAIL.defaultBlockState();
			PalettedContainer<BlockState> palette = new PalettedContainer<>(original, strategy);
			for (int index = 0; index < fillerCount; ++index) {
				int cell = 1024 + index;
				palette.set(cell & 15, cell >> 8, (cell >> 4) & 15, fillers.get(index));
			}
			for (int step : new int[] { 1, -1 }) {
				PalettedContainer<BlockState> batch = palette.copy();
				PalettedContainer<BlockState> reference = palette.copy();
				int beforeBits = batch.bitsPerEntry();
				long[] positions = new long[targets.size()];
				int[] next = new int[targets.size()];
				int[] ids = new int[targets.size()];
				BlockState[] states = targets.toArray(BlockState[]::new);
				for (int index = 0; index < positions.length; ++index) {
					int cell = 17 + 37 * index;
					positions[index] = BlockPos.asLong(cell & 15, cell >> 8, (cell >> 4) & 15);
					next[index] = index + step < positions.length ? index + step : -1;
					reference.set(cell & 15, cell >> 8, (cell >> 4) & 15, states[index]);
				}
				int first = step > 0 ? 0 : positions.length - 1;
				((RailPaletteAccess<BlockState>) (Object) batch).railoptimization$setBatch(positions, states, next, first, ids);
				PalettedContainer<BlockState> saved = PalettedContainer.unpack(strategy, batch.pack(strategy)).getOrThrow();
				for (int y = 0; y < 16; ++y)
					for (int z = 0; z < 16; ++z)
						for (int x = 0; x < 16; ++x) {
							helper.assertTrue(batch.get(x, y, z) == reference.get(x, y, z) && saved.get(x, y, z) == reference.get(x, y, z),
									Component.literal("Palette batch or disk representation differs at fillerCount=" + fillerCount + ", step=" + step));
						}
				if (fillerCount == 0)
					helper.assertTrue(beforeBits == 0 && batch.bitsPerEntry() > 0, Component.literal("Zero-bit resize was not exercised"));
				if (fillerCount == 255)
					helper.assertTrue(beforeBits == 8 && batch.bitsPerEntry() > 8, Component.literal("Local-to-global resize was not exercised"));
				LoggerFactory.getLogger(RailBatchWriteTestCases.class).info(
						"Palette batch: fillers={}, step={}, bits {}->{}, 4096 cells and disk roundtrip match normal setters",
						fillerCount, step, beforeBits, batch.bitsPerEntry());
			}
		}
		helper.succeed();
	}

	private static short[] counts(LevelChunkSection section) {
		String[] names = { "nonEmptyBlockCount", "fluidCount", "tickingBlockCount", "tickingFluidCount" };
		short[] result = new short[names.length];
		try {
			for (int i = 0; i < names.length; i++) {
				Field field = LevelChunkSection.class.getDeclaredField(names[i]);
				field.setAccessible(true);
				result[i] = field.getShort(section);
			}
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	public static void platformSnapshotCaptureUsesNormalWrites(GameTestHelper helper) {
		try {
			Field capturing = net.minecraft.world.level.Level.class.getField("captureBlockSnapshots");
			List<Object> snapshots = (List<Object>) net.minecraft.world.level.Level.class.getField("capturedBlockSnapshots").get(helper.getLevel());
			boolean previousCapture = capturing.getBoolean(helper.getLevel());
			int previousSize = snapshots.size();
			BlockState original = Blocks.POWERED_RAIL.defaultBlockState();
			RailBatchTestAccess.Context context = new RailBatchTestAccess.Context(8);
			context.memo.beginWalk(helper.getLevel());
			context.batchWriter.begin();
			for (int index = 0; index < 2; ++index) {
				BlockPos pos = helper.absolutePos(new BlockPos(4 + index, 3, 4));
				helper.getLevel().setBlock(pos, original, Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE);
				context.batchWriter.queue(pos, original, true);
			}
			long writesBefore = RailBatchWriteAudit.writes;
			long fallbackBefore = RailBatchWriteAudit.fallbackBatches;
			long clientBefore = RailBatchWriteAudit.clientUpdates;
			RailBatchWriteAudit.enabled = true;
			RailBatchTestAccess.beginLaneWrite();
			try {
				capturing.setBoolean(helper.getLevel(), true);
				context.batchWriter.write(helper.getLevel(), context);
			} finally {
				capturing.setBoolean(helper.getLevel(), previousCapture);
				RailBatchWriteAudit.enabled = false;
				RailBatchTestAccess.endLaneWrite();
			}
			try {
				helper.assertTrue(snapshots.size() - previousSize == 2 && RailBatchWriteAudit.writes == writesBefore
						&& RailBatchWriteAudit.fallbackBatches == fallbackBefore + 1 && RailBatchWriteAudit.clientUpdates == clientBefore,
						Component.literal("Platform snapshots bypassed, partially written, or notified clients early"));
				for (int index = 0; index < 2; ++index) {
					Object snapshot = snapshots.get(previousSize + index);
					BlockPos pos = (BlockPos) snapshot.getClass().getMethod("getPos").invoke(snapshot);
					helper.assertTrue(pos.asLong() == context.changeList.position(index)
							&& snapshot.getClass().getMethod("getState").invoke(snapshot) == original
							&& helper.getLevel().getBlockState(pos) == original.setValue(PoweredRailBlock.POWERED, true),
							Component.literal("Captured rail snapshot lost its position or original state"));
					snapshot.getClass().getMethod("restore").invoke(snapshot);
					helper.assertTrue(helper.getLevel().getBlockState(pos) == original, Component.literal("Rail snapshot could not be restored"));
				}
			} finally {
				snapshots.subList(previousSize, snapshots.size()).clear();
			}
			LoggerFactory.getLogger(RailBatchWriteTestCases.class).info("Platform batch capture: two normal snapshots, no early client update, both restored");
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException(failure);
		}
		helper.succeed();
	}
}
