package RailOptimization.gametest.mixin;

import RailOptimization.RailLogic;
import RailOptimization.RailLogicTestAccess;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = RailLogic.class, remap = false)
public abstract class RailLogicGameTestMixin {
	@WrapMethod(method = "setOptimizationEnabled")
	private static void railoptimization$preservePositionBasedMode(boolean enabled, Operation<Void> original) {
		if (!RailLogicTestAccess.isPositionBasedTestModeEnabled()) {
			original.call(enabled);
		}
	}

	@WrapMethod(method = "tryCustomUpdateState")
	private static boolean railoptimization$applyPositionMode(PoweredRailBlock self, BlockState state, Level level,
			BlockPos pos, Block sourceBlock, Operation<Boolean> original) {
		int testMode = RailLogicTestAccess.positionMode(pos);
		if (testMode == RailLogicTestAccess.MODE_VANILLA) {
			return false;
		}
		if (testMode == RailLogicTestAccess.MODE_OPTIMIZED) {
			return original.call(self, state, level, pos, sourceBlock);
		}

		int configuredPowerLimit = RailLogicTestAccess.currentPowerLimit();
		RailLogic.setRailPowerLimit(testMode);
		try {
			return original.call(self, state, level, pos, sourceBlock);
		} finally {
			RailLogic.setRailPowerLimit(configuredPowerLimit);
		}
	}
}
