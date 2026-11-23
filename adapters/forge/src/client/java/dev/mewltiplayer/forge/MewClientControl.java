package dev.mewltiplayer.forge;

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
        if (runtime.sharing() || runtime.onlineSharingEnabled()) {
            MewClientCompat.message("mewltiplayer.chat.alreadyRunning", false);
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
            MewClientCompat.message("mewltiplayer.chat.alreadyStopped", false);
        }
    }

    public static void shareCode() {
        String code = MewRuntime.INSTANCE.shareCode();
        if (code == null) {
            MewClientCompat.notice("mewltiplayer.chat.noShareCode");
        } else {
            MewClientCompat.copyShareCode(code);
        }
    }

    public static void reset() {
        MewRuntime runtime = MewRuntime.INSTANCE;
        if (!MewClientCompat.published() || !runtime.onlineSharingEnabled()) {
            MewClientCompat.notice("mewltiplayer.chat.noShareCode");
            return;
        }
        MewClientCompat.endSharing();
        Path world = MewClientCompat.worldDirectory();
        runtime.resetShareCode(runtime.settings().shareCodeMode, world, null, false)
                .whenComplete((ignored, error) -> MewClientCompat.execute(() -> {
                    if (error != null) {
                        runtime.setOnlineSharingEnabled(false);
                        MewClientCompat.notice("mewltiplayer.chat.resetFailed");
                    }
                }));
    }

    public static void status() {
        MewRuntime runtime = MewRuntime.INSTANCE;
        runtime.poll();
        BridgeStatus bridge = runtime.status();
        if (runtime.shareCode() != null) {
            MewClientCompat.message("mewltiplayer.chat.hostStatus", true, bridge.connections());
        } else if (runtime.sharing()) {
            MewClientCompat.message("mewltiplayer.chat.opening", false);
        } else if (runtime.joining()) {
            route(runtime.network());
        } else {
            MewClientCompat.message("mewltiplayer.chat.idle", false);
        }
    }

    public static void route(NetworkStatus network) {
        if (network == null || network.mode() == ConnectionMode.CONNECTED) {
            MewClientCompat.checkingRoute();
        } else if (network.mode() == ConnectionMode.DIRECT) {
            MewClientCompat.connectionStatus("mewltiplayer.chat.connectionDirect", network.latencyMs());
        } else if (network.mode() == ConnectionMode.RELAY) {
            MewClientCompat.connectionStatus("mewltiplayer.chat.connectionRelay", network.latencyMs(),
                    network.relay().isEmpty() ? "auto" : network.relay());
        } else {
            MewClientCompat.checkingRoute();
        }
    }

    public static void settings() {
        MewClientCompat.showSettings();
    }
}
