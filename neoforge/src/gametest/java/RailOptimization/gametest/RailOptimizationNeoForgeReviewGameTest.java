package RailOptimization.gametest;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;

import RailOptimization.RailLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.redstone.Orientation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

final class RailOptimizationNeoForgeReviewGameTest {
	private static final String MOD_ID = RailOptimizationNeoForgeGameTest.MOD_ID;
	private static final int MIRROR_Y = 20;
	private static final int FIXTURE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;
	private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
	private static final DeferredBlock<WeakPowerRelayBlock> WEAK_POWER_RELAY = BLOCKS.registerBlock("weak_power_relay",
			properties -> new WeakPowerRelayBlock(properties.noLootTable().isRedstoneConductor((state, level, pos) -> false), true));
	private static final DeferredBlock<WeakPowerRelayBlock> ISOLATED_CONDUCTOR = BLOCKS.registerBlock("isolated_conductor",
			properties -> new WeakPowerRelayBlock(properties.noLootTable().isRedstoneConductor((state, level, pos) -> true), false));
	private static final DeferredBlock<PoweredRailBlock> POWERED_VARIANT = BLOCKS.registerBlock("powered_variant",
			properties -> new PoweredRailBlock(properties.noCollision().noLootTable(), true));
	private static final DeferredBlock<PoweredRailBlock> ACTIVATOR_VARIANT = BLOCKS.registerBlock("activator_variant",
			properties -> new PoweredRailBlock(properties.noCollision().noLootTable(), false));
	private static final DeferredBlock<DirectionalRelayBlock> DIRECTIONAL_RELAY = BLOCKS.registerBlock("directional_relay",
			properties -> new DirectionalRelayBlock(properties.noLootTable().isRedstoneConductor((state, level, pos) -> false)));
	private static final DeferredBlock<SourceProbeBlock> SOURCE_PROBE = BLOCKS.registerBlock("source_probe", properties -> new SourceProbeBlock(properties.noLootTable()));
	private static BlockPos[] watchedPositions;
	private static Block[] firstSources;

	private RailOptimizationNeoForgeReviewGameTest() {
	}

	static void registerBlocks(IEventBus eventBus) {
		BLOCKS.register(eventBus);
	}

	static void registerTests(RegisterGameTestsEvent event) {
		register(event, "review_weak_power_opt_in_inside_chunk", helper -> verifyWeakPowerHook(helper, true, 5));
		register(event, "review_weak_power_opt_in_chunk_edge", helper -> verifyWeakPowerHook(helper, true, 0));
		register(event, "review_weak_power_opt_out_inside_chunk", helper -> verifyWeakPowerHook(helper, false, 5));
		register(event, "review_weak_power_opt_out_chunk_edge", helper -> verifyWeakPowerHook(helper, false, 0));
		register(event, "review_mixed_powered_rail_types", helper -> verifyMixedRailTypes(helper, Blocks.POWERED_RAIL, POWERED_VARIANT.get()));
		register(event, "review_mixed_activator_rail_types", helper -> verifyMixedRailTypes(helper, Blocks.ACTIVATOR_RAIL, ACTIVATOR_VARIANT.get()));
		register(event, "review_mixed_powered_variant_input", helper -> verifyMixedRailTypes(helper, Blocks.POWERED_RAIL, POWERED_VARIANT.get(), Direction.EAST, 1, true));
		register(event, "review_mixed_activator_variant_input", helper -> verifyMixedRailTypes(helper, Blocks.ACTIVATOR_RAIL, ACTIVATOR_VARIANT.get(), Direction.EAST, 1, true));
		register(event, "review_mixed_powered_north_south", helper -> verifyMixedRailTypes(helper, Blocks.POWERED_RAIL, POWERED_VARIANT.get(), Direction.SOUTH, 0, true));
		register(event, "review_mixed_activator_north_south", helper -> verifyMixedRailTypes(helper, Blocks.ACTIVATOR_RAIL, ACTIVATOR_VARIANT.get(), Direction.SOUTH, 0, true));
		register(event, "review_powered_rejects_activator", helper -> verifyMixedRailTypes(helper, Blocks.POWERED_RAIL, ACTIVATOR_VARIANT.get(), Direction.EAST, 0, false));
		register(event, "review_activator_rejects_powered", helper -> verifyMixedRailTypes(helper, Blocks.ACTIVATOR_RAIL, POWERED_VARIANT.get(), Direction.EAST, 0, false));
		register(event, "review_directional_relay_west", helper -> verifyWeakPowerHook(helper, DIRECTIONAL_RELAY.get(), Direction.WEST, true, 5));
		register(event, "review_directional_relay_east", helper -> verifyWeakPowerHook(helper, DIRECTIONAL_RELAY.get(), Direction.EAST, false, 5));
	}

