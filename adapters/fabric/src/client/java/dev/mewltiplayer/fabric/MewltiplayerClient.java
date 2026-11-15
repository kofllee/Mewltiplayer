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
            if (ticks % 60 == 0) {
                MewRuntime.INSTANCE.poll();
            }
            if (MewClientCompat.published()) {
                if (MewRuntime.INSTANCE.settings().autoShare
                        && !MewRuntime.INSTANCE.sharingPaused()
                        && !MewRuntime.INSTANCE.sharing()
                        && System.nanoTime() >= nextRetry) {
                    MewRuntime.INSTANCE.share(MewClientCompat.worldPort(), false).whenComplete((code, error) -> {
                        if (error != null) {
                            nextRetry = System.nanoTime()
                                    + java.util.concurrent.TimeUnit.SECONDS.toNanos(MewRuntime.INSTANCE.settings().retrySeconds);
                            MewClientCompat.execute(() -> MewClientCompat.notice("Tailcat sharing unavailable"));
                        }
                    });
                }
            } else {
                nextRetry = 0;
                MewRuntime.INSTANCE.clearSharePause();
                if (MewRuntime.INSTANCE.sharing()) {
                    MewRuntime.INSTANCE.stopShare();
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> MewClientActions.disconnect());
        ClientLoginConnectionEvents.DISCONNECT.register((handler, client) -> MewClientActions.disconnect());
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> MewRuntime.INSTANCE.warmup());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MewRuntime.INSTANCE.shutdown());
    }
}
