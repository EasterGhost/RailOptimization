package RailOptimization;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.slf4j.LoggerFactory;

final class RailLithiumCompatibility {
	private static final Direction[] DIRECTIONS = Direction.values();
	private static final Adapter ADAPTER = resolve();

	private record Adapter(boolean supported, MethodHandle sectionData, MethodHandle listener,
			MethodHandle onChange, Class<?> updateReceiver, MethodHandle invalidateHopper) {
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

	static void invalidateAdjacentHoppers(Level level, LevelChunk chunk, BlockPos pos, BlockPos.MutableBlockPos scratch) {
		if (ADAPTER.invalidateHopper == null) {
			return;
		}
		boolean sameChunk = (pos.getX() & 15) > 0 && (pos.getX() & 15) < 15 && (pos.getZ() & 15) > 0 && (pos.getZ() & 15) < 15;
		if (sameChunk && chunk.getBlockEntities().isEmpty()) {
			return;
		}
		for (Direction direction : DIRECTIONS) {
			scratch.setWithOffset(pos, direction);
			LevelChunk neighborChunk = chunk;
			if ((scratch.getX() >> 4) != chunk.getPos().x() || (scratch.getZ() >> 4) != chunk.getPos().z()) {
				neighborChunk = ((ServerChunkCache) level.getChunkSource()).getChunkNow(scratch.getX() >> 4, scratch.getZ() >> 4);
			}
			if (neighborChunk == null) {
				continue;
			}
			BlockEntity entity = neighborChunk.getBlockEntities().get(scratch);
			if (ADAPTER.updateReceiver.isInstance(entity)) {
				try {
					ADAPTER.invalidateHopper.invokeExact((Object) entity, direction == Direction.DOWN);
				} catch (Throwable failure) {
					throw propagate(failure);
				}
			}
		}
	}

	private static Adapter resolve() {
		ClassLoader loader = RailLithiumCompatibility.class.getClassLoader();
		if (loader.getResource("net/caffeinemc/mods/lithium/common/block/BlockCountingSection.class") == null) {
			return new Adapter(true, null, null, null, null, null);
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
			Class<?> receiver = Class.forName("net.caffeinemc.mods.lithium.common.hopper.UpdateReceiver", false, loader);
			MethodHandle invalidate = receiver.isAssignableFrom(HopperBlockEntity.class)
					? lookup.findVirtual(receiver, "lithium$invalidateCacheOnNeighborUpdate", MethodType.methodType(void.class, boolean.class))
							.asType(MethodType.methodType(void.class, Object.class, boolean.class))
					: null;
			return new Adapter(true, sectionData, listener, onChange, receiver, invalidate);
		} catch (ReflectiveOperationException | LinkageError failure) {
			LoggerFactory.getLogger(RailLithiumCompatibility.class).warn("Unsupported Lithium write hooks; using normal rail writes", failure);
			return new Adapter(false, null, null, null, null, null);
		}
	}

	private static RuntimeException propagate(Throwable failure) {
		if (failure instanceof Error error) {
			throw error;
		}
		return failure instanceof RuntimeException runtime ? runtime : new IllegalStateException("Lithium rail write notification failed", failure);
	}
}
