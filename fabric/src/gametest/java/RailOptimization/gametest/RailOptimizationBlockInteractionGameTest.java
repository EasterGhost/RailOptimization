package RailOptimization.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;

public class RailOptimizationBlockInteractionGameTest extends RailOptimizationGameTestSupport {
	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_96", maxTicks = 240, padding = 40)
	public void budPistonAtDoubleNotifiedPositionMatchesVanilla(GameTestHelper helper) {
		BlockPos start = new BlockPos(3, RAIL_Y, 2);
		BlockPos source = start.west();
		BlockPos budPiston = start.west().relative(Direction.SOUTH, 2);
		BlockPos qcBlock = budPiston.above();
		BlockPos qcLever = qcBlock.south();

		placeRailLinePair(helper, start, Direction.SOUTH, 4, RailShape.NORTH_SOUTH);
		placePistonPair(helper, budPiston, Direction.UP, true);
		helper.setBlock(qcBlock, Blocks.STONE);
		helper.setBlock(mirrorCopy(qcBlock), Blocks.STONE);
		var qcLeverState = Blocks.LEVER.defaultBlockState()
				.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
				.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
				.setValue(LeverBlock.POWERED, false);
		helper.setBlock(qcLever, qcLeverState);
		helper.setBlock(mirrorCopy(qcLever), qcLeverState);

		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertBlockProperty(budPiston, PistonBaseBlock.EXTENDED, false);
					helper.assertBlockProperty(mirrorCopy(budPiston), PistonBaseBlock.EXTENDED, false);
				})
				.thenExecute(() -> {
					helper.pullLever(qcLever);
					helper.pullLever(mirrorCopy(qcLever));
				})
				.thenIdle(4)
				.thenExecute(() -> {
					helper.assertBlockProperty(budPiston, PistonBaseBlock.EXTENDED, false);
					helper.assertBlockProperty(mirrorCopy(budPiston), PistonBaseBlock.EXTENDED, false);
				})
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
					helper.setBlock(source, Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.SOUTH, 4);
					helper.assertBlockProperty(budPiston, PistonBaseBlock.EXTENDED, true);
					helper.assertBlockProperty(mirrorCopy(budPiston), PistonBaseBlock.EXTENDED, true);
				})
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(source), Blocks.AIR);
					helper.setBlock(source, Blocks.AIR);
				})
				.thenIdle(6)
				.thenExecute(() -> {
					assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.SOUTH, 4);
					boolean vanillaExtended = helper.getBlockState(mirrorCopy(budPiston))
							.getValue(PistonBaseBlock.EXTENDED);
					boolean optimizedExtended = helper.getBlockState(budPiston)
							.getValue(PistonBaseBlock.EXTENDED);
					helper.assertTrue(vanillaExtended == optimizedExtended,
							Component.literal("bud piston end state mismatch: vanilla="
									+ vanillaExtended + ", optimized=" + optimizedExtended));
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_97", maxTicks = 240, padding = 40)
	public void stuckDropperAtDoubleNotifiedPositionMatchesVanilla(GameTestHelper helper) {
		BlockPos start = new BlockPos(3, RAIL_Y, 2);
		BlockPos source = start.west();
		BlockPos dropper = start.west().relative(Direction.SOUTH, 2);
		BlockPos powerBlock = dropper.below();
		BlockPos pusherPiston = powerBlock.south();

		placeRailLinePair(helper, start, Direction.SOUTH, 4, RailShape.NORTH_SOUTH);
		placeDropperPair(helper, dropper);
		placePistonPair(helper, pusherPiston, Direction.NORTH, false);

		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(powerBlock), Blocks.REDSTONE_BLOCK);
					helper.setBlock(powerBlock, Blocks.REDSTONE_BLOCK);
				})
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(source), Blocks.REDSTONE_BLOCK);
					helper.setBlock(source, Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(4)
				.thenExecute(() -> {
					helper.setBlock(mirrorCopy(source), Blocks.AIR);
					helper.setBlock(source, Blocks.AIR);
				})
				.thenIdle(8)
				.thenExecute(() -> {
					assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.SOUTH, 4);
					assertStuckDropperMatchesVanilla(helper, dropper);
				})
				.thenSucceed();
	}

	@SuppressWarnings("null")
	private static void placeDropperPair(GameTestHelper helper, BlockPos dropperPos) {
		helper.setBlock(dropperPos.below(), Blocks.STONE);
		helper.setBlock(mirrorCopy(dropperPos).below(), Blocks.STONE);
		helper.setBlock(dropperPos, Blocks.DROPPER.defaultBlockState()
				.setValue(DropperBlock.FACING, Direction.NORTH));
		helper.setBlock(mirrorCopy(dropperPos), Blocks.DROPPER.defaultBlockState()
				.setValue(DropperBlock.FACING, Direction.NORTH));
		DropperBlockEntity dropperEntity = helper.getBlockEntity(dropperPos, DropperBlockEntity.class);
		dropperEntity.setItem(0, new ItemStack(Blocks.STONE, 3));
		DropperBlockEntity mirrorDropperEntity = helper.getBlockEntity(
				mirrorCopy(dropperPos), DropperBlockEntity.class);
		mirrorDropperEntity.setItem(0, new ItemStack(Blocks.STONE, 3));
	}

	@SuppressWarnings("null")
	private static void assertStuckDropperMatchesVanilla(GameTestHelper helper, BlockPos dropperPos) {
		boolean vanillaTriggered = helper.getBlockState(mirrorCopy(dropperPos))
				.getValue(DropperBlock.TRIGGERED);
		boolean optimizedTriggered = helper.getBlockState(dropperPos)
				.getValue(DropperBlock.TRIGGERED);
		helper.assertTrue(vanillaTriggered == optimizedTriggered,
				Component.literal("dropper triggered state mismatch: vanilla="
						+ vanillaTriggered + ", optimized=" + optimizedTriggered));
		int vanillaItems = helper.getLevel().getEntitiesOfClass(
				ItemEntity.class,
				new AABB(helper.absolutePos(mirrorCopy(dropperPos))).inflate(4.0),
				entity -> entity.getItem().is(Blocks.STONE.asItem())).size();
		int optimizedItems = helper.getLevel().getEntitiesOfClass(
				ItemEntity.class,
				new AABB(helper.absolutePos(dropperPos)).inflate(4.0),
				entity -> entity.getItem().is(Blocks.STONE.asItem())).size();
		helper.assertTrue(vanillaItems == optimizedItems,
				Component.literal("dropper item count mismatch: vanilla="
						+ vanillaItems + ", optimized=" + optimizedItems));
	}

	@SuppressWarnings("null")
	private static void placePistonPair(GameTestHelper helper, BlockPos pos, Direction facing, boolean sticky) {
		Block pistonBlock = sticky ? Blocks.STICKY_PISTON : Blocks.PISTON;
		helper.setBlock(pos, pistonBlock.defaultBlockState()
				.setValue(PistonBaseBlock.FACING, facing));
		helper.setBlock(mirrorCopy(pos), pistonBlock.defaultBlockState()
				.setValue(PistonBaseBlock.FACING, facing));
	}
}
