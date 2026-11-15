package dev.mewltiplayer.core.transport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class TailcatProcess implements AutoCloseable {
    private final Process process;
    private final Thread shutdownHook;
    private final BufferedWriter input;
    private final Map<String, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor(task -> daemon(task, "tailcat-timeout"));
    private final ExecutorService readers = Executors.newFixedThreadPool(2, task -> daemon(task, "tailcat-reader"));
    private final AtomicLong nextId = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();

    TailcatProcess(List<String> command) throws IOException {
        process = new ProcessBuilder(command).start();
        shutdownHook = new Thread(process::destroyForcibly, "mewltiplayer-helper-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        readers.execute(this::readResponses);
        readers.execute(this::drainErrors);
    }

    CompletableFuture<JsonObject> request(String type, JsonObject arguments, int timeoutSeconds) {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        if (closed.get()) {
            future.completeExceptionally(new IOException("Tailcat helper is closed"));
            return future;
        }

        String id = Long.toString(nextId.incrementAndGet());
        JsonObject message = new JsonObject();
        message.addProperty("id", id);
        message.addProperty("type", type);
        message.addProperty("protocolVersion", BridgeProtocol.VERSION);
        if (arguments != null) {
            for (Map.Entry<String, JsonElement> entry : arguments.entrySet()) {
                message.add(entry.getKey(), entry.getValue());
            }
        }

        pending.put(id, future);
        ScheduledFuture<?> timeout;
        try {
            timeout = timers.schedule(() -> {
                if (pending.remove(id, future)) {
                    future.completeExceptionally(new IOException("Tailcat helper request timed out: " + type));
                }
            }, timeoutSeconds, TimeUnit.SECONDS);
        } catch (java.util.concurrent.RejectedExecutionException error) {
            pending.remove(id, future);
            future.completeExceptionally(new IOException("Tailcat helper is closed", error));
            return future;
        }
        future.whenComplete((result, error) -> timeout.cancel(false));

        try {
            synchronized (input) {
                if (closed.get()) {
                    throw new IOException("Tailcat helper is closed");
                }
                input.write(message.toString());
                input.newLine();
                input.flush();
            }
        } catch (IOException error) {
            pending.remove(id, future);
            future.completeExceptionally(error);
        }
        return future;
    }

    private void readResponses() {
        try (BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = output.readLine()) != null) {
                JsonObject message = JsonParser.parseString(line).getAsJsonObject();
                if (!message.has("protocolVersion") || message.get("protocolVersion").getAsInt() != BridgeProtocol.VERSION) {
                    throw new IOException("Unsupported Tailcat helper protocol");
                }
                if (!message.has("id")) {
                    throw new IOException("Tailcat helper response has no request ID");
                }
                CompletableFuture<JsonObject> future = pending.remove(message.get("id").getAsString());
                if (future == null) {
                    continue;
                }
                if (message.has("ok") && message.get("ok").getAsBoolean()) {
                    future.complete(message);
                } else {
                    String error = message.has("error") ? message.get("error").getAsString() : "unknown_error";
                    future.completeExceptionally(new IOException("Tailcat helper: " + error));
                }
            }
        } catch (Exception error) {
            failPending(error);
        } finally {
            close();
        }
    }

    private void drainErrors() {
        try (InputStream errors = process.getErrorStream()) {
            byte[] buffer = new byte[4096];
            while (errors.read(buffer) != -1) {
                // Never forward network logs or invitation secrets to Minecraft logs.
            }
        } catch (IOException ignored) {
        }
    }

    private void failPending(Exception error) {
        for (Map.Entry<String, CompletableFuture<JsonObject>> entry : pending.entrySet()) {
            if (pending.remove(entry.getKey(), entry.getValue())) {
                entry.getValue().completeExceptionally(error);
            }
        }
    }

    boolean isAlive() {
        return !closed.get() && process.isAlive();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            input.close();
        } catch (IOException ignored) {
        }
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroy();
                if (!process.waitFor(1, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            }
        } catch (InterruptedException error) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        } finally {
            failPending(new IOException("Tailcat helper stopped"));
            timers.shutdownNow();
            readers.shutdownNow();
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
            }
        }
    }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, "mewltiplayer-" + name);
        thread.setDaemon(true);
        return thread;
    }
}
