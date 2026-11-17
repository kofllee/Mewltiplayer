package dev.mewltiplayer.forge;

import dev.mewltiplayer.core.MewErrors;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class MewClientActions {
    private static final AtomicLong connectionId = new AtomicLong();

    private MewClientActions() {
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
                        failed.accept(MewErrors.code(error));
                    } else {
                        connected.accept(info.localEndpoint().getPort());
                    }
                }));
    }

    public static void disconnect() {
        connectionId.incrementAndGet();
        MewRuntime.INSTANCE.stopJoin();
    }
}
