package RailOptimization;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

final class RailUpdateContext {
    final RailSearchCache searchCache;
    final MutableBlockPos railCursor = new MutableBlockPos();
    final MutableBlockPos scratchPos = new MutableBlockPos();
    final BlockState[] straightRailStates;
    final int railPowerLimit;
    private boolean cachePoweredSearchResults = true;
    private LevelChunk chunk;
    private int chunkX;
    private int chunkZ;

    RailUpdateContext(int railPowerLimit) {
        this.railPowerLimit = railPowerLimit;
        searchCache = new RailSearchCache(railPowerLimit);
        straightRailStates = new BlockState[railPowerLimit];
    }

    void reset() {
        searchCache.clear();
        cachePoweredSearchResults = true;
        chunk = null;
    }

    boolean hasNeighborSignal(Level level, BlockPos pos) {
        long position = pos.asLong();
        byte cached = searchCache.get(position, RailSearchCache.DIRECT_SIGNAL);
        if (cached != RailLogic.CHECKED_UNKNOWN) {
            return cached == RailLogic.CHECKED_POWERED;
        }

        int posChunkX = pos.getX() >> 4;
        int posChunkZ = pos.getZ() >> 4;
        boolean powered = RailSignalSearcher.hasNeighborSignalFast(
                level, pos, scratchPos, getChunk(level, pos), posChunkX, posChunkZ);
        searchCache.put(position, RailSearchCache.DIRECT_SIGNAL,
                powered ? RailLogic.CHECKED_POWERED : RailLogic.CHECKED_BLOCKED);
        return powered;
    }

    @SuppressWarnings("null")
    BlockState getBlockState(Level level, BlockPos pos) {
        if (!level.isInValidBounds(pos)) {
            return level.getBlockState(pos);
        }

        return getChunk(level, pos).getBlockState(pos);
    }

    private LevelChunk getChunk(Level level, BlockPos pos) {
        int nextChunkX = pos.getX() >> 4;
        int nextChunkZ = pos.getZ() >> 4;
        if (chunk == null || nextChunkX != chunkX || nextChunkZ != chunkZ) {
            chunk = level.getChunk(nextChunkX, nextChunkZ);
            chunkX = nextChunkX;
            chunkZ = nextChunkZ;
        }
        return chunk;
    }

    int getPoweredSearchCost(long position, byte flags) {
        return searchCache.getPoweredSearchCost(position, flags);
    }

    void cachePoweredSearchCost(long position, byte flags, int searchCost) {
        if (cachePoweredSearchResults) {
            searchCache.putPoweredSearchCost(position, flags, searchCost);
        }
    }

    void beginPowering() {
        // Successful paths remain valid while rails only transition to powered.
        cachePoweredSearchResults = true;
    }

    void beginDepowering() {
        searchCache.removeSearchResults();
        cachePoweredSearchResults = false;
    }
}
