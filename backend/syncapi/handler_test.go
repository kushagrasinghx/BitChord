package syncapi

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestProfileCreationDisabledWithoutOperatorOptIn(t *testing.T) {
	store, err := NewStore("")
	if err != nil {
		t.Fatal(err)
	}
	res := doJSON(t, NewHandler(store), http.MethodPost, "/v1/profiles", "", map[string]any{})
	if res.Code != http.StatusForbidden {
		t.Fatalf("profile creation status=%d, want %d", res.Code, http.StatusForbidden)
	}
	if len(store.profiles) != 0 {
		t.Fatalf("disabled profile creation created %d profiles", len(store.profiles))
	}
}

func newTestHandler(t *testing.T) http.Handler {
	t.Helper()
	store, err := NewStore("")
	if err != nil {
		t.Fatal(err)
	}
	return NewHandler(store, HandlerOptions{AllowProfileCreation: true})
}

func doJSON(t *testing.T, handler http.Handler, method, path, bearer string, body any) *httptest.ResponseRecorder {
	t.Helper()
	var data []byte
	if body != nil {
		var err error
		data, err = json.Marshal(body)
		if err != nil {
			t.Fatal(err)
		}
	}
	req := httptest.NewRequest(method, path, bytes.NewReader(data))
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if bearer != "" {
		req.Header.Set("Authorization", "Bearer "+bearer)
	}
	res := httptest.NewRecorder()
	handler.ServeHTTP(res, req)
	return res
}

func decodeJSON(t *testing.T, response *httptest.ResponseRecorder, target any) {
	t.Helper()
	if err := json.Unmarshal(response.Body.Bytes(), target); err != nil {
		t.Fatalf("decode response %q: %v", response.Body.String(), err)
	}
}

func createProfile(t *testing.T, handler http.Handler) (string, string, string) {
	t.Helper()
	res := doJSON(t, handler, http.MethodPost, "/v1/profiles", "", map[string]any{})
	if res.Code != http.StatusCreated {
		t.Fatalf("create profile: status=%d body=%s", res.Code, res.Body.String())
	}
	var got struct {
		ProfileID   string `json:"profileId"`
		DeviceID    string `json:"deviceId"`
		DeviceToken string `json:"deviceToken"`
	}
	decodeJSON(t, res, &got)
	if got.ProfileID == "" || got.DeviceID == "" || got.DeviceToken == "" {
		t.Fatalf("create profile returned incomplete identity: %+v", got)
	}
	return got.ProfileID, got.DeviceID, got.DeviceToken
}

func TestProfileCreateRateLimitedPerClient(t *testing.T) {
	h := newTestHandler(t)
	for attempt := 1; attempt <= 6; attempt++ {
		res := doJSON(t, h, http.MethodPost, "/v1/profiles", "", map[string]any{})
		if attempt <= 5 && res.Code != http.StatusCreated {
			t.Fatalf("profile create #%d status=%d, want %d", attempt, res.Code, http.StatusCreated)
		}
		if attempt == 6 {
			if res.Code != http.StatusTooManyRequests {
				t.Fatalf("profile create #%d status=%d, want %d", attempt, res.Code, http.StatusTooManyRequests)
			}
			if res.Header().Get("Retry-After") == "" {
				t.Fatal("rate-limit response omitted Retry-After")
			}
		}
	}
}

func TestSnapshotRequiresDeviceBearer(t *testing.T) {
	h := newTestHandler(t)
	profileID, _, token := createProfile(t, h)

	res := doJSON(t, h, http.MethodGet, "/v1/profiles/"+profileID+"/snapshot", "", nil)
	if res.Code != http.StatusUnauthorized {
		t.Fatalf("unauthorized snapshot status=%d, want %d; body=%s", res.Code, http.StatusUnauthorized, res.Body.String())
	}

	res = doJSON(t, h, http.MethodGet, "/v1/profiles/"+profileID+"/snapshot", token, nil)
	if res.Code != http.StatusOK {
		t.Fatalf("authorized snapshot status=%d, want %d; body=%s", res.Code, http.StatusOK, res.Body.String())
	}
	var got struct {
		Revision int64 `json:"revision"`
		Snapshot *struct {
			SchemaVersion int    `json:"schemaVersion"`
			Ciphertext    string `json:"ciphertext"`
		} `json:"snapshot"`
	}
	decodeJSON(t, res, &got)
	if got.Revision != 0 || got.Snapshot != nil {
		t.Fatalf("new profile snapshot = %+v, want empty revision 0", got)
	}
}

