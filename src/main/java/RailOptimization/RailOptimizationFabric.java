package RailOptimization;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class RailOptimizationFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("railoptimization")
                        .executes(context -> sendStatus(context.getSource()))
                        .then(Commands.literal("on")
                                .executes(context -> setEnabled(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> setEnabled(context.getSource(), false)))));
    }

    private static int setEnabled(net.minecraft.commands.CommandSourceStack source, boolean enabled) {
        RailLogic.setOptimizationEnabled(enabled);
        return sendStatus(source);
    }

    private static int sendStatus(net.minecraft.commands.CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "RailOptimization is " + (RailLogic.isOptimizationEnabled() ? "on" : "off")
        ), false);
        return 1;
    }
}
