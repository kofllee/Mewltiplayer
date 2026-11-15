package dev.mewltiplayer.fabric;

import dev.mewltiplayer.core.MewSettings;
import dev.mewltiplayer.core.MewErrors;
import dev.mewltiplayer.core.transport.BridgeStatus;
import net.minecraft.server.MinecraftServer;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

public final class MewDedicatedControl {
    private MewDedicatedControl() {}

    public static CompletableFuture<String> start(MinecraftServer server) {
        if (!MewServerCompat.dedicated(server)) {
            return CompletableFuture.completedFuture("Use the native Open to LAN screen for singleplayer.");
        }
        return MewRuntime.INSTANCE.share(MewServerCompat.port(server), true)
                .handle((code, error) -> error == null
                        ? "Tailcat is sharing. Use /mew invite to copy the invitation."
                        : MewErrors.describe(error));
    }

    public static void stop() {
        MewRuntime.INSTANCE.stopShare();
    }

    public static CompletableFuture<String> reset(MinecraftServer server) {
        MewRuntime.INSTANCE.stopShare();
        try {
            Files.deleteIfExists(MewRuntime.INSTANCE.configDirectory().resolve("server-identity.json"));
        } catch (IOException error) {
            return CompletableFuture.completedFuture("Could not revoke saved invitation (M302).");
        }
        return start(server).thenApply(message -> "Previous invitation revoked. " + message);
    }

    public static String invite() {
        String code = MewRuntime.INSTANCE.invite();
        return code == null ? "No active invitation. Use /mew start." : code;
    }

    public static String status() {
        BridgeStatus current = MewRuntime.INSTANCE.status();
        return "Tailcat: " + (MewRuntime.INSTANCE.invite() != null ? "sharing"
                : MewRuntime.INSTANCE.sharing() ? "starting" : "idle")
                + " | Helper: " + (MewRuntime.INSTANCE.helperOnline() ? "running" : "stopped")
                + " | TCP connections: " + current.connections()
                + " | Direct/DERP routing: each guest can check /mew status";
    }

    public static String settings() {
        MewSettings s = MewRuntime.INSTANCE.settings();
        return "Settings: config/mewltiplayer/settings.json | DERP: "
                + s.derpMode
                + " | Region: " + (s.regionId == 0 ? "auto" : s.regionId)
                + " | Dedicated server: config/mewltiplayer/server.json";
    }
}
