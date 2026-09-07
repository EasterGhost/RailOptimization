package RailOptimization.gametest;

import RailOptimization.RailLogic;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.redstone.Orientation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

final class RailOptimizationNeoForgeFollowupGameTest {
	private static final String MOD_ID = RailOptimizationNeoForgeGameTest.MOD_ID;
	private static final int SETUP_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;
	private static final BlockPos LOWER = new BlockPos(8, 3, 8);
	private static final BlockPos UPPER = LOWER.above(20);
	private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
	private static final DeferredBlock<UpdateProbe> PROBE = BLOCKS.registerBlock("followup_update_probe",
			p -> new UpdateProbe(p.noLootTable().isRedstoneConductor((state, level, pos) -> false)));
	private static @Nullable Capture active;

	static void registerBlocks(IEventBus bus) {
		BLOCKS.register(bus);
	}

	static void registerTests(RegisterGameTestsEvent event) {
		for (Direction direction : List.of(Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH)) {
			register(event, "followup_updates_" + direction.getName(), h -> verifyUpdates(h, direction));
		}
		register(event, "followup_observer_piston_east_west", h -> verifyObserverPistons(h, Direction.EAST));
		register(event, "followup_observer_piston_north_south", h -> verifyObserverPistons(h, Direction.SOUTH));
	}

	private static void register(RegisterGameTestsEvent event, String name, Consumer<GameTestHelper> body) {
		var environment = event.registerEnvironment(Identifier.fromNamespaceAndPath(MOD_ID, name));
		RailOptimizationNeoForgeGameTest.registerTest(event, environment, name,
				Identifier.fromNamespaceAndPath(MOD_ID, "review_compat_empty"), 180, body);
	}

	private static void verifyUpdates(GameTestHelper helper, Direction axis) {
		RailLogic.setRailPowerLimit(8);
		Fixture lower = placeProbes(helper, LOWER, axis);
		Fixture upper = placeProbes(helper, UPPER, axis);
		Map<String, Edges> evidence = new LinkedHashMap<>();
		evidence.put("native_lower", inMode(false, () -> captureEdges(helper, lower)));
		evidence.put("native_upper", inMode(false, () -> captureEdges(helper, upper)));
		evidence.put("optimized_upper", inMode(true, () -> captureEdges(helper, upper)));
		evidence.put("optimized_lower", inMode(true, () -> captureEdges(helper, lower)));
		write("updates_" + axis.getName(), evidence);
		equal(helper, evidence.get("native_lower"), evidence.get("native_upper"), "native trace position control");
		equal(helper, evidence.get("optimized_lower"), evidence.get("optimized_upper"), "optimized trace position control");
		Edges nativeTrace = evidence.get("native_lower"), optimizedTrace = evidence.get("optimized_lower");
		for (boolean on : List.of(true, false)) {
			List<Update> original = on ? nativeTrace.on() : nativeTrace.off();
			List<Update> optimized = on ? optimizedTrace.on() : optimizedTrace.off();
			List<Update> nativeNc = kind(original, "NC"), optimizedNc = kind(optimized, "NC");
			List<Update> nativePp = kind(original, "PP"), optimizedPp = kind(optimized, "PP");
			LogUtils.getLogger().info("Followup {} {}: NC {}/{}, PP {}/{}, NC-first={}, PP-full={}, merged={}", axis, on,
					nativeNc.size(), optimizedNc.size(), nativePp.size(), optimizedPp.size(),
					first(nativeNc).equals(first(optimizedNc)), nativePp.equals(optimizedPp), original.equals(optimized));
			helper.assertTrue(!nativeNc.isEmpty() && !nativePp.isEmpty(), Component.literal("native trace must exercise NC and PP"));
			equal(helper, first(nativeNc), first(optimizedNc), axis + " " + on + " first NC targets across all sampled heights");
			equal(helper, nativePp, optimizedPp, axis + " " + on + " delivered PP order");
		}
		helper.succeed();
	}

	private static Fixture placeProbes(GameTestHelper helper, BlockPos start, Direction axis) {
		Fixture fixture = placeLine(helper, start, axis, Blocks.POWERED_RAIL, 2, shape(axis));
		Direction side = side(axis);
		List<BlockPos> targets = new ArrayList<>();
		List<BlockPos> row = List.of(start.relative(axis.getOpposite()), start.relative(side),
				start.relative(axis).relative(side), start.relative(axis, 2));
		targets.addAll(row);
		row.forEach(p -> targets.add(p.below()));
		targets.add(start.below()); targets.add(start.relative(axis).below());
		targets.add(start.above()); targets.add(start.relative(axis).above());
		for (BlockPos target : targets) put(helper, target, PROBE.get().defaultBlockState());
		return new Fixture(fixture.rails(), fixture.lever(), targets.toArray(BlockPos[]::new));
	}

	private static Edges captureEdges(GameTestHelper helper, Fixture fixture) {
		return new Edges(captureEdge(helper, fixture, true), captureEdge(helper, fixture, false));
	}

