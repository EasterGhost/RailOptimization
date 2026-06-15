package RailOptimization.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;

import static RailOptimization.RailLogic.customUpdateState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import RailOptimization.PoweredRailBlockInvoker;

@Mixin(value = PoweredRailBlock.class, priority = 990)
public abstract class PoweredRailBlockMixin implements PoweredRailBlockInvoker {

    @Shadow
    protected boolean findPoweredRailSignal(Level level, BlockPos pos, BlockState state, boolean forward, int distance) {
        throw new UnsupportedOperationException();
    }

    @Inject(method = "updateState", at = @At("HEAD"), cancellable = true)
    private void railoptimization$updateState(BlockState state, Level level, BlockPos pos, Block block, CallbackInfo ci) {
        customUpdateState((PoweredRailBlock)(Object)this, state, level, pos);
        ci.cancel();
    }

    @Override
    public boolean invokeFindPoweredRailSignal(Level level, BlockPos pos, BlockState state, boolean forward, int distance) {
        return this.findPoweredRailSignal(level, pos, state, forward, distance);
    }
}