func TestSnapshotCompareAndSwapRejectsStaleRevision(t *testing.T) {
	h := newTestHandler(t)
	profileID, _, token := createProfile(t, h)
	path := "/v1/profiles/" + profileID + "/snapshot"
	first := map[string]any{"expectedRevision": int64(0), "schemaVersion": 1, "ciphertext": "AQIDBA=="}

	res := doJSON(t, h, http.MethodPut, path, token, first)
	if res.Code != http.StatusOK {
		t.Fatalf("first snapshot upload status=%d, want %d; body=%s", res.Code, http.StatusOK, res.Body.String())
	}
	var saved struct {
		Revision int64 `json:"revision"`
	}
	decodeJSON(t, res, &saved)
	if saved.Revision != 1 {
		t.Fatalf("saved revision=%d, want 1", saved.Revision)
	}

	stale := map[string]any{"expectedRevision": int64(0), "schemaVersion": 1, "ciphertext": "BQYHCA=="}
	res = doJSON(t, h, http.MethodPut, path, token, stale)
	if res.Code != http.StatusConflict {
		t.Fatalf("stale upload status=%d, want %d; body=%s", res.Code, http.StatusConflict, res.Body.String())
	}

	res = doJSON(t, h, http.MethodGet, path, token, nil)
	if res.Code != http.StatusOK {
		t.Fatalf("read snapshot status=%d; body=%s", res.Code, res.Body.String())
	}
	var latest struct {
		Revision int64 `json:"revision"`
		Snapshot struct {
			Ciphertext string `json:"ciphertext"`
		} `json:"snapshot"`
	}
	decodeJSON(t, res, &latest)
	if latest.Revision != 1 || latest.Snapshot.Ciphertext != "AQIDBA==" {
		t.Fatalf("stale write replaced latest snapshot: %+v", latest)
	}
}

func TestSnapshotBodyIsBounded(t *testing.T) {
	h := newTestHandler(t)
	profileID, _, token := createProfile(t, h)
	body := `{"expectedRevision":0,"schemaVersion":1,"ciphertext":"` + strings.Repeat("A", 9<<20) + `"}`
	req := httptest.NewRequest(http.MethodPut, "/v1/profiles/"+profileID+"/snapshot", strings.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+token)
	res := httptest.NewRecorder()
	h.ServeHTTP(res, req)
	if res.Code != http.StatusRequestEntityTooLarge {
		t.Fatalf("oversized upload status=%d, want %d", res.Code, http.StatusRequestEntityTooLarge)
	}
}

func TestPutSnapshotRequiresExpectedRevision(t *testing.T) {
	h := newTestHandler(t)
	profileID, _, token := createProfile(t, h)
	res := doJSON(t, h, http.MethodPut, "/v1/profiles/"+profileID+"/snapshot", token,
		map[string]any{"schemaVersion": 1, "ciphertext": "AQIDBA=="})
	if res.Code != http.StatusBadRequest {
		t.Fatalf("missing expectedRevision status=%d, want %d", res.Code, http.StatusBadRequest)
	}
	res = doJSON(t, h, http.MethodGet, "/v1/profiles/"+profileID+"/snapshot", token, nil)
	var got struct {
		Revision int64 `json:"revision"`
		Snapshot any   `json:"snapshot"`
	}
	decodeJSON(t, res, &got)
	if got.Revision != 0 || got.Snapshot != nil {
		t.Fatalf("invalid upload changed state: %+v", got)
	}
}

