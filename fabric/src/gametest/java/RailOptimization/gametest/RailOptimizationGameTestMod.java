package RailOptimization.gametest;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.redstone.Orientation;

public class RailOptimizationGameTestMod implements ModInitializer {
    private static final ResourceLocation NEIGHBOR_COUNTER_ID = ResourceLocation.fromNamespaceAndPath(
            "railoptimization-gametest", "neighbor_counter"
    );
    private static final ResourceKey<Block> NEIGHBOR_COUNTER_KEY = ResourceKey.create(
            Registries.BLOCK, NEIGHBOR_COUNTER_ID
    );

    public static final NeighborCounterBlock NEIGHBOR_COUNTER = new NeighborCounterBlock(
            BlockBehaviour.Properties.of().setId(NEIGHBOR_COUNTER_KEY).strength(1.0F).noLootTable()
    );

    @Override
    public void onInitialize() {
        Registry.register(
                BuiltInRegistries.BLOCK,
                NEIGHBOR_COUNTER_ID,
                NEIGHBOR_COUNTER
        );
    }

    public static class NeighborCounterBlock extends Block {
        public static final IntegerProperty COUNT = IntegerProperty.create("count", 0, 15);

        public NeighborCounterBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(COUNT, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(COUNT);
        }

        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                       Orientation orientation, boolean movedByPiston) {
            if (level.isClientSide) return;
            int count = state.getValue(COUNT);
            if (count < 15) {
                level.setBlock(pos, state.setValue(COUNT, count + 1), UPDATE_CLIENTS);
            }
        }
    }
}
