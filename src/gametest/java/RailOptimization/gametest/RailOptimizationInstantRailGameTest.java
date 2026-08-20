package RailOptimization.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;

public class RailOptimizationInstantRailGameTest extends RailOptimizationGameTestSupport {
    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_88", maxTicks = 160, padding = 40)
    public void straightPoweredRailInstantLinePropagatesSameTick(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 2);
        BlockPos farRail = start.relative(Direction.EAST, 6);
        BlockPos lever = start.north();
        BlockPos observer = farRail.south();

        placeRailLinePair(helper, start, Direction.EAST, 7, RailShape.EAST_WEST);
        placeLeverPair(helper, lever);
        placeObserverWatchingRail(helper, observer, Direction.NORTH);
        placeObserverWatchingRail(helper, mirrorCopy(observer), Direction.NORTH);

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.pullLever(mirrorCopy(lever));
                    helper.assertBlockProperty(mirrorCopy(farRail), PoweredRailBlock.POWERED, true);
                })
                .thenExecute(() -> {
                    helper.pullLever(lever);
                    helper.assertBlockProperty(farRail, PoweredRailBlock.POWERED, true);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    assertObserverPowered(helper, observer, true);
                    assertObserverPowered(helper, mirrorCopy(observer), true);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    assertObserverPowered(helper, observer, false);
                    assertObserverPowered(helper, mirrorCopy(observer), false);
                })
                .thenExecute(() -> {
                    helper.pullLever(mirrorCopy(lever));
                    helper.assertBlockProperty(mirrorCopy(farRail), PoweredRailBlock.POWERED, false);
                })
                .thenExecute(() -> {
                    helper.pullLever(lever);
                    helper.assertBlockProperty(farRail, PoweredRailBlock.POWERED, false);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    assertObserverPowered(helper, observer, true);
                    assertObserverPowered(helper, mirrorCopy(observer), true);
                })
                .thenIdle(4)
                .thenExecute(() -> assertObserverPowered(helper, observer, false))
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_89", maxTicks = 160, padding = 40)
    public void ascendingPoweredRailInstantLinePropagatesSameTick(GameTestHelper helper) {
        BlockPos[] rails = new BlockPos[]{
                new BlockPos(0, RAIL_Y + 3, 2),
                new BlockPos(1, RAIL_Y + 2, 2),
                new BlockPos(2, RAIL_Y + 1, 2),
                new BlockPos(3, RAIL_Y, 2),
                new BlockPos(4, RAIL_Y, 2),
                new BlockPos(5, RAIL_Y + 1, 2),
                new BlockPos(6, RAIL_Y + 2, 2),
                new BlockPos(7, RAIL_Y + 3, 2)
        };
        RailShape[] shapes = new RailShape[]{
                RailShape.EAST_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.ASCENDING_WEST,
                RailShape.ASCENDING_EAST,
                RailShape.ASCENDING_EAST,
                RailShape.ASCENDING_EAST,
                RailShape.EAST_WEST
        };
        BlockPos farRail = rails[7];
        BlockPos lever = rails[0].north();
        BlockPos observer = farRail.south();

        placeRailPathPair(helper, rails, shapes);
        placeLeverPair(helper, lever);
        placeObserverWatchingRail(helper, observer, Direction.NORTH);
        placeObserverWatchingRail(helper, mirrorCopy(observer), Direction.NORTH);

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.pullLever(mirrorCopy(lever));
                    helper.assertBlockProperty(mirrorCopy(farRail), PoweredRailBlock.POWERED, true);
                })
                .thenExecute(() -> {
                    helper.pullLever(lever);
                    helper.assertBlockProperty(farRail, PoweredRailBlock.POWERED, true);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    assertObserverPowered(helper, observer, true);
                    assertObserverPowered(helper, mirrorCopy(observer), true);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    assertObserverPowered(helper, observer, false);
                    assertObserverPowered(helper, mirrorCopy(observer), false);
                })
                .thenExecute(() -> {
                    helper.pullLever(mirrorCopy(lever));
                    helper.assertBlockProperty(mirrorCopy(farRail), PoweredRailBlock.POWERED, false);
                })
                .thenExecute(() -> {
                    helper.pullLever(lever);
                    helper.assertBlockProperty(farRail, PoweredRailBlock.POWERED, false);
                })
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_90", maxTicks = 160, padding = 40)
    public void activatorRailInstantLinePropagatesSameTick(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 2);
        BlockPos farRail = start.relative(Direction.EAST, 6);
        BlockPos lever = start.north();
        BlockPos observer = farRail.south();

        for (int index = 0; index < 7; index++) {
            BlockPos rail = start.relative(Direction.EAST, index);
            placeRail(helper, rail, RailShape.EAST_WEST, Blocks.ACTIVATOR_RAIL);
            placeRail(helper, mirrorCopy(rail), RailShape.EAST_WEST, Blocks.ACTIVATOR_RAIL);
        }
        placeLeverPair(helper, lever);
        placeObserverWatchingRail(helper, observer, Direction.NORTH);
        placeObserverWatchingRail(helper, mirrorCopy(observer), Direction.NORTH);

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.pullLever(mirrorCopy(lever));
                    helper.assertBlockProperty(mirrorCopy(farRail), PoweredRailBlock.POWERED, true);
                })
                .thenExecute(() -> {
                    helper.pullLever(lever);
                    helper.assertBlockProperty(farRail, PoweredRailBlock.POWERED, true);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    assertObserverPowered(helper, observer, true);
                    assertObserverPowered(helper, mirrorCopy(observer), true);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    assertObserverPowered(helper, observer, false);
                    assertObserverPowered(helper, mirrorCopy(observer), false);
                })
                .thenExecute(() -> {
                    helper.pullLever(mirrorCopy(lever));
                    helper.assertBlockProperty(mirrorCopy(farRail), PoweredRailBlock.POWERED, false);
                })
                .thenExecute(() -> {
                    helper.pullLever(lever);
                    helper.assertBlockProperty(farRail, PoweredRailBlock.POWERED, false);
                })
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_91", maxTicks = 120, padding = 40)
    public void bidirectionalInstantLinePropagatesSameTickFromMidSource(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 2);
        BlockPos westEnd = start;
        BlockPos eastEnd = start.relative(Direction.EAST, 6);
        BlockPos midRail = start.relative(Direction.EAST, 3);
        BlockPos lever = midRail.north();

        placeRailLinePair(helper, start, Direction.EAST, 7, RailShape.EAST_WEST);
        placeLeverPair(helper, lever);

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.pullLever(mirrorCopy(lever));
                    helper.assertBlockProperty(mirrorCopy(westEnd), PoweredRailBlock.POWERED, true);
                    helper.assertBlockProperty(mirrorCopy(eastEnd), PoweredRailBlock.POWERED, true);
                })
                .thenExecute(() -> {
                    helper.pullLever(lever);
                    helper.assertBlockProperty(westEnd, PoweredRailBlock.POWERED, true);
                    helper.assertBlockProperty(eastEnd, PoweredRailBlock.POWERED, true);
                })
                .thenExecute(() -> {
                    helper.pullLever(mirrorCopy(lever));
                    helper.assertBlockProperty(mirrorCopy(westEnd), PoweredRailBlock.POWERED, false);
                    helper.assertBlockProperty(mirrorCopy(eastEnd), PoweredRailBlock.POWERED, false);
                })
                .thenExecute(() -> {
                    helper.pullLever(lever);
                    helper.assertBlockProperty(westEnd, PoweredRailBlock.POWERED, false);
                    helper.assertBlockProperty(eastEnd, PoweredRailBlock.POWERED, false);
                })
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_95", maxTicks = 260, padding = 40)
    public void poweredRailBudFlipsInstantlyOnBlockUpdate(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 2);
        BlockPos powerSource = start.north();
        BlockPos piston = powerSource.above();
        BlockPos lever = piston.west();
        BlockPos updateTrigger = start.relative(Direction.EAST, 6).south();

        placeRailLinePair(helper, start, Direction.EAST, 7, RailShape.EAST_WEST);
        helper.setBlock(powerSource, Blocks.REDSTONE_BLOCK);
        helper.setBlock(mirrorCopy(powerSource), Blocks.REDSTONE_BLOCK);
        helper.setBlock(piston, Blocks.STICKY_PISTON.defaultBlockState()
                .setValue(PistonBaseBlock.FACING, Direction.DOWN));
        helper.setBlock(mirrorCopy(piston), Blocks.STICKY_PISTON.defaultBlockState()
                .setValue(PistonBaseBlock.FACING, Direction.DOWN));
        helper.setBlock(lever, wallLeverState(Direction.EAST));
        helper.setBlock(mirrorCopy(lever), wallLeverState(Direction.EAST));

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> assertMatchingRailLinePower(
                        helper, mirrorCopy(start), start, Direction.EAST, 7))
                .thenExecute(() -> {
                    helper.pullLever(mirrorCopy(lever));
                    helper.pullLever(lever);
                })
                .thenIdle(6)
                .thenExecute(() -> assertMatchingRailLinePower(
                        helper, mirrorCopy(start), start, Direction.EAST, 7))
                .thenExecute(() -> triggerRailUpdate(helper, updateTrigger))
                .thenIdle(4)
                .thenExecute(() -> assertMatchingRailLinePower(
                        helper, mirrorCopy(start), start, Direction.EAST, 7))
                .thenExecute(() -> {
                    helper.pullLever(mirrorCopy(lever));
                    helper.pullLever(lever);
                })
                .thenIdle(6)
                .thenExecute(() -> assertMatchingRailLinePower(
                        helper, mirrorCopy(start), start, Direction.EAST, 7))
                .thenExecute(() -> triggerRailUpdate(helper, updateTrigger))
                .thenIdle(4)
                .thenExecute(() -> assertMatchingRailLinePower(
                        helper, mirrorCopy(start), start, Direction.EAST, 7))
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_92", maxTicks = 160, padding = 40)
    public void observerTappedInstantDropperLineFiresSameTick(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 2);
        BlockPos farRail = start.relative(Direction.EAST, 4);
        BlockPos lever = start.north();
        BlockPos observer = farRail.south();
        BlockPos dropper = observer.south();

        placeRailLinePair(helper, start, Direction.EAST, 5, RailShape.EAST_WEST);
        placeLeverPair(helper, lever);
        placeObserverWatchingRail(helper, observer, Direction.NORTH);
        placeObserverWatchingRail(helper, mirrorCopy(observer), Direction.NORTH);
        placeDropperPair(helper, dropper);

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.pullLever(mirrorCopy(lever));
                    helper.assertBlockProperty(mirrorCopy(farRail), PoweredRailBlock.POWERED, true);
                })
                .thenExecute(() -> {
                    helper.pullLever(lever);
                    helper.assertBlockProperty(farRail, PoweredRailBlock.POWERED, true);
                })
                .thenIdle(4)
                .thenExecute(() -> assertDropperFiredMatches(helper, dropper))
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_93", maxTicks = 160, padding = 40)
    public void observerTapOnInstantLineDelaysByItsOwnLatency(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 2);
        BlockPos farRail = start.relative(Direction.EAST, 4);
        BlockPos lever = start.north();
        BlockPos observer = farRail.south();

        placeRailLinePair(helper, start, Direction.EAST, 5, RailShape.EAST_WEST);
        placeLeverPair(helper, lever);
        placeObserverWatchingRail(helper, observer, Direction.NORTH);
        placeObserverWatchingRail(helper, mirrorCopy(observer), Direction.NORTH);

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.pullLever(mirrorCopy(lever));
                    helper.assertBlockProperty(mirrorCopy(farRail), PoweredRailBlock.POWERED, true);
                    assertObserverPowered(helper, mirrorCopy(observer), false);
                })
                .thenExecute(() -> {
                    helper.pullLever(lever);
                    helper.assertBlockProperty(farRail, PoweredRailBlock.POWERED, true);
                    assertObserverPowered(helper, observer, false);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    assertObserverPowered(helper, observer, true);
                    assertObserverPowered(helper, mirrorCopy(observer), true);
                })
                .thenIdle(4)
                .thenExecute(() -> assertObserverPowered(helper, observer, false))
                .thenSucceed();
    }

    @SuppressWarnings("null")
    @GameTest(environment = "railoptimization-gametest:serial_94", maxTicks = 160, padding = 40)
    public void instantRailLineBeatsRedstoneRepeater(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, RAIL_Y, 2);
        BlockPos farRail = start.relative(Direction.EAST, 4);
        BlockPos leverStone = start.north();
        BlockPos lever = leverStone.above();
        BlockPos dust = leverStone.east();
        BlockPos repeater = dust.east();
        BlockPos lamp = repeater.east();

        placeRailLinePair(helper, start, Direction.EAST, 5, RailShape.EAST_WEST);
        helper.setBlock(leverStone, Blocks.STONE);
        helper.setBlock(mirrorCopy(leverStone), Blocks.STONE);
        helper.setBlock(lever, floorLeverState());
        helper.setBlock(mirrorCopy(lever), floorLeverState());
        helper.setBlock(dust.below(), Blocks.STONE);
        helper.setBlock(mirrorCopy(dust).below(), Blocks.STONE);
        helper.setBlock(dust, Blocks.REDSTONE_WIRE.defaultBlockState());
        helper.setBlock(mirrorCopy(dust), Blocks.REDSTONE_WIRE.defaultBlockState());
        helper.setBlock(repeater, Blocks.REPEATER.defaultBlockState()
                .setValue(RepeaterBlock.FACING, Direction.EAST)
                .setValue(RepeaterBlock.DELAY, 1));
        helper.setBlock(mirrorCopy(repeater), Blocks.REPEATER.defaultBlockState()
                .setValue(RepeaterBlock.FACING, Direction.EAST)
                .setValue(RepeaterBlock.DELAY, 1));
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        helper.setBlock(mirrorCopy(lamp), Blocks.REDSTONE_LAMP.defaultBlockState());

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.pullLever(mirrorCopy(lever));
                    helper.assertBlockProperty(mirrorCopy(farRail), PoweredRailBlock.POWERED, true);
                    helper.assertBlockProperty(mirrorCopy(lamp), RedstoneLampBlock.LIT, false);
                })
                .thenExecute(() -> {
                    helper.pullLever(lever);
                    helper.assertBlockProperty(farRail, PoweredRailBlock.POWERED, true);
                    helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, false);
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    boolean vanillaLit = helper.getBlockState(mirrorCopy(lamp)).getValue(RedstoneLampBlock.LIT);
                    boolean optimizedLit = helper.getBlockState(lamp).getValue(RedstoneLampBlock.LIT);
                    helper.assertTrue(vanillaLit == optimizedLit,
                            Component.literal("lamp state mismatch: vanilla="
                                    + vanillaLit + ", optimized=" + optimizedLit));
                })
                .thenExecute(() -> {
                    helper.pullLever(mirrorCopy(lever));
                    helper.assertBlockProperty(mirrorCopy(farRail), PoweredRailBlock.POWERED, false);
                })
                .thenExecute(() -> {
                    helper.pullLever(lever);
                    helper.assertBlockProperty(farRail, PoweredRailBlock.POWERED, false);
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    boolean vanillaLit = helper.getBlockState(mirrorCopy(lamp)).getValue(RedstoneLampBlock.LIT);
                    boolean optimizedLit = helper.getBlockState(lamp).getValue(RedstoneLampBlock.LIT);
                    helper.assertTrue(vanillaLit == optimizedLit,
                            Component.literal("lamp state mismatch: vanilla="
                                    + vanillaLit + ", optimized=" + optimizedLit));
                })
                .thenSucceed();
    }

    @SuppressWarnings("null")
    private static void placeDropperPair(GameTestHelper helper, BlockPos dropperPos) {
        helper.setBlock(dropperPos.below(), Blocks.STONE);
        helper.setBlock(mirrorCopy(dropperPos).below(), Blocks.STONE);
        helper.setBlock(dropperPos, Blocks.DROPPER.defaultBlockState()
                .setValue(DropperBlock.FACING, Direction.SOUTH));
        helper.setBlock(mirrorCopy(dropperPos), Blocks.DROPPER.defaultBlockState()
                .setValue(DropperBlock.FACING, Direction.SOUTH));
        DropperBlockEntity dropperEntity = helper.getBlockEntity(dropperPos, DropperBlockEntity.class);
        dropperEntity.setItem(0, new ItemStack(Blocks.STONE));
        DropperBlockEntity mirrorDropperEntity = helper.getBlockEntity(
                mirrorCopy(dropperPos), DropperBlockEntity.class);
        mirrorDropperEntity.setItem(0, new ItemStack(Blocks.STONE));
    }

    @SuppressWarnings("null")
    private static void assertDropperFiredMatches(GameTestHelper helper, BlockPos dropperPos) {
        int vanillaItems = countItemsNear(helper, mirrorCopy(dropperPos), Blocks.STONE.asItem());
        int optimizedItems = countItemsNear(helper, dropperPos, Blocks.STONE.asItem());
        helper.assertTrue(optimizedItems == vanillaItems,
                Component.literal("dropper item count mismatch: vanilla="
                        + vanillaItems + ", optimized=" + optimizedItems));
    }

    @SuppressWarnings("null")
    private static int countItemsNear(GameTestHelper helper, BlockPos pos, Item item) {
        BlockPos absolute = helper.absolutePos(pos);
        return helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                new AABB(absolute).inflate(4.0),
                entity -> entity.getItem().is(item)).size();
    }

    @SuppressWarnings("null")
    private static BlockState floorLeverState() {
        return Blocks.LEVER.defaultBlockState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(LeverBlock.POWERED, false);
    }

    @SuppressWarnings("null")
    private static BlockState wallLeverState(Direction facing) {
        return Blocks.LEVER.defaultBlockState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
                .setValue(HorizontalDirectionalBlock.FACING, facing)
                .setValue(LeverBlock.POWERED, false);
    }

    @SuppressWarnings("null")
    private static void triggerRailUpdate(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.STONE);
        helper.setBlock(mirrorCopy(pos), Blocks.STONE);
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(mirrorCopy(pos), Blocks.AIR);
    }

    @SuppressWarnings("null")
    private static void placeLeverPair(GameTestHelper helper, BlockPos leverPos) {
        var leverState = Blocks.LEVER.defaultBlockState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(LeverBlock.POWERED, false);
        helper.setBlock(leverPos.below(), Blocks.STONE);
        helper.setBlock(mirrorCopy(leverPos).below(), Blocks.STONE);
        helper.setBlock(leverPos, leverState);
        helper.setBlock(mirrorCopy(leverPos), leverState);
    }
}
