package RailOptimization.gametest.batch;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.slf4j.LoggerFactory;

public final class RailLithiumBatchTestCases {
	private record SectionSnapshot(Object data, Object listener, short[] counts, byte[] randomTicks) {
	}

	@SuppressWarnings("null")
	public static void batchPreservesLithiumTrackingAndCounters(GameTestHelper helper) {
		Class<?> dataAccess = optionalClass("net.caffeinemc.mods.lithium.common.world.section.LithiumSectionData");
		Class<?> counting = optionalClass("net.caffeinemc.mods.lithium.common.block.BlockCountingSection");
		if (dataAccess == null || counting == null || !counting.isAssignableFrom(LevelChunkSection.class)) {
			LoggerFactory.getLogger(RailLithiumBatchTestCases.class)
					.info("Lithium Section tracking is not installed/enabled; compatibility probe not applicable");
			helper.succeed();
			return;
		}
		IdentityHashMap<LevelChunkSection, SectionSnapshot> snapshots = new IdentityHashMap<>();
		try {
			Class<?> callback = Class.forName("net.caffeinemc.mods.lithium.common.tracking.block.ChunkSectionChangeCallback");
			Class<?> tracker = Class.forName("net.caffeinemc.mods.lithium.common.tracking.block.BlockChangeTracker");
			Class<?> flags = Class.forName("net.caffeinemc.mods.lithium.common.block.BlockStateFlagHolder");
			Method getFlags = flags.getMethod("lithium$getAllFlags");
			BlockPos origin = helper.absolutePos(new BlockPos(16, 4, 4));
			origin = origin.offset(Math.floorMod(15 - origin.getX(), 16), Math.floorMod(15 - origin.getY(), 16), 0);
			RailBatchTestAccess.Context context = new RailBatchTestAccess.Context(32);
			context.memo.beginWalk(helper.getLevel());
			context.batchWriter.begin();
			List<Long> observed = new ArrayList<>();
			List<BlockState> variants = new ArrayList<>();
			for (Block block : new Block[] { Blocks.POWERED_RAIL, Blocks.ACTIVATOR_RAIL }) {
				for (BlockState state : block.getStateDefinition().getPossibleStates()) {
					if (!state.getValue(PoweredRailBlock.POWERED))
						variants.add(state);
				}
			}
			for (int index = 0; index < variants.size(); ++index) {
				BlockPos pos = origin.offset(index, index & 1, 0);
				BlockState previous = variants.get(index);
				BlockState target = previous.setValue(PoweredRailBlock.POWERED, true);
				helper.assertTrue(getFlags.invoke(previous).equals(getFlags.invoke(target)),
						Component.literal("Rail POWERED changed Lithium tracked predicates"));
				helper.getLevel().setBlock(pos, previous, Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE);
				LevelChunk chunk = helper.getLevel().getChunkAt(pos);
				LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
				context.batchWriter.queue(pos, previous, true);
				if (snapshots.containsKey(section))
					continue;
				Object data = dataAccess.getMethod("lithium$getSectionData").invoke(section);
				Object oldListener = data.getClass().getMethod("getChangeListener").invoke(data);
				short[] counts = (short[]) data.getClass().getMethod("getCountsByFlag").invoke(data);
				byte[] ticks = (byte[]) data.getClass().getMethod("getRandomTickableBlocksByY").invoke(data);
				snapshots.put(section, new SectionSnapshot(data, oldListener, counts == null ? null : counts.clone(), ticks == null ? null : ticks.clone()));
				Object listener = callback.getConstructor(long.class).newInstance(SectionPos.of(pos).asLong());
				int sectionX = pos.getX() & ~15;
				int sectionY = pos.getY() & ~15;
				int sectionZ = pos.getZ() & ~15;
				Object probe = Proxy.newProxyInstance(tracker.getClassLoader(), new Class<?>[] { tracker }, (proxy, method, arguments) -> {
					return switch (method.getName()) {
						case "setChanged" -> {
							observed.add(BlockPos.asLong(sectionX + (int) arguments[1], sectionY + (int) arguments[2], sectionZ + (int) arguments[3]));
							yield true;
						}
						case "onChunkSectionInvalidated" -> null;
						case "hashCode" -> System.identityHashCode(proxy);
						case "equals" -> proxy == arguments[0];
						case "toString" -> "Rail batch tracking probe";
						default -> throw new UnsupportedOperationException(method.toString());
					};
				});
				callback.getMethod("addTracker", tracker).invoke(listener, probe);
				data.getClass().getMethod("setChangeListener", callback).invoke(data, listener);
			}
			// Fixtures above use normal setters; only the batch's notifications are
			// measured.
			observed.clear();
			for (var entry : new ArrayList<>(snapshots.entrySet())) {
				SectionSnapshot snapshot = entry.getValue();
				short[] counts = (short[]) snapshot.data.getClass().getMethod("getCountsByFlag").invoke(snapshot.data);
				byte[] ticks = (byte[]) snapshot.data.getClass().getMethod("getRandomTickableBlocksByY").invoke(snapshot.data);
				snapshots.put(entry.getKey(),
						new SectionSnapshot(snapshot.data, snapshot.listener, counts == null ? null : counts.clone(), ticks == null ? null : ticks.clone()));
			}
			write(helper, context);
			helper.assertTrue(observed.size() == variants.size(), Component.literal("Lithium did not receive the batch changes"));
			for (int index = 0; index < observed.size(); ++index) {
				helper.assertTrue(observed.get(index) == context.changeList.position(index), Component.literal("Lithium was notified about the wrong rail"));
			}
			for (SectionSnapshot snapshot : snapshots.values()) {
				short[] counts = (short[]) snapshot.data.getClass().getMethod("getCountsByFlag").invoke(snapshot.data);
				byte[] ticks = (byte[]) snapshot.data.getClass().getMethod("getRandomTickableBlocksByY").invoke(snapshot.data);
				helper.assertTrue(Arrays.equals(snapshot.counts, counts) && Arrays.equals(snapshot.randomTicks, ticks),
						Component.literal("Batch damaged Lithium Section counts"));
			}
			LoggerFactory.getLogger(RailLithiumBatchTestCases.class)
					.info("Lithium batch audit: 24 POWERED pairs, tracking callbacks delivered, flag/random-tick counts preserved");
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException(failure);
		} finally {
			for (SectionSnapshot snapshot : snapshots.values()) {
				try {
					Class<?> callback = Class.forName("net.caffeinemc.mods.lithium.common.tracking.block.ChunkSectionChangeCallback");
					snapshot.data.getClass().getMethod("setChangeListener", callback).invoke(snapshot.data, snapshot.listener);
				} catch (ReflectiveOperationException failure) {
					throw new IllegalStateException(failure);
				}
			}
		}
		helper.succeed();
	}

