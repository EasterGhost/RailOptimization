package RailOptimization;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.slf4j.LoggerFactory;

final class RailLithiumCompatibility {
	private static final Adapter ADAPTER = resolve();

	private record Adapter(boolean supported, MethodHandle sectionData, MethodHandle listener,
			MethodHandle onChange) {
	}

	private RailLithiumCompatibility() {
	}

	static boolean supportsBatchWrites() {
		return ADAPTER.supported;
	}

	static void afterSectionWrite(LevelChunkSection section, BlockPos pos, BlockState previous, BlockState target) {
		try {
			if (ADAPTER.sectionData != null) {
				Object data = ADAPTER.sectionData.invokeExact((Object) section);
				Object listener = ADAPTER.listener.invokeExact(data);
				if (listener != null) {
					ADAPTER.onChange.invokeExact(listener, (Object) section, pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, previous, target);
				}
			}
		} catch (Throwable failure) {
			throw propagate(failure);
		}
	}

	private static Adapter resolve() {
		ClassLoader loader = RailLithiumCompatibility.class.getClassLoader();
		if (loader.getResource("net/caffeinemc/mods/lithium/common/block/BlockCountingSection.class") == null) {
			return new Adapter(true, null, null, null);
		}
		try {
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			Class<?> listening = Class.forName("net.caffeinemc.mods.lithium.common.block.BlockListeningSection", false, loader);
			MethodHandle sectionData = null;
			MethodHandle listener = null;
			MethodHandle onChange = null;
			if (listening.isAssignableFrom(LevelChunkSection.class)) {
				Class<?> section = Class.forName("net.caffeinemc.mods.lithium.common.world.section.LithiumSectionData", false, loader);
				Class<?> data = Class.forName("net.caffeinemc.mods.lithium.common.world.section.LithiumSectionData$SectionData", false, loader);
				Class<?> callback = Class.forName("net.caffeinemc.mods.lithium.common.tracking.block.ChunkSectionChangeCallback", false, loader);
				sectionData = lookup.findVirtual(section, "lithium$getSectionData", MethodType.methodType(data))
						.asType(MethodType.methodType(Object.class, Object.class));
				listener = lookup.findVirtual(data, "getChangeListener", MethodType.methodType(callback))
						.asType(MethodType.methodType(Object.class, Object.class));
				onChange = lookup.findVirtual(callback, "onBlockChange", MethodType.methodType(void.class, listening, int.class, int.class, int.class,
						BlockState.class, BlockState.class)).asType(MethodType.methodType(void.class, Object.class, Object.class, int.class, int.class,
								int.class, BlockState.class, BlockState.class));
			}
			return new Adapter(true, sectionData, listener, onChange);
		} catch (ReflectiveOperationException | LinkageError failure) {
			LoggerFactory.getLogger(RailLithiumCompatibility.class).warn("Unsupported Lithium write hooks; using normal rail writes", failure);
			return new Adapter(false, null, null, null);
		}
	}

	private static RuntimeException propagate(Throwable failure) {
		if (failure instanceof Error error) {
			throw error;
		}
		return failure instanceof RuntimeException runtime ? runtime : new IllegalStateException("Lithium rail write notification failed", failure);
	}
}
