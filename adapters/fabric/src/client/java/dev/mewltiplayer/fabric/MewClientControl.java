package dev.mewltiplayer.fabric;

import dev.mewltiplayer.core.transport.BridgeStatus;
import dev.mewltiplayer.core.transport.ConnectionMode;
import dev.mewltiplayer.core.transport.NetworkStatus;
import java.nio.file.Path;

public final class MewClientControl {
    private MewClientControl() {}

    public static void start() {
        if (!MewClientCompat.published()) {
            MewClientCompat.notice("mewltiplayer.chat.openLanFirst");
            return;
        }
        MewRuntime runtime = MewRuntime.INSTANCE;
        if (runtime.invite() != null) {
            invite();
            return;
        }
        runtime.setOnlineSharingEnabled(true);
        // The next client tick starts the helper; no duplicate startup messages.
    }

    public static void stop() {
        MewRuntime runtime = MewRuntime.INSTANCE;
        if (runtime.sharing() || runtime.onlineSharingEnabled()) {
            runtime.setOnlineSharingEnabled(false);
            MewClientCompat.endSharing();
            MewClientCompat.message("mewltiplayer.chat.stopped", false);
        } else {
            MewClientCompat.message("mewltiplayer.chat.idle", false);
        }
    }

    public static void invite() {
        String code = MewRuntime.INSTANCE.invite();
        if (code == null) {
            MewClientCompat.notice("mewltiplayer.chat.noInvite");
        } else {
            MewClientCompat.copyInvite(code);
        }
    }

    public static void reset() {
        MewRuntime runtime = MewRuntime.INSTANCE;
        if (!MewClientCompat.published() || !runtime.onlineSharingEnabled()) {
            MewClientCompat.notice("mewltiplayer.chat.noInvite");
            return;
        }
        runtime.stopShare();
        MewClientCompat.endSharing();
        Path world = MewClientCompat.worldDirectory();
        runtime.resetInvitation(runtime.settings().invitationMode, world, null, false)
                .whenComplete((ignored, error) -> MewClientCompat.execute(() -> {
                    if (error == null) {
                        MewClientCompat.message("mewltiplayer.chat.replacing", false);
                    } else {
                        runtime.setOnlineSharingEnabled(false);
                        MewClientCompat.notice("mewltiplayer.chat.resetFailed");
                    }
                }));
    }

    public static void status() {
        MewRuntime runtime = MewRuntime.INSTANCE;
        runtime.poll();
        BridgeStatus bridge = runtime.status();
        if (runtime.invite() != null) {
            MewClientCompat.message("mewltiplayer.chat.hostStatus", true, bridge.connections());
        } else if (runtime.sharing()) {
            MewClientCompat.message("mewltiplayer.chat.opening", false);
        } else if (runtime.joining()) {
            MewClientCompat.message("mewltiplayer.chat.guestStatus", true);
            route(runtime.network());
        } else {
            MewClientCompat.message("mewltiplayer.chat.idle", false);
        }
    }

    public static void route(NetworkStatus network) {
        if (network == null || network.mode() == ConnectionMode.CONNECTED) {
            MewClientCompat.message("mewltiplayer.chat.routeChecking", false);
        } else if (network.mode() == ConnectionMode.DIRECT) {
            MewClientCompat.message("mewltiplayer.chat.routeDirect", true, network.latencyMs());
        } else if (network.mode() == ConnectionMode.RELAY) {
            MewClientCompat.message("mewltiplayer.chat.routeRelay", false,
                    network.relay().isEmpty() ? "auto" : network.relay(), network.latencyMs());
        } else {
            MewClientCompat.message("mewltiplayer.chat.routeChecking", false);
        }
    }

    public static void settings() {
        MewClientCompat.showSettings();
    }
}
