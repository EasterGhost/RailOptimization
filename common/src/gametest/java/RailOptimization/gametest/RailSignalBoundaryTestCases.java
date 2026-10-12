package RailOptimization.gametest;

import RailOptimization.RailLogic;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.chunk.LevelChunk;

public final class RailSignalBoundaryTestCases {
	private static final Method READ = method("getBlockState", Level.class, LevelChunk.class, int.class, int.class, BlockPos.class);
	private static final Method SIGNAL = method("hasNeighborSignalFast", Level.class, BlockPos.class, MutableBlockPos.class, LevelChunk.class,
			int.class, int.class);

	private RailSignalBoundaryTestCases() {
	}

	public static void heightBoundaryReadsMatchVanilla(GameTestHelper helper) {
		Level level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		int chunkX = origin.getX() >> 4;
		int chunkZ = origin.getZ() >> 4;
		LevelChunk chunk = level.getChunk(chunkX, chunkZ);
		int x = (chunkX << 4) + 8;
		int z = (chunkZ << 4) + 8;
		for (int y : new int[] { level.getMinY() - 1, level.getMinY(), level.getMinY() + 1,
				level.getMaxY() - 1, level.getMaxY(), level.getMaxY() + 1 }) {
			for (int offset : new int[] { 0, 16 }) {
				BlockPos pos = new BlockPos(x + offset, y, z);
				BlockState actual = (BlockState) invoke(READ, level, chunk, chunkX, chunkZ, pos);
				helper.assertTrue(actual == level.getBlockState(pos), Component.literal("Boundary read differs from vanilla at " + pos));
				if (!level.isInsideBuildHeight(pos)) {
					helper.assertTrue(actual.is(Blocks.VOID_AIR), Component.literal("Expected VOID_AIR at " + pos));
				}
			}
		}
		int distantChunkX = chunkX + 256;
		helper.assertTrue(!level.getChunkSource().hasChunk(distantChunkX, chunkZ), Component.literal("Boundary probe chunk must start unloaded"));
		for (int y : new int[] { level.getMinY() - 1, level.getMaxY() + 1 }) {
			BlockPos pos = new BlockPos((distantChunkX << 4) + 8, y, z);
			helper.assertTrue(((BlockState) invoke(READ, level, chunk, chunkX, chunkZ, pos)).is(Blocks.VOID_AIR),
					Component.literal("Cross-chunk out-of-height read must return VOID_AIR"));
		}
		helper.assertTrue(!level.getChunkSource().hasChunk(distantChunkX, chunkZ), Component.literal("Out-of-height reads must not load a chunk"));
		helper.succeed();
	}

	@SuppressWarnings("null")
	public static void heightEdgeSignalsMatchVanilla(GameTestHelper helper) {
		Level level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		int baseX = (origin.getX() >> 4) << 4;
		int baseZ = (origin.getZ() >> 4) << 4;
		boolean enabled = RailLogic.isOptimizationEnabled();
		RailLogic.setOptimizationEnabled(true);
		try {
			for (Block railBlock : new Block[] { Blocks.POWERED_RAIL, Blocks.ACTIVATOR_RAIL }) {
				for (boolean eastWest : new boolean[] { true, false }) {
					for (boolean crossing : new boolean[] { false, true }) {
						for (int y : new int[] { level.getMinY() + 1, level.getMaxY() }) {
							BlockPos rail = new BlockPos(baseX + (!eastWest && crossing ? 0 : 8), y, baseZ + (eastWest && crossing ? 0 : 8));
							Direction side = eastWest ? Direction.NORTH : Direction.WEST;
							BlockPos lever = rail.below().relative(side);
							Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
							try {
								for (int dx = -2; dx <= 2; ++dx) {
									for (int dy = -2; dy <= 2; ++dy) {
										for (int dz = -2; dz <= 2; ++dz) {
											BlockPos pos = rail.offset(dx, dy, dz);
											if (level.isInsideBuildHeight(pos)) {
												saved.put(pos, level.getBlockState(pos));
												level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
											}
										}
									}
								}
								level.setBlock(rail.below(), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
								level.setBlock(rail, railBlock.defaultBlockState().setValue(PoweredRailBlock.SHAPE,
										eastWest ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
								level.setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
										.setValue(HorizontalDirectionalBlock.FACING, side), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
								assertSignal(helper, level, rail, false);
								((LeverBlock) Blocks.LEVER).pull(level.getBlockState(lever), level, lever, null);
								assertSignal(helper, level, rail, true);
								((LeverBlock) Blocks.LEVER).pull(level.getBlockState(lever), level, lever, null);
								assertSignal(helper, level, rail, false);
							} finally {
								saved.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_SKIP_ALL_SIDEEFFECTS));
							}
						}
					}
				}
			}
		} finally {
			RailLogic.setOptimizationEnabled(enabled);
		}
		helper.succeed();
	}

	private static void assertSignal(GameTestHelper helper, Level level, BlockPos rail, boolean expected) {
		int chunkX = rail.getX() >> 4;
		int chunkZ = rail.getZ() >> 4;
		boolean actual = (boolean) invoke(SIGNAL, level, rail, new MutableBlockPos(), level.getChunk(chunkX, chunkZ), chunkX, chunkZ);
		helper.assertTrue(level.hasNeighborSignal(rail) == expected, Component.literal("Vanilla signal differs from fixture expectation at " + rail));
		helper.assertTrue(actual == expected, Component.literal("Optimized signal differs from vanilla at " + rail));
		helper.assertTrue(level.getBlockState(rail).getValue(PoweredRailBlock.POWERED) == expected,
				Component.literal("Rail failed to switch at " + rail));
	}

	private static Method method(String name, Class<?>... parameters) {
		try {
			Method method = Class.forName("RailOptimization.RailNeighborSignalChecker").getDeclaredMethod(name, parameters);
			method.setAccessible(true);
			return method;
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException(failure);
		}
	}

	private static Object invoke(Method method, Object... args) {
		try {
			return method.invoke(null, args);
		} catch (InvocationTargetException failure) {
			Throwable cause = failure.getCause();
			if (cause instanceof RuntimeException exception) throw exception;
			if (cause instanceof Error error) throw error;
			throw new IllegalStateException(cause);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException(failure);
		}
	}
}
