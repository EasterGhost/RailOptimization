package RailOptimization;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

public class RailSearchCacheGameTest {
	@GameTest(environment = "railoptimization-gametest:serial_108", maxTicks = 1)
	public void searchCostRemainsUsableAcrossAllGenerations(GameTestHelper helper) {
		RailSearchCache cache = new RailSearchCache(8);
		long position = new BlockPos(7, 8, 9).asLong();
		byte flags = RailSearchCache.SEARCH | RailSearchCache.SEARCH_FORWARD;

		for (int generation = 0; generation < 300; ++generation) {
			int expectedCost = generation % 64;
			cache.putPoweredSearchCost(position, flags, expectedCost);
			helper.assertValueEqual(
					expectedCost,
					cache.getPoweredSearchCost(position, flags),
					"search cost at generation " + generation);
			cache.advanceSearchGeneration();
		}
		helper.succeed();
	}
}
