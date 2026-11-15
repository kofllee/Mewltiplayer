package main

import (
	"context"
	"encoding/json"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strconv"
	"sync"
	"time"

	"github.com/tailscale/tailcat"
	"tailscale.com/tailcfg"
)

const tunnelPort = 25565
const maxConnections = 32

type sessions struct {
	share *sharedServer
	join  *joinedServer
}

type sharedServer struct {
	server    *tailcat.Server
	pipes     *pipeGroup
	mapServer *httptest.Server
}

type joinedServer struct {
	client    *tailcat.Client
	mapServer *httptest.Server
	listener  net.Listener
	cancel    context.CancelFunc
	pipes     *pipeGroup
}

type pipeGroup struct {
	mu     sync.Mutex
	active map[net.Conn]struct{}
	slots  chan struct{}
	closed bool
}

func newPipeGroup() *pipeGroup {
	return &pipeGroup{active: make(map[net.Conn]struct{}), slots: make(chan struct{}, maxConnections)}
}

func (p *pipeGroup) proxy(in net.Conn, dial func() (net.Conn, error)) {
	select {
	case p.slots <- struct{}{}:
	default:
		in.Close()
		return
	}
	defer func() { <-p.slots }()
	out, err := dial()
	if err != nil {
		in.Close()
		return
	}
	p.mu.Lock()
	if p.closed {
		p.mu.Unlock()
		in.Close()
		out.Close()
		return
	}
	p.active[in] = struct{}{}
	p.active[out] = struct{}{}
	p.mu.Unlock()

	tailcat.ProxyConns(in, out)
	p.mu.Lock()
	delete(p.active, in)
	delete(p.active, out)
	p.mu.Unlock()
}

func (p *pipeGroup) count() int {
	p.mu.Lock()
	defer p.mu.Unlock()
	return len(p.active) / 2
}

func (p *pipeGroup) close() {
	p.mu.Lock()
	p.closed = true
	for conn := range p.active {
		conn.Close()
	}
	p.mu.Unlock()
}

func validDERPMapURL(address string) bool {
	if address == "" {
		return true
	}
	parsed, err := url.Parse(address)
	return err == nil && (parsed.Scheme == "https" || parsed.Scheme == "http") && parsed.Host != "" && parsed.User == nil
}

// Existing entrypoint remains for transport tests; the IPC also accepts inline JSON maps.
func (s *sessions) startShare(port int, derpMapURL string, regionID int, identityFile string) (string, string) {
	return s.startShareMap(port, derpMapURL, "", regionID, identityFile)
}

func localMap(jsonMap string) (*httptest.Server, string, bool) {
	if jsonMap == "" {
		return nil, "", true
	}
	if len(jsonMap) > 262144 {
		return nil, "", false
	}
	var dm tailcfg.DERPMap
	if err := json.Unmarshal([]byte(jsonMap), &dm); err != nil || len(dm.Regions) == 0 {
		return nil, "", false
	}
	for id, region := range dm.Regions {
		if id < 1 || region == nil || len(region.Nodes) == 0 {
			return nil, "", false
		}
	}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(jsonMap))
	}))
	return server, server.URL, true
}

