package dev.mewltiplayer.fabric;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class MewCommands {
    private MewCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                literal("mew")
                        .executes(context -> { MewClientControl.status(); return 1; })
                        .then(literal("invite").requires(source -> MewClientCompat.worldDirectory() != null).executes(context -> { MewClientControl.invite(); return 1; }))
                        .then(literal("status").executes(context -> { MewClientControl.status(); return 1; }))
                        .then(literal("start").requires(source -> MewClientCompat.worldDirectory() != null).executes(context -> { MewClientControl.start(); return 1; }))
                        .then(literal("stop").requires(source -> MewClientCompat.worldDirectory() != null).executes(context -> { MewClientControl.stop(); return 1; }))
                        .then(literal("reset").requires(source -> MewClientCompat.worldDirectory() != null).executes(context -> { MewClientControl.reset(); return 1; }))
                        .then(literal("settings").executes(context -> { MewClientControl.settings(); return 1; }))
        ));
    }
}
