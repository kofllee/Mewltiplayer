package dev.mewltiplayer.core.transport;

import java.util.Objects;

public final class NetworkStatus {
    private final ConnectionMode mode;
    private final long latencyMs;
    private final String relay;

    public NetworkStatus(ConnectionMode mode, long latencyMs, String relay) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.latencyMs = latencyMs;
        this.relay = Objects.requireNonNull(relay, "relay");
    }

    public ConnectionMode mode() {
        return mode;
    }

    public long latencyMs() {
        return latencyMs;
    }

    public String relay() {
        return relay;
    }
}
