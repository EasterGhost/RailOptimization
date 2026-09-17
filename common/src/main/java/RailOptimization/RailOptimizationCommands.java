package RailOptimization;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import RailOptimization.config.RailOptimizationConfigManager;

public final class RailOptimizationCommands {
	private RailOptimizationCommands() {
	}

	@SuppressWarnings("null")
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("railoptimization")
				.executes(context -> sendStatus(context.getSource()))
				.then(Commands.literal("on")
						.requires(RailPermissions::isAdmin)
						.executes(context -> setEnabled(context.getSource(), true)))
				.then(Commands.literal("off")
						.requires(RailPermissions::isAdmin)
						.executes(context -> setEnabled(context.getSource(), false)))
				.then(Commands.literal("powerLimit")
						.requires(RailPermissions::isAdmin)
						.then(Commands.argument("value", IntegerArgumentType.integer())
								.executes(context -> setPowerLimit(context.getSource(), IntegerArgumentType.getInteger(context, "value")))))
				.then(Commands.literal("reload")
						.requires(RailPermissions::isAdmin)
						.executes(context -> reloadConfig(context.getSource()))));
	}

	private static int setEnabled(CommandSourceStack source, boolean enabled) {
		RailOptimizationConfigManager.setEnabled(enabled);
		return sendStatus(source);
	}

	private static int setPowerLimit(CommandSourceStack source, int powerLimit) {
		RailOptimizationConfigManager.setPowerLimit(powerLimit);
		return sendStatus(source);
	}

	private static int reloadConfig(CommandSourceStack source) {
		RailOptimizationConfigManager.reloadAsync().whenComplete((result, throwable) -> source.getServer().execute(() -> {
			if (throwable != null) {
				source.sendFailure(Component.literal("Failed to reload RailOptimization config: " + errorMessage(throwable)));
			} else if (!RailOptimizationConfigManager.applyReloaded(result)) {
				source.sendFailure(Component.literal("RailOptimization config reload was ignored because a newer setting was applied"));
			} else {
				sendStatus(source);
			}
		}));
		return 1;
	}

	private static String errorMessage(Throwable throwable) {
		Throwable cause = throwable;
		while (cause.getCause() != null) {
			cause = cause.getCause();
		}
		String message = cause.getMessage();
		return message != null ? message : cause.getClass().getSimpleName();
	}

	private static int sendStatus(CommandSourceStack source) {
		source.sendSuccess(
				() -> Component
						.literal("RailOptimization is " + (RailLogic.isOptimizationEnabled() ? "on" : "off") + "; powerLimit=" + RailLogic.getRailPowerLimit()),
				false);
		return 1;
	}
}
