package RailOptimization.mixin;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import RailOptimization.LevelEpochAccess;

@Mixin(Level.class)
public abstract class LevelMixin implements LevelEpochAccess {
	@Unique
	private final AtomicLong railoptimization$blockChangeEpoch = new AtomicLong();

	@Override
	public AtomicLong railoptimization$getBlockChangeEpoch() {
		return railoptimization$blockChangeEpoch;
	}
}
