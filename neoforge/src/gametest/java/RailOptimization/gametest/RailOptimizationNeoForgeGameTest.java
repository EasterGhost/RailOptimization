package RailOptimization.gametest;

import RailOptimization.RailLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@Mod(RailOptimizationNeoForgeGameTest.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RailOptimizationNeoForgeGameTest {
	public static final String MOD_ID = "railoptimization_gametest";
	private static final BlockPos FIRST_RAIL = new BlockPos(1, 2, 1);
	private static final BlockPos SOURCE = new BlockPos(1, 2, 2);

	public RailOptimizationNeoForgeGameTest(IEventBus modEventBus) {
		modEventBus.addListener(this::registerTests);
	}

	private void registerTests(RegisterGameTestsEvent event) {
		event.register(RailOptimizationNeoForgeGameTest.class);
	}

	@GameTest(templateNamespace = MOD_ID, template = "review_compat_empty", timeoutTicks = 40)
	public static void poweredRailLine(GameTestHelper helper) {
		testRailLine(helper, Blocks.POWERED_RAIL);
	}

	@GameTest(templateNamespace = MOD_ID, template = "review_compat_empty", timeoutTicks = 40)
	public static void activatorRailLine(GameTestHelper helper) {
		testRailLine(helper, Blocks.ACTIVATOR_RAIL);
	}

	private static void testRailLine(GameTestHelper helper, Block railBlock) {
		RailLogic.setOptimizationEnabled(true);
		RailLogic.setRailPowerLimit(8);
		for (int index = 0; index < 10; index++) {
			BlockPos pos = FIRST_RAIL.offset(index, 0, 0);
			helper.setBlock(pos.below(), Blocks.SMOOTH_STONE);
			helper.setBlock(pos, railBlock.defaultBlockState().setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST));
		}
		helper.setBlock(SOURCE.below(), Blocks.SMOOTH_STONE);
		helper.setBlock(SOURCE, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
		helper.startSequence()
				.thenExecute(() -> helper.pullLever(SOURCE))
				.thenIdle(2)
				.thenExecute(() -> {
					for (int index = 0; index < 10; index++) {
						helper.assertBlockProperty(FIRST_RAIL.offset(index, 0, 0), PoweredRailBlock.POWERED, index <= 8);
					}
					helper.pullLever(SOURCE);
				})
				.thenIdle(2)
				.thenExecute(() -> {
					for (int index = 0; index < 10; index++) {
						helper.assertBlockProperty(FIRST_RAIL.offset(index, 0, 0), PoweredRailBlock.POWERED, false);
					}
				})
				.thenSucceed();
	}
}
