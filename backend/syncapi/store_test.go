package syncapi

import (
	"errors"
	"os"
	"path/filepath"
	"testing"
)

func TestNewStoreRejectsProfileWithoutDeviceMap(t *testing.T) {
	path := filepath.Join(t.TempDir(), "sync-state.json")
	data := []byte(`{"version":1,"profiles":{"profile":{"revision":0,"devices":null,"pairings":null}}}`)
	if err := os.WriteFile(path, data, 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err := NewStore(path); err == nil {
		t.Fatal("NewStore accepted a profile with no device map")
	}
}

func TestActivePairingLimit(t *testing.T) {
	store, err := NewStore("")
	if err != nil {
		t.Fatal(err)
	}
	profileID, _, deviceToken, err := store.createProfile()
	if err != nil {
		t.Fatal(err)
	}
	for i := 0; i < 5; i++ {
		if _, _, err := store.createPairing(profileID, deviceToken); err != nil {
			t.Fatalf("create pairing #%d: %v", i+1, err)
		}
	}
	if _, _, err := store.createPairing(profileID, deviceToken); !errors.Is(err, errPairingLimit) {
		t.Fatalf("sixth active pairing error=%v, want errPairingLimit", err)
	}
}

func TestOpenExistingStoreRestrictsPermissions(t *testing.T) {
	path := filepath.Join(t.TempDir(), "sync-state.json")
	if err := os.WriteFile(path, []byte(`{"version":1,"profiles":{}}`), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.Chmod(path, 0o644); err != nil {
		t.Fatal(err)
	}
	if _, err := NewStore(path); err != nil {
		t.Fatal(err)
	}
	info, err := os.Stat(path)
	if err != nil {
		t.Fatal(err)
	}
	if info.Mode().Perm() != 0o600 {
		t.Fatalf("existing sync store permissions=%#o, want 0600", info.Mode().Perm())
	}
}

func TestFailedPairingPersistRollsBackMemoryState(t *testing.T) {
	dir := t.TempDir()
	store, err := NewStore(filepath.Join(dir, "sync-state.json"))
	if err != nil {
		t.Fatal(err)
	}
	profileID, _, token, err := store.createProfile()
	if err != nil {
		t.Fatal(err)
	}
	if err := os.Chmod(dir, 0o500); err != nil {
		t.Fatal(err)
	}
	defer os.Chmod(dir, 0o700)

	if _, _, err := store.createPairing(profileID, token); err == nil {
		t.Fatal("createPairing succeeded despite read-only store directory")
	}
	if got := len(store.profiles[profileID].Pairings); got != 0 {
		t.Fatalf("failed durable pairing write left %d pairing codes in memory, want 0", got)
	}
}
