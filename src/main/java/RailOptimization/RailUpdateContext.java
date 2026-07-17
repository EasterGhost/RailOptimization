package RailOptimization;

import net.minecraft.core.BlockPos.MutableBlockPos;

final class RailUpdateContext {
    final RailSearchCache searchCache;
    final MutableBlockPos railCursor = new MutableBlockPos();
    final MutableBlockPos scratchPos = new MutableBlockPos();

    RailUpdateContext(int railPowerLimit) {
        searchCache = new RailSearchCache(railPowerLimit);
    }
}
