package RailOptimization;

import RailOptimization.gametest.mixin.BaseRailBlockGameTestInvoker;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailSupportCacheGameTest {
	private static final String STRUCTURE = "railoptimization-gametest:review_tall_empty";
	private static final BlockPos RAIL = new BlockPos(3, 3, 3);

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_183", structure = STRUCTURE, maxTicks = 1)
	public void supportAndShapeChangesMatchVanilla(GameTestHelper helper) {
		isolated(helper, level -> {
			BlockPos pos = helper.absolutePos(RAIL);
			RailLogicTestAccess.forceOptimizedAt(pos);
			put(level, pos.below(), Blocks.STONE);
			assertSupport(helper, pos, level, RailShape.EAST_WEST, false);
			assertSupport(helper, pos, level, RailShape.NORTH_SOUTH, false);
			RailShape[] shapes = {RailShape.ASCENDING_EAST, RailShape.ASCENDING_WEST, RailShape.ASCENDING_NORTH, RailShape.ASCENDING_SOUTH};
			Direction[] directions = {Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH};
			for (int index = 0; index < shapes.length; index++) {
				BlockPos side = pos.relative(directions[index]);
				assertSupport(helper, pos, level, shapes[index], true);
				put(level, side, Blocks.STONE);
				assertSupport(helper, pos, level, shapes[index], false);
				put(level, pos.below(), Blocks.AIR);
				assertSupport(helper, pos, level, shapes[index], true);
				put(level, pos.below(), Blocks.STONE);
				assertSupport(helper, pos, level, shapes[index], false);
				put(level, side, Blocks.AIR);
				assertSupport(helper, pos, level, shapes[index], true);
				assertSupport(helper, pos, level, RailShape.EAST_WEST, false);
			}
		});
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_184", structure = STRUCTURE, maxTicks = 1)
	public void nestedWritesDoNotReuseOrPublishSupport(GameTestHelper helper) {
		isolated(helper, level -> {
			BlockPos pos = helper.absolutePos(RAIL);
			RailLogicTestAccess.forceOptimizedAt(pos);
			put(level, pos.below(), Blocks.STONE);
			assertSupport(helper, pos, level, RailShape.EAST_WEST, false);
			long before = epoch(level);
			RailUpdateMemo.beginLaneWrite();
			try {
				RailUpdateMemo.beginLaneWrite();
				try {
					put(level, pos.below(), Blocks.AIR);
					assertSupport(helper, pos, level, RailShape.EAST_WEST, true);
				} finally {
					RailUpdateMemo.endLaneWrite();
				}
				put(level, pos.below(), Blocks.STONE);
				assertSupport(helper, pos, level, RailShape.EAST_WEST, false);
				put(level, pos.below(), Blocks.AIR);
			} finally {
				RailUpdateMemo.endLaneWrite();
			}
			helper.assertValueEqual(before, epoch(level), "suppressed writes leave epoch unchanged");
			assertSupport(helper, pos, level, RailShape.EAST_WEST, true);
		});
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_185", structure = STRUCTURE, maxTicks = 1)
	public void supportCacheDoesNotCrossLevels(GameTestHelper helper) {
		isolated(helper, first -> {
			Level second = first.getServer().getLevel(Level.NETHER);
			if (second == null) throw new IllegalStateException("Nether unavailable");
			BlockPos origin = helper.absolutePos(RAIL);
			BlockPos pos = new BlockPos(origin.getX(), 32, origin.getZ());
			RailLogicTestAccess.forceOptimizedAt(pos);
			BlockState firstSupport = first.getBlockState(pos.below());
			BlockState secondSupport = second.getBlockState(pos.below());
			try {
				put(first, pos.below(), Blocks.STONE);
				put(second, pos.below(), Blocks.AIR);
				assertSupport(helper, pos, first, RailShape.EAST_WEST, false);
				assertSupport(helper, pos, second, RailShape.EAST_WEST, true);
				assertSupport(helper, pos, first, RailShape.EAST_WEST, false);
			} finally {
				first.setBlock(pos.below(), firstSupport, Block.UPDATE_NONE);
				second.setBlock(pos.below(), secondSupport, Block.UPDATE_NONE);
			}
		});
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_186", structure = STRUCTURE, maxTicks = 1)
	public void supportGenerationRolloverClearsOldEntries(GameTestHelper helper) {
		isolated(helper, level -> {
			BlockPos pos = helper.absolutePos(RAIL);
			RailLogicTestAccess.forceOptimizedAt(pos);
			put(level, pos.below(), Blocks.STONE);
			assertSupport(helper, pos, level, RailShape.EAST_WEST, false);
			Object cache = caches().get();
			try {
				// Recreate an old generation-one entry before the counter wraps back to one.
				Arrays.fill((int[]) field(cache, "generations").get(cache), 1);
				field(cache, "generation").setInt(cache, -1);
			} catch (ReflectiveOperationException exception) {
				throw new IllegalStateException("Cannot stage cache rollover", exception);
			}
			long before = epoch(level);
			RailUpdateMemo.beginLaneWrite();
			try {
				put(level, pos.below(), Blocks.AIR);
			} finally {
				RailUpdateMemo.endLaneWrite();
			}
			helper.assertValueEqual(before, epoch(level), "rollover must not rely on epoch invalidation");
			assertSupport(helper, pos, level, RailShape.EAST_WEST, true);
		});
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_187", structure = STRUCTURE, maxTicks = 1)
	public void actualNeighborUpdatesKeepTheReferenceUncached(GameTestHelper helper) {
		isolated(helper, level -> {
			BlockPos pos = helper.absolutePos(RAIL);
			for (Block block : new Block[]{Blocks.POWERED_RAIL, Blocks.ACTIVATOR_RAIL, Blocks.RAIL}) {
				helper.setBlock(RAIL.below(), Blocks.STONE);
				helper.setBlock(RAIL, block);
				caches().remove();
				RailLogicTestAccess.forceVanillaAt(pos);
				try {
					level.neighborChanged(pos, Blocks.STONE, null);
					helper.assertValueEqual(false, hasOwner(), "vanilla neighbor must not enter the support cache");
				} finally {
					RailLogicTestAccess.forceOptimizedAt(pos);
				}
				level.neighborChanged(pos, Blocks.STONE, null);
				helper.assertValueEqual(block != Blocks.RAIL, hasOwner(), "only powered/activator rails populate support cache");
				helper.assertBlockPresent(block, RAIL);
			}
		});
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_188", structure = STRUCTURE, maxTicks = 1)
	public void cachedRailsDropWhenSupportDisappearsDuringBatchWrites(GameTestHelper helper) {
		isolated(helper, level -> {
			BlockPos pos = helper.absolutePos(RAIL);
			RailLogicTestAccess.forceOptimizedAt(pos);
			for (Block block : new Block[]{Blocks.POWERED_RAIL, Blocks.ACTIVATOR_RAIL}) {
				helper.setBlock(RAIL.below(), Blocks.STONE);
				helper.setBlock(RAIL, block.defaultBlockState().setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST));
				level.neighborChanged(pos, Blocks.STONE, null);
				long before = epoch(level);
				RailUpdateMemo.beginLaneWrite();
				try {
					put(level, pos.below(), Blocks.AIR);
					level.neighborChanged(pos, Blocks.STONE, null);
					helper.assertBlockNotPresent(block, RAIL);
					helper.assertItemEntityPresent(block.asItem(), RAIL, 1.5);
					helper.assertValueEqual(before, epoch(level), "support loss exercised suppressed epoch path");
				} finally {
					RailUpdateMemo.endLaneWrite();
				}
			}
		});
	}

	private static void isolated(GameTestHelper helper, Consumer<Level> test) {
		ThreadLocal<?> caches = caches();
		caches.remove();
		try {
			test.accept(helper.getLevel());
		} finally {
			caches.remove();
		}
		helper.succeed();
	}

	private static void assertSupport(GameTestHelper helper, BlockPos pos, Level level, RailShape shape, boolean removed) {
		helper.assertValueEqual(removed, BaseRailBlockGameTestInvoker.railoptimization$shouldBeRemoved(pos, level, shape), "vanilla " + shape);
		for (int attempt = 0; attempt < 2; attempt++) {
			helper.assertValueEqual(removed, RailSupportCache.shouldBeRemoved(pos, level, shape), "optimized " + shape + " attempt " + attempt);
		}
	}

	@SuppressWarnings("null")
	private static void put(Level level, BlockPos pos, Block block) {
		level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_NONE);
	}

	private static long epoch(Level level) {
		return ((LevelEpochAccess) level).railoptimization$getBlockChangeEpoch().get();
	}

	private static ThreadLocal<?> caches() {
		try {
			Field field = RailSupportCache.class.getDeclaredField("CACHE");
			field.setAccessible(true);
			return (ThreadLocal<?>) field.get(null);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Cannot inspect support cache", exception);
		}
	}

	private static Field field(Object owner, String name) throws NoSuchFieldException {
		Field field = owner.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}

	private static boolean hasOwner() {
		Object cache = caches().get();
		try {
			return field(cache, "level").get(cache) != null;
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Cannot inspect support cache owner", exception);
		}
	}
}
