package RailOptimization.gametest;

import RailOptimization.RailLogicTestAccess;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.redstone.Orientation;

public class RailOptimizationGameTestMod implements ModInitializer {
    private static final Identifier NEIGHBOR_COUNTER_ID = Identifier.fromNamespaceAndPath(
            "railoptimization-gametest", "neighbor_counter");
    private static final Identifier CASCADING_NEIGHBOR_COUNTER_ID = Identifier.fromNamespaceAndPath(
            "railoptimization-gametest", "cascading_neighbor_counter");
    private static final Identifier ORDER_RECORDER_ID = Identifier.fromNamespaceAndPath(
            "railoptimization-gametest", "order_recorder");
    @SuppressWarnings("null")
    private static final ResourceKey<Block> NEIGHBOR_COUNTER_KEY = ResourceKey.create(
            Registries.BLOCK, NEIGHBOR_COUNTER_ID);
    @SuppressWarnings("null")
    private static final ResourceKey<Block> CASCADING_NEIGHBOR_COUNTER_KEY = ResourceKey.create(
            Registries.BLOCK, CASCADING_NEIGHBOR_COUNTER_ID);
    @SuppressWarnings("null")
    private static final ResourceKey<Block> ORDER_RECORDER_KEY = ResourceKey.create(
            Registries.BLOCK, ORDER_RECORDER_ID);

    private static final Map<Long, OrderProbeRecord> ORDER_PROBES = new HashMap<>();
    private static int orderSequence;

    @SuppressWarnings("null")
    public static final NeighborCounterBlock NEIGHBOR_COUNTER = new NeighborCounterBlock(
            BlockBehaviour.Properties.of().setId(NEIGHBOR_COUNTER_KEY).strength(1.0F).noLootTable());
    @SuppressWarnings("null")
    public static final CascadingNeighborCounterBlock CASCADING_NEIGHBOR_COUNTER = new CascadingNeighborCounterBlock(
            BlockBehaviour.Properties.of().setId(CASCADING_NEIGHBOR_COUNTER_KEY).strength(1.0F).noLootTable());
    @SuppressWarnings("null")
    public static final OrderRecorderBlock ORDER_RECORDER = new OrderRecorderBlock(
            BlockBehaviour.Properties.of().setId(ORDER_RECORDER_KEY).strength(1.0F).noLootTable());

    @SuppressWarnings("null")
    @Override
    public void onInitialize() {
        RailLogicTestAccess.enablePositionBasedTestMode();

        Registry.register(
                BuiltInRegistries.BLOCK,
                NEIGHBOR_COUNTER_ID,
                NEIGHBOR_COUNTER);
        Registry.register(
                BuiltInRegistries.BLOCK,
                CASCADING_NEIGHBOR_COUNTER_ID,
                CASCADING_NEIGHBOR_COUNTER);
        Registry.register(
                BuiltInRegistries.BLOCK,
                ORDER_RECORDER_ID,
                ORDER_RECORDER);
    }

    public static void registerOrderProbe(BlockPos probePos, BlockPos[] watchedRails) {
        ORDER_PROBES.put(probePos.asLong(), new OrderProbeRecord(watchedRails));
    }

    public static void resetOrderProbe(BlockPos probePos) {
        OrderProbeRecord record = ORDER_PROBES.get(probePos.asLong());
        if (record != null) {
            record.order = 0;
            record.snapshot = 0;
        }
    }

    public static OrderProbeSnapshot orderProbeSnapshot(BlockPos probePos) {
        OrderProbeRecord record = ORDER_PROBES.get(probePos.asLong());
        if (record == null) {
            return new OrderProbeSnapshot(0, 0);
        }
        return new OrderProbeSnapshot(record.order, record.snapshot);
    }

    public static class NeighborCounterBlock extends Block {
        public static final IntegerProperty COUNT = IntegerProperty.create("count", 0, 15);

        @SuppressWarnings("null")
        public NeighborCounterBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(COUNT, 0));
        }

        @Override
        protected void createBlockStateDefinition(
                @SuppressWarnings("null") StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(COUNT);
        }

        @SuppressWarnings("null")
        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                Orientation orientation, boolean movedByPiston) {
            if (level.isClientSide())
                return;
            int count = state.getValue(COUNT);
            if (count < 15) {
                level.setBlock(pos, state.setValue(COUNT, count + 1), UPDATE_CLIENTS);
            }
        }
    }

    public static class CascadingNeighborCounterBlock extends NeighborCounterBlock {
        @SuppressWarnings("null")
        public CascadingNeighborCounterBlock(Properties properties) {
            super(properties);
        }

        @SuppressWarnings("null")
        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                Orientation orientation, boolean movedByPiston) {
            if (level.isClientSide()) {
                return;
            }

            int count = state.getValue(COUNT);
            if (count < 15) {
                level.setBlock(pos, state.setValue(COUNT, count + 1), UPDATE_ALL);
            }
        }
    }

    public static class OrderRecorderBlock extends Block {
        @SuppressWarnings("null")
        public OrderRecorderBlock(Properties properties) {
            super(properties);
        }

        @SuppressWarnings("null")
        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                Orientation orientation, boolean movedByPiston) {
            if (level.isClientSide()) {
                return;
            }

            OrderProbeRecord record = ORDER_PROBES.get(pos.asLong());
            if (record == null || record.order != 0) {
                return;
            }

            int snapshot = 0;
            for (int railIndex = 0; railIndex < record.watchedRails.length; railIndex++) {
                BlockState railState = level.getBlockState(record.watchedRails[railIndex]);
                if (railState.hasProperty(PoweredRailBlock.POWERED)
                        && railState.getValue(PoweredRailBlock.POWERED)) {
                    snapshot |= 1 << railIndex;
                }
            }

            record.snapshot = snapshot;
            record.order = ++orderSequence;
        }
    }

    public record OrderProbeSnapshot(int order, int snapshot) {
    }

    private static final class OrderProbeRecord {
        private final BlockPos[] watchedRails;
        private int order;
        private int snapshot;

        private OrderProbeRecord(BlockPos[] watchedRails) {
            this.watchedRails = watchedRails;
        }
    }
}
