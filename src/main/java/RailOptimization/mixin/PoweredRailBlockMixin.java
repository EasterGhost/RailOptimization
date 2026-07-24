package RailOptimization.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;

import RailOptimization.RailLogic;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PoweredRailBlock.class, priority = 990)
public abstract class PoweredRailBlockMixin {

    @Inject(method = "updateState", at = @At("HEAD"), cancellable = true)
    private void railoptimization$updateState(
            BlockState state, Level level, BlockPos pos, Block block, CallbackInfo ci) {
        if (RailLogic.tryCustomUpdateState((PoweredRailBlock) (Object) this, state, level, pos)) {
            ci.cancel();
        }
    }
}
