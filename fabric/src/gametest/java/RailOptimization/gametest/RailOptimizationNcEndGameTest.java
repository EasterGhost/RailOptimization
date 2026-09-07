package RailOptimization.gametest;

import RailOptimization.RailLogicTestAccess;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.RedstoneSide;

public class RailOptimizationNcEndGameTest extends RailOptimizationGameTestSupport {
	private static final String STRUCTURE = "railoptimization-gametest:review_tall_empty";
	private static final int FIXTURE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;
	private static final BlockPos ORIGIN = new BlockPos(2, 1, 2);
	private static final BlockPos SIZE = new BlockPos(3, 5, 3);
	private static final BlockPos TOP_LEVER = new BlockPos(0, 4, 2);
	private static final BlockPos RAIL_LEVER = new BlockPos(1, 1, 0);
	private static final BlockPos[] RAILS = {new BlockPos(1, 1, 1), new BlockPos(2, 1, 1)};
	private static final BlockPos[] PISTONS = {new BlockPos(0, 1, 1), new BlockPos(2, 1, 2)};

	@GameTest(environment = "railoptimization-gametest:serial_169", structure = STRUCTURE, maxTicks = 100, padding = 40)
	public void ncEndVanillaControl(GameTestHelper helper) {
		verifyFixture(helper, false, false, false);
	}

	@GameTest(environment = "railoptimization-gametest:serial_170", structure = STRUCTURE, maxTicks = 100, padding = 40)
	public void ncEndOptimizedControl(GameTestHelper helper) {
		verifyFixture(helper, true, true, true);
	}

	@GameTest(environment = "railoptimization-gametest:serial_171", structure = STRUCTURE, maxTicks = 100, padding = 40)
	public void ncEndPistonOutcomeMatchesVanilla(GameTestHelper helper) {
		verifyFixture(helper, true, false, true);
	}

	@GameTest(environment = "railoptimization-gametest:serial_172", structure = STRUCTURE, maxTicks = 100, padding = 40)
	public void ncEndPistonOutcomeMatchesVanillaAfterModeSwap(GameTestHelper helper) {
		verifyFixture(helper, false, true, false);
	}

	@GameTest(environment = "railoptimization-gametest:serial_173", structure = STRUCTURE, maxTicks = 100, padding = 40)
	public void ncEndSupportLevelFirstNotificationsMatchVanilla(GameTestHelper helper) {
		verifySupportLevelNotifications(helper, true);
	}

	@GameTest(environment = "railoptimization-gametest:serial_174", structure = STRUCTURE, maxTicks = 100, padding = 40)
	public void ncEndSupportLevelFirstNotificationsMatchVanillaAfterModeSwap(GameTestHelper helper) {
		verifySupportLevelNotifications(helper, false);
	}

	@GameTest(environment = "railoptimization-gametest:serial_175", structure = STRUCTURE, maxTicks = 100, padding = 40)
	public void ncEndLoweredPistonsMatchVanilla(GameTestHelper helper) {
		verifyFixture(helper, true, false, true, -1);
	}

	@GameTest(environment = "railoptimization-gametest:serial_176", structure = STRUCTURE, maxTicks = 100, padding = 40)
	public void ncEndLoweredPistonsMatchVanillaAfterModeSwap(GameTestHelper helper) {
		verifyFixture(helper, false, true, false, -1);
	}

	@SuppressWarnings("null")
	private static void verifySupportLevelNotifications(GameTestHelper helper, boolean lowerOptimized) {
		BlockPos upperOrigin = mirrorCopy(ORIGIN);
		placeFixture(helper, ORIGIN, lowerOptimized);
		placeFixture(helper, upperOrigin, !lowerOptimized);
		BlockPos[] targets = Arrays.stream(PISTONS).map(BlockPos::below).toArray(BlockPos[]::new);
		Map<String, List<RailNeighborUpdateTrace.Update>> traces = new LinkedHashMap<>();
		helper.startSequence().thenIdle(4).thenExecute(() -> {
			for (String edge : List.of("on", "off")) {
				List<RailNeighborUpdateTrace.Update> first;
				List<RailNeighborUpdateTrace.Update> second;
				if (lowerOptimized) {
					second = captureSupportLevel(helper, upperOrigin, targets);
					first = captureSupportLevel(helper, ORIGIN, targets);
				} else {
					first = captureSupportLevel(helper, ORIGIN, targets);
					second = captureSupportLevel(helper, upperOrigin, targets);
				}
				traces.put("lower-" + edge, first);
				traces.put("upper-" + edge, second);
			}
			writeEvidence(lowerOptimized, !lowerOptimized, List.of(), List.of(), traces, "test_NC_end_support_");
			for (String edge : List.of("on", "off")) {
				List<RailNeighborUpdateTrace.Update> first = firstPerTarget(helper, traces.get("lower-" + edge));
				List<RailNeighborUpdateTrace.Update> second = firstPerTarget(helper, traces.get("upper-" + edge));
				helper.assertTrue(first.equals(second), Component.literal("support-level " + edge
						+ " first NC order/state differs: lower=" + first + ", upper=" + second));
			}
		}).thenSucceed();
	}

