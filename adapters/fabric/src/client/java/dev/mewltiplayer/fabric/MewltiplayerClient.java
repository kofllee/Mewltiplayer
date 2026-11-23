package dev.mewltiplayer.fabric;

import dev.mewltiplayer.core.MewWorldSettings;
import dev.mewltiplayer.core.MewSettings;
import dev.mewltiplayer.core.MewShareCodes;
import dev.mewltiplayer.core.transport.ConnectionMode;
import dev.mewltiplayer.core.transport.NetworkStatus;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;

public final class MewltiplayerClient implements ClientModInitializer {
    private int ticks;
    private volatile long nextRetry;
    private boolean startingNotified;
    private boolean failureNotified;
    private boolean wasPublished;
    private ConnectionMode lastRoute;

    @Override
    public void onInitializeClient() {
        MewCommands.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
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

            // A route is determined by the Tailcat probe after the Minecraft login succeeds.
            if (runtime.joining() && client.player != null) {
                NetworkStatus network = runtime.network();
                if (network != null && (network.mode() == ConnectionMode.DIRECT
                        || network.mode() == ConnectionMode.RELAY)
                        && network.mode() != lastRoute) {
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
            runtime.share(MewClientCompat.worldPort(), identity).whenComplete((shareCode, error) -> {
                MewClientCompat.execute(() -> {
                    MewClientCompat.endSharing();
                    if (error == null) {
                        failureNotified = false;
                        if (runtime.onlineSharingEnabled() && shareCode.equals(runtime.shareCode())) {
                            try {
                                MewShareCodes.record(mode, world, runtime.configDirectory(), shareCode);
                            } catch (java.io.IOException exception) {
                                MewClientCompat.notice("mewltiplayer.settings.saveFailed");
                            }
                            MewClientCompat.publishedShareCode(shareCode);
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
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (MewRuntime.INSTANCE.joining()) {
                MewRuntime.INSTANCE.poll();
                NetworkStatus network = MewRuntime.INSTANCE.network();
                lastRoute = network != null && (network.mode() == ConnectionMode.DIRECT
                        || network.mode() == ConnectionMode.RELAY) ? network.mode() : null;
                MewClientControl.route(network);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            lastRoute = null;
            MewClientActions.disconnect();
        });
        ClientLoginConnectionEvents.DISCONNECT.register((handler, client) -> {
            lastRoute = null;
            MewClientActions.disconnect();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MewRuntime.INSTANCE.shutdown());
    }
}
