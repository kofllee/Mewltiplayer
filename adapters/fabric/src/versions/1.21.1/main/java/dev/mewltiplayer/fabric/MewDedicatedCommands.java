package dev.mewltiplayer.fabric;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public final class MewDedicatedCommands {
    private MewDedicatedCommands() {}

    private static void reply(ServerCommandSource source, String message) {
        source.sendFeedback(() -> Text.literal("[Mewltiplayer] " + message), false);
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            dispatcher.register(command("mew"));
            dispatcher.register(command("mewserver"));
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> command(String name) {
        return literal(name).requires(source -> source.getServer().isDedicated()
                        && source.hasPermissionLevel(2))
                        .executes(context -> { reply(context.getSource(), MewDedicatedControl.status()); return 1; })
                        .then(literal("status").executes(context -> {
                            reply(context.getSource(), MewDedicatedControl.status()); return 1;
                        }))
                        .then(literal("invite").executes(context -> {
                            reply(context.getSource(), MewDedicatedControl.invite()); return 1;
                        }))
                        .then(literal("stop").executes(context -> {
                            MewDedicatedControl.stop();
                            reply(context.getSource(), "Tailcat stopped; Minecraft server is still running.");
                            return 1;
                        }))
                        .then(literal("start").executes(context -> {
                            ServerCommandSource source = context.getSource();
                            MewDedicatedControl.start(source.getServer()).thenAccept(message ->
                                    source.getServer().execute(() -> reply(source, message)));
                            return 1;
                        }))
                        .then(literal("reset").executes(context -> {
                            ServerCommandSource source = context.getSource();
                            MewDedicatedControl.reset(source.getServer()).thenAccept(message ->
                                    source.getServer().execute(() -> reply(source, message)));
                            return 1;
                        }))
                        .then(literal("settings").executes(context -> {
                            reply(context.getSource(), MewDedicatedControl.settings()); return 1;
                        }))
        ;
    }
}