func TestSnapshotSurvivesStoreRestart(t *testing.T) {
	path := filepath.Join(t.TempDir(), "sync-state.json")
	store, err := NewStore(path)
	if err != nil {
		t.Fatal(err)
	}
	h := NewHandler(store, HandlerOptions{AllowProfileCreation: true})
	profileID, _, token := createProfile(t, h)
	res := doJSON(t, h, http.MethodPut, "/v1/profiles/"+profileID+"/snapshot", token,
		map[string]any{"expectedRevision": int64(0), "schemaVersion": 1, "ciphertext": "AQIDBA=="})
	if res.Code != http.StatusOK {
		t.Fatalf("snapshot upload status=%d body=%s", res.Code, res.Body.String())
	}
	persisted, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if bytes.Contains(persisted, []byte(token)) {
		t.Fatal("persistent store contains the raw device bearer token")
	}
	info, err := os.Stat(path)
	if err != nil {
		t.Fatal(err)
	}
	if info.Mode().Perm()&0o077 != 0 {
		t.Fatalf("sync store permissions=%#o, want no group/other access", info.Mode().Perm())
	}

	reopened, err := NewStore(path)
	if err != nil {
		t.Fatal(err)
	}
	res = doJSON(t, NewHandler(reopened), http.MethodGet, "/v1/profiles/"+profileID+"/snapshot", token, nil)
	if res.Code != http.StatusOK {
		t.Fatalf("snapshot after restart status=%d body=%s", res.Code, res.Body.String())
	}
	var got struct {
		Revision int64 `json:"revision"`
		Snapshot struct {
			Ciphertext string `json:"ciphertext"`
		} `json:"snapshot"`
	}
	decodeJSON(t, res, &got)
	if got.Revision != 1 || got.Snapshot.Ciphertext != "AQIDBA==" {
		t.Fatalf("persisted snapshot=%+v, want revision 1 and original ciphertext", got)
	}
}

func TestDeviceListAndProfileDelete(t *testing.T) {
	h := newTestHandler(t)
	profileID, ownerDeviceID, ownerToken := createProfile(t, h)
	res := doJSON(t, h, http.MethodPost, "/v1/profiles/"+profileID+"/pairings", ownerToken, map[string]any{})
	if res.Code != http.StatusCreated {
		t.Fatalf("create pairing status=%d body=%s", res.Code, res.Body.String())
	}
	var pairing struct {
		Token string `json:"pairingToken"`
	}
	decodeJSON(t, res, &pairing)
	res = doJSON(t, h, http.MethodPost, "/v1/pairings/claim", "", map[string]any{"profileId": profileID, "pairingToken": pairing.Token})
	if res.Code != http.StatusCreated {
		t.Fatalf("claim pairing status=%d body=%s", res.Code, res.Body.String())
	}
	var paired struct {
		DeviceID string `json:"deviceId"`
	}
	decodeJSON(t, res, &paired)

	res = doJSON(t, h, http.MethodGet, "/v1/profiles/"+profileID+"/devices", ownerToken, nil)
	if res.Code != http.StatusOK {
		t.Fatalf("list devices status=%d body=%s", res.Code, res.Body.String())
	}
	var devices struct {
		Devices []struct {
			DeviceID string `json:"deviceId"`
			Current  bool   `json:"current"`
		} `json:"devices"`
	}
	decodeJSON(t, res, &devices)
	if len(devices.Devices) != 2 {
		t.Fatalf("device count=%d, want 2", len(devices.Devices))
	}
	currentFound, pairedFound := false, false
	for _, device := range devices.Devices {
		currentFound = currentFound || (device.DeviceID == ownerDeviceID && device.Current)
		pairedFound = pairedFound || (device.DeviceID == paired.DeviceID && !device.Current)
	}
	if !currentFound || !pairedFound {
		t.Fatalf("device list missing expected identities/current marker: %+v", devices.Devices)
	}

	res = doJSON(t, h, http.MethodDelete, "/v1/profiles/"+profileID, ownerToken, nil)
	if res.Code != http.StatusNoContent {
		t.Fatalf("delete profile status=%d body=%s", res.Code, res.Body.String())
	}
	res = doJSON(t, h, http.MethodGet, "/v1/profiles/"+profileID+"/snapshot", ownerToken, nil)
	if res.Code != http.StatusNotFound {
		t.Fatalf("deleted profile status=%d, want %d", res.Code, http.StatusNotFound)
	}
}

