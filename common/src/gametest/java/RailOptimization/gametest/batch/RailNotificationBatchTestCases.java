package RailOptimization.gametest.batch;

import RailOptimization.RailNeighborBatchAccess;
import RailOptimization.RailNotificationBatch;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.redstone.NeighborUpdater;

public final class RailNotificationBatchTestCases {
	public static void bitmapPlansMatchHashAndSurviveReuse(GameTestHelper helper) {
		try {
			Class<?> type = RailNotificationBatch.class;
			Class<?> changesType = Class.forName("RailOptimization.RailChangeList");
			Constructor<?> changesConstructor = changesType.getDeclaredConstructor(int.class);
			changesConstructor.setAccessible(true);
			Constructor<?> constructor = type.getDeclaredConstructor(int.class);
			constructor.setAccessible(true);
			Method add = method(changesType, "add", BlockPos.class, BlockState.class, BlockState.class);
			Method plan = method(type, "plan", Level.class, int.class, int.class, changesType);
			Method prepare = method(type, "prepareColumns", RailShape.class, changesType);
			Object indexed = constructor.newInstance(129);
			int cases = 0;
			for (boolean eastWest : new boolean[] { true, false }) {
				RailShape shape = eastWest ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
				for (int limit : new int[] { 64, 1, 8, 1, 64 }) {
					for (int geometry = 0; geometry < 9; ++geometry) {
						for (boolean center : new boolean[] { true, false }) {
							for (boolean activator : new boolean[] { false, true }) {
								Object changes = changesConstructor.newInstance(129);
								int first = center ? limit : 0;
								BlockPos[] positions = new BlockPos[first + limit + 1];
								BlockState[] states = new BlockState[positions.length];
								BlockState[] targets = new BlockState[positions.length];
								for (int index = 0; index < positions.length; ++index) {
									int distance = index <= first ? -index : index - first;
									int up = height(distance, geometry);
									positions[index] = new BlockPos(eastWest ? 15 + distance : 15, -56 + up, eastWest ? 15 : 15 + distance);
									RailShape railShape = height(distance + 1, geometry) > up ? (eastWest ? RailShape.ASCENDING_EAST : RailShape.ASCENDING_SOUTH)
											: height(distance - 1, geometry) > up ? (eastWest ? RailShape.ASCENDING_WEST : RailShape.ASCENDING_NORTH) : shape;
									states[index] = (activator ? Blocks.ACTIVATOR_RAIL : Blocks.POWERED_RAIL).defaultBlockState()
											.setValue(PoweredRailBlock.SHAPE, railShape).setValue(PoweredRailBlock.POWERED, (geometry & 1) != 0);
									targets[index] = states[index].cycle(PoweredRailBlock.POWERED);
									add.invoke(changes, positions[index], states[index], targets[index]);
								}
								ReferencePlan reference = new ReferencePlan(eastWest, first, limit, positions, states, targets);
								field(type, "size").setInt(indexed, 0);
								field(type, "cost").setInt(indexed, 0);
								prepare.invoke(indexed, shape, changes);
								plan.invoke(indexed, helper.getLevel(), first, limit, changes);
								int size = reference.notifications.size();
								helper.assertTrue(size == field(type, "size").getInt(indexed) && reference.cost == field(type, "cost").getInt(indexed),
										Component.literal("Planner size/cost mismatch"));
								helper.assertTrue(field(type, "source").get(indexed) == states[0].getBlock(), Component.literal("Wrong batch notification source"));
								long[] actualPositions = (long[]) field(type, "positions").get(indexed);
								byte[] actualKinds = (byte[]) field(type, "kinds").get(indexed);
								BlockState[] actualShapes = (BlockState[]) field(type, "shapes").get(indexed);
								byte[] actualMasks = (byte[]) field(type, "directionMasks").get(indexed);
								for (int index = 0; index < size; ++index) {
									Notification expected = reference.notifications.get(index);
									helper.assertTrue(actualPositions[index] == expected.position && actualKinds[index] == expected.kind
											&& (expected.kind != 1 || actualShapes[index] == expected.shape)
											&& (expected.kind != 0 || actualMasks[index] == expected.mask),
											Component.literal("Planner sequence differs at " + index));
								}
								++cases;
							}
						}
					}
				}
			}
			helper.assertTrue(cases == 360, Component.literal("Incomplete planner matrix"));
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException(failure);
		}
		helper.succeed();
	}

