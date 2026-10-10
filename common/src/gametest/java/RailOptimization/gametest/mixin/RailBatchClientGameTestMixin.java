package RailOptimization.gametest.mixin;

import RailOptimization.gametest.batch.RailBatchWriteAudit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class RailBatchClientGameTestMixin {
	@Inject(method = "sendBlockUpdated", at = @At("HEAD"))
	private void railoptimization$recordClientUpdate(BlockPos pos, BlockState previous, BlockState target, int flags, CallbackInfo ci) {
		if (RailBatchWriteAudit.enabled && (target.is(Blocks.POWERED_RAIL) || target.is(Blocks.ACTIVATOR_RAIL))) {
			++RailBatchWriteAudit.clientUpdates;
		}
	}
}
