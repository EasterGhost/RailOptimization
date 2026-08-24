package RailOptimization.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;

public class RailOptimizationSignalEdgeGameTest extends RailOptimizationGameTestSupport {
	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_84", maxTicks = 120, padding = 40)
	public void indirectPowerThroughConductorMatchesVanilla(GameTestHelper helper) {
		BlockPos rail = new BlockPos(3, RAIL_Y, 3);
		BlockPos conductor = rail.north();
		BlockPos source = conductor.north();

		placeRail(helper, rail, RailShape.EAST_WEST);
		placeRail(helper, mirrorCopy(rail), RailShape.EAST_WEST);
		helper.setBlock(conductor, Blocks.STONE);
		helper.setBlock(mirrorCopy(conductor), Blocks.STONE);

		BlockPos trigger = rail.south();
		helper.startSequence()
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
					helper.setBlock(source, Blocks.REDSTONE_BLOCK);
					triggerRailUpdate(helper, trigger);
				})
				.thenIdle(4)
				.thenExecute(() -> assertMatchingRailPower(helper, mirrorCopy(rail), rail))
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(source), Blocks.AIR);
					helper.setBlock(source, Blocks.AIR);
					triggerRailUpdate(helper, trigger);
				})
				.thenIdle(4)
				.thenExecute(() -> assertMatchingRailPower(helper, mirrorCopy(rail), rail))
				.thenSucceed();
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_85", maxTicks = 120, padding = 40)
	public void waterloggedPoweredRailPowerMatchesVanilla(GameTestHelper helper) {
		BlockPos rail = new BlockPos(3, RAIL_Y, 3);
		BlockPos source = rail.west();

		helper.setBlock(rail.below(), Blocks.STONE);
		helper.setBlock(mirrorCopy(rail).below(), Blocks.STONE);
		markVanillaForMirrorRail(helper, mirrorCopy(rail));
		var waterloggedRail = Blocks.POWERED_RAIL.defaultBlockState()
				.setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST)
				.setValue(PoweredRailBlock.WATERLOGGED, true);
		helper.setBlock(rail, waterloggedRail);
		helper.setBlock(mirrorCopy(rail), waterloggedRail);

		helper.startSequence()
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
					helper.setBlock(source, Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					assertMatchingRailPower(helper, mirrorCopy(rail), rail);
					helper.assertBlockProperty(rail, PoweredRailBlock.POWERED, true);
					helper.assertBlockProperty(rail, PoweredRailBlock.WATERLOGGED, true);
					helper.assertBlockProperty(mirrorCopy(rail), PoweredRailBlock.WATERLOGGED, true);
				})
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(source), Blocks.AIR);
					helper.setBlock(source, Blocks.AIR);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					assertMatchingRailPower(helper, mirrorCopy(rail), rail);
					helper.assertBlockProperty(rail, PoweredRailBlock.POWERED, false);
					helper.assertBlockProperty(rail, PoweredRailBlock.WATERLOGGED, true);
				})
				.thenSucceed();
	}

	@GameTest(environment = "railoptimization-gametest:serial_86", maxTicks = 200, padding = 40)
	public void detectorRailWithMinecartPowersAdjacentLaneLikeVanilla(GameTestHelper helper) {
		BlockPos detector = new BlockPos(2, RAIL_Y, 3);
		BlockPos laneStart = detector.east();

		placeShapedRail(helper, detector, RailShape.EAST_WEST, Blocks.DETECTOR_RAIL);
		placeShapedRail(helper, mirrorCopy(detector), RailShape.EAST_WEST, Blocks.DETECTOR_RAIL);
		placeRailLinePair(helper, laneStart, Direction.EAST, 4, RailShape.EAST_WEST);

		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					spawnMinecartOnRail(helper, mirrorCopy(detector));
					spawnMinecartOnRail(helper, detector);
				})
				.thenIdle(6)
				.thenExecute(() -> {
					assertDetectorPoweredMatches(helper, detector);
					assertMatchingRailLinePower(helper, mirrorCopy(laneStart), laneStart, Direction.EAST, 4);
					assertRailLinePowered(helper, laneStart, Direction.EAST, 4, true);
				})
				.thenExecute(() -> {
					killMinecartsNear(helper, mirrorCopy(detector));
					killMinecartsNear(helper, detector);
				})
				.thenIdle(2)
				.thenExecute(() -> triggerRailUpdate(helper, detector.above()))
				.thenIdle(6)
				.thenExecute(() -> {
					assertDetectorPoweredMatches(helper, detector);
					assertMatchingRailLinePower(helper, mirrorCopy(laneStart), laneStart, Direction.EAST, 4);
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	private static void spawnMinecartOnRail(GameTestHelper helper, BlockPos railPos) {
		Minecart cart = new Minecart(EntityType.MINECART, helper.getLevel());
		BlockPos absolute = helper.absolutePos(railPos);
		cart.setPos(absolute.getX() + 0.5, absolute.getY() + 0.2, absolute.getZ() + 0.5);
		helper.getLevel().addFreshEntity(cart);
	}

	@SuppressWarnings("null")
	private static void killMinecartsNear(GameTestHelper helper, BlockPos railPos) {
		BlockPos absolute = helper.absolutePos(railPos);
		helper.getLevel().getEntitiesOfClass(
				Minecart.class,
				new AABB(absolute).inflate(2.0),
				cart -> true).forEach(cart -> cart.discard());
	}

	@SuppressWarnings("null")
	private static void triggerRailUpdate(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, Blocks.STONE);
		helper.setBlock(mirrorCopy(pos), Blocks.STONE);
		helper.setBlock(pos, Blocks.AIR);
		helper.setBlock(mirrorCopy(pos), Blocks.AIR);
	}

	@SuppressWarnings("null")
	private static void assertDetectorPoweredMatches(GameTestHelper helper, BlockPos detector) {
		helper.assertBlockPresent(Blocks.DETECTOR_RAIL, mirrorCopy(detector));
		helper.assertBlockPresent(Blocks.DETECTOR_RAIL, detector);
		boolean vanillaPowered = helper.getBlockState(mirrorCopy(detector))
				.getValue(DetectorRailBlock.POWERED);
		boolean optimizedPowered = helper.getBlockState(detector)
				.getValue(DetectorRailBlock.POWERED);
		helper.assertTrue(vanillaPowered == optimizedPowered,
				Component.literal("detector rail powered mismatch: vanilla="
						+ vanillaPowered + ", optimized=" + optimizedPowered));
	}
}
