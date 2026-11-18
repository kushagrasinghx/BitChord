package syncapi

import (
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"sync"
	"time"
)

var (
	errNotFound         = errors.New("sync profile not found")
	errUnauthorized     = errors.New("sync device is not authorized")
	errRevisionStale    = errors.New("sync snapshot revision is stale")
	errInvalidSnapshot  = errors.New("sync snapshot is invalid")
	errCannotRevokeSelf = errors.New("a device cannot revoke itself")
	errDeviceLimit      = errors.New("sync profile device limit reached")
	errPairingLimit     = errors.New("too many active pairing codes")
	errProfileLimit     = errors.New("sync profile capacity reached")
)

const (
	maxDevicesPerProfile        = 10
	maxActivePairingsPerProfile = 5
	maxSyncProfiles             = 10000
	maxStoreBytes               = 64 << 20
)

type snapshot struct {
	SchemaVersion int    `json:"schemaVersion"`
	Ciphertext    string `json:"ciphertext"`
}

type deviceRecord struct {
	TokenHash string    `json:"tokenHash"`
	CreatedAt time.Time `json:"createdAt"`
}

type pairingRecord struct {
	TokenHash string    `json:"tokenHash"`
	ExpiresAt time.Time `json:"expiresAt"`
}

type deviceInfo struct {
	DeviceID  string    `json:"deviceId"`
	CreatedAt time.Time `json:"createdAt"`
	Current   bool      `json:"current"`
}

type profileRecord struct {
	Revision  int64                    `json:"revision"`
	Snapshot  *snapshot                `json:"snapshot,omitempty"`
	Devices   map[string]deviceRecord  `json:"devices"`
	Pairings  map[string]pairingRecord `json:"pairings"`
	CreatedAt time.Time                `json:"createdAt"`
	UpdatedAt time.Time                `json:"updatedAt"`
}

func cloneProfile(value profileRecord) profileRecord {
	if value.Snapshot != nil {
		snapshotCopy := *value.Snapshot
		value.Snapshot = &snapshotCopy
	}
	if value.Devices != nil {
		devices := make(map[string]deviceRecord, len(value.Devices))
		for id, device := range value.Devices {
			devices[id] = device
		}
		value.Devices = devices
	}
	if value.Pairings != nil {
		pairings := make(map[string]pairingRecord, len(value.Pairings))
		for id, pairing := range value.Pairings {
			pairings[id] = pairing
		}
		value.Pairings = pairings
	}
	return value
}

type diskState struct {
	Version  int                      `json:"version"`
	Profiles map[string]profileRecord `json:"profiles"`
}

// validateDiskState rejects malformed or oversized persistent records before any
// request handler can encounter a nil map or inconsistent snapshot revision.
func validateDiskState(state *diskState) error {
	if state.Version != 1 || state.Profiles == nil {
		return fmt.Errorf("unsupported or invalid store version %d", state.Version)
	}
	if len(state.Profiles) > maxSyncProfiles {
		return fmt.Errorf("profile count exceeds %d", maxSyncProfiles)
	}
	for profileID, profile := range state.Profiles {
		if profileID == "" || profile.Devices == nil || len(profile.Devices) == 0 || len(profile.Devices) > maxDevicesPerProfile {
			return fmt.Errorf("profile %q has invalid device state", profileID)
		}
		if profile.CreatedAt.IsZero() || profile.UpdatedAt.IsZero() || profile.Revision < 0 {
			return fmt.Errorf("profile %q has invalid metadata", profileID)
		}
		if profile.Pairings == nil {
			profile.Pairings = make(map[string]pairingRecord)
		}
		if len(profile.Pairings) > maxActivePairingsPerProfile {
			return fmt.Errorf("profile %q has too many pairing records", profileID)
		}
		for deviceID, device := range profile.Devices {
			if deviceID == "" || !validTokenHash(device.TokenHash) || device.CreatedAt.IsZero() {
				return fmt.Errorf("profile %q has invalid device metadata", profileID)
			}
		}
		for tokenHash, pairing := range profile.Pairings {
			if tokenHash == "" || tokenHash != pairing.TokenHash || !validTokenHash(pairing.TokenHash) || pairing.ExpiresAt.IsZero() {
				return fmt.Errorf("profile %q has invalid pairing metadata", profileID)
			}
		}
		if profile.Snapshot == nil && profile.Revision != 0 {
			return fmt.Errorf("profile %q has a revision without a snapshot", profileID)
		}
		if profile.Snapshot != nil {
			if profile.Revision == 0 || profile.Snapshot.SchemaVersion != 1 || profile.Snapshot.Ciphertext == "" {
				return fmt.Errorf("profile %q has invalid snapshot metadata", profileID)
			}
			if _, err := base64.StdEncoding.DecodeString(profile.Snapshot.Ciphertext); err != nil {
				return fmt.Errorf("profile %q has invalid snapshot encoding", profileID)
			}
		}
		state.Profiles[profileID] = profile
	}
	return nil
}