	private static List<Update> captureEdge(GameTestHelper helper, Fixture fixture, boolean power) {
		helper.assertBlockProperty(fixture.lever(), LeverBlock.POWERED, !power);
		Capture capture = new Capture(Arrays.stream(fixture.targets()).map(helper::absolutePos).toList(), new ArrayList<>());
		if (active != null) throw new IllegalStateException("nested update capture");
		active = capture;
		try { helper.pullLever(fixture.lever()); } finally { active = null; }
		helper.assertBlockProperty(fixture.lever(), LeverBlock.POWERED, power);
		for (BlockPos rail : fixture.rails()) helper.assertBlockProperty(rail, PoweredRailBlock.POWERED, power);
		return List.copyOf(capture.updates());
	}

	private static List<Update> kind(List<Update> updates, String kind) {
		return updates.stream().filter(u -> u.kind().equals(kind)).toList();
	}

	private static List<Integer> first(List<Update> updates) {
		LinkedHashSet<Integer> targets = new LinkedHashSet<>();
		updates.forEach(u -> targets.add(u.target()));
		return List.copyOf(targets);
	}

	private static void verifyObserverPistons(GameTestHelper helper, Direction axis) {
		String evidenceName = "observer_pistons_" + axis.getName();
		RailLogic.setRailPowerLimit(8);
		boolean previous = RailLogic.isOptimizationEnabled();
		Map<String, List<MachineSnapshot>> evidence = new LinkedHashMap<>();
		var sequence = helper.startSequence();
		for (String phase : List.of("native_lower", "optimized_upper", "native_upper", "optimized_lower")) {
			boolean enabled = phase.startsWith("optimized");
			BlockPos start = phase.endsWith("lower") ? LOWER : UPPER;
			List<MachineSnapshot> snapshots = new ArrayList<>();
			evidence.put(phase, snapshots);
			sequence.thenExecute(() -> inMode(false, () -> { placeMachine(helper, start, axis); return null; })).thenIdle(6);
			sequence.thenExecute(() -> {
				for (int index = 0; index < 2; index++) {
					BlockPos initialGlass = start.relative(axis, index).relative(side(axis), 2).above();
					helper.assertTrue(helper.getBlockState(initialGlass).is(Blocks.GLASS), Component.literal("setup moved glass before lever input"));
				}
			});
			for (boolean on : List.of(true, false)) {
				sequence.thenExecute(() -> {
					RailLogic.setOptimizationEnabled(enabled);
					try {
						helper.assertBlockProperty(start.relative(side(axis).getOpposite()), LeverBlock.POWERED, !on);
						helper.pullLever(start.relative(side(axis).getOpposite()));
					} catch (RuntimeException | Error failure) {
						RailLogic.setOptimizationEnabled(previous);
						throw failure;
					}
				});
				for (int tick = 1; tick <= 8; tick++) {
					sequence.thenIdle(1).thenExecute(() -> {
						try { snapshots.add(machineSnapshot(helper, start, axis)); }
						catch (RuntimeException | Error failure) { RailLogic.setOptimizationEnabled(previous); throw failure; }
					});
				}
			}
			sequence.thenExecute(() -> RailLogic.setOptimizationEnabled(previous));
		}
		sequence.thenExecute(() -> {
			write(evidenceName, evidence);
			List<MachineSnapshot> original = evidence.get("native_lower");
			equal(helper, original, evidence.get("native_upper"), "observer native position control");
			equal(helper, evidence.get("optimized_lower"), evidence.get("optimized_upper"), "observer optimized position control");
			helper.assertTrue(original.stream().anyMatch(s -> s.observers().stream().anyMatch(state -> state.contains("powered=true")))
					&& original.stream().anyMatch(s -> s.pistons().stream().anyMatch(state -> state.contains("extended=true"))),
					Component.literal("native PP must cause an observer pulse and piston motion"));
			helper.assertTrue(original.getLast().glassMoved().equals(List.of(true, true)),
					Component.literal("native pistons must push both glass blocks"));
			LogUtils.getLogger().info("Followup {} glass moved: native={}, optimized={}", evidenceName,
					original.getLast().glassMoved(), evidence.get("optimized_lower").getLast().glassMoved());
			equal(helper, original.getLast().glassMoved(), evidence.get("optimized_lower").getLast().glassMoved(),
					evidenceName + " glass moved by observer-driven pistons");
			equal(helper, original, evidence.get("optimized_lower"), "PP to observer NC to piston/shape result at every sampled tick");
			LogUtils.getLogger().info("Followup observer/pistons {}: all 16 tick samples match in both positions", axis);
		}).thenSucceed();
	}

