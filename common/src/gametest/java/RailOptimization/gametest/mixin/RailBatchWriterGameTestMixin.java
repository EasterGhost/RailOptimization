package RailOptimization.gametest.mixin;

import RailOptimization.gametest.batch.RailBatchWriteAudit;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "RailOptimization.RailBatchWriter")
public abstract class RailBatchWriterGameTestMixin {
	@Shadow
	@Final
	private int[] next;
	@Unique
	private long railoptimization$writesBefore;

	@Inject(method = "write", at = @At("HEAD"))
	private void railoptimization$beginBatch(CallbackInfo ci) {
		railoptimization$writesBefore = RailBatchWriteAudit.writes;
	}

	@Inject(method = "write", at = @At("RETURN"))
	private void railoptimization$endBatch(CallbackInfo ci) {
		if (RailBatchWriteAudit.enabled && RailBatchWriteAudit.writes - railoptimization$writesBefore > 1) {
			++RailBatchWriteAudit.multiRailBatches;
		}
	}

	@Inject(method = "writeSection", at = @At("HEAD"))
	private void railoptimization$recordPaletteWrite(LevelChunkSection section, int firstIndex, CallbackInfo ci) {
		if (RailBatchWriteAudit.enabled) {
			++RailBatchWriteAudit.sectionGroups;
			for (int index = firstIndex; index >= 0; index = next[index]) {
				++RailBatchWriteAudit.writes;
			}
		}
	}

	@Inject(method = "writeFallback", at = @At("HEAD"))
	private void railoptimization$recordFallback(CallbackInfo ci) {
		if (RailBatchWriteAudit.enabled) {
			++RailBatchWriteAudit.fallbackBatches;
		}
	}
}
