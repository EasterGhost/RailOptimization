package RailOptimization;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.Level;

final class RailUpdateContext {
    final RailSearchCache searchCache;
    final MutableBlockPos railCursor = new MutableBlockPos();
    final MutableBlockPos scratchPos = new MutableBlockPos();
    final MutableBlockPos sourcePos = new MutableBlockPos();
    private boolean cachePoweredSearchResults = true;

    RailUpdateContext(int railPowerLimit) {
        searchCache = new RailSearchCache(railPowerLimit);
    }

    boolean hasNeighborSignal(Level level, BlockPos pos) {
        long position = pos.asLong();
        byte cached = searchCache.get(position, RailSearchCache.DIRECT_SIGNAL);
        if (cached != RailLogic.CHECKED_UNKNOWN) {
            return cached == RailLogic.CHECKED_POWERED;
        }

        boolean powered = level.hasNeighborSignal(pos);
        searchCache.put(position, RailSearchCache.DIRECT_SIGNAL,
                powered ? RailLogic.CHECKED_POWERED : RailLogic.CHECKED_BLOCKED);
        return powered;
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
