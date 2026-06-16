package RailOptimization.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailOptimizationNeighborUpdateGameTest extends RailOptimizationGameTestSupport {
    @GameTest(maxTicks = 100)
    public void neighborCountersReceiveNorthSouthRailUpdatePositions(GameTestHelper helper) {
        BlockPos start = new BlockPos(3, RAIL_Y, 2);
        BlockPos monitoredRail = start.relative(Direction.SOUTH, 2);
        BlockPos[] counters = new BlockPos[]{
                monitoredRail.west(),
                monitoredRail.east(),
                monitoredRail.above(),
                monitoredRail.below().west(),
                monitoredRail.below().east(),
                monitoredRail.below().below(),
                start.relative(Direction.SOUTH, 4),
                start.relative(Direction.SOUTH, 4).below(),
                start.north(),
                start.north().below()
        };

        placeRailLinePair(helper, start, Direction.SOUTH, 4, RailShape.NORTH_SOUTH);
        placeNeighborCounters(helper, counters);

        compareMirroredAndOptimizedPower(
                helper,
                () -> helper.setBlock(mirrorCopy(start.west()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(start.west(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.SOUTH, 4);
                    assertNeighborCountersUpdated(helper, counters);
                }
        );
    }

    @GameTest(maxTicks = 100)
    public void neighborCountersReceiveEastWestRailUpdatePositions(GameTestHelper helper) {
        BlockPos start = new BlockPos(2, RAIL_Y, 3);
        BlockPos monitoredRail = start.relative(Direction.EAST, 2);
        BlockPos[] counters = new BlockPos[]{
                monitoredRail.north(),
                monitoredRail.south(),
                monitoredRail.above(),
                monitoredRail.below().north(),
                monitoredRail.below().south(),
                monitoredRail.below().below(),
                start.relative(Direction.EAST, 4),
                start.relative(Direction.EAST, 4).below()
        };

        placeRailLinePair(helper, start, Direction.EAST, 4, RailShape.EAST_WEST);
        placeNeighborCounters(helper, counters);

        compareMirroredAndOptimizedPower(
                helper,
                () -> helper.setBlock(mirrorCopy(start.north()), Blocks.REDSTONE_BLOCK),
                () -> helper.setBlock(start.north(), Blocks.REDSTONE_BLOCK),
                () -> {
                    assertMatchingRailLinePower(helper, mirrorCopy(start), start, Direction.EAST, 4);
                    assertNeighborCountersUpdated(helper, counters);
                }
        );
    }
}
