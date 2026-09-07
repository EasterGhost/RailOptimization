package RailOptimization.neoforge;

import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class NeoForgeRailCompatibility {
	private NeoForgeRailCompatibility() {
	}

	public static boolean isCompatibleRail(BlockState state, PoweredRailBlock source) {
		if (state.is(source)) return true;
		return state.getBlock() instanceof PoweredRailBlock rail
				&& rail.isActivatorRail() == source.isActivatorRail();
	}
}