	@SuppressWarnings("null")
	public static void batchInvalidatesActiveLithiumEntityCacheWithoutDisablingIt(GameTestHelper helper) {
		Class<?> provider = optionalClass("net.caffeinemc.mods.lithium.common.tracking.VicinityCacheProvider");
		if (provider == null || !provider.isAssignableFrom(Entity.class)) {
			LoggerFactory.getLogger(RailLithiumBatchTestCases.class).info("Lithium entity cache is not enabled; cache probe not applicable");
			helper.succeed();
			return;
		}
		BlockPos initialRail = helper.absolutePos(new BlockPos(8, 4, 8));
		BlockPos rail = new BlockPos(8, 4, 8).offset(Math.floorMod(8 - initialRail.getX(), 16), 0, Math.floorMod(8 - initialRail.getZ(), 16));
		helper.setBlock(rail.below(), Blocks.SMOOTH_STONE);
		helper.setBlock(rail, Blocks.POWERED_RAIL);
		Entity entity = helper.spawn(EntityType.ARMOR_STAND, rail.above());
		entity.setNoGravity(true);
		try {
			Object cache = provider.getMethod("lithium$getVicinityCache").invoke(entity);
			Class<?> type = cache.getClass();
			helper.startSequence().thenIdle(2).thenExecute(() -> {
				try {
					if (!(boolean) type.getMethod("isTracking").invoke(cache))
						type.getMethod("initTracking", Entity.class).invoke(cache, entity);
					type.getMethod("updateCache", Entity.class).invoke(cache, entity);
				} catch (ReflectiveOperationException failure) {
					throw new IllegalStateException(failure);
				}
			}).thenIdle(2).thenExecute(() -> {
				try {
					type.getMethod("updateCache", Entity.class).invoke(cache, entity);
					type.getMethod("setCanSkipSupportingBlockSearch", boolean.class).invoke(cache, true);
					type.getMethod("updateCache", Entity.class).invoke(cache, entity);
					helper.assertTrue((boolean) type.getMethod("canSkipSupportingBlockSearch").invoke(cache),
							Component.literal("Entity cache did not become active"));
					RailBatchTestAccess.Context context = new RailBatchTestAccess.Context(8);
					context.memo.beginWalk(helper.getLevel());
					context.batchWriter.begin();
					BlockPos absolute = helper.absolutePos(rail);
					context.batchWriter.queue(absolute, helper.getLevel().getBlockState(absolute), true);
					write(helper, context);
					type.getMethod("updateCache", Entity.class).invoke(cache, entity);
					helper.assertTrue(!(boolean) type.getMethod("canSkipSupportingBlockSearch").invoke(cache)
							&& (boolean) type.getMethod("isTracking").invoke(cache),
							Component.literal("Batch left a stale cache or disabled Lithium tracking"));
					LoggerFactory.getLogger(RailLithiumBatchTestCases.class)
							.info("Lithium real entity cache: active before batch, invalidated after batch, tracking remains enabled");
				} catch (ReflectiveOperationException failure) {
					throw new IllegalStateException(failure);
				} finally {
					try {
						type.getMethod("remove").invoke(cache);
					} catch (ReflectiveOperationException failure) {
						throw new IllegalStateException(failure);
					}
					entity.discard();
				}
			}).thenSucceed();
		} catch (ReflectiveOperationException failure) {
			entity.discard();
			throw new IllegalStateException(failure);
		}
	}

