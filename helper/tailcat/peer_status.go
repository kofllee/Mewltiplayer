package main

import (
	"bufio"
	"context"
	"fmt"
	"net"
	"sort"
	"strings"
	"time"
)

type peerMeasurement struct {
	playerName string
	latencyMs  int64
	updated    time.Time
}

type peerInfo struct {
	ID        string `json:"id"`
	Mode      string `json:"mode"`
	LatencyMs *int64 `json:"latencyMs,omitempty"`
	Relay     string `json:"relay,omitempty"`
}

func validPlayerName(name string) bool {
	if len(name) < 1 || len(name) > 16 {
		return false
	}
	for _, r := range name {
		if !(r >= 'a' && r <= 'z' || r >= 'A' && r <= 'Z' || r >= '0' && r <= '9' || r == '_') {
			return false
		}
	}
	return true
}

func (s *sharedServer) track(key string, delta int) {
	s.peerMu.Lock()
	defer s.peerMu.Unlock()
	s.activePeers[key] += delta
	if s.activePeers[key] <= 0 {
		delete(s.activePeers, key)
		delete(s.measurements, key)
	}
}

func (s *sharedServer) receiveTelemetry(conn net.Conn) {
	defer conn.Close()
	key, ok := s.server.PeerKey(conn.RemoteAddr())
	if !ok {
		return
	}
	_ = conn.SetReadDeadline(time.Now().Add(3 * time.Second))
	line, err := bufio.NewReader(conn).ReadString('\n')
	if err != nil || len(line) > 128 {
		return
	}
	var latency int64
	var name string
	if _, err := fmt.Sscanf(strings.TrimSpace(line), "%d %s", &latency, &name); err != nil || latency < 0 || latency > 60000 || !validPlayerName(name) {
		return
	}
	s.peerMu.Lock()
	if s.activePeers[key.String()] > 0 {
		s.measurements[key.String()] = peerMeasurement{playerName: name, latencyMs: latency, updated: time.Now()}
	}
	s.peerMu.Unlock()
}

func (j *joinedServer) reportTelemetry(playerName string, latencyMs int64) {
	if !validPlayerName(playerName) {
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	conn, err := j.client.DialTCPPort(ctx, telemetryPort)
	if err != nil {
		return
	}
	defer conn.Close()
	_ = conn.SetWriteDeadline(time.Now().Add(2 * time.Second))
	_, _ = fmt.Fprintf(conn, "%d %s\n", latencyMs, playerName)
}

func (s *sharedServer) peerStatuses() []peerInfo {
	status := s.server.Status()
	s.peerMu.Lock()
	defer s.peerMu.Unlock()
	keys := make([]string, 0, len(s.activePeers))
	for key, n := range s.activePeers {
		if n > 0 {
			keys = append(keys, key)
		}
	}
	sort.Strings(keys)
	peers := make([]peerInfo, 0, len(keys))
	for _, key := range keys {
		info := peerInfo{ID: "Connecting", Mode: "checking"}
		for publicKey, peer := range status.Peer {
			if publicKey.String() != key || peer == nil {
				continue
			}
			if peer.CurAddr != "" {
				info.Mode = "direct"
			} else if peer.Relay != "" {
				info.Mode = "relay"
				info.Relay = peer.Relay
			}
			break
		}
		if measurement, ok := s.measurements[key]; ok && time.Since(measurement.updated) < 30*time.Second {
			info.ID = measurement.playerName
			ms := measurement.latencyMs
			info.LatencyMs = &ms
		}
		peers = append(peers, info)
	}
	return peers
}
