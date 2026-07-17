package RailOptimization;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.Level;

final class RailUpdateContext {
    private enum SearchPhase {
        READ_ONLY,
        POWERING,
        DEPOWERING
    }

    final RailSearchCache searchCache;
    final MutableBlockPos railCursor = new MutableBlockPos();
    final MutableBlockPos scratchPos = new MutableBlockPos();
    private SearchPhase searchPhase = SearchPhase.READ_ONLY;

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

    byte getSearchResult(long position, byte flags, int distance) {
        return searchCache.get(position, flags, distance);
    }

    void cacheSearchResult(long position, byte flags, int distance, boolean powered) {
        if ((searchPhase == SearchPhase.POWERING && !powered) ||
                (searchPhase == SearchPhase.DEPOWERING && powered)) {
            return;
        }

        searchCache.put(position, flags, distance,
                powered ? RailLogic.CHECKED_POWERED : RailLogic.CHECKED_BLOCKED);
    }

    // Rail state changes are monotonic within a batch, so only matching results remain valid.
    void beginPowering() {
        searchCache.retainSearchResults(RailLogic.CHECKED_POWERED);
        searchPhase = SearchPhase.POWERING;
    }

    void beginDepowering() {
        searchCache.retainSearchResults(RailLogic.CHECKED_BLOCKED);
        searchPhase = SearchPhase.DEPOWERING;
    }
}
