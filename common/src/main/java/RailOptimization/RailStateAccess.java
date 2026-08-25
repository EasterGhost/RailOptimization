package RailOptimization;

public interface RailStateAccess {
	int SHAPE_MASK = 0x0F;
	int POWERED_MASK = 1 << 4;
	int INITIALIZED_MASK = 1 << 5;

	int railoptimization$getRailData();
}
