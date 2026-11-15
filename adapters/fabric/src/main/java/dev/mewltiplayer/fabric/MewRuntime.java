package dev.mewltiplayer.fabric;

import dev.mewltiplayer.core.MewSettings;
import dev.mewltiplayer.core.transport.BridgeStatus;
import dev.mewltiplayer.core.transport.BundledHelper;
import dev.mewltiplayer.core.transport.ConnectionInfo;
import dev.mewltiplayer.core.transport.NetworkStatus;
import dev.mewltiplayer.core.transport.ProcessTailcatBridge;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MewRuntime {
    public static final MewRuntime INSTANCE = new MewRuntime();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "mewltiplayer-runtime");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicBoolean polling = new AtomicBoolean();
    private volatile MewSettings settings;
    private ProcessTailcatBridge helper;
    private CompletableFuture<String> sharingFuture;
    private CompletableFuture<ConnectionInfo> joiningFuture;
    private long shareGeneration;
    private long joinGeneration;
    private int sharedPort;
    private String invite;
    private String lastError = "";
    private volatile BridgeStatus bridgeStatus = new BridgeStatus(false, false, 0, 0);
    private volatile NetworkStatus networkStatus;
    private volatile boolean helperOnline;
    private volatile boolean closed;
    private volatile boolean onlineSharingEnabled;

    private MewRuntime() {
        settings = MewSettings.load(configDirectory().resolve("settings.json"));
    }

    public Path configDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("mewltiplayer");
    }

    public MewSettings settings() {
        return settings.copy();
    }

    public synchronized void saveSettings(MewSettings updated) throws IOException {
        updated.save(configDirectory().resolve("settings.json"));
        settings = updated.copy();
        if (!sharing() && !joining()) {
            executor.execute(() -> closeHelper());
        }
    }

    public synchronized String invite() {
        return invite;
    }

    public synchronized boolean sharing() {
        return sharingFuture != null;
    }

    public boolean onlineSharingEnabled() {
        return onlineSharingEnabled;
    }

    public synchronized void setOnlineSharingEnabled(boolean enabled) {
        if (onlineSharingEnabled == enabled) {
            return;
        }
        onlineSharingEnabled = enabled;
        if (!enabled) {
            stopShare();
        }
    }

    public synchronized boolean joining() {
        return joiningFuture != null;
    }

    public synchronized String error() {
        return lastError;
    }

    public BridgeStatus status() {
        return bridgeStatus;
    }

    public NetworkStatus network() {
        return networkStatus;
    }

    public boolean helperOnline() {
        return helperOnline;
    }

    public void poll() {
        if (closed || !helperOnline || !polling.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    pollHelper();
                } finally {
                    polling.set(false);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            polling.set(false);
        }
    }

    private void pollHelper() {
        if (helper == null) {
            return;
        }
        if (!helper.isAlive()) {
            synchronized (MewRuntime.this) {
                helperOnline = false;
                bridgeStatus = new BridgeStatus(false, false, 0, 0);
                sharingFuture = null;
                joiningFuture = null;
                invite = null;
                lastError = "Tailcat helper exited unexpectedly";
            }
            closeHelper();
            return;
        }
        try {
            bridgeStatus = helper.status().toCompletableFuture().get(12, TimeUnit.SECONDS);
            if (joining() && bridgeStatus.isJoining()) {
                networkStatus = helper.probe().toCompletableFuture().get(10, TimeUnit.SECONDS);
            } else {
                networkStatus = null;
            }
        } catch (Exception error) {
            recordError(error);
        }
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
        lastError = "";
        CompletableFuture<String> result = new CompletableFuture<>();
        sharingFuture = result;

        executor.execute(() -> {
            try {
                String code = helper().share(port, identity).toCompletableFuture().get(55, TimeUnit.SECONDS);
                synchronized (MewRuntime.this) {
                    if (shareGeneration != generation) {
                        result.completeExceptionally(new CancellationException("Sharing cancelled"));
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
                        helper.stopShare().toCompletableFuture().get(12, TimeUnit.SECONDS);
                    } catch (Exception ignored) {
                    }
                }
                synchronized (MewRuntime.this) {
                    if (shareGeneration == generation) {
                        sharingFuture = null;
                        sharedPort = 0;
                        recordError(error);
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
        lastError = "";
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
                        recordError(error);
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
                if (helper != null && helper.isAlive()) {
                    try {
                        helper.stopShare().toCompletableFuture().get(12, TimeUnit.SECONDS);
                    } catch (Exception error) {
                        recordError(error);
                    }
                }
            });
        }
    }

    public synchronized void stopJoin() {
        ++joinGeneration;
        joiningFuture = null;
        networkStatus = null;
        if (!executor.isShutdown()) {
            executor.execute(() -> {
                if (helper != null && helper.isAlive()) {
                    try {
                        helper.stopJoin().toCompletableFuture().get(12, TimeUnit.SECONDS);
                    } catch (Exception error) {
                        recordError(error);
                    }
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
        executor.execute(this::closeHelper);
        executor.shutdown();
    }

    private void closeHelper() {
        if (helper != null) {
            helper.close();
            helper = null;
        }
        helperOnline = false;
    }

    private ProcessTailcatBridge helper() throws IOException {
        if (helper != null && !helper.isAlive()) {
            closeHelper();
        }
        if (helper == null) {
            String configured = System.getenv("MEWLTIPLAYER_HELPER");
            Path executable = configured != null && !configured.isEmpty()
                    ? Paths.get(configured) : BundledHelper.extract(configDirectory());
            MewSettings current = settings;
            helper = new ProcessTailcatBridge(executable, current.derpMapUrl, current.regionId);
            helperOnline = true;
        }
        return helper;
    }

    private void writeInvite(String code) throws IOException {
        Path file = configDirectory().resolve("invite.txt");
        Files.createDirectories(file.getParent());
        Files.write(file, code.getBytes(StandardCharsets.UTF_8));
    }

    private synchronized void recordError(Throwable error) {
        Throwable cause = cause(error);
        lastError = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private static Throwable cause(Throwable error) {
        while ((error instanceof java.util.concurrent.CompletionException
                || error instanceof java.util.concurrent.ExecutionException) && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    private static <T> CompletableFuture<T> failed(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }
}
