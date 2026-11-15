package dev.mewltiplayer.core.transport;

public final class BridgeStatus {
    private final boolean sharing;
    private final boolean joining;
    private final int localPort;
    private final int connections;

    public BridgeStatus(boolean sharing, boolean joining, int localPort, int connections) {
        this.sharing = sharing;
        this.joining = joining;
        this.localPort = localPort;
        this.connections = connections;
    }

    public boolean isSharing() {
        return sharing;
    }

    public boolean isJoining() {
        return joining;
    }

    public int localPort() {
        return localPort;
    }

    public int connections() {
        return connections;
    }
}
