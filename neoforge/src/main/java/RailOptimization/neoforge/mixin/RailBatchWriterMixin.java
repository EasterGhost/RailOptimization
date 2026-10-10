package RailOptimization.neoforge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "RailOptimization.RailBatchWriter")
public abstract class RailBatchWriterMixin {
	@Shadow
	@Final
	private MutableBlockPos cursor;

	@ModifyExpressionValue(method = "write", at = @At(value = "INVOKE", target = "LRailOptimization/RailLithiumCompatibility;supportsBatchWrites()Z"))
	private boolean railoptimization$allowBatch(boolean supported, @Local(argsOnly = true) Level level) {
		return supported && !level.captureBlockSnapshots;
	}

	@Inject(method = "write", at = @At(value = "INVOKE", target = "LRailOptimization/RailUpdateMemo;confirm(Lnet/minecraft/core/BlockPos;ZI)V"))
	private void railoptimization$notifyStateChange(CallbackInfo ci, @Local(argsOnly = true) Level level,
			@Local(name = "previous") BlockState previous, @Local(name = "target") BlockState target) {
		target.onBlockStateChange(level, cursor.immutable(), previous);
	}
}
