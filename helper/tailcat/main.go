package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"os"
	"strings"
)

const protocolVersion = 1
const helperVersion = "0.2.0"

type request struct {
	ID              string `json:"id"`
	Type            string `json:"type"`
	ProtocolVersion int    `json:"protocolVersion"`
	Port            int    `json:"port,omitempty"`
	Code            string `json:"code,omitempty"`
	DERPMapURL      string `json:"derpMapUrl,omitempty"`
	RegionID        int    `json:"regionId,omitempty"`
}

type response struct {
	ID              string `json:"id,omitempty"`
	OK              bool   `json:"ok"`
	ProtocolVersion int    `json:"protocolVersion"`
	HelperVersion   string `json:"helperVersion,omitempty"`
	Code            string `json:"code,omitempty"`
	LocalPort       int    `json:"localPort,omitempty"`
	Sharing         bool   `json:"sharing,omitempty"`
	Joining         bool   `json:"joining,omitempty"`
	Error           string `json:"error,omitempty"`
}

func main() {
	if err := run(os.Stdin, os.Stdout); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func run(input io.Reader, output io.Writer) error {
	scanner := bufio.NewScanner(input)
	scanner.Buffer(make([]byte, 4096), 1024*1024)
	writer := bufio.NewWriter(output)
	sessions := &sessions{}
	defer sessions.close()

	for scanner.Scan() {
		var req request
		var result response
		if err := json.Unmarshal(scanner.Bytes(), &req); err != nil {
			result = response{Error: "invalid_json"}
		} else {
			result = sessions.handle(req)
		}
		result.ID = req.ID
		result.ProtocolVersion = protocolVersion

		data, err := json.Marshal(result)
		if err != nil {
			return err
		}
		if _, err := writer.Write(append(data, '\n')); err != nil {
			return err
		}
		if err := writer.Flush(); err != nil {
			return err
		}
		if strings.EqualFold(req.Type, "shutdown") && result.OK {
			return nil
		}
	}
	return scanner.Err()
}

func (s *sessions) handle(req request) response {
	if req.ProtocolVersion != protocolVersion {
		return response{Error: "unsupported_protocol_version"}
	}
	if req.ID == "" {
		return response{Error: "missing_id"}
	}

	switch req.Type {
	case "hello":
		return response{OK: true, HelperVersion: helperVersion}
	case "ping":
		return response{OK: true}
	case "share":
		code, err := s.startShare(req.Port, req.DERPMapURL, req.RegionID)
		if err != "" {
			return response{Error: err}
		}
		return response{OK: true, Code: code}
	case "join":
		port, err := s.startJoin(req.Code, req.DERPMapURL)
		if err != "" {
			return response{Error: err}
		}
		return response{OK: true, LocalPort: port}
	case "stopShare":
		s.stopShare()
		return response{OK: true}
	case "stopJoin":
		s.stopJoin()
		return response{OK: true}
	case "status":
		result := response{OK: true, Sharing: s.share != nil, Joining: s.join != nil}
		if s.join != nil {
			result.LocalPort = s.join.listener.Addr().(*net.TCPAddr).Port
		}
		return result
	case "shutdown":
		s.close()
		return response{OK: true}
	default:
		return response{Error: "unknown_command"}
	}
}
