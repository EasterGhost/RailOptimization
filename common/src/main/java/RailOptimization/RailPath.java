package RailOptimization;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

final class RailPath {
	static final byte AXIS_NONE = 0;
	static final byte AXIS_EAST_WEST = 1;
	static final byte AXIS_NORTH_SOUTH = 2;
	static final Direction[] EAST_WEST_DIRECTIONS = new Direction[] { Direction.WEST, Direction.EAST };
	static final Direction[] NORTH_SOUTH_DIRECTIONS = new Direction[] { Direction.SOUTH, Direction.NORTH };
	static final RailShape[] RAIL_SHAPES = RailShape.values();
	static final byte[] RAIL_AXIS = new byte[RailShape.values().length];
	private static final boolean[] RAIL_ASCENDING = new boolean[RailShape.values().length];
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

		int stepCount = RailShape.values().length << 1;
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

	static boolean supportsFastSearch(RailShape railShape) {
		return RAIL_AXIS[railShape.ordinal()] != AXIS_NONE;
	}

	static boolean isAscending(RailShape railShape) {
		return RAIL_ASCENDING[railShape.ordinal()];
	}

	static boolean isMismatchedRailAxis(RailShape expected, RailShape actual) {
		if (expected == RailShape.EAST_WEST) {
			return actual == RailShape.NORTH_SOUTH || actual == RailShape.ASCENDING_NORTH || actual == RailShape.ASCENDING_SOUTH;
		}
		return actual == RailShape.EAST_WEST || actual == RailShape.ASCENDING_EAST || actual == RailShape.ASCENDING_WEST;
	}

	static BlockState findNextRailState(PoweredRailBlock self, Level level, MutableBlockPos railPos, BlockState state, boolean forward,
			RailUpdateContext context) {
		MutableBlockPos scratchPos = context.scratchPos;
		int x = railPos.getX();
		int y = railPos.getY();
		int z = railPos.getZ();
		RailShape railShape = railShape(state);
		if (RAIL_AXIS[railShape.ordinal()] == AXIS_NONE) {
			return null;
		}

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

	static boolean connectsBackTo(PoweredRailBlock self, Level level, BlockPos railPos, BlockState state, long expectedPreviousPos, BlockState previousState,
			RailUpdateContext context) {
		if (directionConnectsBackTo(self, level, railPos, state, true, expectedPreviousPos, previousState, context)) {
			return true;
		}
		return directionConnectsBackTo(self, level, railPos, state, false, expectedPreviousPos, previousState, context);
	}

	private static boolean directionConnectsBackTo(PoweredRailBlock self, Level level, BlockPos railPos, BlockState state, boolean forward,
			long expectedPreviousPos, BlockState previousState, RailUpdateContext context) {
		int x = railPos.getX();
		int y = railPos.getY();
		int z = railPos.getZ();
		RailShape railShape = railShape(state);
		if (RAIL_AXIS[railShape.ordinal()] == AXIS_NONE) {
			return false;
		}

		int stepIndex = (railShape.ordinal() << 1) | (forward ? 0 : 1);
		int nextX = x + STEP_X[stepIndex];
		int nextY = y + STEP_Y[stepIndex];
		int nextZ = z + STEP_Z[stepIndex];
		RailShape flatShape = STEP_FLAT[stepIndex];

		if (BlockPos.asLong(nextX, nextY, nextZ) == expectedPreviousPos) {
			return isSameRailWithAxis(self, previousState, flatShape);
		}
		if (STEP_BELOW[stepIndex] == 0 || BlockPos.asLong(nextX, nextY - 1, nextZ) != expectedPreviousPos
				|| !isSameRailWithAxis(self, previousState, flatShape)) {
			return false;
		}

		context.scratchPos.set(nextX, nextY, nextZ);
		return !isSameRailWithAxis(self, context.getBlockState(level, context.scratchPos), flatShape);
	}

	private static boolean isSameRailWithAxis(PoweredRailBlock self, BlockState state, RailShape expectedShape) {
		return state.is(self) && !isMismatchedRailAxis(expectedShape, railShape(state));
	}

	static boolean isPoweredRailWithAxis(PoweredRailBlock self, BlockState state, RailShape expectedShape) {
		if (!state.is(self)) {
			return false;
		}
		int railData = railData(state);
		return !isMismatchedRailAxis(expectedShape, RAIL_SHAPES[railData & RailStateAccess.SHAPE_MASK]) && (railData & RailStateAccess.POWERED_MASK) != 0;
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
