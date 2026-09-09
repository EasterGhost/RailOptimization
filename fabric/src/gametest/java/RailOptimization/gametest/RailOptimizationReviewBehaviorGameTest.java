package RailOptimization.gametest;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

public class RailOptimizationReviewBehaviorGameTest extends RailOptimizationGameTestSupport {
	@GameTest(environment = "railoptimization-gametest:serial_155", maxTicks = 80, padding = 40)
	public void eastBranchAndWestNeighborPoweringOrderMatchesVanilla(GameTestHelper helper) {
		verifyBranchOrder(helper, true, Direction.EAST, Direction.SOUTH, RailShape.EAST_WEST);
	}

	@GameTest(environment = "railoptimization-gametest:serial_158", maxTicks = 80, padding = 40)
	public void eastBranchAndWestNeighborDepoweringOrderMatchesVanilla(GameTestHelper helper) {
		verifyBranchOrder(helper, false, Direction.EAST, Direction.SOUTH, RailShape.EAST_WEST);
	}

	@GameTest(environment = "railoptimization-gametest:serial_159", maxTicks = 80, padding = 40)
	public void southBranchAndNorthNeighborPoweringOrderMatchesVanilla(GameTestHelper helper) {
		verifyBranchOrder(helper, true, Direction.SOUTH, Direction.EAST, RailShape.NORTH_SOUTH);
	}

	@GameTest(environment = "railoptimization-gametest:serial_160", maxTicks = 80, padding = 40)
	public void southBranchAndNorthNeighborDepoweringOrderMatchesVanilla(GameTestHelper helper) {
		verifyBranchOrder(helper, false, Direction.SOUTH, Direction.EAST, RailShape.NORTH_SOUTH);
	}

	@SuppressWarnings("null")
	private static void verifyBranchOrder(GameTestHelper helper, boolean powering, Direction axis, Direction side, RailShape shape) {
		BlockPos start = new BlockPos(3, RAIL_Y, 3);
		BlockPos[] rails = {start, start.relative(axis)};
		BlockPos[] probes = {start.relative(axis.getOpposite()), start.relative(side), rails[1].relative(side)};
		BlockPos lever = start.relative(side.getOpposite());
		placeRailLinePair(helper, start, axis, rails.length, shape);
		placeLeverPair(helper, lever, false);
		for (BlockPos probe : probes) {
			placeOrderRecorder(helper, probe, rails);
			placeOrderRecorder(helper, mirrorCopy(probe), mirrorCopy(rails));
		}

		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					if (!powering) {
						helper.pullLever(mirrorCopy(lever));
						helper.pullLever(lever);
					}
				})
				.thenIdle(2)
				.thenExecute(() -> {
					assertRailsPowered(helper, rails, !powering);
					assertRailsPowered(helper, mirrorCopy(rails), !powering);
					assertRailShapes(helper, rails, shape);
					assertRailShapes(helper, mirrorCopy(rails), shape);
					resetOrderRecorders(helper, probes);
					resetOrderRecorders(helper, mirrorCopy(probes));
					helper.pullLever(mirrorCopy(lever));
				})
				.thenIdle(2)
				.thenExecute(() -> helper.pullLever(lever))
				.thenIdle(2)
				.thenExecute(() -> assertBranchNotificationsMatch(helper, probes, rails, powering, shape))
				.thenSucceed();
	}

	private static void assertBranchNotificationsMatch(GameTestHelper helper, BlockPos[] probes, BlockPos[] rails, boolean powered,
			RailShape shape) {
		assertRailsPowered(helper, rails, powered);
		assertRailsPowered(helper, mirrorCopy(rails), powered);
		assertRailShapes(helper, rails, shape);
		assertRailShapes(helper, mirrorCopy(rails), shape);
		var vanilla = Arrays.stream(probes).map(probe -> orderProbeSnapshot(helper, mirrorCopy(probe)))
				.toArray(RailOptimizationGameTestMod.OrderProbeSnapshot[]::new);
		var optimized = Arrays.stream(probes).map(probe -> orderProbeSnapshot(helper, probe))
				.toArray(RailOptimizationGameTestMod.OrderProbeSnapshot[]::new);
		for (int index = 0; index < probes.length; index++) {
			helper.assertTrue(vanilla[index].order() > 0 && optimized[index].order() > 0,
					Component.literal("all endpoint and side probes must receive updates"));
		}
		String trace = shape + " " + (powered ? "powering" : "depowering")
				+ ": vanilla=" + describeBranchTrace(vanilla) + ", optimized=" + describeBranchTrace(optimized)
				+ " (P=rear endpoint, S=source side, B=branch side; power=[source,branch])";
		helper.assertTrue(Integer.compare(vanilla[1].order(), vanilla[2].order()) == Integer.compare(optimized[1].order(), optimized[2].order()),
				Component.literal("side propagation order mismatch; " + trace));
		helper.assertTrue(Integer.compare(vanilla[0].order(), vanilla[2].order()) == Integer.compare(optimized[0].order(), optimized[2].order()),
				Component.literal("endpoint/branch order mismatch; " + trace));
		helper.assertTrue(Integer.compare(vanilla[0].order(), vanilla[1].order()) == Integer.compare(optimized[0].order(), optimized[1].order()),
				Component.literal("endpoint/source order mismatch; " + trace));
	}

	private static String describeBranchTrace(RailOptimizationGameTestMod.OrderProbeSnapshot[] records) {
		String[] labels = {"P", "S", "B"};
		return IntStream.range(0, records.length).boxed()
				.sorted(Comparator.comparingInt(index -> records[index].order()))
				.map(index -> labels[index] + "[" + ((records[index].snapshot() & 1) != 0)
						+ "," + ((records[index].snapshot() & 2) != 0) + "]")
				.collect(Collectors.joining(" -> "));
	}

	@SuppressWarnings("null")
	private static void assertRailShapes(GameTestHelper helper, BlockPos[] rails, RailShape shape) {
		for (BlockPos rail : rails) {
			helper.assertBlockProperty(rail, PoweredRailBlock.SHAPE, shape);
		}
	}

	private static void placeLeverPair(GameTestHelper helper, BlockPos lever, boolean powered) {
		placeLever(helper, lever, powered);
		placeLever(helper, mirrorCopy(lever), powered);
	}

	@SuppressWarnings("null")
	private static void placeLever(GameTestHelper helper, BlockPos lever, boolean powered) {
		helper.setBlock(lever.below(), Blocks.GLASS);
		helper.setBlock(lever, Blocks.LEVER.defaultBlockState()
				.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
				.setValue(LeverBlock.FACING, Direction.NORTH)
				.setValue(LeverBlock.POWERED, powered));
	}
}
