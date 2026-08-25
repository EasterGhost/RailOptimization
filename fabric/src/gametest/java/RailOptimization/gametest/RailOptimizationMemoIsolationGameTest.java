package RailOptimization.gametest;

import RailOptimization.RailLogicTestAccess;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationMemoIsolationGameTest {
	private static final int SHARED_Y = 200;

	@SuppressWarnings("null")
	@GameTest(environment = "railoptimization-gametest:serial_112", maxTicks = 1)
	public void memoDoesNotCrossLevelBoundary(GameTestHelper helper) {
		ServerLevel firstLevel = helper.getLevel();
		ServerLevel secondLevel = firstLevel.getServer().getLevel(Level.NETHER);
		if (secondLevel == null) {
			throw new IllegalStateException("Nether level is unavailable");
		}

		BlockPos testOrigin = helper.absolutePos(BlockPos.ZERO);
		BlockPos railPos = new BlockPos(testOrigin.getX() + 3, SHARED_Y, testOrigin.getZ() + 3);
		BlockPos supportPos = railPos.below();
		BlockPos sourcePos = railPos.east();
		BlockPos sourceSupportPos = sourcePos.below();
		BlockState firstRailBefore = firstLevel.getBlockState(railPos);
		BlockState firstSupportBefore = firstLevel.getBlockState(supportPos);
		BlockState firstSourceBefore = firstLevel.getBlockState(sourcePos);
		BlockState secondRailBefore = secondLevel.getBlockState(railPos);
		BlockState secondSupportBefore = secondLevel.getBlockState(supportPos);
		BlockState secondSourceBefore = secondLevel.getBlockState(sourcePos);
		BlockState secondSourceSupportBefore = secondLevel.getBlockState(sourceSupportPos);

		try {
			BlockState unpoweredRail = Blocks.POWERED_RAIL.defaultBlockState()
					.setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST);
			BlockState poweredRail = unpoweredRail.setValue(PoweredRailBlock.POWERED, true);
			firstLevel.setBlock(supportPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_NONE);
			firstLevel.setBlock(railPos, unpoweredRail, Block.UPDATE_NONE);
			secondLevel.setBlock(supportPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_NONE);
			secondLevel.setBlock(sourceSupportPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_NONE);
			secondLevel.setBlock(sourcePos, unpoweredRail, Block.UPDATE_NONE);
			secondLevel.setBlock(railPos, unpoweredRail, Block.UPDATE_NONE);
			secondLevel.setBlock(railPos, poweredRail, Block.UPDATE_NONE);
			helper.assertTrue(
					secondLevel.getBlockState(railPos).getValue(PoweredRailBlock.POWERED),
					Component.literal("second-level rail must start in the powered BUD state"));

			firstLevel.setBlock(sourcePos, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
			firstLevel.neighborChanged(railPos, Blocks.REDSTONE_BLOCK, null);
			helper.assertTrue(
					firstLevel.getBlockState(railPos).getValue(PoweredRailBlock.POWERED),
					Component.literal("first-level rail should prime a powered memo entry"));
			helper.assertTrue(
					RailLogicTestAccess.isMemoConfirmed(firstLevel, railPos, 8, true),
					Component.literal("first-level rail should remain confirmed before the cross-level update"));
			helper.assertTrue(
					secondLevel.getBlockState(railPos).getValue(PoweredRailBlock.POWERED),
					Component.literal("second-level rail must remain powered before its neighbor update"));

			secondLevel.updateNeighborsAt(sourcePos, Blocks.POWERED_RAIL);
			helper.assertTrue(
					!secondLevel.getBlockState(railPos).getValue(PoweredRailBlock.POWERED),
					Component.literal("memo entry from another level must not suppress depowering"));
		} finally {
			firstLevel.setBlock(sourcePos, firstSourceBefore, Block.UPDATE_NONE);
			firstLevel.setBlock(railPos, firstRailBefore, Block.UPDATE_NONE);
			firstLevel.setBlock(supportPos, firstSupportBefore, Block.UPDATE_NONE);
			secondLevel.setBlock(railPos, secondRailBefore, Block.UPDATE_NONE);
			secondLevel.setBlock(supportPos, secondSupportBefore, Block.UPDATE_NONE);
			secondLevel.setBlock(sourcePos, secondSourceBefore, Block.UPDATE_NONE);
			secondLevel.setBlock(sourceSupportPos, secondSourceSupportBefore, Block.UPDATE_NONE);
		}
		helper.succeed();
	}
}
