package dev.mewltiplayer.fabric;

import dev.mewltiplayer.core.transport.ConnectionInfo;
import dev.mewltiplayer.core.transport.ProcessTailcatBridge;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class MewRuntime {
    public static final MewRuntime INSTANCE = new MewRuntime();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "mewltiplayer-runtime");
        thread.setDaemon(true);
        return thread;
    });

    private ProcessTailcatBridge helper;
    private CompletableFuture<String> sharingFuture;
    private CompletableFuture<ConnectionInfo> joiningFuture;
    private long shareGeneration;
    private long joinGeneration;
    private int sharedPort;
    private String invite;
    private boolean closed;

    private MewRuntime() {
    }

    public Path configDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("mewltiplayer");
    }

    public synchronized String invite() {
        return invite;
    }

    public synchronized boolean sharing() {
        return sharingFuture != null;
    }

    public synchronized boolean joining() {
        return joiningFuture != null;
    }

    public synchronized CompletableFuture<String> share(int port, boolean persistent) {
        if (port < 1 || port > 65535) {
            return failed(new IllegalArgumentException("Invalid Minecraft port"));
        }
        if (closed) {
            return failed(new IllegalStateException("Mewltiplayer has stopped"));
        }
        if (sharingFuture != null) {
            return sharedPort == port ? sharingFuture : failed(new IllegalStateException("Another world is already shared"));
        }

        long generation = ++shareGeneration;
        Path identity = persistent ? configDirectory().resolve("server-identity.json") : null;
        sharedPort = port;
        CompletableFuture<String> result = new CompletableFuture<>();
        sharingFuture = result;

        executor.execute(() -> {
            try {
                String code = helper().share(port, identity).toCompletableFuture().get(55, TimeUnit.SECONDS);
                synchronized (MewRuntime.this) {
                    if (shareGeneration != generation) {
                        result.completeExceptionally(new CancellationException("World sharing stopped"));
                        return;
                    }
                    if (persistent) {
                        writeInvite(code);
                    }
                    invite = code;
                    result.complete(code);
                }
            } catch (Exception error) {
                if (persistent && helper != null) {
                    try {
                        helper.stopShare().toCompletableFuture().get(15, TimeUnit.SECONDS);
                    } catch (Exception ignored) {
                    }
                }
                synchronized (MewRuntime.this) {
                    if (shareGeneration == generation) {
                        sharingFuture = null;
                        sharedPort = 0;
                    }
                }
                result.completeExceptionally(cause(error));
            }
        });
        return result;
    }

    public synchronized CompletableFuture<ConnectionInfo> join(String code) {
        if (closed) {
            return failed(new IllegalStateException("Mewltiplayer has stopped"));
        }
        if (joiningFuture != null) {
            return failed(new IllegalStateException("A Tailcat connection is already active"));
        }
        long generation = ++joinGeneration;
        CompletableFuture<ConnectionInfo> result = new CompletableFuture<>();
        joiningFuture = result;
        executor.execute(() -> {
            try {
                ConnectionInfo info = helper().join(code).toCompletableFuture().get(55, TimeUnit.SECONDS);
                synchronized (MewRuntime.this) {
                    if (joinGeneration == generation) {
                        result.complete(info);
                    } else {
                        result.completeExceptionally(new CancellationException("Connection cancelled"));
                    }
                }
            } catch (Exception error) {
                synchronized (MewRuntime.this) {
                    if (joinGeneration == generation) {
                        joiningFuture = null;
                    }
                }
                result.completeExceptionally(cause(error));
            }
        });
        return result;
    }

    public synchronized void stopShare() {
        ++shareGeneration;
        sharingFuture = null;
        sharedPort = 0;
        invite = null;
        try {
            Files.deleteIfExists(configDirectory().resolve("invite.txt"));
        } catch (IOException ignored) {
        }
        if (!executor.isShutdown()) {
            executor.execute(() -> {
                if (helper != null) {
                    helper.stopShare().toCompletableFuture().join();
                }
            });
        }
    }

    public synchronized void stopJoin() {
        ++joinGeneration;
        joiningFuture = null;
        if (!executor.isShutdown()) {
            executor.execute(() -> {
                if (helper != null) {
                    helper.stopJoin().toCompletableFuture().join();
                }
            });
        }
    }

    public synchronized void shutdown() {
        if (closed) {
            return;
        }
        closed = true;
        stopShare();
        stopJoin();
        executor.execute(() -> {
            if (helper != null) {
                helper.close();
                helper = null;
            }
        });
        executor.shutdown();
    }

    private ProcessTailcatBridge helper() throws IOException {
        if (helper == null) {
            String configured = System.getenv("MEWLTIPLAYER_HELPER");
            String executable = System.getProperty("os.name").toLowerCase().contains("win")
                    ? "mewltiplayer-helper.exe" : "mewltiplayer-helper";
            Path path = configured != null && !configured.isEmpty()
                    ? Paths.get(configured) : configDirectory().resolve(executable);
            helper = new ProcessTailcatBridge(path);
        }
        return helper;
    }

    private void writeInvite(String code) throws IOException {
        Path file = configDirectory().resolve("invite.txt");
        Files.createDirectories(file.getParent());
        Files.write(file, code.getBytes(StandardCharsets.UTF_8));
    }

    private static Throwable cause(Exception error) {
        if (error instanceof java.util.concurrent.ExecutionException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    private static <T> CompletableFuture<T> failed(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }
}
