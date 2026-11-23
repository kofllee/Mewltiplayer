package dev.mewltiplayer.forge;

import dev.mewltiplayer.core.MewShareCodes;
import dev.mewltiplayer.core.MewWorldSettings;
import dev.mewltiplayer.core.transport.ConnectionMode;
import dev.mewltiplayer.core.transport.NetworkStatus;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;

public final class MewltiplayerClient {
    private static final MewltiplayerClient INSTANCE = new MewltiplayerClient();
    private int ticks;
    private volatile long nextRetry;
    private boolean startingNotified;
    private boolean failureNotified;
    private boolean wasPublished;
    private ConnectionMode lastRoute;

    private MewltiplayerClient() {}

    public static void register() {
        MewCommands.register();
        MinecraftForge.EVENT_BUS.addListener(INSTANCE::tick);
        MinecraftForge.EVENT_BUS.addListener(INSTANCE::connected);
        MinecraftForge.EVENT_BUS.addListener(INSTANCE::disconnected);
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MewClientCompat.openPendingSettings();
        ++ticks;
        MewRuntime runtime = MewRuntime.INSTANCE;
        if (ticks % 2 == 0 && runtime.sharing() && runtime.shareCode() == null) {
            MewClientCompat.updateSharing();
        }
        if (ticks % 20 != 0) return;
        if (ticks % 60 == 0) runtime.poll();
        boolean published = MewClientCompat.published();
        if (published && !wasPublished) {
            runtime.setOnlineSharingEnabled(MewWorldSettings.shareViaTailcat(MewClientCompat.worldDirectory()));
        }
        wasPublished = published;

        if (runtime.joining() && Minecraft.getInstance().player != null) {
            NetworkStatus network = runtime.network();
            if (network != null && (network.mode() == ConnectionMode.DIRECT
                    || network.mode() == ConnectionMode.RELAY) && network.mode() != lastRoute) {
                MewClientControl.route(network);
                lastRoute = network.mode();
            }
        }

        if (!published || !runtime.onlineSharingEnabled()) {
            MewClientCompat.endSharing();
            nextRetry = 0;
            startingNotified = false;
            failureNotified = false;
            if (!published) {
                if (runtime.onlineSharingEnabled()) runtime.setOnlineSharingEnabled(false);
                else if (runtime.sharing()) runtime.stopShare();
            }
            return;
        }

        if (runtime.resetInProgress() || runtime.sharing() || System.nanoTime() < nextRetry) return;
        if (!startingNotified) {
            MewClientCompat.beginSharing();
            startingNotified = true;
        }
        Path world = MewClientCompat.worldDirectory();
        String mode = runtime.settings().shareCodeMode;
        Path identity = MewShareCodes.identity(mode, world, runtime.configDirectory());
        runtime.share(MewClientCompat.worldPort(), identity).whenComplete((code, error) -> {
            MewClientCompat.execute(() -> {
                MewClientCompat.endSharing();
                if (error == null) {
                    failureNotified = false;
                    if (runtime.onlineSharingEnabled() && code.equals(runtime.shareCode())) {
                        try {
                            MewShareCodes.record(mode, world, runtime.configDirectory(), code);
                        } catch (java.io.IOException exception) {
                            MewClientCompat.notice("mewltiplayer.settings.saveFailed");
                        }
                        MewClientCompat.publishedShareCode(code);
                    }
                } else {
                    startingNotified = false;
                    nextRetry = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                    if (!failureNotified && runtime.onlineSharingEnabled()) {
                        MewClientCompat.notice("mewltiplayer.shareFailed");
                        failureNotified = true;
                    }
                }
            });
        });
    }

    private void connected(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!MewRuntime.INSTANCE.joining()) return;
        MewRuntime.INSTANCE.poll();
        NetworkStatus network = MewRuntime.INSTANCE.network();
        lastRoute = network != null && (network.mode() == ConnectionMode.DIRECT
                || network.mode() == ConnectionMode.RELAY) ? network.mode() : null;
        MewClientControl.route(network);
    }

    private void disconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        lastRoute = null;
        MewClientActions.disconnect();
    }
}