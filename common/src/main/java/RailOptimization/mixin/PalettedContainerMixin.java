package RailOptimization.mixin;

import RailOptimization.RailPaletteAccess;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.PaletteResize;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PalettedContainer.class)
public abstract class PalettedContainerMixin<T> implements RailPaletteAccess<T>, PaletteResize<T> {
	@Shadow
	private volatile PalettedContainer.Data<T> data;

	@Shadow
	public abstract void acquire();

	@Shadow
	public abstract void release();

	@Override
	public void railoptimization$setBatch(long[] positions, T[] targets, int[] next, int first, int[] paletteIds) {
		acquire();
		try {
			PalettedContainer.Data<T> initial;
			do {
				initial = data;
				@SuppressWarnings("null")
				T previousTarget = null;
				int id = 0;
				for (int index = first; index >= 0; index = next[index]) {
					T target = targets[index];
					if (target != previousTarget) {
						id = data.palette().idFor(target, this);
						previousTarget = target;
					}
					paletteIds[index] = id;
				}
			} while (data != initial);

			BitStorage storage = data.storage();
			for (int index = first; index >= 0; index = next[index]) {
				long position = positions[index];
				int cell = ((int) (position & 15) << 8) | ((int) ((position >>> 12) & 15) << 4) | (int) ((position >>> 38) & 15);
				storage.set(cell, paletteIds[index]);
			}
		} finally {
			release();
		}
	}
}
