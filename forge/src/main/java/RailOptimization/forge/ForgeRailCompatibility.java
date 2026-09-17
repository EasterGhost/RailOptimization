package RailOptimization.forge;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class ForgeRailCompatibility {
	private ForgeRailCompatibility() {
	}

	public static boolean isCompatibleRail(BlockState state, PoweredRailBlock source) {
		if (state.is(source)) return true;
		return state.getBlock() instanceof PoweredRailBlock rail
				&& rail.isActivatorRail() == source.isActivatorRail();
	}

	public static Block notificationSource(BlockState state) {
		return state.getBlock();
	}
}
