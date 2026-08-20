package RailOptimization.gametest;

import RailOptimization.RailLogic;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;

public class RailOptimizationDuplicatorGameTest extends RailOptimizationGameTestSupport {
    private static final int DUPLICATOR_LEVER_CYCLES = 1;

    @GameTest(environment = "railoptimization-gametest:serial_75", maxTicks = 200, padding = 40)
    public void observerSupportedRailDuplicatorDuplicatesOnLeverCycles(GameTestHelper helper) {
        BlockPos origin = new BlockPos(2, 2, 1);
        BlockPos lever = placeRidingDuplicatorPair(
                helper, origin, RailShape.EAST_WEST, Blocks.RAIL);

        GameTestSequence sequence = helper.startSequence().thenIdle(2);
        sequence = withLeverCycles(sequence, helper, mirrorCopy(lever), DUPLICATOR_LEVER_CYCLES);
        sequence = withLeverCycles(sequence, helper, lever, DUPLICATOR_LEVER_CYCLES);
        sequence.thenIdle(4).thenExecute(() -> assertDuplicatorBehaviorMatchesVanilla(
                helper, origin, RailShape.EAST_WEST, Blocks.RAIL, Blocks.RAIL.asItem())).thenSucceed();
    }

    @GameTest(environment = "railoptimization-gametest:serial_76", maxTicks = 200, padding = 40)
    public void poweredRailDuplicatorDuplicatesOnLeverCycles(GameTestHelper helper) {
        BlockPos origin = new BlockPos(2, 2, 1);
        BlockPos lever = placePoweredRailDuplicatorPair(helper, origin);

        GameTestSequence sequence = helper.startSequence().thenIdle(2);
        sequence = withLeverCycles(sequence, helper, mirrorCopy(lever), DUPLICATOR_LEVER_CYCLES);
        sequence = withLeverCycles(sequence, helper, lever, DUPLICATOR_LEVER_CYCLES);
        sequence.thenIdle(4).thenExecute(() -> assertDuplicatorBehaviorMatchesVanilla(
                helper, origin, RailShape.EAST_WEST, Blocks.POWERED_RAIL, Blocks.POWERED_RAIL.asItem()))
                .thenSucceed();
    }

    @GameTest(environment = "railoptimization-gametest:serial_77", maxTicks = 200, padding = 40)
    public void activatorRailDuplicatorDuplicatesOnLeverCycles(GameTestHelper helper) {
        BlockPos origin = new BlockPos(2, 2, 1);
        BlockPos lever = placeRidingDuplicatorPair(
                helper, origin, RailShape.EAST_WEST, Blocks.ACTIVATOR_RAIL);

        GameTestSequence sequence = helper.startSequence().thenIdle(2);
        sequence = withLeverCycles(sequence, helper, mirrorCopy(lever), DUPLICATOR_LEVER_CYCLES);
        sequence = withLeverCycles(sequence, helper, lever, DUPLICATOR_LEVER_CYCLES);
        sequence.thenIdle(4).thenExecute(() -> assertDuplicatorBehaviorMatchesVanilla(
                helper, origin, RailShape.EAST_WEST, Blocks.ACTIVATOR_RAIL, Blocks.ACTIVATOR_RAIL.asItem()))
                .thenSucceed();
    }

