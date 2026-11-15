package dev.mewltiplayer.fabric;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

public final class MewDedicatedCommands {
    private MewDedicatedCommands() {}

    private static void reply(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal("[Mewltiplayer] " + message), false);
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            dispatcher.register(command("mew"));
            dispatcher.register(command("mewserver"));
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String name) {
        return literal(name).requires(source -> source.getServer().isDedicatedServer()
                        && Commands.LEVEL_ADMINS.check(source.permissions()))
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
                            CommandSourceStack source = context.getSource();
                            MewDedicatedControl.start(source.getServer()).thenAccept(message ->
                                    source.getServer().execute(() -> reply(source, message)));
                            return 1;
                        }))
                        .then(literal("reset").executes(context -> {
                            CommandSourceStack source = context.getSource();
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