	public static void nativeQueueHonorsBudgetAndDebugFallback(GameTestHelper helper) {
		try {
			Method enqueue = method(CollectingNeighborUpdater.class, "addAndRun", BlockPos.class, CollectingNeighborUpdater.NeighborUpdates.class);
			BlockPos pos = helper.absolutePos(new BlockPos(8, 4, 8));
			for (int limit : new int[] { 2, 20, -1 }) {
				CollectingNeighborUpdater updater = new CollectingNeighborUpdater(helper.getLevel(), limit);
				RailNeighborBatchAccess access = (RailNeighborBatchAccess) updater;
				List<RailNotificationBatch> batches = new ArrayList<>();
				helper.assertTrue(!access.railoptimization$canBatch(1), Component.literal("Idle queue must use the original notifier"));
				enqueue.invoke(updater, pos, new CollectingNeighborUpdater.NeighborUpdates() {
					@SuppressWarnings("null")
					@Override
					public boolean runNext(Level level) {
						helper.assertTrue(access.railoptimization$canBatch(limit < 0 ? Integer.MAX_VALUE : limit - 1), Component.literal("Available budget rejected"));
						if (limit >= 0) helper.assertTrue(!access.railoptimization$canBatch(limit), Component.literal("Insufficient budget accepted"));
						updater.setDebugListener(ignored -> {});
						helper.assertTrue(!access.railoptimization$canBatch(1), Component.literal("Debug listener must use the original notifier"));
						updater.setDebugListener(null);
						if (limit != 2) {
							RailNotificationBatch batch = singleRailPlan(level, pos);
							batches.add(batch);
							int cost = batch.taskCost();
							access.railoptimization$enqueueBatch(batch);
							try {
								helper.assertTrue(field(CollectingNeighborUpdater.class, "count").getInt(updater) == cost + 1,
										Component.literal("Batch changed native task accounting"));
							} catch (ReflectiveOperationException failure) {
								throw new IllegalStateException(failure);
							}
						}
						return false;
					}

					@SuppressWarnings("null")
					@Override
					public void forEachUpdatedPos(Consumer<BlockPos> output) {
						output.accept(pos);
					}
				});
				helper.assertTrue(field(CollectingNeighborUpdater.class, "count").getInt(updater) == 0, Component.literal("Native queue did not finish"));
				for (RailNotificationBatch batch : batches) {
					helper.assertTrue(field(RailNotificationBatch.class, "origin").get(batch) == null
							&& field(RailNotificationBatch.class, "readChunk").get(batch) == null
							&& field(RailNotificationBatch.class, "readSection").get(batch) == null,
							Component.literal("Completed batch retained dispatch position or chunk"));
				}
			}
			sectionReadsMatchChunkAfterWrites(helper);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException(failure);
		}
		helper.succeed();
	}

