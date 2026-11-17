package dev.mewltiplayer.forge;

import net.minecraft.commands.Commands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

import static net.minecraft.commands.Commands.literal;

public final class MewCommands {
    private MewCommands() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> event.getDispatcher().register(
                literal("mew")
                        .executes(context -> { MewClientControl.status(); return 1; })
                        .then(literal("code").requires(source -> MewClientCompat.worldDirectory() != null).executes(context -> { MewClientControl.shareCode(); return 1; }))
                        .then(literal("status").executes(context -> { MewClientControl.status(); return 1; }))
                        .then(literal("start").requires(source -> MewClientCompat.worldDirectory() != null).executes(context -> { MewClientControl.start(); return 1; }))
                        .then(literal("stop").requires(source -> MewClientCompat.worldDirectory() != null).executes(context -> { MewClientControl.stop(); return 1; }))
                        .then(literal("reset").requires(source -> MewClientCompat.worldDirectory() != null).executes(context -> { MewClientControl.reset(); return 1; }))
                        .then(literal("settings").executes(context -> { MewClientControl.settings(); return 1; }))
        ));
    }
}