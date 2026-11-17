package dev.mewltiplayer.forge;

import dev.mewltiplayer.core.transport.BundledHelper;
import dev.mewltiplayer.core.transport.ConnectionInfo;
import dev.mewltiplayer.core.transport.MinecraftStatusProbe;
import dev.mewltiplayer.core.transport.ProcessTailcatBridge;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class MewServerPing {
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(3, task -> {
        Thread thread = new Thread(task, "mewltiplayer-server-ping");
        thread.setDaemon(true);
        return thread;
    });

    private MewServerPing() {}

    public static void query(String code, Consumer<MinecraftStatusProbe.Result> success,
                             Consumer<Throwable> failed) {
        WORKERS.execute(() -> {
            try {
                Path binary;
                String override = System.getenv("MEWLTIPLAYER_HELPER");
                if (override != null && !override.isEmpty()) {
                    binary = Paths.get(override);
                } else {
                    binary = BundledHelper.extract(MewRuntime.INSTANCE.configDirectory());
                }
                try (ProcessTailcatBridge bridge = new ProcessTailcatBridge(
                        binary, MewRuntime.INSTANCE.settings().activeMapUrl(),
                        MewRuntime.INSTANCE.settings().activeMapJson(), MewRuntime.INSTANCE.settings().regionId)) {
                    ConnectionInfo connection = bridge.join(code).toCompletableFuture().get(12, TimeUnit.SECONDS);
                    MinecraftStatusProbe.Result result = MinecraftStatusProbe.query(connection.localEndpoint());
                    MewClientCompat.execute(() -> success.accept(result));
                }
            } catch (Exception error) {
                MewClientCompat.execute(() -> failed.accept(error));
            }
        });
    }
}
