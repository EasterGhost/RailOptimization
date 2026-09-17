package RailOptimization;

import RailOptimization.config.RailOptimizationConfigManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod("railoptimization")
public final class RailOptimizationForge {
	public RailOptimizationForge() {
		RailOptimizationConfigManager.initialize(FMLPaths.CONFIGDIR.get());
		MinecraftForge.EVENT_BUS.register(this);
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