func validTokenHash(value string) bool {
	decoded, err := hex.DecodeString(value)
	return err == nil && len(decoded) == sha256.Size
}

// Store keeps encrypted profile snapshots and device-token hashes. The optional
// path enables a single-process, atomically replaced JSON store; an empty path
// selects memory-only storage for tests. Never put profile keys or plaintext
// settings in this store.
type Store struct {
	mu       sync.RWMutex
	path     string
	profiles map[string]profileRecord
}

func NewStore(path string) (*Store, error) {
	s := &Store{path: path, profiles: make(map[string]profileRecord)}
	if path == "" {
		return s, nil
	}
	info, err := os.Stat(path)
	if errors.Is(err, os.ErrNotExist) {
		return s, nil
	}
	if err != nil {
		return nil, fmt.Errorf("stat sync store: %w", err)
	}
	if info.Size() > maxStoreBytes {
		return nil, fmt.Errorf("sync store exceeds %d-byte limit", maxStoreBytes)
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("read sync store: %w", err)
	}
	if err := os.Chmod(path, 0o600); err != nil {
		return nil, fmt.Errorf("restrict sync store permissions: %w", err)
	}
	var state diskState
	if err := json.Unmarshal(data, &state); err != nil {
		return nil, fmt.Errorf("decode sync store: %w", err)
	}
	if err := validateDiskState(&state); err != nil {
		return nil, fmt.Errorf("validate sync store: %w", err)
	}
	s.profiles = state.Profiles
	return s, nil
}

