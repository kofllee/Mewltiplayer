package main

import (
	"bytes"
	"os"
	"path/filepath"
	"testing"
)

func TestPersistentIdentity(t *testing.T) {
	file := filepath.Join(t.TempDir(), "private", "identity.json")
	firstKey, firstPSK, err := loadIdentity(file)
	if err != nil {
		t.Fatal(err)
	}
	secondKey, secondPSK, err := loadIdentity(file)
	if err != nil {
		t.Fatal(err)
	}
	if firstKey.Raw32() != secondKey.Raw32() || firstPSK != secondPSK || firstKey.IsZero() || firstPSK.IsZero() {
		t.Fatal("persistent keys did not survive reload")
	}
	otherKey, otherPSK, err := loadIdentity(filepath.Join(t.TempDir(), "identity.json"))
	if err != nil {
		t.Fatal(err)
	}
	if firstKey.Raw32() == otherKey.Raw32() || bytes.Equal(firstPSK[:], otherPSK[:]) {
		t.Fatal("two identities are not independent")
	}

	if err := os.WriteFile(file, []byte("not valid"), 0600); err != nil {
		t.Fatal(err)
	}
	if _, _, err := loadIdentity(file); err == nil {
		t.Fatal("corrupt identity was silently replaced")
	}
}

func TestEphemeralIdentity(t *testing.T) {
	private, psk, err := loadIdentity("")
	if err != nil || !private.IsZero() || !psk.IsZero() {
		t.Fatal("an ephemeral server must allow Tailcat to generate its keys")
	}
}

func TestSavedDERPRegion(t *testing.T) {
	path := filepath.Join(t.TempDir(), "identity.json")
	if _, _, err := loadIdentity(path); err != nil {
		t.Fatal(err)
	}
	if region, err := savedRegion(path); err != nil || region != 0 {
		t.Fatalf("unexpected initial region: %d, %v", region, err)
	}
	if err := saveRegion(path, 303); err != nil {
		t.Fatal(err)
	}
	if region, err := savedRegion(path); err != nil || region != 303 {
		t.Fatalf("region not persisted: %d, %v", region, err)
	}
	if _, _, err := loadIdentity(path); err != nil {
		t.Fatal("updating the region corrupted the identity:", err)
	}
}