	@SuppressWarnings("null")
	private static void sectionReadsMatchChunkAfterWrites(GameTestHelper helper) throws ReflectiveOperationException {
		Level level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		BlockPos pos = new BlockPos((origin.getX() & ~15) + 15, (level.getMaxY() & ~15) - 1, (origin.getZ() & ~15) + 8);
		BlockPos[] probes = { pos, pos.above(), pos.east(), pos.east().above() };
		BlockState[] saved = new BlockState[probes.length];
		for (int index = 0; index < probes.length; ++index) {
			saved[index] = level.getBlockState(probes[index]);
		}
		RailNotificationBatch batch = singleRailPlan(level, pos);
		Method read = method(RailNotificationBatch.class, "neighborState", Level.class, BlockPos.class);
		try {
			var chunk = level.getChunkAt(pos);
			helper.assertTrue(chunk.getSection(chunk.getSectionIndex(pos.getY())).hasOnlyAir(), Component.literal("Read probe requires an empty sky section"));
			level.setBlock(pos, Blocks.CAVE_AIR.defaultBlockState(), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
			helper.assertTrue(read.invoke(batch, level, pos) == Blocks.AIR.defaultBlockState(), Component.literal("Empty section must normalize cave air"));
			for (BlockState state : new BlockState[] { Blocks.SMOOTH_STONE.defaultBlockState(), Blocks.AIR.defaultBlockState() }) {
				for (BlockPos probe : probes) {
					level.setBlock(probe, state, Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
					BlockState expected = level.getChunkAt(probe).getBlockState(probe);
					helper.assertTrue(read.invoke(batch, level, probe) == expected, Component.literal("NC read differs after section/chunk transition"));
				}
				BlockState expected = level.getChunkAt(pos).getBlockState(pos);
				helper.assertTrue(read.invoke(batch, level, pos) == expected, Component.literal("NC read retained a stale section or state"));
			}
		} finally {
			for (int index = 0; index < probes.length; ++index) {
				level.setBlock(probes[index], saved[index], Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
			}
		}
	}

	@SuppressWarnings("null")
	private static RailNotificationBatch singleRailPlan(Level level, BlockPos pos) {
		try {
			Class<?> changesType = Class.forName("RailOptimization.RailChangeList");
			Constructor<?> changesConstructor = changesType.getDeclaredConstructor(int.class);
			changesConstructor.setAccessible(true);
			Object changes = changesConstructor.newInstance(1);
			BlockState previous = Blocks.POWERED_RAIL.defaultBlockState();
			method(changesType, "add", BlockPos.class, BlockState.class, BlockState.class)
					.invoke(changes, pos, previous, previous.setValue(PoweredRailBlock.POWERED, true));
			Constructor<RailNotificationBatch> constructor = RailNotificationBatch.class.getDeclaredConstructor(int.class);
			constructor.setAccessible(true);
			RailNotificationBatch batch = constructor.newInstance(1);
			method(RailNotificationBatch.class, "prepareColumns", RailShape.class, changesType).invoke(batch, RailShape.NORTH_SOUTH, changes);
			method(RailNotificationBatch.class, "plan", Level.class, int.class, int.class, changesType)
					.invoke(batch, level, 0, 0, changes);
			return batch;
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException(failure);
		}
	}

	private static int height(int distance, int geometry) {
		return switch (geometry) {
			case 1 -> Math.abs(distance) / 3;
			case 2 -> Math.abs(Math.floorMod(distance, 6) - 3);
			case 3 -> distance;
			case 4 -> -distance;
			case 5 -> Math.abs(distance);
			case 6 -> Math.abs(distance) & 1;
			case 7 -> -Math.abs(distance);
			case 8 -> Math.floorDiv(distance, 2);
			default -> 0;
		};
	}

	private record Notification(long position, byte kind, BlockState shape, byte mask) {
	}

	private static final class ReferencePlan {
		private final Set<Long> seen = new HashSet<>();
		private final List<Notification> notifications = new ArrayList<>();
		private final BlockPos[] positions;
		private final BlockState[] states;
		private final BlockState[] targets;
		private int cost;

		ReferencePlan(boolean eastWest, int first, int second, BlockPos[] positions, BlockState[] states, BlockState[] targets) {
			this.positions = positions;
			this.states = states;
			this.targets = targets;
			boolean slope = false;
			for (BlockState state : states) slope |= state.getValue(PoweredRailBlock.SHAPE).isSlope();
			boolean endpoint = !slope && first == 0 && second > 0;
			if (eastWest) {
				for (int index = first; index > 0; --index) rail(index);
				if (endpoint) single(positions[0].west());
				for (int index = first + second; index > first; --index) rail(index);
				neighbors(positions[0], endpoint ? Direction.WEST : null);
			} else {
				neighbors(positions[0], endpoint ? Direction.SOUTH : null);
				branch(first + 1, first + second);
				if (endpoint) single(positions[0].south());
				branch(1, first);
			}
			shape(0);
			support(0);
		}

		private void branch(int start, int end) {
			for (int index = start; index <= end; ++index) neighbors(positions[index], null);
			for (int index = end; index >= start; --index) {
				shape(index);
				support(index);
			}
		}

		private void rail(int index) {
			neighbors(positions[index], null);
			shape(index);
			support(index);
		}

		private void support(int index) {
			neighbors(positions[index].below(), null);
			if (states[index].getValue(PoweredRailBlock.SHAPE).isSlope()) neighbors(positions[index].above(), null);
		}

		@SuppressWarnings("null")
		private void neighbors(BlockPos pos, Direction skipped) {
			++cost;
			int mask = 0;
			for (int index = 0; index < NeighborUpdater.UPDATE_ORDER.length; ++index) {
				Direction direction = NeighborUpdater.UPDATE_ORDER[index];
				if (direction != skipped && seen.add(pos.relative(direction).asLong())) mask |= 1 << index;
			}
			if (mask != 0) notifications.add(new Notification(pos.asLong(), (byte) 0, null, (byte) mask));
		}

		private void shape(int index) {
			BlockPos pos = positions[index];
			notifications.add(new Notification(pos.asLong(), (byte) 1, targets[index], (byte) 0));
			cost += 6;
		}

		private void single(BlockPos pos) {
			++cost;
			if (seen.add(pos.asLong())) notifications.add(new Notification(pos.asLong(), (byte) 2, null, (byte) 0));
		}
	}

	private static Field field(Class<?> type, String name) throws NoSuchFieldException {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}

	private static Method method(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException {
		Method method = type.getDeclaredMethod(name, parameters);
		method.setAccessible(true);
		return method;
	}
}
