package RailOptimization.gametest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;

public final class RailNeighborUpdateTrace {
	private static Capture active;

	private RailNeighborUpdateTrace() {
	}

	static List<Update> capture(Level level, BlockPos[] targets, BlockPos[] rails, Runnable input) {
		if (active != null) {
			throw new IllegalStateException("Nested neighbor trace capture");
		}
		Capture capture = new Capture(level, targets, rails);
		active = capture;
		try {
			input.run();
			return List.copyOf(capture.updates);
		} finally {
			active = null;
		}
	}

	@SuppressWarnings("null")
	public static void record(Level level, BlockPos target, Block source) {
		Capture capture = active;
		if (capture == null || capture.level != level) {
			return;
		}
		for (int index = 0; index < capture.targets.length; index++) {
			if (capture.targets[index].equals(target)) {
				capture.updates.add(new Update(index, BuiltInRegistries.BLOCK.getKey(source).toString(),
						level.getBlockState(capture.rails[0]).getValue(PoweredRailBlock.POWERED),
						level.getBlockState(capture.rails[1]).getValue(PoweredRailBlock.POWERED)));
				return;
			}
		}
	}

	public record Update(int target, String sourceBlock, boolean sourceRailPowered, boolean branchRailPowered) {
	}

	private static final class Capture {
		private final Level level;
		private final BlockPos[] targets;
		private final BlockPos[] rails;
		private final List<Update> updates = new ArrayList<>();

		private Capture(Level level, BlockPos[] targets, BlockPos[] rails) {
			this.level = level;
			this.targets = targets;
			this.rails = rails;
		}
	}
}