	private static void register(RegisterGameTestsEvent event, String name, Consumer<GameTestHelper> body) {
		var environment = event.registerEnvironment(Identifier.fromNamespaceAndPath(MOD_ID, name));
		RailOptimizationNeoForgeGameTest.registerTest(event, environment, name,
				Identifier.fromNamespaceAndPath(MOD_ID, "review_compat_empty"), 40, body);
	}

	private static void verifyWeakPowerHook(GameTestHelper helper, boolean checkWeakPower, int localX) {
		verifyWeakPowerHook(helper, checkWeakPower ? WEAK_POWER_RELAY.get() : ISOLATED_CONDUCTOR.get(), Direction.WEST, checkWeakPower, localX);
	}

	private static void verifyWeakPowerHook(GameTestHelper helper, Block relay, Direction side, boolean checkWeakPower, int localX) {
		RailLogic.setRailPowerLimit(8);
		BlockPos rail = alignedRail(helper, localX);
		BlockPos vanillaRail = rail.above(MIRROR_Y);
		inMode(false, () -> {
			placeRelayFixture(helper, vanillaRail, relay, side);
			placeRelayFixture(helper, rail, relay, side);
			return null;
		});

		PowerTrace vanilla = inMode(false, () -> toggleAndRead(helper, vanillaRail.relative(side).above(), new BlockPos[]{vanillaRail}, null));
		PowerTrace optimized = inMode(true, () -> toggleAndRead(helper, rail.relative(side).above(), new BlockPos[]{rail}, null));
		helper.assertTrue(vanilla.on()[0] == checkWeakPower && !vanilla.off()[0],
				Component.literal("NeoForge reference must honor the relay hook: " + vanilla));
		assertMatchingTraces(helper, "shouldCheckWeakPower=" + checkWeakPower + ", localX=" + localX, vanilla, optimized);
		helper.succeed();
	}

	private static void verifyMixedRailTypes(GameTestHelper helper, Block vanillaType, PoweredRailBlock variant) {
		verifyMixedRailTypes(helper, vanillaType, variant, Direction.EAST, 0, true);
	}