func (s *Store) createProfile() (profileID, deviceID, deviceToken string, err error) {
	profileID, err = randomToken(18)
	if err != nil {
		return "", "", "", err
	}
	deviceID, err = randomToken(12)
	if err != nil {
		return "", "", "", err
	}
	deviceToken, err = randomToken(32)
	if err != nil {
		return "", "", "", err
	}
	now := time.Now().UTC()
	record := profileRecord{
		Devices:   map[string]deviceRecord{deviceID: {TokenHash: hashToken(deviceToken), CreatedAt: now}},
		Pairings:  make(map[string]pairingRecord),
		CreatedAt: now,
		UpdatedAt: now,
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if len(s.profiles) >= maxSyncProfiles {
		return "", "", "", errProfileLimit
	}
	s.profiles[profileID] = record
	if err := s.persistLocked(); err != nil {
		delete(s.profiles, profileID)
		return "", "", "", err
	}
	return profileID, deviceID, deviceToken, nil
}

func (s *Store) createPairing(profileID, deviceToken string) (string, time.Time, error) {
	token, err := randomToken(32)
	if err != nil {
		return "", time.Time{}, err
	}
	now := time.Now().UTC()
	expiresAt := now.Add(5 * time.Minute)
	s.mu.Lock()
	defer s.mu.Unlock()
	profile, ok := s.profiles[profileID]
	if !ok {
		return "", time.Time{}, errNotFound
	}
	if !profileHasToken(profile, deviceToken) {
		return "", time.Time{}, errUnauthorized
	}
	previous := cloneProfile(profile)
	if profile.Pairings == nil {
		profile.Pairings = make(map[string]pairingRecord)
	}
	for key, pairing := range profile.Pairings {
		if !pairing.ExpiresAt.After(now) {
			delete(profile.Pairings, key)
		}
	}
	if len(profile.Pairings) >= maxActivePairingsPerProfile {
		return "", time.Time{}, errPairingLimit
	}
	tokenHash := hashToken(token)
	profile.Pairings[tokenHash] = pairingRecord{TokenHash: tokenHash, ExpiresAt: expiresAt}
	profile.UpdatedAt = now
	s.profiles[profileID] = profile
	if err := s.persistLocked(); err != nil {
		s.profiles[profileID] = previous
		return "", time.Time{}, err
	}
	return token, expiresAt, nil
}

func (s *Store) claimPairing(profileID, pairingToken string) (string, string, error) {
	deviceID, err := randomToken(12)
	if err != nil {
		return "", "", err
	}
	deviceToken, err := randomToken(32)
	if err != nil {
		return "", "", err
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	profile, ok := s.profiles[profileID]
	if !ok {
		return "", "", errNotFound
	}
	providedHash := hashToken(pairingToken)
	var matchingHash string
	for tokenHash, pairing := range profile.Pairings {
		if subtle.ConstantTimeCompare([]byte(pairing.TokenHash), []byte(providedHash)) == 1 {
			matchingHash = tokenHash
			if !pairing.ExpiresAt.After(time.Now().UTC()) {
				previous := cloneProfile(profile)
				delete(profile.Pairings, tokenHash)
				s.profiles[profileID] = profile
				if err := s.persistLocked(); err != nil {
					s.profiles[profileID] = previous
					return "", "", err
				}
				return "", "", errUnauthorized
			}
			break
		}
	}
	if matchingHash == "" {
		return "", "", errUnauthorized
	}
	if len(profile.Devices) >= maxDevicesPerProfile {
		return "", "", errDeviceLimit
	}
	previous := cloneProfile(profile)
	delete(profile.Pairings, matchingHash)
	now := time.Now().UTC()
	profile.Devices[deviceID] = deviceRecord{TokenHash: hashToken(deviceToken), CreatedAt: now}
	profile.UpdatedAt = now
	s.profiles[profileID] = profile
	if err := s.persistLocked(); err != nil {
		s.profiles[profileID] = previous
		return "", "", err
	}
	return deviceID, deviceToken, nil
}

func (s *Store) revokeDevice(profileID, requesterID, targetID string) error {
	if requesterID == targetID {
		return errCannotRevokeSelf
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	profile, ok := s.profiles[profileID]
	if !ok {
		return errNotFound
	}
	if _, ok := profile.Devices[requesterID]; !ok {
		return errUnauthorized
	}
	if _, ok := profile.Devices[targetID]; !ok {
		return errNotFound
	}
	previous := cloneProfile(profile)
	delete(profile.Devices, targetID)
	profile.UpdatedAt = time.Now().UTC()
	s.profiles[profileID] = profile
	if err := s.persistLocked(); err != nil {
		s.profiles[profileID] = previous
		return err
	}
	return nil
}

func (s *Store) authorizedDevice(profileID, token string) (string, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	profile, ok := s.profiles[profileID]
	if !ok {
		return "", errNotFound
	}
	provided := hashToken(token)
	for deviceID, device := range profile.Devices {
		if subtle.ConstantTimeCompare([]byte(device.TokenHash), []byte(provided)) == 1 {
			return deviceID, nil
		}
	}
	return "", errUnauthorized
}

func profileHasToken(profile profileRecord, token string) bool {
	provided := hashToken(token)
	for _, device := range profile.Devices {
		if subtle.ConstantTimeCompare([]byte(device.TokenHash), []byte(provided)) == 1 {
			return true
		}
	}
	return false
}

func (s *Store) listDevices(profileID, token string) ([]deviceInfo, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	profile, ok := s.profiles[profileID]
	if !ok {
		return nil, errNotFound
	}
	provided := hashToken(token)
	currentID := ""
	for id, device := range profile.Devices {
		if subtle.ConstantTimeCompare([]byte(device.TokenHash), []byte(provided)) == 1 {
			currentID = id
			break
		}
	}
	if currentID == "" {
		return nil, errUnauthorized
	}
	devices := make([]deviceInfo, 0, len(profile.Devices))
	for id, device := range profile.Devices {
		devices = append(devices, deviceInfo{DeviceID: id, CreatedAt: device.CreatedAt, Current: id == currentID})
	}
	sort.Slice(devices, func(i, j int) bool {
		if devices[i].CreatedAt.Equal(devices[j].CreatedAt) {
			return devices[i].DeviceID < devices[j].DeviceID
		}
		return devices[i].CreatedAt.Before(devices[j].CreatedAt)
	})
	return devices, nil
}

func (s *Store) deleteProfile(profileID, token string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	profile, ok := s.profiles[profileID]
	if !ok {
		return errNotFound
	}
	if !profileHasToken(profile, token) {
		return errUnauthorized
	}
	delete(s.profiles, profileID)
	if err := s.persistLocked(); err != nil {
		s.profiles[profileID] = profile
		return err
	}
	return nil
}

func (s *Store) getSnapshot(profileID, token string) (int64, *snapshot, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	profile, ok := s.profiles[profileID]
	if !ok {
		return 0, nil, errNotFound
	}
	if !profileHasToken(profile, token) {
		return 0, nil, errUnauthorized
	}
	var copyOfSnapshot *snapshot
	if profile.Snapshot != nil {
		value := *profile.Snapshot
		copyOfSnapshot = &value
	}
	return profile.Revision, copyOfSnapshot, nil
}

func (s *Store) putSnapshot(profileID, token string, expectedRevision int64, value snapshot) (int64, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	profile, ok := s.profiles[profileID]
	if !ok {
		return 0, errNotFound
	}
	if !profileHasToken(profile, token) {
		return profile.Revision, errUnauthorized
	}
	if profile.Revision != expectedRevision {
		return profile.Revision, errRevisionStale
	}
	if value.SchemaVersion != 1 || value.Ciphertext == "" {
		return profile.Revision, errInvalidSnapshot
	}
	if _, err := base64.StdEncoding.DecodeString(value.Ciphertext); err != nil {
		return profile.Revision, errInvalidSnapshot
	}
	previous := cloneProfile(profile)
	profile.Revision++
	profile.Snapshot = &value
	profile.UpdatedAt = time.Now().UTC()
	s.profiles[profileID] = profile
	if err := s.persistLocked(); err != nil {
		s.profiles[profileID] = previous
		return previous.Revision, err
	}
	return profile.Revision, nil
}

func (s *Store) persistLocked() error {
	if s.path == "" {
		return nil
	}
	state := diskState{Version: 1, Profiles: s.profiles}
	data, err := json.Marshal(state)
	if err != nil {
		return fmt.Errorf("encode sync store: %w", err)
	}
	if len(data) > maxStoreBytes {
		return fmt.Errorf("sync store exceeds %d-byte limit", maxStoreBytes)
	}
	dir := filepath.Dir(s.path)
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return fmt.Errorf("create sync store directory: %w", err)
	}
	file, err := os.CreateTemp(dir, ".sync-state-*.tmp")
	if err != nil {
		return fmt.Errorf("create sync store temp file: %w", err)
	}
	tempPath := file.Name()
	defer os.Remove(tempPath)
	if err := file.Chmod(0o600); err != nil {
		_ = file.Close()
		return fmt.Errorf("restrict sync store permissions: %w", err)
	}
	if _, err := file.Write(data); err != nil {
		_ = file.Close()
		return fmt.Errorf("write sync store: %w", err)
	}
	if err := file.Sync(); err != nil {
		_ = file.Close()
		return fmt.Errorf("sync sync store: %w", err)
	}
	if err := file.Close(); err != nil {
		return fmt.Errorf("close sync store: %w", err)
	}
	if err := os.Rename(tempPath, s.path); err != nil {
		return fmt.Errorf("replace sync store: %w", err)
	}
	if dirFile, err := os.Open(dir); err == nil {
		_ = dirFile.Sync()
		_ = dirFile.Close()
	}
	return nil
}

func randomToken(bytesCount int) (string, error) {
	value := make([]byte, bytesCount)
	if _, err := rand.Read(value); err != nil {
		return "", fmt.Errorf("generate random token: %w", err)
	}
	return base64.RawURLEncoding.EncodeToString(value), nil
}

func hashToken(token string) string {
	digest := sha256.Sum256([]byte(token))
	return hex.EncodeToString(digest[:])
}
