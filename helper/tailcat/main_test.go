package main

import (
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestProtocol(t *testing.T) {
	input := strings.Join([]string{
		`{"id":"hello","type":"hello","protocolVersion":1}`,
		`{"id":"ping","type":"ping","protocolVersion":1}`,
		`{"id":"bad","type":"share","protocolVersion":1,"port":0}`,
		`{"id":"join","type":"join","protocolVersion":1,"code":"not-an-invite"}`,
		`{"id":"status","type":"status","protocolVersion":1}`,
		`{"id":"bye","type":"shutdown","protocolVersion":1}`,
	}, "\n") + "\n"
	var output bytes.Buffer
	if err := run(strings.NewReader(input), &output); err != nil {
		t.Fatal(err)
	}

	scanner := bufio.NewScanner(&output)
	expected := []struct {
		id    string
		ok    bool
		error string
	}{
		{"hello", true, ""},
		{"ping", true, ""},
		{"bad", false, "invalid_port"},
		{"join", false, "invalid_invite"},
		{"status", true, ""},
		{"bye", true, ""},
	}
	for _, want := range expected {
		if !scanner.Scan() {
			t.Fatalf("missing response for %s", want.id)
		}
		var got response
		if err := json.Unmarshal(scanner.Bytes(), &got); err != nil {
			t.Fatal(err)
		}
		if got.ID != want.id || got.OK != want.ok || got.Error != want.error || got.ProtocolVersion != protocolVersion {
			t.Fatalf("response for %s: %+v", want.id, got)
		}
		if want.id == "hello" && got.HelperVersion == "" {
			t.Fatal("missing helper version")
		}
	}
	if scanner.Scan() {
		t.Fatal("extra response")
	}
}

func TestValidation(t *testing.T) {
	s := &sessions{}
	for _, test := range []struct {
		req   request
		error string
	}{
		{request{ID: "a", Type: "ping", ProtocolVersion: 2}, "unsupported_protocol_version"},
		{request{Type: "ping", ProtocolVersion: 1}, "missing_id"},
		{request{ID: "a", Type: "bad", ProtocolVersion: 1}, "unknown_command"},
		{request{ID: "a", Type: "probe", ProtocolVersion: 1}, "not_joined"},
		{request{ID: "a", Type: "share", ProtocolVersion: 1, Port: 65536}, "invalid_port"},
		{request{ID: "a", Type: "share", ProtocolVersion: 1, Port: 25565, DERPMapURL: "file:///etc/passwd"}, "invalid_network_settings"},
	} {
		if got := s.handle(test.req); got.Error != test.error {
			t.Fatalf("unexpected response: %+v", got)
		}
	}
}

func TestTCPBridge(t *testing.T) {
	if os.Getenv("MEWLTIPLAYER_E2E") != "1" {
		t.Skip("requires live Tailcat DERP connectivity")
	}

	echo, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer echo.Close()
	go func() {
		for {
			conn, err := echo.Accept()
			if err != nil {
				return
			}
			go func() {
				defer conn.Close()
				io.Copy(conn, conn)
			}()
		}
	}()

	host := &sessions{}
	defer host.close()
	guest := &sessions{}
	defer guest.close()

	code, failure := host.startShare(echo.Addr().(*net.TCPAddr).Port, "", 0, "")
	if failure != "" {
		t.Fatalf("host: %s", failure)
	}
	port, failure := guest.startJoin(code, "")
	if failure != "" {
		t.Fatalf("guest: %s", failure)
	}

	conn, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", port), 10*time.Second)
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	conn.SetDeadline(time.Now().Add(20 * time.Second))
	if _, err := conn.Write([]byte("hello")); err != nil {
		t.Fatal(err)
	}
	result := make([]byte, 5)
	if _, err := io.ReadFull(conn, result); err != nil {
		t.Fatal(err)
	}
	if string(result) != "hello" {
		t.Fatalf("unexpected response: %q", result)
	}
	mode, latency, _, errCode := guest.probe()
	if errCode != "" || (mode != "direct" && mode != "relay" && mode != "connected") || latency < 0 {
		t.Fatalf("unexpected network probe: mode=%s latency=%d error=%s", mode, latency, errCode)
	}
	guest.stopJoin()
	host.stopShare()
}

func TestPersistentInvitation(t *testing.T) {
	if os.Getenv("MEWLTIPLAYER_E2E") != "1" {
		t.Skip("requires live Tailcat DERP connectivity")
	}
	identity := filepath.Join(t.TempDir(), "server-identity.json")
	var codes [2]string
	for i := range codes {
		server := &sessions{}
		code, failure := server.startShare(25565, "", 0, identity)
		if failure != "" {
			t.Fatalf("share %d: %s", i, failure)
		}
		codes[i] = code
		server.stopShare()
	}
	if codes[0] != codes[1] {
		t.Fatal("dedicated invitation changed after restart")
	}
}

func TestJoinCreatesListenerBeforeNetworkDial(t *testing.T) {
	const example = "tcomFwWCCcjS5nKNqAod034nWoJZW0LZqDhhC8U_dKdnDRYQ8uNGFpGQEu"
	session := &sessions{}
	defer session.close()
	port, failure := session.startJoin(example, "")
	if failure != "" || port <= 0 {
		t.Fatalf("local listener not created: port=%d error=%s", port, failure)
	}
}
