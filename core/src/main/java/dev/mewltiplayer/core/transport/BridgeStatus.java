package dev.mewltiplayer.core.transport;

public final class BridgeStatus {
    private final boolean sharing;
    private final boolean joining;
    private final int localPort;

    public BridgeStatus(boolean sharing, boolean joining, int localPort) {
        this.sharing = sharing;
        this.joining = joining;
        this.localPort = localPort;
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
}
