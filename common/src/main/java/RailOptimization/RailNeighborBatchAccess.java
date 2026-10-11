package RailOptimization;

public interface RailNeighborBatchAccess {
	boolean railoptimization$canBatch(int taskBound);

	void railoptimization$enqueueBatch(RailNotificationBatch batch);
}
