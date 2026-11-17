package dev.mewltiplayer.neoforge;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

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
                            style.withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                            net.minecraft.network.chat.ClickEvent.Action.COPY_TO_CLIPBOARD, code))
                                    .withUnderlined(true).withColor(0x55ffff))), false);
        }
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> {
            event.getDispatcher().register(command("mew"));
            event.getDispatcher().register(command("mewserver"));
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String name) {
        return literal(name).requires(source -> source.getServer().isDedicatedServer()
                        && source.hasPermission(2))
                        .executes(context -> { reply(context.getSource(), MewDedicatedControl.status()); return 1; })
                        .then(literal("status").executes(context -> {
                            reply(context.getSource(), MewDedicatedControl.status()); return 1;
                        }))
                        .then(literal("code").executes(context -> {
                            replyCode(context.getSource()); return 1;
                        }))
                        .then(literal("stop").executes(context -> {
                            reply(context.getSource(), MewDedicatedControl.stop()); return 1;
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
                        }));
    }
}