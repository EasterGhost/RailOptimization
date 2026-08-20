package RailOptimization.mixin;

import RailOptimization.RailUpdateMemo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LevelChunk.class, priority = 990)
public abstract class LevelChunkMixin {

    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void railoptimization$setBlockState(
            BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
        RailUpdateMemo.onBlockStateChanged();
    }
}