	@SuppressWarnings("null")
	private static List<RailNeighborUpdateTrace.Update> captureSupportLevel(GameTestHelper helper, BlockPos origin, BlockPos[] targets) {
		return RailNeighborUpdateTrace.capture(helper.getLevel(), absolute(helper, origin, targets), absolute(helper, origin, RAILS),
				() -> helper.pullLever(origin.offset(RAIL_LEVER)));
	}

	private static List<RailNeighborUpdateTrace.Update> firstPerTarget(GameTestHelper helper, List<RailNeighborUpdateTrace.Update> updates) {
		Map<Integer, RailNeighborUpdateTrace.Update> first = new LinkedHashMap<>();
		for (RailNeighborUpdateTrace.Update update : updates) {
			helper.assertTrue(update.sourceBlock().equals("minecraft:powered_rail"), Component.literal("unrelated source in support NC capture"));
			first.putIfAbsent(update.target(), update);
		}
		helper.assertTrue(first.size() == 2, Component.literal("one support-level target received no NC"));
		return List.copyOf(first.values());
	}

	private static void verifyFixture(GameTestHelper helper, boolean lowerOptimized, boolean upperOptimized, boolean upperFirst) {
		verifyFixture(helper, lowerOptimized, upperOptimized, upperFirst, 0);
	}

	private static void verifyFixture(GameTestHelper helper, boolean lowerOptimized, boolean upperOptimized, boolean upperFirst, int pistonYOffset) {
		BlockPos upperOrigin = mirrorCopy(ORIGIN);
		BlockPos[] pistons = Arrays.stream(PISTONS).map(pos -> pos.offset(0, pistonYOffset, 0)).toArray(BlockPos[]::new);
		BlockPos topLever = TOP_LEVER.offset(0, pistonYOffset, 0);
		placeFixture(helper, ORIGIN, lowerOptimized, pistonYOffset);
		placeFixture(helper, upperOrigin, upperOptimized, pistonYOffset);
		List<Step> lower = new ArrayList<>();
		List<Step> upper = new ArrayList<>();
		Map<String, List<RailNeighborUpdateTrace.Update>> updates = new LinkedHashMap<>();
		helper.startSequence()
				.thenIdle(4)
				.thenExecute(() -> {
					recordPair(helper, "initial", lower, upper, pistons);
					assertPrepared(helper, ORIGIN, false, topLever, pistons);
					assertPrepared(helper, upperOrigin, false, topLever, pistons);
					pullPair(helper, topLever, upperFirst);
				})
				.thenIdle(8)
				.thenExecute(() -> {
					recordPair(helper, "top-on", lower, upper, pistons);
					assertPrepared(helper, ORIGIN, true, topLever, pistons);
					assertPrepared(helper, upperOrigin, true, topLever, pistons);
					if (upperFirst) {
						updates.put("upper", pullRailLever(helper, upperOrigin, pistons));
						updates.put("lower", pullRailLever(helper, ORIGIN, pistons));
					} else {
						updates.put("lower", pullRailLever(helper, ORIGIN, pistons));
						updates.put("upper", pullRailLever(helper, upperOrigin, pistons));
					}
				})
				.thenIdle(8)
				.thenExecute(() -> recordPair(helper, "rail-on+8t", lower, upper, pistons))
				.thenIdle(20)
				.thenExecute(() -> {
					recordPair(helper, "rail-on+28t", lower, upper, pistons);
					writeEvidence(lowerOptimized, upperOptimized, lower, upper, updates,
							pistonYOffset == 0 ? "test_NC_end_" : "test_NC_end_lowered_");
					List<Step> vanilla = lowerOptimized ? upper : lower;
					helper.assertTrue(vanilla.getLast().extended().contains(true), Component.literal("fixture never triggered a piston in the reference copy"));
					helper.assertTrue(lower.get(2).blocks().equals(lower.get(3).blocks())
							&& upper.get(2).blocks().equals(upper.get(3).blocks()), Component.literal("piston outputs have not settled"));
					for (int index = 0; index < lower.size(); index++) {
						Step a = lower.get(index);
						Step b = upper.get(index);
						helper.assertTrue(a.equals(b), Component.literal("test_NC_end " + a.stage()
								+ " differs; lower=" + mode(lowerOptimized) + ", upper=" + mode(upperOptimized)
								+ "; pistons [west-end,south-of-east-rail]=" + a.extended() + " / " + b.extended()
								+ "; glass positions=" + a.glass() + " / " + b.glass()));
					}
				})
				.thenSucceed();
	}