	@SuppressWarnings("null")
	public static void batchInvalidatesAdjacentLithiumHopperCaches(GameTestHelper helper) {
		Class<?> receiver = optionalClass("net.caffeinemc.mods.lithium.common.hopper.UpdateReceiver");
		if (receiver == null || !receiver.isAssignableFrom(HopperBlockEntity.class)) {
			LoggerFactory.getLogger(RailLithiumBatchTestCases.class).info("Lithium hopper cache is not enabled; cache probe not applicable");
			helper.succeed();
			return;
		}
		try {
			Method insert = HopperBlockEntity.class.getMethod("lithium$getInsertBlockInventory", Level.class);
			Method extract = HopperBlockEntity.class.getMethod("lithium$getExtractBlockInventory", Level.class, BlockPos.class, BlockState.class);
			Field insertion = HopperBlockEntity.class.getDeclaredField("insertionMode");
			Field extraction = HopperBlockEntity.class.getDeclaredField("extractionMode");
			insertion.setAccessible(true);
			extraction.setAccessible(true);
			BlockPos origin = helper.absolutePos(new BlockPos(16, 4, 4));
			origin = origin.offset(Math.floorMod(-origin.getX(), 16), 0, Math.floorMod(-origin.getZ(), 16));
			int[][] offsets = { { 8, 8 }, { 0, 0 }, { 15, 0 }, { 15, 15 } };
			int checks = 0;
			for (int[] offset : offsets) {
				BlockPos rail = origin.offset(offset[0], 0, offset[1]);
				BlockState previous = Blocks.POWERED_RAIL.defaultBlockState();
				for (Direction direction : Direction.values()) {
					helper.assertTrue(helper.getBounds().contains(rail.relative(direction).getCenter()),
							Component.literal("Hopper cache fixture outside structure"));
					BlockState hopper = Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING,
							direction == Direction.DOWN ? Direction.DOWN : direction.getOpposite());
					helper.getLevel().setBlock(rail.relative(direction), hopper, Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE);
				}
				helper.getLevel().setBlock(rail, previous, Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE);
				for (boolean powered : new boolean[] { true, false }) {
					List<HopperBlockEntity> hoppers = new ArrayList<>();
					for (Direction direction : Direction.values()) {
						HopperBlockEntity hopper = (HopperBlockEntity) helper.getLevel().getBlockEntity(rail.relative(direction));
						if (direction == Direction.DOWN)
							extract.invoke(hopper, helper.getLevel(), rail, previous);
						else
							insert.invoke(hopper, helper.getLevel());
						Field mode = direction == Direction.DOWN ? extraction : insertion;
						helper.assertTrue(mode.get(hopper).toString().equals("NO_BLOCK_INVENTORY"), Component.literal("Hopper cache did not warm up"));
						hoppers.add(hopper);
					}
					RailBatchTestAccess.Context context = new RailBatchTestAccess.Context(8);
					context.memo.beginWalk(helper.getLevel());
					context.batchWriter.begin();
					context.batchWriter.queue(rail, previous, powered);
					write(helper, context);
					int index = 0;
					for (Direction direction : Direction.values()) {
						HopperBlockEntity hopper = hoppers.get(index++);
						Field mode = direction == Direction.DOWN ? extraction : insertion;
						helper.assertTrue(mode.get(hopper).toString().equals("UNKNOWN"), Component.literal("Batch left a warm hopper cache stale"));
						++checks;
					}
					previous = previous.setValue(PoweredRailBlock.POWERED, powered);
				}
			}
			LoggerFactory.getLogger(RailLithiumBatchTestCases.class).info("Lithium hopper cache audit: {} invalidations, six directions, loaded chunk edges",
					checks);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException(failure);
		}
		helper.succeed();
	}

	private static void write(GameTestHelper helper, RailBatchTestAccess.Context context) {
		RailBatchTestAccess.beginLaneWrite();
		try {
			context.batchWriter.write(helper.getLevel(), context);
		} finally {
			RailBatchTestAccess.endLaneWrite();
		}
	}

	private static Class<?> optionalClass(String name) {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException absent) {
			return null;
		}
	}
}