	private static void verifyMixedRailTypes(GameTestHelper helper, Block vanillaType, PoweredRailBlock variant,
			Direction direction, int sourceIndex, boolean compatible) {
		RailLogic.setRailPowerLimit(8);
		BlockPos start = new BlockPos(3, 2, 3);
		BlockPos[] rails = {start, start.relative(direction), start.relative(direction, 2)};
		BlockPos[] vanillaRails = Arrays.stream(rails).map(pos -> pos.above(MIRROR_Y)).toArray(BlockPos[]::new);
		Direction leverSide = direction.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.WEST;
		Direction probeSide = leverSide.getOpposite();
		RailShape shape = direction.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
		helper.assertTrue((((PoweredRailBlock) vanillaType).isActivatorRail() == variant.isActivatorRail()) == compatible,
				Component.literal("fixture category mismatch"));
		helper.assertTrue(BaseRailBlock.isRail(variant.defaultBlockState()), Component.literal("the registered variant must be in the rails tag"));
		inMode(false, () -> {
			placeMixedRailFixture(helper, vanillaRails, vanillaType, variant, shape, sourceIndex, leverSide, compatible);
			placeMixedRailFixture(helper, rails, vanillaType, variant, shape, sourceIndex, leverSide, compatible);
			return null;
		});

		PowerTrace vanilla = inMode(false, () -> toggleAndRead(helper, vanillaRails[sourceIndex].relative(leverSide), vanillaRails, compatible ? probeSide : null));
		PowerTrace optimized = inMode(true, () -> toggleAndRead(helper, rails[sourceIndex].relative(leverSide), rails, compatible ? probeSide : null));
		for (int index = 0; index < vanillaRails.length; index++) {
			helper.assertTrue(vanilla.on()[index] == (compatible || index == sourceIndex) && !vanilla.off()[index],
					Component.literal("NeoForge reference must propagate across the registered variant: " + vanilla));
			if (compatible) helper.assertTrue(vanilla.onSources()[index] != null && vanilla.offSources()[index] != null,
					Component.literal("native reference must notify every rail probe"));
		}
		assertMatchingTraces(helper, "mixed " + vanillaType, vanilla, optimized);
		helper.succeed();
	}

	private static BlockPos alignedRail(GameTestHelper helper, int localX) {
		BlockPos start = new BlockPos(2, 2, 2);
		BlockPos absolute = helper.absolutePos(start);
		return start.offset(Math.floorMod(localX - absolute.getX(), 16), 0, Math.floorMod(5 - absolute.getZ(), 16));
	}

	private static void placeRelayFixture(GameTestHelper helper, BlockPos rail, Block relay, Direction side) {
		helper.setBlock(rail.below(), Blocks.GLASS);
		helper.setBlock(rail.relative(side), relay);
		placeLever(helper, rail.relative(side).above());
		placeRail(helper, rail, Blocks.POWERED_RAIL);
		helper.assertTrue(!helper.getLevel().hasNeighborSignal(helper.absolutePos(rail)), Component.literal("relay fixture must start unpowered"));
	}

	private static void placeMixedRailFixture(GameTestHelper helper, BlockPos[] rails, Block vanillaType, Block variant,
			RailShape shape, int sourceIndex, Direction leverSide, boolean probes) {
		for (int index = 0; index < rails.length; index++) {
			helper.setBlock(rails[index].below(), Blocks.GLASS);
			placeRail(helper, rails[index], index == 1 ? variant : vanillaType, shape);
			if (probes) helper.setBlock(rails[index].relative(leverSide.getOpposite()), SOURCE_PROBE.get());
		}
		helper.setBlock(rails[sourceIndex].relative(leverSide).below(), Blocks.GLASS);
		placeLever(helper, rails[sourceIndex].relative(leverSide));
	}

	private static void placeRail(GameTestHelper helper, BlockPos pos, Block block) {
		placeRail(helper, pos, block, RailShape.EAST_WEST);
	}

	private static void placeRail(GameTestHelper helper, BlockPos pos, Block block, RailShape shape) {
		helper.assertTrue(helper.getBounds().contains(helper.absolutePos(pos).getCenter()), Component.literal("rail outside comparison structure"));
		helper.getLevel().setBlock(helper.absolutePos(pos), block.defaultBlockState()
				.setValue(PoweredRailBlock.SHAPE, shape).setValue(PoweredRailBlock.POWERED, false), FIXTURE_FLAGS);
		helper.assertBlockProperty(pos, PoweredRailBlock.SHAPE, shape);
	}

