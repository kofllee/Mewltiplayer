package dev.mewltiplayer.core;

import dev.mewltiplayer.core.MewSettings;
import dev.mewltiplayer.core.MewShareCodes;
import dev.mewltiplayer.core.transport.BridgeStatus;
import dev.mewltiplayer.core.transport.BundledHelper;
import dev.mewltiplayer.core.transport.ConnectionInfo;
import dev.mewltiplayer.core.transport.NetworkStatus;
import dev.mewltiplayer.core.transport.ProcessTailcatBridge;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MewRuntimeBase {
    private final Path configDirectory;

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
    private Path sharedIdentity;
    private String shareCode;
    private String lastError = "";
    private volatile BridgeStatus bridgeStatus = new BridgeStatus(false, false, 0, 0);
    private volatile NetworkStatus networkStatus;
    private volatile boolean helperOnline;
    private volatile boolean closed;
    private volatile boolean onlineSharingEnabled;
    private volatile boolean resetInProgress;
    private volatile boolean helperNeedsRestart;

    protected MewRuntimeBase(Path configDirectory) {
        this.configDirectory = configDirectory;
        settings = MewSettings.load(configDirectory().resolve("settings.json"));
    }

    public Path configDirectory() {
        return configDirectory;
    }

    public MewSettings settings() {
        return settings.copy();
    }

    public synchronized void saveSettings(MewSettings updated) throws IOException {
        MewSettings previous = settings;
        updated.save(configDirectory().resolve("settings.json"));
        settings = updated.copy();
        if (previous.regionId != updated.regionId
                || !previous.activeMapUrl().equals(updated.activeMapUrl())
                || !previous.activeMapJson().equals(updated.activeMapJson())) {
            helperNeedsRestart = true;
        }
        // A settings change never disconnects the current Minecraft session.
        if (helperNeedsRestart && !joining() && !sharing()) {
            executor.execute(this::closeHelper);
        }
    }

    public synchronized CompletableFuture<Void> resetShareCode(String mode, Path world, Path saves, boolean all) {
        // Resetting a code while sharing always restarts the active Tailcat session.
        // The selected mode can differ from the mode used before this reset.
        boolean restart = sharing() && onlineSharingEnabled;
        if (restart) {
            resetInProgress = true;
            stopShare();
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                if (all) MewShareCodes.resetAll(configDirectory(), saves);
                else MewShareCodes.reset(mode, world, configDirectory());
                if (restart) closeHelper();
                result.complete(null);
            } catch (IOException error) {
                result.completeExceptionally(error);
            } finally {
                resetInProgress = false;
            }
        });
        return result;
    }

    public synchronized String shareCode() {
        return shareCode;
    }

    public synchronized boolean isSharingMode(String mode, Path world) {
        return sharingFuture != null
                && Objects.equals(sharedIdentity, MewShareCodes.identity(mode, world, configDirectory()));
    }

    public synchronized boolean sharing() {
        return sharingFuture != null;
    }

    public boolean onlineSharingEnabled() {
        return onlineSharingEnabled;
    }

    public boolean resetInProgress() {
        return resetInProgress;
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
            synchronized (MewRuntimeBase.this) {
                helperOnline = false;
                bridgeStatus = new BridgeStatus(false, false, 0, 0);
                sharingFuture = null;
                joiningFuture = null;
                shareCode = null;
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

    public CompletableFuture<String> share(int port, boolean persistent) {
        return shareInternal(port, persistent ? configDirectory().resolve("server-identity.json") : null, persistent);
    }

    public CompletableFuture<String> share(int port, Path identityFile) {
        return shareInternal(port, identityFile, false);
    }

    private synchronized CompletableFuture<String> shareInternal(int port, Path identityFile, boolean dedicated) {
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
        sharedPort = port;
        sharedIdentity = identityFile;
        lastError = "";
        CompletableFuture<String> result = new CompletableFuture<>();
        sharingFuture = result;

        executor.execute(() -> {
            try {
                String code = helper().share(port, identityFile).toCompletableFuture().get(55, TimeUnit.SECONDS);
                synchronized (MewRuntimeBase.this) {
                    if (shareGeneration != generation) {
                        result.completeExceptionally(new CancellationException("Sharing cancelled"));
                        return;
                    }
                    if (dedicated) {
                        writeShareCode(code);
                    }
                    shareCode = code;
                    result.complete(code);
                }
            } catch (Exception error) {
                if (dedicated && helper != null) {
                    try {
                        helper.stopShare().toCompletableFuture().get(12, TimeUnit.SECONDS);
                    } catch (Exception ignored) {
                    }
                }
                synchronized (MewRuntimeBase.this) {
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
                synchronized (MewRuntimeBase.this) {
                    if (joinGeneration == generation) {
                        result.complete(info);
                    } else {
                        result.completeExceptionally(new CancellationException("Connection cancelled"));
                    }
                }
            } catch (Exception error) {
                synchronized (MewRuntimeBase.this) {
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
        sharedIdentity = null;
        shareCode = null;
        try {
            Files.deleteIfExists(configDirectory().resolve("share-code.txt"));
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

    /** Restart the active share without disabling the world's Tailcat sharing preference. */
    public synchronized void restartShare() {
        if (!onlineSharingEnabled || !sharing()) return;
        stopShare();
        // The executor serializes stopping the old share, closing its helper,
        // and the new share requested on the next client tick.
        executor.execute(this::closeHelper);
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
        helperNeedsRestart = false;
    }

    private ProcessTailcatBridge helper() throws IOException {
        if (helperNeedsRestart && !joining() && helper != null) closeHelper();
        if (helper != null && !helper.isAlive()) {
            closeHelper();
        }
        if (helper == null) {
            String configured = System.getenv("MEWLTIPLAYER_HELPER");
            Path executable = configured != null && !configured.isEmpty()
                    ? Paths.get(configured) : BundledHelper.extract(configDirectory());
            MewSettings current = settings;
            helper = new ProcessTailcatBridge(executable, current.activeMapUrl(), current.activeMapJson(), current.regionId);
            helperOnline = true;
        }
        return helper;
    }

    private void writeShareCode(String code) throws IOException {
        Path file = configDirectory().resolve("share-code.txt");
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
