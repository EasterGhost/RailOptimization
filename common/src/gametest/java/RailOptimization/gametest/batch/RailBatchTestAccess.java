package RailOptimization.gametest.batch;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

final class RailBatchTestAccess {
	private static final Class<?> CONTEXT = type("RailOptimization.RailUpdateContext");
	private static final Class<?> MEMO = type("RailOptimization.RailUpdateMemo");
	private static final Class<?> LOGIC = type("RailOptimization.RailLogic");

	static int powerLimit() {
		return (int) invoke(LOGIC, "getRailPowerLimit", new Class<?>[0]);
	}

	static void beginLaneWrite() {
		invoke(MEMO, "beginLaneWrite", new Class<?>[0]);
	}

	static void endLaneWrite() {
		invoke(MEMO, "endLaneWrite", new Class<?>[0]);
	}

	static final class Context {
		private final Object delegate;
		final Memo memo;
		final Changes changeList;
		final Writer batchWriter;

		Context(int limit) {
			try {
				Constructor<?> constructor = CONTEXT.getDeclaredConstructor(int.class);
				constructor.setAccessible(true);
				delegate = constructor.newInstance(limit);
				memo = new Memo(field(delegate, "memo"));
				changeList = new Changes(field(delegate, "changeList"));
				batchWriter = new Writer(field(delegate, "batchWriter"));
			} catch (ReflectiveOperationException failure) {
				throw new IllegalStateException(failure);
			}
		}

		void reset() {
			invoke(delegate, "reset", new Class<?>[0]);
		}

		BlockState getBlockState(Level level, BlockPos pos) {
			return (BlockState) invoke(delegate, "getBlockState", new Class<?>[] { Level.class, BlockPos.class }, level, pos);
		}
	}

	static final class Memo {
		private final Object delegate;

		Memo(Object delegate) {
			this.delegate = delegate;
		}

		void beginWalk(Level level) {
			invoke(delegate, "beginWalk", new Class<?>[] { Level.class }, level);
		}
	}

	static final class Changes {
		private final Object delegate;

		Changes(Object delegate) {
			this.delegate = delegate;
		}

		long position(int index) {
			return (long) invoke(delegate, "position", new Class<?>[] { int.class }, index);
		}

		BlockState state(int index) {
			return (BlockState) invoke(delegate, "state", new Class<?>[] { int.class }, index);
		}
	}

	static final class Writer {
		private final Object delegate;

		Writer(Object delegate) {
			this.delegate = delegate;
		}

		void begin() {
			invoke(delegate, "begin", new Class<?>[0]);
		}

		void queue(BlockPos pos, BlockState previous, boolean powered) {
			invoke(delegate, "queue", new Class<?>[] { BlockPos.class, BlockState.class, boolean.class }, pos, previous, powered);
		}

		void write(Level level, Context context) {
			invoke(delegate, "write", new Class<?>[] { Level.class, CONTEXT }, level, context.delegate);
		}
	}

	private static Object field(Object receiver, String name) throws ReflectiveOperationException {
		var field = receiver.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(receiver);
	}

	private static Object invoke(Object receiver, String name, Class<?>[] parameters, Object... args) {
		try {
			Class<?> owner = receiver instanceof Class<?> type ? type : receiver.getClass();
			var method = owner.getDeclaredMethod(name, parameters);
			method.setAccessible(true);
			return method.invoke(receiver instanceof Class<?> ? null : receiver, args);
		} catch (InvocationTargetException failure) {
			Throwable cause = failure.getCause();
			if (cause instanceof Error error)
				throw error;
			if (cause instanceof RuntimeException runtime)
				throw runtime;
			throw new IllegalStateException(cause);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException(failure);
		}
	}

	private static Class<?> type(String name) {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException failure) {
			throw new IllegalStateException(failure);
		}
	}
}
