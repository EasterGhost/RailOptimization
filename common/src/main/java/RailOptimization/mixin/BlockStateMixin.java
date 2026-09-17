package RailOptimization.mixin;

import RailOptimization.RailStateAccess;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockState.class)
public abstract class BlockStateMixin implements RailStateAccess {
	@Unique
	private byte railoptimization$railData;

	@Override
	public int railoptimization$getRailData() {
		int data = railoptimization$railData;
		if (data == 0) {
			BlockState state = (BlockState) (Object) this;
			RailShape shape = state.getValue(PoweredRailBlock.SHAPE);
			data = INITIALIZED_MASK | shape.ordinal();
			if (state.getValue(PoweredRailBlock.POWERED)) {
				data |= POWERED_MASK;
			}
			railoptimization$railData = (byte) data;
		}
		return data;
	}
}
