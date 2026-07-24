package RailOptimization;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

public class RailOptimizationFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("railoptimization")
                        .executes(context -> sendStatus(context.getSource()))
                        .then(Commands.literal("on")
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                                .executes(context -> setEnabled(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                                .executes(context -> setEnabled(context.getSource(), false)))
                        .then(Commands.literal("powerLimit")
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                                .then(Commands.argument("value", IntegerArgumentType.integer())
                                        .executes(context -> setPowerLimit(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "value")))))));
    }

    private static int setEnabled(CommandSourceStack source, boolean enabled) {
        RailLogic.setOptimizationEnabled(enabled);
        return sendStatus(source);
    }

    private static int setPowerLimit(CommandSourceStack source, int powerLimit) {
        RailLogic.setRailPowerLimit(powerLimit);
        return sendStatus(source);
    }

    private static int sendStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "RailOptimization is " + (RailLogic.isOptimizationEnabled() ? "on" : "off")
                        + "; powerLimit=" + RailLogic.getRailPowerLimit()
        ), false);
        return 1;
    }
}
