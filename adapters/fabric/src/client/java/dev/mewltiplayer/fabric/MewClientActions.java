package dev.mewltiplayer.fabric;

import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class MewClientActions {
    private static final AtomicLong connectionId = new AtomicLong();

    private MewClientActions() {
    }

    public static void share() {
        MewClientCompat.publishWorld().whenComplete((published, error) -> {
            if (error != null || !published) {
                MewClientCompat.execute(() -> MewClientCompat.notice("Unable to open world to LAN"));
                return;
            }
            int port = MewClientCompat.worldPort();
            MewRuntime.INSTANCE.share(port, false).whenComplete((code, shareError) ->
                    MewClientCompat.execute(() -> {
                        if (shareError == null) {
                            if (MewClientCompat.published() && code.equals(MewRuntime.INSTANCE.invite())) {
                                MewClientCompat.copyInvite(code);
                            }
                        } else {
                            MewClientCompat.notice("Unable to share world: " + explanation(shareError));
                        }
                    }));
        });
    }

    public static void join(String code, IntConsumer connected, Consumer<String> failed) {
        long id = connectionId.incrementAndGet();
        MewRuntime.INSTANCE.stopJoin();
        MewRuntime.INSTANCE.join(code).whenComplete((info, error) ->
                MewClientCompat.execute(() -> {
                    if (connectionId.get() != id) {
                        return;
                    }
                    if (error != null) {
                        failed.accept("Tailcat connection failed: " + explanation(error));
                    } else {
                        connected.accept(info.localEndpoint().getPort());
                    }
                }));
    }

    public static void disconnect() {
        connectionId.incrementAndGet();
        MewRuntime.INSTANCE.stopJoin();
    }

    private static String explanation(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
    }
}
