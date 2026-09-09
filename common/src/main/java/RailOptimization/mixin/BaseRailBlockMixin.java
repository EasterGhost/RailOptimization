package RailOptimization.mixin;

import RailOptimization.RailLogic;
import RailOptimization.RailSupportCache;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BaseRailBlock.class)
public abstract class BaseRailBlockMixin {
	@WrapOperation(method = "neighborChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/BaseRailBlock;shouldBeRemoved(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/properties/RailShape;)Z"))
	private boolean railoptimization$reuseSupportCheck(BlockPos pos, Level level, RailShape shape, Operation<Boolean> original) {
		if (RailLogic.isOptimizationEnabled() && ((Object) this == Blocks.POWERED_RAIL || (Object) this == Blocks.ACTIVATOR_RAIL)) {
			return RailSupportCache.shouldBeRemoved(pos, level, shape);
		}
		return original.call(pos, level, shape);
	}
}