	private static void placeFixture(GameTestHelper helper, BlockPos origin, boolean optimized) {
		placeFixture(helper, origin, optimized, 0);
	}

	@SuppressWarnings("null")
	private static void placeFixture(GameTestHelper helper, BlockPos origin, boolean optimized, int pistonYOffset) {
		for (BlockPos rail : RAILS) {
			BlockPos absolute = helper.absolutePos(origin.offset(rail));
			if (optimized) {
				RailLogicTestAccess.forceOptimizedAt(absolute);
			} else {
				RailLogicTestAccess.forceVanillaAt(absolute);
			}
			helper.assertTrue(RailLogicTestAccess.isPositionBasedTestModeEnabled()
					&& RailLogicTestAccess.positionMode(absolute) == (optimized ? RailLogicTestAccess.MODE_OPTIMIZED : RailLogicTestAccess.MODE_VANILLA),
					Component.literal("fixture mode was not applied"));
		}
		helper.assertTrue(helper.absolutePos(origin.east()).equals(helper.absolutePos(origin).east()),
				Component.literal("test structure rotates the east-west axis"));
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, 0, -1), origin.offset(3, 5, 4))) {
			helper.assertTrue(helper.getBounds().contains(helper.absolutePos(pos).getCenter()), Component.literal("output cell outside structure"));
			helper.getLevel().setBlock(helper.absolutePos(pos), Blocks.AIR.defaultBlockState(), FIXTURE_FLAGS);
		}
		buildCircuit(helper, origin);
		if (pistonYOffset != 0) {
			movePistonAssembly(helper, origin, pistonYOffset);
		}
		helper.assertBlockProperty(origin.offset(PISTONS[0]).offset(0, pistonYOffset, 0), PistonBaseBlock.FACING, Direction.SOUTH);
		helper.assertBlockProperty(origin.offset(PISTONS[1]).offset(0, pistonYOffset, 0), PistonBaseBlock.FACING, Direction.WEST);
	}

	@SuppressWarnings("null")
	private static void buildCircuit(GameTestHelper helper, BlockPos origin) {
		BlockState glass = Blocks.GLASS.defaultBlockState();
		BlockState stone = Blocks.SMOOTH_STONE.defaultBlockState();
		BlockState lever = Blocks.LEVER.defaultBlockState()
				.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
				.setValue(LeverBlock.POWERED, false);
		BlockState rail = Blocks.POWERED_RAIL.defaultBlockState()
				.setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST)
				.setValue(PoweredRailBlock.POWERED, false)
				.setValue(PoweredRailBlock.WATERLOGGED, false);
		BlockState northSouthWire = Blocks.REDSTONE_WIRE.defaultBlockState()
				.setValue(RedStoneWireBlock.POWER, 0)
				.setValue(RedStoneWireBlock.NORTH, RedstoneSide.SIDE)
				.setValue(RedStoneWireBlock.SOUTH, RedstoneSide.SIDE)
				.setValue(RedStoneWireBlock.EAST, RedstoneSide.NONE)
				.setValue(RedStoneWireBlock.WEST, RedstoneSide.NONE);
		BlockState eastWestWire = northSouthWire
				.setValue(RedStoneWireBlock.NORTH, RedstoneSide.NONE)
				.setValue(RedStoneWireBlock.SOUTH, RedstoneSide.NONE)
				.setValue(RedStoneWireBlock.EAST, RedstoneSide.SIDE)
				.setValue(RedStoneWireBlock.WEST, RedstoneSide.SIDE);

		setFixtureBlock(helper, origin, 1, 0, 0, glass);
		setFixtureBlock(helper, origin, 1, 0, 1, glass);
		setFixtureBlock(helper, origin, 2, 0, 1, glass);
		setFixtureBlock(helper, origin, 1, 1, 0, lever.setValue(LeverBlock.FACING, Direction.SOUTH));
		setFixtureBlock(helper, origin, 0, 1, 1, Blocks.PISTON.defaultBlockState()
				.setValue(PistonBaseBlock.FACING, Direction.SOUTH).setValue(PistonBaseBlock.EXTENDED, false));
		setFixtureBlock(helper, origin, 1, 1, 1, rail);
		setFixtureBlock(helper, origin, 2, 1, 1, rail);
		setFixtureBlock(helper, origin, 1, 1, 2, glass);
		setFixtureBlock(helper, origin, 2, 1, 2, Blocks.STICKY_PISTON.defaultBlockState()
				.setValue(PistonBaseBlock.FACING, Direction.WEST).setValue(PistonBaseBlock.EXTENDED, false));
		setFixtureBlock(helper, origin, 0, 2, 1, glass);
		setFixtureBlock(helper, origin, 2, 2, 2, glass);
		setFixtureBlock(helper, origin, 0, 3, 1, stone);
		setFixtureBlock(helper, origin, 0, 3, 2, stone);
		setFixtureBlock(helper, origin, 1, 3, 2, stone);
		setFixtureBlock(helper, origin, 2, 3, 2, stone);
		setFixtureBlock(helper, origin, 0, 4, 1, northSouthWire);
		setFixtureBlock(helper, origin, 0, 4, 2, lever.setValue(LeverBlock.FACING, Direction.EAST));
		setFixtureBlock(helper, origin, 1, 4, 2, eastWestWire);
		setFixtureBlock(helper, origin, 2, 4, 2, eastWestWire);
	}

	@SuppressWarnings("null")
	private static void setFixtureBlock(GameTestHelper helper, BlockPos origin, int x, int y, int z, BlockState state) {
		BlockPos pos = origin.offset(x, y, z);
		helper.assertTrue(helper.getBounds().contains(helper.absolutePos(pos).getCenter()), Component.literal("fixture cell outside structure"));
		helper.getLevel().setBlock(helper.absolutePos(pos), state, FIXTURE_FLAGS);
		helper.assertTrue(helper.getBlockState(pos).equals(state), Component.literal("fixture cell did not place exactly: " + pos));
	}

	@SuppressWarnings("null")
	private static void movePistonAssembly(GameTestHelper helper, BlockPos origin, int offsetY) {
		Map<BlockPos, BlockState> moving = new LinkedHashMap<>();
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.ZERO, SIZE.offset(-1, -1, -1))) {
			BlockState state = helper.getBlockState(origin.offset(pos));
			boolean assembly = pos.getY() >= 2 || pos.equals(PISTONS[0]) || pos.equals(PISTONS[1]) || pos.equals(new BlockPos(1, 1, 2));
			if (assembly && !state.isAir()) {
				moving.put(pos.immutable(), state);
			}
		}
		helper.assertTrue(moving.size() == 13, Component.literal("unexpected number of blocks in the piston assembly"));
		for (BlockPos pos : moving.keySet()) {
			helper.getLevel().setBlock(helper.absolutePos(origin.offset(pos)), Blocks.AIR.defaultBlockState(), FIXTURE_FLAGS);
		}
		for (var entry : moving.entrySet()) {
			BlockPos destination = origin.offset(entry.getKey()).offset(0, offsetY, 0);
			helper.assertTrue(helper.getBounds().contains(helper.absolutePos(destination).getCenter()), Component.literal("moved fixture cell outside structure"));
			helper.assertBlockPresent(Blocks.AIR, destination);
			helper.getLevel().setBlock(helper.absolutePos(destination), entry.getValue(), FIXTURE_FLAGS);
			helper.assertTrue(helper.getBlockState(destination).equals(entry.getValue()), Component.literal("moved fixture cell did not place exactly"));
		}
	}

	@SuppressWarnings("null")
	private static void assertPrepared(GameTestHelper helper, BlockPos origin, boolean topPowered, BlockPos topLever, BlockPos[] pistons) {
		helper.assertBlockProperty(origin.offset(topLever), LeverBlock.POWERED, topPowered);
		helper.assertBlockProperty(origin.offset(RAIL_LEVER), LeverBlock.POWERED, false);
		for (BlockPos piston : pistons) {
			helper.assertBlockProperty(origin.offset(piston), PistonBaseBlock.EXTENDED, false);
		}
		for (BlockPos rail : RAILS) {
			helper.assertBlockProperty(origin.offset(rail), PoweredRailBlock.SHAPE, RailShape.EAST_WEST);
			helper.assertBlockProperty(origin.offset(rail), PoweredRailBlock.POWERED, false);
		}
	}

	@SuppressWarnings("null")
	private static void pullPair(GameTestHelper helper, BlockPos relativeLever, boolean upperFirst) {
		BlockPos lower = ORIGIN.offset(relativeLever);
		helper.pullLever(upperFirst ? mirrorCopy(lower) : lower);
		helper.pullLever(upperFirst ? lower : mirrorCopy(lower));
	}

	@SuppressWarnings("null")
	private static List<RailNeighborUpdateTrace.Update> pullRailLever(GameTestHelper helper, BlockPos origin, BlockPos[] pistons) {
		return RailNeighborUpdateTrace.capture(helper.getLevel(), absolute(helper, origin, pistons), absolute(helper, origin, RAILS),
				() -> helper.pullLever(origin.offset(RAIL_LEVER)));
	}

	@SuppressWarnings("null")
	private static BlockPos[] absolute(GameTestHelper helper, BlockPos origin, BlockPos[] positions) {
		return Arrays.stream(positions).map(pos -> helper.absolutePos(origin.offset(pos))).toArray(BlockPos[]::new);
	}

	private static void recordPair(GameTestHelper helper, String stage, List<Step> lower, List<Step> upper, BlockPos[] pistons) {
		lower.add(readStep(helper, ORIGIN, stage, pistons));
		upper.add(readStep(helper, mirrorCopy(ORIGIN), stage, pistons));
	}

	@SuppressWarnings("null")
	private static Step readStep(GameTestHelper helper, BlockPos origin, String stage, BlockPos[] pistons) {
		Map<String, String> blocks = new LinkedHashMap<>();
		List<String> glass = new ArrayList<>();
		for (BlockPos relative : BlockPos.betweenClosed(new BlockPos(-1, 0, -1), new BlockPos(3, 5, 4))) {
			BlockState state = helper.getBlockState(origin.offset(relative));
			String coordinates = relative.getX() + "," + relative.getY() + "," + relative.getZ();
			blocks.put(coordinates, state.toString());
			if (state.is(Blocks.GLASS)) {
				glass.add(coordinates);
			}
		}
		List<Boolean> extended = Arrays.stream(pistons)
				.map(pos -> helper.getBlockState(origin.offset(pos)).getValue(PistonBaseBlock.EXTENDED)).toList();
		return new Step(stage, blocks, extended, glass);
	}

	private static String mode(boolean optimized) {
		return optimized ? "optimized" : "vanilla";
	}

	private static void writeEvidence(boolean lowerOptimized, boolean upperOptimized, List<Step> lower, List<Step> upper,
			Map<String, List<RailNeighborUpdateTrace.Update>> updates, String prefix) {
		String directory = System.getProperty("railoptimization.gametest.traceDirectory");
		if (directory == null) {
			return;
		}
		try {
			Path output = Path.of(directory);
			Files.createDirectories(output);
			String pair = (lowerOptimized ? "O" : "V") + (upperOptimized ? "O" : "V");
			Files.writeString(output.resolve(prefix + pair + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(
					Map.of("lowerMode", mode(lowerOptimized), "upperMode", mode(upperOptimized), "lower", lower, "upper", upper, "updates", updates)) + "\n");
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private record Step(String stage, Map<String, String> blocks, List<Boolean> extended, List<String> glass) {
	}
}
