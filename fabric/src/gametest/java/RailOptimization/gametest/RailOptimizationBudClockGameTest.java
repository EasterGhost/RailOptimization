package RailOptimization.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationBudClockGameTest extends RailOptimizationGameTestSupport {
	private static final int RAIL_COUNT = 4;
	private static final int[] DROPPER_STACK_COUNTS = {61, 61, 59, 59, 57, 61, 59, 58, 57};
	private static final int INITIAL_DROPPER_ITEM_COUNT = 532;

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_86", maxTicks = 180, padding = 40)
	public void railStateChangesRunAndThenStopQuasiPoweredDropper(GameTestHelper helper) {
		BlockPos firstRail = new BlockPos(3, RAIL_Y, 3);
		BlockPos mirrorFirstRail = mirrorCopy(firstRail);
		BlockPos upperLever = firstRail.above(3);
		BlockPos lowerLever = firstRail.above();
		BlockPos dropper = firstRail.east(RAIL_COUNT);
		int[] lockedItemCounts = new int[2];

		placeFixture(helper, firstRail);
		placeFixture(helper, mirrorFirstRail);

		helper.startSequence()
				.thenIdle(8)
				.thenExecute(() -> {
					assertFixtureRailsPowered(helper, firstRail, false);
					assertFixtureRailsPowered(helper, mirrorFirstRail, false);
					assertDropperItemCount(helper, dropper, INITIAL_DROPPER_ITEM_COUNT, "optimized initial state");
					assertDropperItemCount(
							helper,
							mirrorCopy(dropper),
							INITIAL_DROPPER_ITEM_COUNT,
							"vanilla initial state");
				})
				.thenExecute(() -> {
					helper.pullLever(mirrorCopy(upperLever));
					helper.pullLever(upperLever);
				})
				.thenIdle(34)
				.thenExecute(() -> {
					int vanillaItems = dropperItemCount(helper, mirrorCopy(dropper));
					int optimizedItems = dropperItemCount(helper, dropper);
					helper.assertTrue(vanillaItems < INITIAL_DROPPER_ITEM_COUNT,
							Component.literal("vanilla dropper did not start"));
					helper.assertTrue(optimizedItems < INITIAL_DROPPER_ITEM_COUNT,
							Component.literal("optimized dropper did not start"));
					assertMatchingItemCounts(helper, vanillaItems, optimizedItems, "running observer clock");
				})
				.thenExecute(() -> {
					helper.pullLever(mirrorCopy(lowerLever));
					helper.pullLever(lowerLever);
				})
				.thenIdle(2)
				.thenExecute(() -> {
					assertFixtureRailsPowered(helper, firstRail, true);
					assertFixtureRailsPowered(helper, mirrorFirstRail, true);
					lockedItemCounts[0] = dropperItemCount(helper, mirrorCopy(dropper));
					lockedItemCounts[1] = dropperItemCount(helper, dropper);
					assertMatchingItemCounts(
							helper,
							lockedItemCounts[0],
							lockedItemCounts[1],
							"after locking rail power");
				})
				.thenIdle(32)
				.thenExecute(() -> {
					assertDropperItemCount(helper, mirrorCopy(dropper), lockedItemCounts[0], "vanilla locked state");
					assertDropperItemCount(helper, dropper, lockedItemCounts[1], "optimized locked state");
					assertFixtureRailsPowered(helper, firstRail, true);
					assertFixtureRailsPowered(helper, mirrorFirstRail, true);
					helper.assertBlockProperty(firstRail.east().above(3), PistonBaseBlock.EXTENDED, true);
					helper.assertBlockProperty(
							mirrorFirstRail.east().above(3),
							PistonBaseBlock.EXTENDED,
							true);
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	private static void placeFixture(GameTestHelper helper, BlockPos firstRail) {
		for (int offset = 0; offset < RAIL_COUNT; offset++) {
			BlockPos rail = firstRail.east(offset);
			markVanillaForMirrorRail(helper, rail);
			helper.setBlock(rail.below(), Blocks.GLASS);
			helper.setBlock(rail, Blocks.ACTIVATOR_RAIL.defaultBlockState()
					.setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST));
		}

		BlockPos dropper = firstRail.east(RAIL_COUNT);
		helper.setBlock(dropper, Blocks.DROPPER.defaultBlockState()
				.setValue(DropperBlock.FACING, Direction.EAST));
		fillDropper(helper.getBlockEntity(dropper, DropperBlockEntity.class));

		helper.setBlock(firstRail.above(2), Blocks.GLASS);
		helper.setBlock(firstRail.above(), leverState(AttachFace.CEILING));
		helper.setBlock(firstRail.above(3), leverState(AttachFace.FLOOR));
		helper.setBlock(firstRail.east().above(2), observerState(Direction.EAST));
		helper.setBlock(firstRail.east(2).above(), observerState(Direction.WEST));
		helper.setBlock(firstRail.east(3).above(), Blocks.STONE);
		helper.setBlock(firstRail.east().above(3), Blocks.STICKY_PISTON.defaultBlockState()
				.setValue(PistonBaseBlock.FACING, Direction.DOWN));
	}

	private static BlockState leverState(AttachFace face) {
		return Blocks.LEVER.defaultBlockState()
				.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, face)
				.setValue(HorizontalDirectionalBlock.FACING, Direction.EAST)
				.setValue(LeverBlock.POWERED, false);
	}

	private static BlockState observerState(Direction facing) {
		return Blocks.OBSERVER.defaultBlockState()
				.setValue(ObserverBlock.FACING, facing)
				.setValue(ObserverBlock.POWERED, false);
	}

	private static void fillDropper(DropperBlockEntity dropper) {
		for (int slot = 0; slot < DROPPER_STACK_COUNTS.length; slot++) {
			dropper.setItem(slot, new ItemStack(Items.KELP, DROPPER_STACK_COUNTS[slot]));
		}
	}

	private static void assertFixtureRailsPowered(GameTestHelper helper, BlockPos firstRail, boolean powered) {
		for (int offset = 0; offset < RAIL_COUNT; offset++) {
			helper.assertBlockProperty(firstRail.east(offset), PoweredRailBlock.POWERED, powered);
		}
	}

	private static void assertDropperItemCount(
			GameTestHelper helper, BlockPos dropper, int expected, String stage) {
		int actual = dropperItemCount(helper, dropper);
		helper.assertTrue(actual == expected,
				Component.literal(stage + ": expected " + expected + " items, found " + actual));
	}

	private static void assertMatchingItemCounts(
			GameTestHelper helper, int vanillaItems, int optimizedItems, String stage) {
		helper.assertTrue(vanillaItems == optimizedItems,
				Component.literal(stage + ": dropper item count mismatch, vanilla="
						+ vanillaItems + ", optimized=" + optimizedItems));
	}

	@SuppressWarnings("null")
	private static int dropperItemCount(GameTestHelper helper, BlockPos dropper) {
		DropperBlockEntity blockEntity = helper.getBlockEntity(dropper, DropperBlockEntity.class);
		int itemCount = 0;
		for (int slot = 0; slot < blockEntity.getContainerSize(); slot++) {
			itemCount += blockEntity.getItem(slot).getCount();
		}
		return itemCount;
	}
}
