package RailOptimization.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationTransitionGameTest extends RailOptimizationGameTestSupport {
	@GameTest(environment = "railoptimization-gametest:serial_55", maxTicks = 260, padding = 40)
	public void slopedEndpointObserverTracksPowerDepowerAndMovedSource(GameTestHelper helper) {
		BlockPos[] rails = valleyRails();
		BlockPos firstSource = rails[0].north();
		BlockPos movedSource = rails[3].north();
		BlockPos observer = rails[rails.length - 1].east();

		placeValleyPair(helper, rails);
		placeObserverWatchingRail(helper, observer, Direction.WEST);
		placeObserverWatchingRail(helper, mirrorCopy(observer), Direction.WEST);

		helper.startSequence()
				.thenIdle(4)
				.thenExecute(() -> assertObserverPairPowered(helper, observer, false))
				.thenExecute(() -> placeSourcePair(helper, firstSource))
				.thenIdle(2)
				.thenExecute(() -> assertValleyTransition(helper, rails, observer, true, true))
				.thenIdle(4)
				.thenExecute(() -> assertObserverPairPowered(helper, observer, false))
				.thenExecute(() -> removeSourcePair(helper, firstSource))
				.thenIdle(2)
				.thenExecute(() -> assertValleyTransition(helper, rails, observer, false, true))
				.thenIdle(4)
				.thenExecute(() -> assertObserverPairPowered(helper, observer, false))
				.thenExecute(() -> placeSourcePair(helper, movedSource))
				.thenIdle(2)
				.thenExecute(() -> assertValleyTransition(helper, rails, observer, true, true))
				.thenIdle(4)
				.thenExecute(() -> assertObserverPairPowered(helper, observer, false))
				.thenExecute(() -> removeSourcePair(helper, movedSource))
				.thenIdle(2)
				.thenExecute(() -> assertValleyTransition(helper, rails, observer, false, true))
				.thenIdle(4)
				.thenExecute(() -> assertObserverPairPowered(helper, observer, false))
				.thenSucceed();
	}

	@GameTest(environment = "railoptimization-gametest:serial_56", maxTicks = 220, padding = 40)
	public void slopedBudTransitionNotifiesEndpointObserverLikeVanilla(GameTestHelper helper) {
		BlockPos[] rails = valleyRails();
		BlockPos source = rails[0].north();
		BlockPos trigger = rails[0].above();
		BlockPos observer = rails[rails.length - 1].east();

		placeValleyPair(helper, rails);
		placeObserverWatchingRail(helper, observer, Direction.WEST);
		placeObserverWatchingRail(helper, mirrorCopy(observer), Direction.WEST);

		helper.startSequence()
				.thenExecute(() -> setSourcePairWithoutUpdates(helper, source, Blocks.REDSTONE_BLOCK))
				.thenIdle(4)
				.thenExecute(() -> assertValleyTransition(helper, rails, observer, false, false))
				.thenExecute(() -> setTriggerPair(helper, trigger, Blocks.STONE))
				.thenIdle(2)
				.thenExecute(() -> assertValleyTransition(helper, rails, observer, true, true))
				.thenIdle(4)
				.thenExecute(() -> assertObserverPairPowered(helper, observer, false))
				.thenExecute(() -> setSourcePairWithoutUpdates(helper, source, Blocks.AIR))
				.thenIdle(4)
				.thenExecute(() -> assertValleyTransition(helper, rails, observer, true, false))
				.thenExecute(() -> setTriggerPair(helper, trigger, Blocks.AIR))
				.thenIdle(2)
				.thenExecute(() -> assertValleyTransition(helper, rails, observer, false, true))
				.thenIdle(4)
				.thenExecute(() -> assertObserverPairPowered(helper, observer, false))
				.thenSucceed();
	}

	private static BlockPos[] valleyRails() {
		return new BlockPos[]{
				new BlockPos(1, RAIL_Y + 3, 3),
				new BlockPos(2, RAIL_Y + 2, 3),
				new BlockPos(3, RAIL_Y + 1, 3),
				new BlockPos(4, RAIL_Y, 3),
				new BlockPos(5, RAIL_Y, 3),
				new BlockPos(6, RAIL_Y + 1, 3),
				new BlockPos(7, RAIL_Y + 2, 3),
				new BlockPos(8, RAIL_Y + 3, 3)
		};
	}

	private static RailShape[] valleyShapes() {
		return new RailShape[]{
				RailShape.EAST_WEST,
				RailShape.ASCENDING_WEST,
				RailShape.ASCENDING_WEST,
				RailShape.ASCENDING_WEST,
				RailShape.ASCENDING_EAST,
				RailShape.ASCENDING_EAST,
				RailShape.ASCENDING_EAST,
				RailShape.EAST_WEST
		};
	}

	private static void placeValleyPair(GameTestHelper helper, BlockPos[] rails) {
		placeRailPathPair(helper, rails, valleyShapes());
	}

	private static void assertValleyTransition(GameTestHelper helper, BlockPos[] rails,
											   BlockPos observer, boolean powered, boolean observerPowered) {
		assertMatchingRailPower(helper, mirrorCopy(rails), rails);
		assertRailsPowered(helper, rails, powered);
		assertObserverPairPowered(helper, observer, observerPowered);
	}

	private static void assertObserverPairPowered(GameTestHelper helper, BlockPos observer, boolean powered) {
		assertObserverPowered(helper, mirrorCopy(observer), powered);
		assertObserverPowered(helper, observer, powered);
	}

	@SuppressWarnings("null")
	private static void placeSourcePair(GameTestHelper helper, BlockPos source) {
		helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
		helper.setBlock(source, Blocks.REDSTONE_BLOCK);
	}

	@SuppressWarnings("null")
	private static void removeSourcePair(GameTestHelper helper, BlockPos source) {
		helper.setBlock(mirrorCopy(source), Blocks.AIR);
		helper.setBlock(source, Blocks.AIR);
	}

	private static void setSourcePairWithoutUpdates(GameTestHelper helper, BlockPos source, Block block) {
		setBlockWithoutUpdates(helper, mirrorCopy(source), block.defaultBlockState());
		setBlockWithoutUpdates(helper, source, block.defaultBlockState());
	}

	@SuppressWarnings("null")
	private static void setTriggerPair(GameTestHelper helper, BlockPos trigger, Block block) {
		helper.setBlock(mirrorCopy(trigger), block);
		helper.setBlock(trigger, block);
	}

	@SuppressWarnings("null")
	private static void setBlockWithoutUpdates(GameTestHelper helper, BlockPos pos, BlockState state) {
		helper.getLevel().setBlock(helper.absolutePos(pos), state, Block.UPDATE_NONE);
	}
}
