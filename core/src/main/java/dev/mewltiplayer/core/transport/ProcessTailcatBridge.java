package dev.mewltiplayer.core.transport;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ProcessTailcatBridge implements TailcatBridge {
    private final TailcatProcess process;
    private final String derpMapUrl;
    private final String derpMapJson;
    private final int regionId;
    private volatile String playerName = "";

    public ProcessTailcatBridge(Path helperExecutable) throws IOException {
        this(helperExecutable, "", "", 0);
    }

    public ProcessTailcatBridge(Path helperExecutable, String derpMapUrl, int regionId) throws IOException {
        this(helperExecutable, derpMapUrl, "", regionId);
    }

    public ProcessTailcatBridge(Path helperExecutable, String derpMapUrl, String derpMapJson, int regionId) throws IOException {
        Objects.requireNonNull(helperExecutable, "helperExecutable");
        if (!Files.isRegularFile(helperExecutable)) {
            throw new IOException("Tailcat helper not found: " + helperExecutable);
        }
        if (regionId < 0 || !validUrl(derpMapUrl)) {
            throw new IllegalArgumentException("Invalid Tailcat network settings");
        }
        if (derpMapJson != null && !derpMapJson.isEmpty() && derpMapJson.length() > 262144) {
            throw new IllegalArgumentException("DERP map too large");
        }
        this.derpMapUrl = derpMapUrl == null ? "" : derpMapUrl;
        this.derpMapJson = derpMapJson == null ? "" : derpMapJson;
        this.regionId = regionId;
        process = new TailcatProcess(Collections.singletonList(helperExecutable.toAbsolutePath().toString()));
        try {
            JsonObject hello = process.request("hello", null, 10).get(12, TimeUnit.SECONDS);
            if (!hello.has("helperVersion")) {
                throw new IOException("Unrecognized Tailcat helper");
            }
        } catch (InterruptedException error) {
            process.close();
            Thread.currentThread().interrupt();
            throw new IOException("Tailcat helper startup interrupted", error);
        } catch (ExecutionException | TimeoutException error) {
            process.close();
            throw new IOException("Tailcat helper failed to initialize", error);
        } catch (IOException error) {
            process.close();
            throw error;
        }
    }

    @Override
    public CompletionStage<String> share(int minecraftPort) {
        return share(minecraftPort, null);
    }

    @Override
    public CompletionStage<String> share(int minecraftPort, Path identityFile) {
        if (minecraftPort < 1 || minecraftPort > 65535) {
            throw new IllegalArgumentException("Invalid Minecraft port");
        }
        JsonObject args = networkArguments();
        args.addProperty("port", minecraftPort);
        if (identityFile != null) {
            args.addProperty("identityFile", identityFile.toAbsolutePath().toString());
        }
        return process.request("share", args, 45).thenApply(reply -> reply.get("code").getAsString());
    }

    @Override
    public CompletionStage<ConnectionInfo> join(String shareCode) {
        if (shareCode == null || !shareCode.trim().startsWith("tc") || shareCode.trim().length() > 8192) {
            throw new IllegalArgumentException("Invalid Tailcat share code");
        }
        JsonObject args = networkArguments();
        args.addProperty("code", shareCode.trim());
        return process.request("join", args, 45).thenApply(reply ->
                new ConnectionInfo(new InetSocketAddress("127.0.0.1", reply.get("localPort").getAsInt()), ConnectionMode.CONNECTED));
    }

    @Override
    public CompletionStage<Void> stopJoin() {
        return process.request("stopJoin", null, 10).thenApply(reply -> null);
    }

    @Override
    public CompletionStage<Void> stopShare() {
        return process.request("stopShare", null, 10).thenApply(reply -> null);
    }

    @Override
    public CompletionStage<BridgeStatus> status() {
        return process.request("status", null, 10).thenApply(reply -> {
            java.util.List<BridgeStatus.Peer> peers = new java.util.ArrayList<>();
            if (reply.has("peers") && reply.get("peers").isJsonArray()) {
                JsonArray array = reply.getAsJsonArray("peers");
                for (com.google.gson.JsonElement element : array) {
                    JsonObject peer = element.getAsJsonObject();
                    peers.add(new BridgeStatus.Peer(
                            peer.get("id").getAsString(),
                            peer.get("mode").getAsString(),
                            peer.has("latencyMs") ? peer.get("latencyMs").getAsLong() : -1,
                            peer.has("relay") ? peer.get("relay").getAsString() : ""));
                }
            }
            return new BridgeStatus(
                        reply.has("sharing") && reply.get("sharing").getAsBoolean(),
                        reply.has("joining") && reply.get("joining").getAsBoolean(),
                        reply.has("localPort") ? reply.get("localPort").getAsInt() : 0,
                        reply.has("connections") ? reply.get("connections").getAsInt() : 0,
                        peers);
        });
    }

    @Override
    public CompletionStage<NetworkStatus> probe() {
        JsonObject args = new JsonObject();
        args.addProperty("playerName", playerName);
        return process.request("probe", args, 10).thenApply(reply -> {
            String path = reply.get("mode").getAsString();
            ConnectionMode mode = ConnectionMode.CONNECTED;
            if ("direct".equals(path)) {
                mode = ConnectionMode.DIRECT;
            } else if ("relay".equals(path)) {
                mode = ConnectionMode.RELAY;
            }
            long latencyMs = reply.has("latencyMs") ? reply.get("latencyMs").getAsLong() : 0;
            String relay = reply.has("relay") ? reply.get("relay").getAsString() : "";
            return new NetworkStatus(mode, latencyMs, relay);
        });
    }

    public void setPlayerName(String name) {
        playerName = name == null ? "" : name;
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    @Override
    public void close() {
        process.close();
    }

    private JsonObject networkArguments() {
        JsonObject args = new JsonObject();
        if (!derpMapUrl.isEmpty()) {
            args.addProperty("derpMapUrl", derpMapUrl);
        }
        if (!derpMapJson.isEmpty()) {
            args.addProperty("derpMapJson", derpMapJson);
        }
        if (regionId > 0) {
            args.addProperty("regionId", regionId);
        }
        return args;
    }

    private static boolean validUrl(String input) {
        if (input == null || input.isEmpty()) {
            return true;
        }
        try {
            URI uri = URI.create(input);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }
}