    @GameTest(environment = "railoptimization-gametest:serial_78", maxTicks = 200, padding = 40)
    public void detectorRailDuplicatorDuplicatesOnLeverCycles(GameTestHelper helper) {
        BlockPos origin = new BlockPos(2, 2, 1);
        BlockPos lever = placeRidingDuplicatorPair(
                helper, origin, RailShape.EAST_WEST, Blocks.DETECTOR_RAIL);

        GameTestSequence sequence = helper.startSequence().thenIdle(2);
        sequence = withLeverCycles(sequence, helper, mirrorCopy(lever), DUPLICATOR_LEVER_CYCLES);
        sequence = withLeverCycles(sequence, helper, lever, DUPLICATOR_LEVER_CYCLES);
        sequence.thenIdle(4).thenExecute(() -> assertDuplicatorBehaviorMatchesVanilla(
                helper, origin, RailShape.EAST_WEST, Blocks.DETECTOR_RAIL, Blocks.DETECTOR_RAIL.asItem()))
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_79", maxTicks = 160, padding = 40)
    public void pistonTopRailPopDuplicatorDuplicatesOnExtension(GameTestHelper helper) {
        BlockPos piston = new BlockPos(3, 2, 3);
        BlockPos topRail = piston.above();
        BlockPos railLever = piston.west().above();
        BlockPos pistonLever = piston.east().north();

        placePistonTopDuplicatorPair(helper, piston);

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(false);
                    helper.pullLever(mirrorCopy(railLever));
                    helper.pullLever(mirrorCopy(pistonLever));
                })
                .thenIdle(6)
                .thenExecute(() -> helper.pullLever(mirrorCopy(pistonLever)))
                .thenIdle(6)
                .thenExecute(() -> {
                    RailLogic.setOptimizationEnabled(true);
                    helper.pullLever(railLever);
                    helper.pullLever(pistonLever);
                })
                .thenIdle(6)
                .thenExecute(() -> helper.pullLever(pistonLever))
                .thenIdle(6)
                .thenExecute(() -> {
                    helper.assertBlockNotPresent(Blocks.POWERED_RAIL, mirrorCopy(topRail));
                    helper.assertBlockNotPresent(Blocks.POWERED_RAIL, topRail);
                    assertPistonTopDuplicatorBehaviorMatchesVanilla(helper, piston);
                })
                .thenSucceed();
    }

    private static void assertPistonTopDuplicatorBehaviorMatchesVanilla(
            GameTestHelper helper, BlockPos piston) {
        int vanillaPoweredCount = countRailItems(
                helper, mirrorCopy(piston.above()), 4.0, Blocks.POWERED_RAIL.asItem());
        int optimizedPoweredCount = countRailItems(
                helper, piston.above(), 4.0, Blocks.POWERED_RAIL.asItem());
        int vanillaRailCount = countRailItems(
                helper, mirrorCopy(piston.above()), 4.0, Blocks.RAIL.asItem());
        int optimizedRailCount = countRailItems(
                helper, piston.above(), 4.0, Blocks.RAIL.asItem());
        helper.assertTrue(optimizedPoweredCount == vanillaPoweredCount,
                Component.literal("powered rail item count mismatch: vanilla="
                        + vanillaPoweredCount + ", optimized=" + optimizedPoweredCount));
        helper.assertTrue(optimizedRailCount == vanillaRailCount,
                Component.literal("normal rail item count mismatch: vanilla="
                        + vanillaRailCount + ", optimized=" + optimizedRailCount));
    }

    @SuppressWarnings("null")
    private static void assertDuplicatorBehaviorMatchesVanilla(
            GameTestHelper helper, BlockPos origin, RailShape railShape, Block railBlock, Item railItem) {
        BlockPos railPos = origin.south(3).above();
        int vanillaCount = countRailItems(helper, mirrorCopy(railPos), 4.0, railItem);
        int optimizedCount = countRailItems(helper, railPos, 4.0, railItem);
        helper.assertTrue(optimizedCount == vanillaCount,
                Component.literal("rail item count mismatch: vanilla="
                        + vanillaCount + ", optimized=" + optimizedCount));

        boolean vanillaRailPresent = helper.getBlockState(mirrorCopy(railPos)).is(railBlock);
        boolean optimizedRailPresent = helper.getBlockState(railPos).is(railBlock);
        helper.assertTrue(vanillaRailPresent == optimizedRailPresent,
                Component.literal("rail presence mismatch: vanilla=" + vanillaRailPresent
                        + ", optimized=" + optimizedRailPresent));

        boolean vanillaPistonExtended = helper.getBlockState(mirrorCopy(origin).south())
                .getValue(PistonBaseBlock.EXTENDED);
        boolean optimizedPistonExtended = helper.getBlockState(origin.south())
                .getValue(PistonBaseBlock.EXTENDED);
        helper.assertTrue(vanillaPistonExtended == optimizedPistonExtended,
                Component.literal("piston end state mismatch: vanilla extended="
                        + vanillaPistonExtended + ", optimized extended=" + optimizedPistonExtended));

        if (vanillaRailPresent && optimizedRailPresent) {
            helper.assertBlockProperty(railPos, ((BaseRailBlock) railBlock).getShapeProperty(), railShape);
        }
    }

    private static BlockPos placeRidingDuplicatorPair(
            GameTestHelper helper, BlockPos origin, RailShape railShape, Block railBlock) {
        placeRidingDuplicator(helper, origin, railShape, railBlock);
        placeRidingDuplicator(helper, mirrorCopy(origin), railShape, railBlock);
        return origin.west();
    }

    @SuppressWarnings("null")
    private static void placeRidingDuplicator(
            GameTestHelper helper, BlockPos origin, RailShape railShape, Block railBlock) {
        BlockPos concrete = origin;
        BlockPos piston = origin.south();
        BlockPos slime = origin.south(3);
        BlockPos railPos = slime.above();
        BlockPos lever = origin.west();

        helper.setBlock(concrete, Blocks.BLACK_CONCRETE);
        helper.setBlock(piston, Blocks.STICKY_PISTON.defaultBlockState()
                .setValue(PistonBaseBlock.FACING, Direction.SOUTH));
        helper.setBlock(slime, Blocks.SLIME_BLOCK);
        placeDuplicatorRail(helper, railPos, railShape, railBlock);
        helper.setBlock(lever, wallLeverState(Direction.WEST));
    }

    private static BlockPos placePoweredRailDuplicatorPair(GameTestHelper helper, BlockPos origin) {
        placePoweredRailDuplicator(helper, origin);
        placePoweredRailDuplicator(helper, mirrorCopy(origin));
        return origin.west();
    }

    @SuppressWarnings("null")
    private static void placePoweredRailDuplicator(GameTestHelper helper, BlockPos origin) {
        BlockPos concrete = origin;
        BlockPos piston = origin.south();
        BlockPos slime = origin.south(3);
        BlockPos railPos = slime.above();
        BlockPos powerStone = railPos.east(2);
        BlockPos powerTorch = railPos.east();
        BlockPos lever = origin.west();

        helper.setBlock(concrete, Blocks.BLACK_CONCRETE);
        helper.setBlock(piston, Blocks.STICKY_PISTON.defaultBlockState()
                .setValue(PistonBaseBlock.FACING, Direction.SOUTH));
        helper.setBlock(slime, Blocks.SLIME_BLOCK);
        helper.setBlock(powerStone, Blocks.STONE);
        helper.setBlock(powerTorch, Blocks.REDSTONE_WALL_TORCH.defaultBlockState()
                .setValue(RedstoneWallTorchBlock.FACING, Direction.WEST));
        placeDuplicatorRail(helper, railPos, RailShape.EAST_WEST, Blocks.POWERED_RAIL);
        helper.setBlock(lever, wallLeverState(Direction.WEST));
    }

    private static void placePistonTopDuplicatorPair(GameTestHelper helper, BlockPos piston) {
        placePistonTopDuplicator(helper, piston);
        placePistonTopDuplicator(helper, mirrorCopy(piston));
    }

    @SuppressWarnings("null")
    private static void placePistonTopDuplicator(GameTestHelper helper, BlockPos piston) {
        BlockPos topRail = piston.above();
        BlockPos sideRail = piston.north();
        BlockPos railPowerStone = piston.west(2).above();
        BlockPos railLever = railPowerStone.east();
        BlockPos pistonPowerStone = piston.east();
        BlockPos pistonLever = pistonPowerStone.north();

        helper.setBlock(piston.below(), Blocks.STONE);
        helper.setBlock(sideRail.below(), Blocks.STONE);
        helper.setBlock(piston, Blocks.PISTON.defaultBlockState()
                .setValue(PistonBaseBlock.FACING, Direction.UP));
        placeDuplicatorRail(helper, topRail, RailShape.EAST_WEST, Blocks.POWERED_RAIL);
        placeDuplicatorRail(helper, sideRail, RailShape.NORTH_SOUTH, Blocks.RAIL);
        helper.setBlock(railPowerStone, Blocks.STONE);
        helper.setBlock(railLever, wallLeverState(Direction.EAST));
        helper.setBlock(pistonPowerStone, Blocks.STONE);
        helper.setBlock(pistonLever, wallLeverState(Direction.NORTH));
    }

    private static BlockState wallLeverState(Direction facing) {
        return Blocks.LEVER.defaultBlockState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
                .setValue(HorizontalDirectionalBlock.FACING, facing)
                .setValue(LeverBlock.POWERED, false);
    }

    @SuppressWarnings("null")
    private static void placeDuplicatorRail(GameTestHelper helper, BlockPos railPos, RailShape railShape, Block railBlock) {
        markVanillaForMirrorRail(helper, railPos);
        helper.setBlock(railPos, railBlock.defaultBlockState().setValue(
                ((BaseRailBlock) railBlock).getShapeProperty(), railShape));
    }

    private static GameTestSequence withLeverCycles(
            GameTestSequence sequence, GameTestHelper helper, BlockPos lever, int cycles) {
        for (int cycle = 0; cycle < cycles; cycle++) {
            sequence = sequence.thenExecute(() -> helper.pullLever(lever)).thenIdle(8)
                    .thenExecute(() -> helper.pullLever(lever)).thenIdle(8);
        }
        return sequence;
    }

    @SuppressWarnings("null")
    private static int countRailItems(GameTestHelper helper, BlockPos center, double radius, Item item) {
        return helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                new AABB(center).inflate(radius),
                entity -> entity.getItem().is(item)).size();
    }
}
