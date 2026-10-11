package RailOptimization.mixin;

import RailOptimization.RailNeighborBatchAccess;
import RailOptimization.RailNotificationBatch;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(CollectingNeighborUpdater.class)
public abstract class RailCollectingNeighborUpdaterMixin implements RailNeighborBatchAccess {
	@Shadow
	private int count;
	@Shadow
	@Final
	private int maxChainedNeighborUpdates;
	@Shadow
	private @Nullable Consumer<BlockPos> debugListener;

	@Shadow
	private void addAndRun(BlockPos pos, CollectingNeighborUpdater.NeighborUpdates update) {
		throw new AssertionError();
	}

	@Override
	public boolean railoptimization$canBatch(int taskBound) {
		return count > 0 && debugListener == null
				&& (maxChainedNeighborUpdates < 0 || taskBound <= maxChainedNeighborUpdates - count);
	}

	@Override
	public void railoptimization$enqueueBatch(RailNotificationBatch batch) {
		// Reserve the original queue cost even though the batch occupies one task.
		count += batch.taskCost() - 1;
		addAndRun(batch.firstPosition(), batch);
	}
}
