package RailOptimization;

public interface RailPaletteAccess<T> {

	void railoptimization$setBatch(long[] positions, T[] targets, int[] next, int first, int[] paletteIds);
}
