package RailOptimization.neoforge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "RailOptimization.RailBatchWriter")
public abstract class RailBatchWriterMixin {
	@ModifyExpressionValue(method = "write", at = @At(value = "INVOKE", target = "LRailOptimization/RailLithiumCompatibility;supportsBatchWrites()Z"))
	private boolean railoptimization$allowBatch(boolean supported, @Local(argsOnly = true) Level level) {
		return supported && !level.captureBlockSnapshots;
	}

	@WrapOperation(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;updatePOIOnBlockStateChange(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V"))
	private void railoptimization$notifyStateChange(Level level, BlockPos pos, BlockState previous, BlockState target, Operation<Void> original) {
		original.call(level, pos, previous, target);
		target.onBlockStateChange(level, pos.immutable(), previous);
	}
}
