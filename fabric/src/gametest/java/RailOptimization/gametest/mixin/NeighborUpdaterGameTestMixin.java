package RailOptimization.gametest.mixin;

import RailOptimization.gametest.RailNeighborUpdateTrace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NeighborUpdater.class)
public interface NeighborUpdaterGameTestMixin {
	@Inject(method = "executeUpdate", at = @At("HEAD"))
	private static void railoptimization$traceDeliveredUpdate(Level level, BlockState state, BlockPos pos,
			Block sourceBlock, Orientation orientation, boolean movedByPiston, CallbackInfo ci) {
		RailNeighborUpdateTrace.record(level, pos, sourceBlock);
	}
}
