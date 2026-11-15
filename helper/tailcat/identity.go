package main

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"

	"github.com/tailscale/tailcat"
	"go4.org/mem"
	"tailscale.com/types/key"
)

type serverIdentity struct {
	Version  int    `json:"version"`
	NodeKey  string `json:"nodeKey"`
	PSK      string `json:"psk"`
	RegionID int    `json:"regionId,omitempty"`
}

func loadIdentity(path string) (key.NodePrivate, tailcat.PresharedKey, error) {
	var zero tailcat.PresharedKey
	if path == "" {
		return key.NodePrivate{}, zero, nil
	}

	data, err := os.ReadFile(path)
	if errors.Is(err, os.ErrNotExist) {
		private := key.NewNode()
		psk := tailcat.NewPresharedKey()
		raw := private.Raw32()
		record := serverIdentity{Version: 1, NodeKey: base64.RawURLEncoding.EncodeToString(raw[:]), PSK: base64.RawURLEncoding.EncodeToString(psk[:])}
		data, err = json.Marshal(record)
		if err != nil {
			return key.NodePrivate{}, zero, err
		}
		if err = os.MkdirAll(filepath.Dir(path), 0700); err != nil {
			return key.NodePrivate{}, zero, err
		}
		file, createErr := os.OpenFile(path, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0600)
		if errors.Is(createErr, os.ErrExist) {
			data, err = os.ReadFile(path)
		} else if createErr != nil {
			err = createErr
		} else {
			_, err = file.Write(data)
			if closeErr := file.Close(); err == nil {
				err = closeErr
			}
			if err != nil {
				os.Remove(path)
			}
		}
		if err != nil {
			return key.NodePrivate{}, zero, err
		}
	} else if err != nil {
		return key.NodePrivate{}, zero, err
	}

	var record serverIdentity
	if err := json.Unmarshal(data, &record); err != nil {
		return key.NodePrivate{}, zero, err
	}
	nodeBytes, nodeErr := base64.RawURLEncoding.DecodeString(record.NodeKey)
	pskBytes, pskErr := base64.RawURLEncoding.DecodeString(record.PSK)
	if record.Version != 1 || nodeErr != nil || pskErr != nil || len(nodeBytes) != 32 || len(pskBytes) != 32 {
		return key.NodePrivate{}, zero, errors.New("invalid persistent server identity")
	}
	var psk tailcat.PresharedKey
	copy(psk[:], pskBytes)
	private := key.NodePrivateFromRaw32(mem.B(nodeBytes))
	if private.IsZero() || psk.IsZero() {
		return key.NodePrivate{}, zero, errors.New("invalid persistent server identity")
	}
	return private, psk, nil
}

func savedRegion(path string) (int, error) {
	if path == "" {
		return 0, nil
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return 0, err
	}
	var record serverIdentity
	if err := json.Unmarshal(data, &record); err != nil {
		return 0, err
	}
	if record.RegionID < 0 {
		return 0, errors.New("invalid saved DERP region")
	}
	return record.RegionID, nil
}

func saveRegion(path string, regionID int) error {
	if path == "" || regionID <= 0 {
		return nil
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return err
	}
	var record serverIdentity
	if err := json.Unmarshal(data, &record); err != nil {
		return err
	}
	if record.RegionID == regionID {
		return nil
	}
	record.RegionID = regionID
	data, err = json.Marshal(record)
	if err != nil {
		return err
	}
	temp, err := os.CreateTemp(filepath.Dir(path), ".identity-")
	if err != nil {
		return err
	}
	defer os.Remove(temp.Name())
	if err := temp.Chmod(0600); err != nil {
		temp.Close()
		return err
	}
	if _, err := temp.Write(data); err != nil {
		temp.Close()
		return err
	}
	if err := temp.Close(); err != nil {
		return err
	}
	return os.Rename(temp.Name(), path)
}
