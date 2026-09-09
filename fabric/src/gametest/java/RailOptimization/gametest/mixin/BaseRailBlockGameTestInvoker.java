package RailOptimization.gametest.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BaseRailBlock.class)
public interface BaseRailBlockGameTestInvoker {
	@Invoker("shouldBeRemoved")
	static boolean railoptimization$shouldBeRemoved(BlockPos pos, Level level, RailShape shape) {
		throw new AssertionError();
	}
}
