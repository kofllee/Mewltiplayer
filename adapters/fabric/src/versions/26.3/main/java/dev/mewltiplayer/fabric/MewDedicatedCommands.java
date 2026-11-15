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

    private static void replyCode(CommandSourceStack source) {
        String code = MewRuntime.INSTANCE.shareCode();
        if (code == null) {
            reply(source, "No share code yet (use /mewserver start)");
        } else if (source.getPlayer() == null) {
            reply(source, "Share code " + code);
        } else {
            source.sendSuccess(() -> Component.literal("[Mewltiplayer] Share code ")
                    .append(Component.literal("[Copy]").withStyle(style ->
                            style.withClickEvent(new net.minecraft.network.chat.ClickEvent.CopyToClipboard(code))
                                    .withUnderlined(true).withColor(0x55ffff))), false);
        }
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
                        .then(literal("code").executes(context -> {
                            replyCode(context.getSource()); return 1;
                        }))
                        .then(literal("stop").executes(context -> {
                            reply(context.getSource(), MewDedicatedControl.stop());
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
