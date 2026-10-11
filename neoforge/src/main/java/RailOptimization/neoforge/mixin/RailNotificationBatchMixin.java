package RailOptimization.neoforge.mixin;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "RailOptimization.RailNotificationBatch")
public abstract class RailNotificationBatchMixin {
	@Inject(method = "appendNeighbors", at = @At("HEAD"), cancellable = true)
	private void railoptimization$notifyEvent(Level level, long position, @Nullable Direction skipped, CallbackInfo ci) {
		BlockPos pos = BlockPos.of(position);
		EnumSet<Direction> directions = EnumSet.allOf(Direction.class);
		if (skipped != null) directions.remove(skipped);
		boolean canceled = EventHooks.onNeighborNotify(level, pos, level.getBlockState(pos), directions, false).isCanceled();
		if (skipped != null && canceled) ci.cancel();
	}
}
