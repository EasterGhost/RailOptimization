package RailOptimization;

import RailOptimization.config.RailOptimizationConfigManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

public class RailOptimizationFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		RailOptimizationConfigManager.initialize(FabricLoader.getInstance().getConfigDir());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> RailOptimizationConfigManager.flushWrites());
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> RailOptimizationCommands.register(dispatcher));
	}
}