func TestProfileDeviceLimit(t *testing.T) {
	h := newTestHandler(t)
	profileID, _, ownerToken := createProfile(t, h)
	for index := 0; index < 10; index++ {
		res := doJSON(t, h, http.MethodPost, "/v1/profiles/"+profileID+"/pairings", ownerToken, map[string]any{})
		if res.Code != http.StatusCreated {
			t.Fatalf("pairing #%d status=%d body=%s", index+1, res.Code, res.Body.String())
		}
		var pairing struct {
			Token string `json:"pairingToken"`
		}
		decodeJSON(t, res, &pairing)
		res = doJSON(t, h, http.MethodPost, "/v1/pairings/claim", "", map[string]any{"profileId": profileID, "pairingToken": pairing.Token})
		if index < 9 && res.Code != http.StatusCreated {
			t.Fatalf("claim #%d status=%d body=%s", index+1, res.Code, res.Body.String())
		}
		if index == 9 && res.Code != http.StatusConflict {
			t.Fatalf("claim over device limit status=%d, want %d; body=%s", res.Code, http.StatusConflict, res.Body.String())
		}
	}
}

func TestPairingCreatesAndRevokesAnotherDevice(t *testing.T) {
	h := newTestHandler(t)
	profileID, ownerDeviceID, ownerToken := createProfile(t, h)

	res := doJSON(t, h, http.MethodPost, "/v1/profiles/"+profileID+"/pairings", ownerToken, map[string]any{})
	if res.Code != http.StatusCreated {
		t.Fatalf("create pairing status=%d body=%s", res.Code, res.Body.String())
	}
	var pairing struct {
		Token string `json:"pairingToken"`
	}
	decodeJSON(t, res, &pairing)
	if pairing.Token == "" {
		t.Fatal("pairing response omitted one-time token")
	}

	res = doJSON(t, h, http.MethodPost, "/v1/pairings/claim", "", map[string]any{
		"profileId": profileID, "pairingToken": pairing.Token,
	})
	if res.Code != http.StatusCreated {
		t.Fatalf("claim pairing status=%d body=%s", res.Code, res.Body.String())
	}
	var paired struct {
		ProfileID   string `json:"profileId"`
		DeviceID    string `json:"deviceId"`
		DeviceToken string `json:"deviceToken"`
	}
	decodeJSON(t, res, &paired)
	if paired.ProfileID != profileID || paired.DeviceID == "" || paired.DeviceToken == "" || paired.DeviceID == ownerDeviceID {
		t.Fatalf("invalid paired device response: %+v", paired)
	}

	res = doJSON(t, h, http.MethodPost, "/v1/pairings/claim", "", map[string]any{
		"profileId": profileID, "pairingToken": pairing.Token,
	})
	if res.Code != http.StatusUnauthorized {
		t.Fatalf("reused pairing token status=%d, want %d", res.Code, http.StatusUnauthorized)
	}

	res = doJSON(t, h, http.MethodDelete, "/v1/profiles/"+profileID+"/devices/"+paired.DeviceID, ownerToken, nil)
	if res.Code != http.StatusNoContent {
		t.Fatalf("revoke device status=%d body=%s", res.Code, res.Body.String())
	}
	res = doJSON(t, h, http.MethodGet, "/v1/profiles/"+profileID+"/snapshot", paired.DeviceToken, nil)
	if res.Code != http.StatusUnauthorized {
		t.Fatalf("revoked device snapshot status=%d, want %d", res.Code, http.StatusUnauthorized)
	}

	res = doJSON(t, h, http.MethodDelete, "/v1/profiles/"+profileID+"/devices/"+ownerDeviceID, ownerToken, nil)
	if res.Code != http.StatusBadRequest {
		t.Fatalf("self-revoke status=%d, want %d", res.Code, http.StatusBadRequest)
	}
}
