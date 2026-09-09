package RailOptimization.gametest.mixin;

import RailOptimization.RailLogicTestAccess;
import RailOptimization.RailSupportCache;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = RailSupportCache.class, remap = false)
public abstract class RailSupportCacheGameTestMixin {
	@WrapMethod(method = "shouldBeRemoved")
	private static boolean railoptimization$applyPositionMode(BlockPos pos, Level level, RailShape shape, Operation<Boolean> original) {
		if (RailLogicTestAccess.positionMode(pos) == RailLogicTestAccess.MODE_VANILLA) {
			return BaseRailBlockGameTestInvoker.railoptimization$shouldBeRemoved(pos, level, shape);
		}
		return original.call(pos, level, shape);
	}
}
