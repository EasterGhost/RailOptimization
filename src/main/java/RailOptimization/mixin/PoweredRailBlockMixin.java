package RailOptimization.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;

import RailOptimization.RailLogic;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = PoweredRailBlock.class, priority = 990)
public abstract class PoweredRailBlockMixin {

	@WrapMethod(method = "updateState")
	private void railoptimization$updateState(
			BlockState state, Level level, BlockPos pos, Block block, Operation<Void> original) {
		if (!RailLogic.tryCustomUpdateState((PoweredRailBlock) (Object) this, state, level, pos)) {
			original.call(state, level, pos, block);
		}
	}
}
