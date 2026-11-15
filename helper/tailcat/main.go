package main

import (
    "bufio"
    "encoding/json"
    "fmt"
    "os"
)

const protocolVersion = 1

type request struct {
    ID string `json:"id"`
    Type string `json:"type"`
    ProtocolVersion int `json:"protocolVersion"`
}

type response struct {
    ID string `json:"id,omitempty"`
    OK bool `json:"ok"`
    ProtocolVersion int `json:"protocolVersion"`
    HelperVersion string `json:"helperVersion,omitempty"`
    Error string `json:"error,omitempty"`
}

func handle(req request) response {
    out := response{ID: req.ID, ProtocolVersion: protocolVersion}
    if req.ProtocolVersion != protocolVersion {
        out.Error = "unsupported_protocol_version"
        return out
    }
    switch req.Type {
    case "hello":
        out.OK = true
        out.HelperVersion = "0.1.0-dev"
    case "ping":
        out.OK = true
    default:
        out.Error = "not_implemented"
    }
    return out
}

func main() {
    scanner := bufio.NewScanner(os.Stdin)
    scanner.Buffer(make([]byte, 4*1024), 1024*1024)
    writer := bufio.NewWriter(os.Stdout)
    defer writer.Flush()
    for scanner.Scan() {
        var req request
        if err := json.Unmarshal(scanner.Bytes(), &req); err != nil {
            fmt.Fprintln(writer, `{"ok":false,"protocolVersion":1,"error":"invalid_json"}`)
        } else {
            encoded, err := json.Marshal(handle(req))
            if err != nil {
                fmt.Fprintln(os.Stderr, err)
                os.Exit(1)
            }
            fmt.Fprintln(writer, string(encoded))
        }
        if err := writer.Flush(); err != nil {
            fmt.Fprintln(os.Stderr, err)
            os.Exit(1)
        }
    }
    if err := scanner.Err(); err != nil {
        fmt.Fprintln(os.Stderr, err)
        os.Exit(1)
    }
}
