package dev.mewltiplayer.fabric;

import dev.mewltiplayer.core.MewWorldSettings;
import dev.mewltiplayer.core.MewSettings;
import dev.mewltiplayer.core.MewInvitations;
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
    private ConnectionMode lastRoute;

    @Override
    public void onInitializeClient() {
        MewCommands.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            MewClientCompat.openPendingSettings();
            ++ticks;
            MewRuntime runtime = MewRuntime.INSTANCE;
            if (ticks % 4 == 0 && runtime.sharing() && runtime.invite() == null) {
                MewClientCompat.updateSharing();
            }
            if (ticks % 20 != 0) return;
            if (ticks % 60 == 0) runtime.poll();

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

            if (!MewClientCompat.published() || !runtime.onlineSharingEnabled()) {
                MewClientCompat.endSharing();
                nextRetry = 0;
                startingNotified = false;
                failureNotified = false;
                if (!MewClientCompat.published()) {
                    if (runtime.onlineSharingEnabled()) runtime.setOnlineSharingEnabled(false);
                    else if (runtime.sharing()) runtime.stopShare();
                }
                return;
            }

            if (runtime.sharing() || System.nanoTime() < nextRetry) return;

            if (!startingNotified) {
                MewClientCompat.beginSharing();
                startingNotified = true;
            }
            Path world = MewClientCompat.worldDirectory();
            String mode = runtime.settings().invitationMode;
            Path identity = MewInvitations.identity(mode, world, runtime.configDirectory());
            runtime.share(MewClientCompat.worldPort(), identity).whenComplete((invite, error) -> {
                MewClientCompat.execute(() -> {
                    MewClientCompat.endSharing();
                    if (error == null) {
                        failureNotified = false;
                        if (runtime.onlineSharingEnabled() && invite.equals(runtime.invite())) {
                            try {
                                MewInvitations.record(mode, world, runtime.configDirectory(), invite);
                            } catch (java.io.IOException exception) {
                                MewClientCompat.notice("mewltiplayer.settings.saveFailed");
                            }
                            MewClientCompat.publishedInvite(invite);
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
                lastRoute = null;
                MewClientCompat.message("mewltiplayer.chat.joined", true);
                MewRuntime.INSTANCE.poll();
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