func (s *sessions) startShareMap(port int, derpMapURL, derpMapJSON string, regionID int, identityFile string) (string, string) {
	if s.share != nil {
		return "", "already_sharing"
	}
	if port < 1 || port > 65535 {
		return "", "invalid_port"
	}
	if !validDERPMapURL(derpMapURL) || regionID < 0 {
		return "", "invalid_network_settings"
	}

	mapServer, localURL, ok := localMap(derpMapJSON)
	if !ok || (derpMapJSON != "" && derpMapURL != "") {
		return "", "invalid_network_settings"
	}
	if mapServer != nil {
		derpMapURL = localURL
		defer func() {
			if mapServer != nil {
				mapServer.Close()
			}
		}()
	}
	private, preshared, err := loadIdentity(identityFile)
	if err != nil {
		return "", "identity_unavailable"
	}
	if identityFile != "" && regionID == 0 {
		regionID, err = savedRegion(identityFile)
		if err != nil {
			return "", "identity_unavailable"
		}
	}
	pipes := newPipeGroup()
	server := &tailcat.Server{
		Key:          private,
		PresharedKey: preshared,
		DERPMapURL:   derpMapURL,
		RegionID:     tailcfg.DERPRegionID(regionID),
		Logf:         func(string, ...any) {},
		OnTCP: func(remotePort uint16) func(net.Conn) {
			if remotePort != tunnelPort {
				return nil
			}
			return func(conn net.Conn) {
				go pipes.proxy(conn, func() (net.Conn, error) {
					return net.DialTimeout("tcp", net.JoinHostPort("127.0.0.1", strconv.Itoa(port)), 5*time.Second)
				})
			}
		},
	}
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	if err := server.StartContext(ctx); err != nil {
		server.Close()
		return "", "share_start_failed"
	}
	code := server.TailcatAddr()
	if identityFile != "" {
		info, parseErr := tailcat.ParseAddr(code)
		if parseErr != nil || saveRegion(identityFile, int(info.RegionID)) != nil {
			server.Close()
			return "", "identity_unavailable"
		}
	}
	s.share = &sharedServer{server: server, pipes: pipes, mapServer: mapServer}
	mapServer = nil
	return string(code), ""
}

func (s *sessions) startJoin(code, derpMapURL string) (int, string) {
	return s.startJoinMap(code, derpMapURL, "")
}

func (s *sessions) startJoinMap(code, derpMapURL, derpMapJSON string) (int, string) {
	if s.join != nil {
		return 0, "already_joined"
	}
	if !validDERPMapURL(derpMapURL) {
		return 0, "invalid_network_settings"
	}
	if _, err := tailcat.ParseAddr(tailcat.Addr(code)); err != nil {
		return 0, "invalid_share_code"
	}

	mapServer, localURL, ok := localMap(derpMapJSON)
	if !ok || (derpMapJSON != "" && derpMapURL != "") {
		return 0, "invalid_network_settings"
	}
	if mapServer != nil {
		derpMapURL = localURL
	}
	client := &tailcat.Client{Server: tailcat.Addr(code), DERPMapURL: derpMapURL, Logf: func(string, ...any) {}}
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		client.Close()
		if mapServer != nil {
			mapServer.Close()
		}
		return 0, "local_listener_failed"
	}
	sessionCtx, stop := context.WithCancel(context.Background())
	joined := &joinedServer{client: client, mapServer: mapServer, listener: listener, cancel: stop, pipes: newPipeGroup()}
	s.join = joined
	go joined.accept(sessionCtx)
	return listener.Addr().(*net.TCPAddr).Port, ""
}

func (j *joinedServer) accept(ctx context.Context) {
	for {
		conn, err := j.listener.Accept()
		if err != nil {
			return
		}
		go j.pipes.proxy(conn, func() (net.Conn, error) {
			dialCtx, cancel := context.WithTimeout(ctx, 20*time.Second)
			defer cancel()
			return j.client.DialTCPPort(dialCtx, tunnelPort)
		})
	}
}

func (s *sessions) probe() (string, int64, string, string) {
	if s.join == nil {
		return "", 0, "", "not_joined"
	}
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	result, err := s.join.client.DiscoPing(ctx)
	if err != nil {
		return "", 0, "", "probe_failed"
	}

	mode := "connected"
	if result.Endpoint != "" {
		mode = "direct"
	} else if result.DERPRegionID > 0 {
		mode = "relay"
	}
	return mode, int64(result.LatencySeconds * 1000), result.DERPRegionCode, ""
}

func (s *sessions) stopShare() {
	if s.share == nil {
		return
	}
	s.share.pipes.close()
	s.share.server.Close()
	if s.share.mapServer != nil {
		s.share.mapServer.Close()
	}
	s.share = nil
}

func (s *sessions) stopJoin() {
	if s.join == nil {
		return
	}
	s.join.cancel()
	s.join.listener.Close()
	s.join.pipes.close()
	s.join.client.Close()
	if s.join.mapServer != nil {
		s.join.mapServer.Close()
	}
	s.join = nil
}

func (s *sessions) close() {
	s.stopJoin()
	s.stopShare()
}
