package RailOptimization;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

final class RailPath {
	static final byte AXIS_EAST_WEST = 1;
	static final byte AXIS_NORTH_SOUTH = 2;
	static final RailShape[] RAIL_SHAPES = RailShape.values();
	static final byte[] RAIL_AXIS = new byte[RAIL_SHAPES.length];
	private static final boolean[] RAIL_ASCENDING = new boolean[RAIL_SHAPES.length];
	static final byte[] STEP_X;
	static final byte[] STEP_Y;
	static final byte[] STEP_Z;
	static final byte[] STEP_BELOW;
	static final RailShape[] STEP_FLAT;

	static {
		RAIL_AXIS[RailShape.EAST_WEST.ordinal()] = AXIS_EAST_WEST;
		RAIL_AXIS[RailShape.ASCENDING_EAST.ordinal()] = AXIS_EAST_WEST;
		RAIL_AXIS[RailShape.ASCENDING_WEST.ordinal()] = AXIS_EAST_WEST;
		RAIL_AXIS[RailShape.NORTH_SOUTH.ordinal()] = AXIS_NORTH_SOUTH;
		RAIL_AXIS[RailShape.ASCENDING_NORTH.ordinal()] = AXIS_NORTH_SOUTH;
		RAIL_AXIS[RailShape.ASCENDING_SOUTH.ordinal()] = AXIS_NORTH_SOUTH;

		RAIL_ASCENDING[RailShape.ASCENDING_EAST.ordinal()] = true;
		RAIL_ASCENDING[RailShape.ASCENDING_WEST.ordinal()] = true;
		RAIL_ASCENDING[RailShape.ASCENDING_NORTH.ordinal()] = true;
		RAIL_ASCENDING[RailShape.ASCENDING_SOUTH.ordinal()] = true;

		int stepCount = RAIL_SHAPES.length << 1;
		RailShape[] flatShapes = new RailShape[stepCount];
		byte[] stepX = new byte[stepCount];
		byte[] stepY = new byte[stepCount];
		byte[] stepZ = new byte[stepCount];
		byte[] stepBelow = new byte[stepCount];

		setStep(flatShapes, stepX, stepY, stepZ, stepBelow, RailShape.NORTH_SOUTH, true, RailShape.NORTH_SOUTH, 0, 0, 1, true);
		setStep(flatShapes, stepX, stepY, stepZ, stepBelow, RailShape.NORTH_SOUTH, false, RailShape.NORTH_SOUTH, 0, 0, -1, true);
		setStep(flatShapes, stepX, stepY, stepZ, stepBelow, RailShape.EAST_WEST, true, RailShape.EAST_WEST, -1, 0, 0, true);
		setStep(flatShapes, stepX, stepY, stepZ, stepBelow, RailShape.EAST_WEST, false, RailShape.EAST_WEST, 1, 0, 0, true);
		setStep(flatShapes, stepX, stepY, stepZ, stepBelow, RailShape.ASCENDING_EAST, true, RailShape.EAST_WEST, -1, 0, 0, true);
		setStep(flatShapes, stepX, stepY, stepZ, stepBelow, RailShape.ASCENDING_EAST, false, RailShape.EAST_WEST, 1, 1, 0, false);
		setStep(flatShapes, stepX, stepY, stepZ, stepBelow, RailShape.ASCENDING_WEST, true, RailShape.EAST_WEST, -1, 1, 0, false);
		setStep(flatShapes, stepX, stepY, stepZ, stepBelow, RailShape.ASCENDING_WEST, false, RailShape.EAST_WEST, 1, 0, 0, true);
		setStep(flatShapes, stepX, stepY, stepZ, stepBelow, RailShape.ASCENDING_NORTH, true, RailShape.NORTH_SOUTH, 0, 0, 1, true);
		setStep(flatShapes, stepX, stepY, stepZ, stepBelow, RailShape.ASCENDING_NORTH, false, RailShape.NORTH_SOUTH, 0, 1, -1, false);
		setStep(flatShapes, stepX, stepY, stepZ, stepBelow, RailShape.ASCENDING_SOUTH, true, RailShape.NORTH_SOUTH, 0, 1, 1, false);
		setStep(flatShapes, stepX, stepY, stepZ, stepBelow, RailShape.ASCENDING_SOUTH, false, RailShape.NORTH_SOUTH, 0, 0, -1, true);

		STEP_X = stepX;
		STEP_Y = stepY;
		STEP_Z = stepZ;
		STEP_BELOW = stepBelow;
		STEP_FLAT = flatShapes;
	}