	private static void placeLever(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, Blocks.LEVER.defaultBlockState()
				.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
				.setValue(LeverBlock.FACING, Direction.NORTH).setValue(LeverBlock.POWERED, false));
	}

	private static PowerTrace toggleAndRead(GameTestHelper helper, BlockPos lever, BlockPos[] rails, Direction probeSide) {
		helper.assertBlockProperty(lever, LeverBlock.POWERED, false);
		for (BlockPos rail : rails) {
			helper.assertBlockProperty(rail, PoweredRailBlock.POWERED, false);
		}
		Block[] onSources = pullAndCapture(helper, lever, rails, probeSide);
		helper.assertBlockProperty(lever, LeverBlock.POWERED, true);
		boolean[] on = readPower(helper, rails);
		Block[] offSources = pullAndCapture(helper, lever, rails, probeSide);
		helper.assertBlockProperty(lever, LeverBlock.POWERED, false);
		return new PowerTrace(on, readPower(helper, rails), onSources, offSources);
	}

	private static Block[] pullAndCapture(GameTestHelper helper, BlockPos lever, BlockPos[] rails, Direction probeSide) {
		watchedPositions = probeSide == null ? new BlockPos[0]
				: Arrays.stream(rails).map(pos -> helper.absolutePos(pos.relative(probeSide))).toArray(BlockPos[]::new);
		Block[] captured = new Block[watchedPositions.length];
		firstSources = captured;
		try {
			helper.pullLever(lever);
			return captured;
		} finally {
			watchedPositions = null;
			firstSources = null;
		}
	}

	private static boolean[] readPower(GameTestHelper helper, BlockPos[] rails) {
		boolean[] powered = new boolean[rails.length];
		for (int index = 0; index < rails.length; index++) {
			powered[index] = helper.getBlockState(rails[index]).getValue(PoweredRailBlock.POWERED);
		}
		return powered;
	}

	private static <T> T inMode(boolean enabled, Supplier<T> action) {
		boolean previous = RailLogic.isOptimizationEnabled();
		RailLogic.setOptimizationEnabled(enabled);
		try {
			return action.get();
		} finally {
			RailLogic.setOptimizationEnabled(previous);
		}
	}

	private static void assertMatchingTraces(GameTestHelper helper, String label, PowerTrace vanilla, PowerTrace optimized) {
		helper.assertTrue(Arrays.equals(vanilla.on(), optimized.on()) && Arrays.equals(vanilla.off(), optimized.off()),
				Component.literal(label + " mismatch: vanilla=" + vanilla + ", optimized=" + optimized));
		helper.assertTrue(Arrays.equals(vanilla.onSources(), optimized.onSources()) && Arrays.equals(vanilla.offSources(), optimized.offSources()),
				Component.literal(label + " NC source mismatch: vanilla=" + Arrays.toString(vanilla.onSources()) + "/" + Arrays.toString(vanilla.offSources())
						+ ", optimized=" + Arrays.toString(optimized.onSources()) + "/" + Arrays.toString(optimized.offSources())));
	}

	private record PowerTrace(boolean[] on, boolean[] off, Block[] onSources, Block[] offSources) {
		@Override
		public String toString() {
			return "{on=" + Arrays.toString(on) + ", off=" + Arrays.toString(off) + "}";
		}
	}

	private static final class DirectionalRelayBlock extends Block {
		private DirectionalRelayBlock(Properties properties) { super(properties); }
		@Override
		public boolean shouldCheckWeakPower(BlockState state, SignalGetter level, BlockPos pos, Direction direction) {
			return direction == Direction.WEST;
		}
	}

	private static final class SourceProbeBlock extends Block {
		private SourceProbeBlock(Properties properties) { super(properties); }
		@Override
		protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block source, Orientation orientation, boolean movedByPiston) {
			if (watchedPositions == null || !(source instanceof PoweredRailBlock)) return;
			for (int index = 0; index < watchedPositions.length; index++) {
				if (firstSources[index] == null && watchedPositions[index].equals(pos)) firstSources[index] = source;
			}
		}
	}

	private static final class WeakPowerRelayBlock extends Block {
		private final boolean checkWeakPower;

		private WeakPowerRelayBlock(Properties properties, boolean checkWeakPower) {
			super(properties);
			this.checkWeakPower = checkWeakPower;
		}

		@Override
		public boolean shouldCheckWeakPower(BlockState state, SignalGetter level, BlockPos pos, Direction direction) {
			return checkWeakPower;
		}
	}
}
