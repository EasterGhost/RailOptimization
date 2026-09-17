package RailOptimization;

import RailOptimization.config.RailOptimizationConfigManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(RailOptimizationNeoForge.MOD_ID)
public final class RailOptimizationNeoForge {
	public static final String MOD_ID = "railoptimization";

	public RailOptimizationNeoForge() {
		RailOptimizationConfigManager.initialize(FMLPaths.CONFIGDIR.get());
		NeoForge.EVENT_BUS.register(this);
	}

	@SubscribeEvent
	public void registerCommands(RegisterCommandsEvent event) {
		RailOptimizationCommands.register(event.getDispatcher());
	}

	@SubscribeEvent
	public void onServerStopping(ServerStoppingEvent event) {
		RailOptimizationConfigManager.flushWrites();
	}
}