	private static void placeMachine(GameTestHelper helper, BlockPos start, Direction axis) {
		Direction side = side(axis);
		for (int index = 0; index < 2; index++) {
			BlockPos rail = start.relative(axis, index);
			put(helper, rail.relative(side), Blocks.AIR.defaultBlockState());
			put(helper, rail.relative(side, 2), Blocks.AIR.defaultBlockState());
			put(helper, rail.relative(side, 2).above(), Blocks.AIR.defaultBlockState());
			put(helper, rail.relative(side, 2).above(2), Blocks.AIR.defaultBlockState());
		}
		Fixture fixture = placeLine(helper, start, axis, Blocks.POWERED_RAIL, 2, shape(axis));
		for (int index = 0; index < 2; index++) {
			BlockPos rail = fixture.rails()[index];
			BlockPos piston = rail.relative(side, 2);
			put(helper, piston.above(2), Blocks.AIR.defaultBlockState());
			put(helper, piston.above(), Blocks.GLASS.defaultBlockState());
			put(helper, piston, Blocks.PISTON.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.UP));
			put(helper, rail.relative(side), Blocks.OBSERVER.defaultBlockState().setValue(DirectionalBlock.FACING, side.getOpposite()));
		}
	}

	private static MachineSnapshot machineSnapshot(GameTestHelper helper, BlockPos start, Direction axis) {
		List<String> observers = new ArrayList<>(), pistons = new ArrayList<>();
		List<Boolean> glass = new ArrayList<>();
		for (int index = 0; index < 2; index++) {
			BlockPos observer = start.relative(axis, index).relative(side(axis));
			BlockPos piston = observer.relative(side(axis));
			observers.add(helper.getBlockState(observer).toString());
			pistons.add(helper.getBlockState(piston).toString());
			glass.add(helper.getBlockState(piston.above(2)).is(Blocks.GLASS));
		}
		return new MachineSnapshot(observers, pistons, glass);
	}

	private static Fixture placeLine(GameTestHelper helper, BlockPos start, Direction axis, Block rail, int count, RailShape storedShape) {
		BlockPos[] positions = new BlockPos[count];
		for (int index = 0; index < count; index++) {
			BlockPos pos = start.relative(axis, index);
			positions[index] = pos;
			put(helper, pos.below(), Blocks.GLASS.defaultBlockState());
			put(helper, pos, rail.defaultBlockState().setValue(PoweredRailBlock.SHAPE, storedShape).setValue(PoweredRailBlock.POWERED, false));
			helper.assertBlockProperty(pos, PoweredRailBlock.SHAPE, storedShape);
		}
		BlockPos lever = start.relative(side(axis).getOpposite());
		put(helper, lever.below(), Blocks.GLASS.defaultBlockState());
		put(helper, lever, Blocks.LEVER.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
				.setValue(LeverBlock.FACING, Direction.NORTH).setValue(LeverBlock.POWERED, false));
		return new Fixture(positions, lever, new BlockPos[0]);
	}

	private static void put(GameTestHelper helper, BlockPos pos, BlockState state) {
		helper.assertTrue(helper.getBounds().contains(helper.absolutePos(pos).getCenter()), Component.literal("fixture outside structure: " + pos));
		helper.getLevel().setBlock(helper.absolutePos(pos), state, SETUP_FLAGS);
	}

	private static Direction side(Direction axis) { return axis.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST; }
	private static RailShape shape(Direction axis) { return axis.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH; }
	private static <T> T inMode(boolean enabled, Supplier<T> action) {
		boolean previous = RailLogic.isOptimizationEnabled();
		RailLogic.setOptimizationEnabled(enabled);
		try { return action.get(); } finally { RailLogic.setOptimizationEnabled(previous); }
	}
	private static void equal(GameTestHelper helper, Object original, Object optimized, String label) {
		helper.assertTrue(original.equals(optimized), Component.literal(label + ": native=" + original + ", optimized=" + optimized));
	}
	private static void write(String name, Object value) {
		Path output = Path.of("../../../build/review-results/quick-followup", name + ".json").toAbsolutePath().normalize();
		try {
			Files.createDirectories(output.getParent());
			Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(value));
		} catch (IOException failure) { throw new UncheckedIOException(failure); }
	}

	private record Fixture(BlockPos[] rails, BlockPos lever, BlockPos[] targets) {}
	private record Update(String kind, int target, String direction) {}
	private record Capture(List<BlockPos> targets, List<Update> updates) {}
	private record Edges(List<Update> on, List<Update> off) {}
	private record MachineSnapshot(List<String> observers, List<String> pistons, List<Boolean> glassMoved) {}

	private static final class UpdateProbe extends Block {
		UpdateProbe(Properties properties) { super(properties); }
		private static void record(BlockPos pos, String kind, String direction) {
			Capture capture = active;
			if (capture == null) return;
			int index = capture.targets().indexOf(pos);
			if (index >= 0) capture.updates().add(new Update(kind, index, direction));
		}
		@Override
		protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block source, @Nullable Orientation orientation, boolean moved) {
			if (source instanceof PoweredRailBlock) record(pos, "NC", "");
		}
		@Override
		protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
				Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
			if (neighborState.getBlock() instanceof PoweredRailBlock) record(pos, "PP", direction.getName());
			return state;
		}
	}
}
