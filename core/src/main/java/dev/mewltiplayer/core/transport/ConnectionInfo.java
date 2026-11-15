package dev.mewltiplayer.core.transport;

import java.net.InetSocketAddress;
import java.util.Objects;

public final class ConnectionInfo {
    private final InetSocketAddress localEndpoint;
    private final ConnectionMode mode;

    public ConnectionInfo(InetSocketAddress localEndpoint, ConnectionMode mode) {
        this.localEndpoint = Objects.requireNonNull(localEndpoint, "localEndpoint");
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    public InetSocketAddress localEndpoint() {
        return localEndpoint;
    }

    public ConnectionMode mode() {
        return mode;
    }
}