	private RailPath() {
	}

	private static void setStep(RailShape[] flatShapes, byte[] stepX, byte[] stepY, byte[] stepZ, byte[] stepBelow, RailShape railShape, boolean forward,
			RailShape flatShape, int x, int y, int z, boolean below) {
		int index = (railShape.ordinal() << 1) | (forward ? 0 : 1);
		flatShapes[index] = flatShape;
		stepX[index] = (byte) x;
		stepY[index] = (byte) y;
		stepZ[index] = (byte) z;
		stepBelow[index] = (byte) (below ? 1 : 0);
	}

	static boolean isAscending(RailShape railShape) {
		return RAIL_ASCENDING[railShape.ordinal()];
	}

	static boolean isMismatchedRailAxis(RailShape expected, int actual) {
		return RAIL_AXIS[actual] == (expected == RailShape.EAST_WEST ? AXIS_NORTH_SOUTH : AXIS_EAST_WEST);
	}

	static BlockState findNextRailState(PoweredRailBlock self, Level level, MutableBlockPos railPos, BlockState state, boolean forward,
			RailUpdateContext context) {
		MutableBlockPos scratchPos = context.scratchPos;
		int x = railPos.getX();
		int y = railPos.getY();
		int z = railPos.getZ();
		RailShape railShape = railShape(state);
		int stepIndex = (railShape.ordinal() << 1) | (forward ? 0 : 1);
		int nextX = x + STEP_X[stepIndex];
		int nextY = y + STEP_Y[stepIndex];
		int nextZ = z + STEP_Z[stepIndex];
		RailShape flatShape = STEP_FLAT[stepIndex];

		scratchPos.set(nextX, nextY, nextZ);
		BlockState nextState = context.getBlockState(level, scratchPos);
		if (isSameRailWithAxis(self, nextState, flatShape)) {
			railPos.set(scratchPos);
			return nextState;
		}

		if (STEP_BELOW[stepIndex] == 0) {
			return null;
		}

		scratchPos.set(nextX, nextY - 1, nextZ);
		nextState = context.getBlockState(level, scratchPos);
		if (isSameRailWithAxis(self, nextState, flatShape)) {
			railPos.set(scratchPos);
			return nextState;
		}

		return null;
	}

	static boolean connectsBackTo(PoweredRailBlock self, Level level, BlockPos railPos, BlockState state, boolean forward,
			int previousY, BlockState previousState, RailUpdateContext context) {
		RailShape railShape = railShape(state);
		// Walkers already establish horizontal adjacency; only reverse height and priority remain.
		int stepIndex = (railShape.ordinal() << 1) | (forward ? 1 : 0);
		int nextY = railPos.getY() + STEP_Y[stepIndex];
		RailShape flatShape = STEP_FLAT[stepIndex];

		if (nextY == previousY) {
			return isSameRailWithAxis(self, previousState, flatShape);
		}
		if (STEP_BELOW[stepIndex] == 0 || nextY - 1 != previousY
				|| !isSameRailWithAxis(self, previousState, flatShape)) {
			return false;
		}

		context.scratchPos.set(railPos.getX() + STEP_X[stepIndex], nextY, railPos.getZ() + STEP_Z[stepIndex]);
		return !isSameRailWithAxis(self, context.getBlockState(level, context.scratchPos), flatShape);
	}

	static boolean isSameRailWithAxis(PoweredRailBlock self, BlockState state, RailShape expectedShape) {
		return state.is(self) && !isMismatchedRailAxis(expectedShape, railData(state) & RailStateAccess.SHAPE_MASK);
	}

	static boolean isPoweredRailWithAxis(PoweredRailBlock self, BlockState state, RailShape expectedShape) {
		if (!state.is(self)) {
			return false;
		}
		int railData = railData(state);
		return !isMismatchedRailAxis(expectedShape, railData & RailStateAccess.SHAPE_MASK) && (railData & RailStateAccess.POWERED_MASK) != 0;
	}

	static RailShape railShape(BlockState state) {
		return RAIL_SHAPES[railData(state) & RailStateAccess.SHAPE_MASK];
	}

	static boolean isPowered(BlockState state) {
		return (railData(state) & RailStateAccess.POWERED_MASK) != 0;
	}

	static int railData(BlockState state) {
		return ((RailStateAccess) (Object) state).railoptimization$getRailData();
	}
}
