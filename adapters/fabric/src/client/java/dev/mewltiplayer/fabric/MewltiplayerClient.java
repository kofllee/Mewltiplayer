package dev.mewltiplayer.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;

public final class MewltiplayerClient implements ClientModInitializer {
    private int ticks;
    private volatile long nextRetry;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++ticks % 20 != 0) {
                return;
            }
            MewRuntime runtime = MewRuntime.INSTANCE;
            if (ticks % 60 == 0) {
                runtime.poll();
            }
            if (!MewClientCompat.published()) {
                nextRetry = 0;
                if (runtime.onlineSharingEnabled()) {
                    runtime.setOnlineSharingEnabled(false);
                } else if (runtime.sharing()) {
                    runtime.stopShare();
                }
                return;
            }
            if (!runtime.onlineSharingEnabled() || runtime.sharing() || System.nanoTime() < nextRetry) {
                return;
            }
            runtime.share(MewClientCompat.worldPort(), false).whenComplete((invite, error) -> {
                if (error == null) {
                    MewClientCompat.execute(() -> {
                        if (runtime.onlineSharingEnabled() && invite.equals(runtime.invite())) {
                            MewClientCompat.copyInvite(invite);
                        }
                    });
                } else {
                    nextRetry = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
                    MewClientCompat.execute(() -> MewClientCompat.notice("mewltiplayer.shareFailed"));
                }
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> MewClientActions.disconnect());
        ClientLoginConnectionEvents.DISCONNECT.register((handler, client) -> MewClientActions.disconnect());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MewRuntime.INSTANCE.shutdown());
    }
}
