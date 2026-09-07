package RailOptimization.gametest;

import RailOptimization.RailLogicTestAccess;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationNotificationControlGameTest extends RailOptimizationGameTestSupport {
	@GameTest(environment = "railoptimization-gametest:serial_163", maxTicks = 80, padding = 40)
	public void eastWestRecorderControlsAndModeSwap(GameTestHelper helper) {
		verifyControls(helper, Direction.EAST, Direction.SOUTH, RailShape.EAST_WEST, true);
	}

	@GameTest(environment = "railoptimization-gametest:serial_164", maxTicks = 80, padding = 40)
	public void northSouthRecorderControlsAndModeSwap(GameTestHelper helper) {
		verifyControls(helper, Direction.SOUTH, Direction.EAST, RailShape.NORTH_SOUTH, true);
	}

	@GameTest(environment = "railoptimization-gametest:serial_165", maxTicks = 80, padding = 40)
	public void eastWestVanillaBlockControlsAndModeSwap(GameTestHelper helper) {
		verifyControls(helper, Direction.EAST, Direction.SOUTH, RailShape.EAST_WEST, false);
	}

	@GameTest(environment = "railoptimization-gametest:serial_166", maxTicks = 80, padding = 40)
	public void northSouthVanillaBlockControlsAndModeSwap(GameTestHelper helper) {
		verifyControls(helper, Direction.SOUTH, Direction.EAST, RailShape.NORTH_SOUTH, false);
	}

	@GameTest(environment = "railoptimization-gametest:serial_177", structure = "railoptimization-gametest:review_tall_empty", maxTicks = 100, padding = 40)
	public void westEndFirstNotificationsMatchVanilla(GameTestHelper helper) {
		verifyEndpointControls(helper, Direction.EAST);
	}

	@GameTest(environment = "railoptimization-gametest:serial_178", structure = "railoptimization-gametest:review_tall_empty", maxTicks = 100, padding = 40)
	public void eastEndFirstNotificationsMatchVanilla(GameTestHelper helper) {
		verifyEndpointControls(helper, Direction.WEST);
	}

	@GameTest(environment = "railoptimization-gametest:serial_179", structure = "railoptimization-gametest:review_tall_empty", maxTicks = 100, padding = 40)
	public void northEndFirstNotificationsMatchVanilla(GameTestHelper helper) {
		verifyEndpointControls(helper, Direction.SOUTH);
	}

	@GameTest(environment = "railoptimization-gametest:serial_180", structure = "railoptimization-gametest:review_tall_empty", maxTicks = 100, padding = 40)
	public void southEndFirstNotificationsMatchVanilla(GameTestHelper helper) {
		verifyEndpointControls(helper, Direction.NORTH);
	}

	private static void verifyEndpointControls(GameTestHelper helper, Direction axis) {
		Direction side = axis.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
		RailShape shape = axis.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
		Fixture lower = placeFixture(helper, new BlockPos(3, RAIL_Y, 3), axis, side, shape, false, true);
		Fixture upper = placeFixture(helper, mirrorCopy(lower.rails()[0]), axis, side, shape, false, true);
		helper.startSequence().thenIdle(2).thenExecute(() -> {
			PairTrace vv = runPair(helper, lower, upper, false, false, false, shape, false);
			PairTrace oo = runPair(helper, lower, upper, true, true, true, shape, false);
			PairTrace vo = runPair(helper, lower, upper, false, true, true, shape, false);
			PairTrace ov = runPair(helper, lower, upper, true, false, false, shape, false);
			writeEvidence("ends_branch_" + axis.getName() + ".json", Map.of("VV", vv, "OO", oo, "VO", vo, "OV", ov));
			assertEqual(helper, vv.lower(), vv.upper(), "endpoint vanilla/vanilla control");
			assertEqual(helper, oo.lower(), oo.upper(), "endpoint optimized/optimized control");
			assertEqual(helper, vo.lower(), vv.lower(), "endpoint vanilla lower position");
			assertEqual(helper, vo.upper(), oo.upper(), "endpoint optimized upper position");
			assertEqual(helper, ov.lower(), oo.lower(), "endpoint optimized lower position after swap");
			assertEqual(helper, ov.upper(), vv.upper(), "endpoint vanilla upper position after swap");
			for (boolean powering : List.of(true, false)) {
				List<RailNeighborUpdateTrace.Update> vanilla = powering ? vv.lower().on() : vv.lower().off();
				List<RailNeighborUpdateTrace.Update> optimized = powering ? oo.lower().on() : oo.lower().off();
				for (int height = 1; height >= 0; height--) {
					List<Integer> originalOrder = firstTargetsAtHeight(vanilla, height);
					List<Integer> optimizedOrder = firstTargetsAtHeight(optimized, height);
					helper.assertTrue(originalOrder.equals(optimizedOrder), Component.literal("branch=" + axis
							+ ", " + (powering ? "on" : "off") + ", layer=" + (height == 0 ? "rail" : "support")
							+ ": first targets [0=rear endpoint,1=near side,2=far side,3=far endpoint]; vanilla="
							+ originalOrder + ", optimized=" + optimizedOrder));
				}
			}
		}).thenSucceed();
	}

	private static List<Integer> firstTargetsAtHeight(List<RailNeighborUpdateTrace.Update> updates, int height) {
		LinkedHashSet<Integer> first = new LinkedHashSet<>();
		for (RailNeighborUpdateTrace.Update update : updates) {
			if (update.target() / 4 == height) {
				first.add(update.target() % 4);
			}
		}
		return List.copyOf(first);
	}

	private static void verifyControls(GameTestHelper helper, Direction axis, Direction side, RailShape shape, boolean recorders) {
		Fixture lower = placeFixture(helper, new BlockPos(3, RAIL_Y, 3), axis, side, shape, recorders);
		Fixture upper = placeFixture(helper, mirrorCopy(lower.rails()[0]), axis, side, shape, recorders);
		helper.startSequence().thenIdle(2).thenExecute(() -> {
			try {
				PairTrace vv = runPair(helper, lower, upper, false, false, false, shape, recorders);
				PairTrace oo = runPair(helper, lower, upper, true, true, true, shape, recorders);
				PairTrace vo = runPair(helper, lower, upper, false, true, true, shape, recorders);
				PairTrace ov = runPair(helper, lower, upper, true, false, false, shape, recorders);
				assertEqual(helper, vv.lower(), vv.upper(), "vanilla/vanilla control");
				assertEqual(helper, oo.lower(), oo.upper(), "optimized/optimized control");
				assertEqual(helper, vo.lower(), vv.lower(), "vanilla lower position");
				assertEqual(helper, vo.upper(), oo.upper(), "optimized upper position");
				assertEqual(helper, ov.lower(), oo.lower(), "optimized lower position after swap");
				assertEqual(helper, ov.upper(), vv.upper(), "vanilla upper position after swap");
				writeEvidence(shape, recorders, Map.of("VV", vv, "OO", oo, "VO", vo, "OV", ov));
			} finally {
				setMode(helper, lower, true);
				setMode(helper, upper, false);
			}
		}).thenSucceed();
	}

	private static PairTrace runPair(GameTestHelper helper, Fixture lower, Fixture upper, boolean lowerOptimized,
			boolean upperOptimized, boolean upperFirst, RailShape shape, boolean recorders) {
		setMode(helper, lower, lowerOptimized);
		setMode(helper, upper, upperOptimized);
		if (upperFirst) {
			Trace upperTrace = toggleAndCapture(helper, upper, shape, recorders);
			return new PairTrace(toggleAndCapture(helper, lower, shape, recorders), upperTrace);
		}
		Trace lowerTrace = toggleAndCapture(helper, lower, shape, recorders);
		return new PairTrace(lowerTrace, toggleAndCapture(helper, upper, shape, recorders));
	}

	@SuppressWarnings("null")
	private static Trace toggleAndCapture(GameTestHelper helper, Fixture fixture, RailShape shape, boolean recorders) {
		helper.assertBlockProperty(fixture.lever(), LeverBlock.POWERED, false);
		assertRailsPowered(helper, fixture.rails(), false);
		List<RailNeighborUpdateTrace.Update> on = captureEdge(helper, fixture, true, recorders);
		List<RailNeighborUpdateTrace.Update> off = captureEdge(helper, fixture, false, recorders);
		for (BlockPos rail : fixture.rails()) {
			helper.assertBlockProperty(rail, PoweredRailBlock.SHAPE, shape);
		}
		return new Trace(on, off);
	}

	@SuppressWarnings("null")
	private static List<RailNeighborUpdateTrace.Update> captureEdge(GameTestHelper helper, Fixture fixture, boolean powering, boolean recorders) {
		if (recorders) {
			resetOrderRecorders(helper, fixture.probes());
		}
		List<RailNeighborUpdateTrace.Update> updates = RailNeighborUpdateTrace.capture(helper.getLevel(),
				absolute(helper, fixture.probes()), absolute(helper, fixture.rails()), () -> helper.pullLever(fixture.lever()));
		helper.assertBlockProperty(fixture.lever(), LeverBlock.POWERED, powering);
		assertRailsPowered(helper, fixture.rails(), powering);
		int previousRecorderOrder = 0;
		boolean[] seen = new boolean[fixture.probes().length];
		for (RailNeighborUpdateTrace.Update update : updates) {
			helper.assertTrue(update.sourceBlock().equals("minecraft:powered_rail"), Component.literal("probe was updated by an unrelated source: " + update));
			if (!seen[update.target()]) {
				seen[update.target()] = true;
				if (recorders) {
					var recorded = orderProbeSnapshot(helper, fixture.probes()[update.target()]);
					helper.assertTrue(recorded.order() > previousRecorderOrder, Component.literal("custom recorder order disagrees with delivered callbacks: " + updates));
					previousRecorderOrder = recorded.order();
					helper.assertTrue(((recorded.snapshot() & 1) != 0) == update.sourceRailPowered()
							&& ((recorded.snapshot() & 2) != 0) == update.branchRailPowered(),
							Component.literal("custom recorder snapshot disagrees with delivered callback: " + update));
				}
			}
		}
		for (boolean receivedUpdate : seen) {
			helper.assertTrue(receivedUpdate, Component.literal("a target never received a neighbor update"));
		}
		return updates;
	}

	private static Fixture placeFixture(GameTestHelper helper, BlockPos start, Direction axis, Direction side, RailShape shape, boolean recorders) {
		return placeFixture(helper, start, axis, side, shape, recorders, false);
	}

	@SuppressWarnings("null")
	private static Fixture placeFixture(GameTestHelper helper, BlockPos start, Direction axis, Direction side, RailShape shape, boolean recorders, boolean bothEndsAndHeights) {
		BlockPos[] rails = {start, start.relative(axis)};
		BlockPos[] probes = {start.relative(axis.getOpposite()), start.relative(side), rails[1].relative(side)};
		if (bothEndsAndHeights) {
			BlockPos[] sameLevel = {probes[0], probes[1], probes[2], rails[1].relative(axis)};
			probes = Stream.concat(Arrays.stream(sameLevel), Arrays.stream(sameLevel).map(BlockPos::below)).toArray(BlockPos[]::new);
			for (BlockPos probe : probes) {
				helper.assertTrue(helper.getBounds().contains(helper.absolutePos(probe).getCenter()), Component.literal("endpoint probe outside test structure"));
			}
		}
		BlockPos lever = start.relative(side.getOpposite());
		helper.assertTrue(helper.absolutePos(rails[1]).equals(helper.absolutePos(start).relative(axis)),
				Component.literal("test coordinates rotate the requested world axis"));
		for (BlockPos rail : rails) {
			RailLogicTestAccess.forceVanillaAt(helper.absolutePos(rail));
			helper.setBlock(rail.below(), Blocks.GLASS);
			helper.setBlock(rail, Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, shape));
		}
		helper.setBlock(lever.below(), Blocks.GLASS);
		helper.setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
				.setValue(LeverBlock.FACING, Direction.NORTH).setValue(LeverBlock.POWERED, false));
		for (BlockPos probe : probes) {
			if (recorders) {
				placeOrderRecorder(helper, probe, rails);
			} else {
				helper.setBlock(probe, Blocks.STONE);
			}
		}
		return new Fixture(rails, probes, lever);
	}

	@SuppressWarnings("null")
	private static void setMode(GameTestHelper helper, Fixture fixture, boolean optimized) {
		for (BlockPos rail : fixture.rails()) {
			BlockPos absolutePos = helper.absolutePos(rail);
			if (optimized) {
				RailLogicTestAccess.forceOptimizedAt(absolutePos);
			} else {
				RailLogicTestAccess.forceVanillaAt(absolutePos);
			}
			helper.assertTrue(RailLogicTestAccess.positionMode(absolutePos)
					== (optimized ? RailLogicTestAccess.MODE_OPTIMIZED : RailLogicTestAccess.MODE_VANILLA),
					Component.literal("fixture mode was not applied"));
		}
	}

	@SuppressWarnings("null")
	private static BlockPos[] absolute(GameTestHelper helper, BlockPos[] positions) {
		return Arrays.stream(positions).map(helper::absolutePos).toArray(BlockPos[]::new);
	}

	private static void assertEqual(GameTestHelper helper, Trace first, Trace second, String label) {
		helper.assertTrue(first.equals(second), Component.literal(label + " mismatch: first=" + first + ", second=" + second));
	}

	private static void writeEvidence(RailShape shape, boolean recorders, Map<String, PairTrace> traces) {
		writeEvidence(shape.getSerializedName() + (recorders ? "_recorders.json" : "_stone.json"), traces);
	}

	private static void writeEvidence(String filename, Map<String, PairTrace> traces) {
		String directory = System.getProperty("railoptimization.gametest.traceDirectory");
		if (directory == null) {
			return;
		}
		try {
			Path outputDirectory = Path.of(directory);
			Files.createDirectories(outputDirectory);
			Files.writeString(outputDirectory.resolve(filename),
					new GsonBuilder().setPrettyPrinting().create().toJson(traces) + "\n");
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private record Fixture(BlockPos[] rails, BlockPos[] probes, BlockPos lever) {
	}

	private record Trace(List<RailNeighborUpdateTrace.Update> on, List<RailNeighborUpdateTrace.Update> off) {
	}

	private record PairTrace(Trace lower, Trace upper) {
	}
}
