package dev.mewltiplayer.core.transport;

import java.util.Collections;
import java.util.List;

public final class BridgeStatus {
    public static final class Peer {
        private final String id;
        private final String mode;
        private final long latencyMs;
        private final String relay;

        public Peer(String id, String mode, long latencyMs, String relay) {
            this.id = id;
            this.mode = mode;
            this.latencyMs = latencyMs;
            this.relay = relay;
        }

        public String description() {
            String path = "direct".equals(mode) ? "Direct P2P"
                    : "relay".equals(mode) ? "DERP" + (relay.isEmpty() ? "" : " " + relay)
                    : "Checking route";
            return id + ": " + path + (latencyMs < 0 ? " (checking latency)" : "  " + latencyMs + " ms");
        }
    }

    private final boolean sharing;
    private final boolean joining;
    private final int localPort;
    private final int connections;
    private final List<Peer> peers;

    public BridgeStatus(boolean sharing, boolean joining, int localPort, int connections) {
        this(sharing, joining, localPort, connections, Collections.emptyList());
    }

    public BridgeStatus(boolean sharing, boolean joining, int localPort, int connections, List<Peer> peers) {
        this.sharing = sharing;
        this.joining = joining;
        this.localPort = localPort;
        this.connections = connections;
        this.peers = Collections.unmodifiableList(new java.util.ArrayList<>(peers));
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

    public List<Peer> peers() {
        return peers;
    }
}
