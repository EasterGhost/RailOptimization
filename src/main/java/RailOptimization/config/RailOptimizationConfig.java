package RailOptimization.config;

public record RailOptimizationConfig(boolean enabled, int powerLimit) {
	public static final boolean DEFAULT_ENABLED = true;
	public static final int DEFAULT_POWER_LIMIT = 8;
	public static final int MIN_POWER_LIMIT = 1;
	public static final int MAX_POWER_LIMIT = 64;

	public RailOptimizationConfig {
		powerLimit = normalizePowerLimit(powerLimit);
	}

	public static RailOptimizationConfig defaults() {
		return new RailOptimizationConfig(DEFAULT_ENABLED, DEFAULT_POWER_LIMIT);
	}

	public static int normalizePowerLimit(int powerLimit) {
		return Math.clamp(powerLimit, MIN_POWER_LIMIT, MAX_POWER_LIMIT);
	}
}
