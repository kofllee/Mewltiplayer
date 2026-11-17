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

    private static void replyCode(ServerCommandSource source) {
        String code = MewRuntime.INSTANCE.shareCode();
        if (code == null) {
            reply(source, "No share code yet (use /mewserver start)");
        } else if (source.getPlayer() == null) {
            reply(source, "Share code " + code);
        } else {
            source.sendFeedback(() -> Text.literal("[Mewltiplayer] Share code ")
                    .append(Text.literal("[Copy]").styled(style ->
                            style.withClickEvent(new net.minecraft.text.ClickEvent(
                                    net.minecraft.text.ClickEvent.Action.COPY_TO_CLIPBOARD, code))
                                    .withUnderline(true).withColor(0x55ffff))), false);
        }
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
                        .then(literal("code").executes(context -> {
                            replyCode(context.getSource()); return 1;
                        }))
                        .then(literal("stop").executes(context -> {
                            reply(context.getSource(), MewDedicatedControl.stop());
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
