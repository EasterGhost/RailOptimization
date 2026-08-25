package RailOptimization.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import RailOptimization.RailLogic;
import net.fabricmc.loader.api.FabricLoader;

public final class RailOptimizationConfigManager {
	private static final String CONFIG_FILE_NAME = "railoptimization.json";
	private static final Logger LOGGER = LoggerFactory.getLogger("RailOptimization");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Object STATE_LOCK = new Object();
	private static final AtomicLong REVISION = new AtomicLong();
	private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "RailOptimization Config I/O");
		thread.setDaemon(true);
		return thread;
	});

	private static volatile RailOptimizationConfig currentConfig = RailOptimizationConfig.defaults();

	private RailOptimizationConfigManager() {
	}

	public static void initialize() {
		Path configPath = getConfigPath();
		RailOptimizationConfig config = RailOptimizationConfig.defaults();
		boolean configExists = Files.exists(configPath);
		if (configExists) {
			try {
				config = readConfig(configPath);
			} catch (IOException | JsonParseException exception) {
				LOGGER.error("Failed to load RailOptimization config from {}; using defaults", configPath, exception);
			}
		}

		synchronized (STATE_LOCK) {
			applyConfig(config);
			REVISION.incrementAndGet();
		}

		if (!configExists) {
			saveAsync(config);
		}
	}

	public static CompletableFuture<Void> setEnabled(boolean enabled) {
		synchronized (STATE_LOCK) {
			RailOptimizationConfig config = new RailOptimizationConfig(enabled, currentConfig.powerLimit());
			applyConfig(config);
			REVISION.incrementAndGet();
			return saveAsync(config);
		}
	}

	public static CompletableFuture<Void> setPowerLimit(int powerLimit) {
		synchronized (STATE_LOCK) {
			RailOptimizationConfig config = new RailOptimizationConfig(currentConfig.enabled(), powerLimit);
			applyConfig(config);
			REVISION.incrementAndGet();
			return saveAsync(config);
		}
	}

	@SuppressWarnings("null")
	public static CompletableFuture<ReloadResult> reloadAsync() {
		long expectedRevision = REVISION.get();
		return CompletableFuture.supplyAsync(() -> {
			Path configPath = getConfigPath();
			try {
				RailOptimizationConfig config;
				if (Files.exists(configPath)) {
					config = readConfig(configPath);
				} else {
					config = RailOptimizationConfig.defaults();
					writeConfig(configPath, config);
				}
				return new ReloadResult(config, expectedRevision);
			} catch (IOException | JsonParseException exception) {
				throw new CompletionException(exception);
			}
		}, IO_EXECUTOR);
	}

	public static boolean applyReloaded(ReloadResult result) {
		synchronized (STATE_LOCK) {
			if (REVISION.get() != result.expectedRevision()) {
				return false;
			}
			applyConfig(result.config());
			REVISION.incrementAndGet();
			return true;
		}
	}

	public static void flushWrites() {
		try {
			IO_EXECUTOR.submit(() -> {
			}).get();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			LOGGER.error("Interrupted while waiting for RailOptimization config writes", exception);
		} catch (ExecutionException exception) {
			LOGGER.error("Failed while waiting for RailOptimization config writes", exception.getCause());
		}
	}

	private static void applyConfig(RailOptimizationConfig config) {
		currentConfig = config;
		RailLogic.setRailPowerLimit(config.powerLimit());
		RailLogic.setOptimizationEnabled(config.enabled());
	}

	private static CompletableFuture<Void> saveAsync(RailOptimizationConfig config) {
		Path configPath = getConfigPath();
		return CompletableFuture.runAsync(() -> {
			try {
				writeConfig(configPath, config);
			} catch (IOException exception) {
				throw new CompletionException(exception);
			}
		}, IO_EXECUTOR).whenComplete((ignored, throwable) -> {
			if (throwable != null) {
				LOGGER.error("Failed to save RailOptimization config to {}", configPath, throwable);
			}
		});
	}

	private static RailOptimizationConfig readConfig(Path configPath) throws IOException, JsonParseException {
		try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (root == null || !root.isJsonObject()) {
				throw new JsonParseException("Config root must be a JSON object");
			}

			JsonObject object = root.getAsJsonObject();
			boolean enabled = readBoolean(object, "enabled", RailOptimizationConfig.DEFAULT_ENABLED);
			int powerLimit = readInteger(object, "powerLimit", RailOptimizationConfig.DEFAULT_POWER_LIMIT);
			return new RailOptimizationConfig(enabled, powerLimit);
		}
	}

	private static boolean readBoolean(JsonObject object, String name, boolean defaultValue) {
		JsonElement element = object.get(name);
		if (element == null) {
			return defaultValue;
		}
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			throw new JsonParseException(name + " must be a boolean");
		}
		return element.getAsBoolean();
	}

	private static int readInteger(JsonObject object, String name, int defaultValue) {
		JsonElement element = object.get(name);
		if (element == null) {
			return defaultValue;
		}
		if (!element.isJsonPrimitive()) {
			throw new JsonParseException(name + " must be an integer");
		}
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		if (!primitive.isNumber()) {
			throw new JsonParseException(name + " must be an integer");
		}
		try {
			return new BigDecimal(primitive.getAsString()).intValueExact();
		} catch (ArithmeticException | NumberFormatException exception) {
			throw new JsonParseException(name + " must be an integer", exception);
		}
	}

	private static void writeConfig(Path configPath, RailOptimizationConfig config) throws IOException {
		Path configDirectory = configPath.getParent();
		Files.createDirectories(configDirectory);
		Path temporaryPath = Files.createTempFile(configDirectory, "railoptimization-", ".tmp");
		boolean moved = false;
		try {
			JsonObject object = new JsonObject();
			object.addProperty("enabled", config.enabled());
			object.addProperty("powerLimit", config.powerLimit());
			try (BufferedWriter writer = Files.newBufferedWriter(temporaryPath, StandardCharsets.UTF_8)) {
				GSON.toJson(object, writer);
				writer.write('\n');
			}

			try {
				Files.move(temporaryPath, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporaryPath, configPath, StandardCopyOption.REPLACE_EXISTING);
			}
			moved = true;
		} finally {
			if (!moved) {
				Files.deleteIfExists(temporaryPath);
			}
		}
	}

	private static Path getConfigPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
	}

	public record ReloadResult(RailOptimizationConfig config, long expectedRevision) {
	}
}
