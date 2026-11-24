package dev.mewltiplayer.forge;

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
            return CompletableFuture.completedFuture("Use Open to LAN in a singleplayer world");
        }
        MewRuntime runtime = MewRuntime.INSTANCE;
        if (runtime.sharing()) {
            return CompletableFuture.completedFuture("Tailcat is already running");
        }
        return runtime.share(MewServerCompat.port(server), true)
                .handle((code, error) -> error == null
                        ? "Server is now shared through Tailcat\nShare code: " + code
                        : MewErrors.describe(error).replaceAll("[.]+$", ""));
    }

    public static String stop() {
        MewRuntime runtime = MewRuntime.INSTANCE;
        if (!runtime.sharing()) return "Tailcat is already stopped";
        runtime.stopShare();
        return "Tailcat stopped (Minecraft server remains online)";
    }

    public static CompletableFuture<String> reset(MinecraftServer server) {
        MewRuntime.INSTANCE.stopShare();
        try {
            Files.deleteIfExists(MewRuntime.INSTANCE.configDirectory().resolve("server-identity.json"));
        } catch (IOException error) {
            return CompletableFuture.completedFuture("Failed to reset share code (M302)");
        }
        return start(server).thenApply(message -> message.startsWith("Server is now shared through Tailcat")
                ? "Share code reset\n" + message.substring("Server is now shared through Tailcat\n".length())
                : message);
    }

    public static String shareCode() {
        String code = MewRuntime.INSTANCE.shareCode();
        return code == null ? "No share code yet (use /mewserver start)" : code;
    }

    public static String status() {
        MewRuntime runtime = MewRuntime.INSTANCE;
        BridgeStatus current = runtime.status();
        if (runtime.shareCode() != null) {
            StringBuilder details = new StringBuilder("World shared (" + current.connections() + " connections)")
                    .append("\nHelper ").append(runtime.helperOnline() ? "running" : "stopped");
            for (BridgeStatus.Peer peer : current.peers()) details.append("\n").append(peer.description());
            return details.toString();
        }
        if (runtime.sharing()) return "Opening world through Tailcat";
        return "Tailcat is not running";
    }

    public static String settings() {
        MewSettings settings = MewRuntime.INSTANCE.settings();
        String mode = switch (settings.derpMode) {
            case MewSettings.AUTO -> "Automatic";
            case MewSettings.MAP -> "Custom map";
            case MewSettings.URL -> "Custom URL";
            default -> settings.derpMode;
        };
        return "DERP " + mode + " (Region " + (settings.regionId == 0 ? "Auto" : settings.regionId) + ")"
                + "\nSettings config/mewltiplayer/settings.json"
                + "\nServer config/mewltiplayer/server.json";
    }
}
