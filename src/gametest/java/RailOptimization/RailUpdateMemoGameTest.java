package RailOptimization;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

public class RailUpdateMemoGameTest {
	private static final long OLD_EPOCH_PERIOD = 1L << Integer.SIZE;

	@GameTest(environment = "railoptimization-gametest:serial_110", maxTicks = 1)
	public void memoDoesNotAliasAfterOldEpochPeriod(GameTestHelper helper) {
		try {
			Field epochField = RailUpdateMemo.class.getDeclaredField("blockChangeEpoch");
			epochField.setAccessible(true);
			Method checkEntry = RailUpdateMemo.class.getDeclaredMethod(
					"checkEntry", long.class, int.class, boolean.class);
			checkEntry.setAccessible(true);

			long originalEpoch = epochField.getLong(null);
			try {
				long recordedEpoch = 0x12345678L;
				long position = new BlockPos(12_345_678, 64, -12_345_678).asLong();
				RailUpdateMemo memo = new RailUpdateMemo();
				epochField.setLong(null, recordedEpoch);
				memo.beginWalk();
				memo.confirm(BlockPos.of(position), true, 8);

				helper.assertValueEqual(
						1,
						invokeCheck(checkEntry, memo, position),
						"memo match at the recorded epoch");
				epochField.setLong(null, recordedEpoch + OLD_EPOCH_PERIOD);
				helper.assertValueEqual(
						-1,
						invokeCheck(checkEntry, memo, position),
						"memo mismatch after the old 32-bit epoch period");
			} finally {
				epochField.setLong(null, originalEpoch);
			}
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Unable to inspect RailUpdateMemo", exception);
		}
		helper.succeed();
	}

	private static int invokeCheck(Method checkEntry, RailUpdateMemo memo, long position)
			throws ReflectiveOperationException {
		return (int) checkEntry.invoke(memo, position, 8, true);
	}
}
