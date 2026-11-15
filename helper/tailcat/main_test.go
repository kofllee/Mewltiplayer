package main

import "testing"

func TestHandshake(t *testing.T) {
    got := handle(request{ID: "a", Type: "hello", ProtocolVersion: 1})
    if !got.OK || got.ID != "a" || got.HelperVersion == "" {
        t.Fatalf("unexpected handshake: %+v", got)
    }
}

func TestTransportNotYetImplemented(t *testing.T) {
    got := handle(request{Type: "join", ProtocolVersion: 1})
    if got.OK || got.Error != "not_implemented" {
        t.Fatalf("unexpected transport response: %+v", got)
    }
}
