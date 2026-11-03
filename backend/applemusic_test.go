package main

import (
	"bytes"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"io"
	"log"
	"math/big"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/KabirSinghBhatia/BitChord/backend/applemusic"
	"github.com/KabirSinghBhatia/BitChord/backend/config"
)

func configureAppleMusicTest(t *testing.T, teamID, keyID, path string) {
	t.Helper()
	oldTeam, oldKey, oldFile := config.AppleMusicTeamID, config.AppleMusicKeyID, config.AppleMusicPrivateKeyFile
	t.Cleanup(func() {
		config.AppleMusicTeamID, config.AppleMusicKeyID, config.AppleMusicPrivateKeyFile = oldTeam, oldKey, oldFile
	})
	config.AppleMusicTeamID, config.AppleMusicKeyID, config.AppleMusicPrivateKeyFile = teamID, keyID, path
}

func appleMusicTestKey(t *testing.T) (string, *ecdsa.PrivateKey) {
	t.Helper()
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	der, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(t.TempDir(), "AuthKey_test.p8")
	if err := os.WriteFile(path, pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: der}), 0600); err != nil {
		t.Fatal(err)
	}
	return path, key
}

func TestAppleMusicTokenHTTPContract(t *testing.T) {
	path, key := appleMusicTestKey(t)
	configureAppleMusicTest(t, "TEAM123456", "KEY1234567", path)
	oldOrigins := config.AllowedOrigins
	config.AllowedOrigins = []string{"https://music.example"}
	t.Cleanup(func() { config.AllowedOrigins = oldOrigins })
	ts := setupTestServer()
	defer ts.Close()
	client := ts.Client()
	client.Timeout = 5 * time.Second

	before := time.Now().Unix()
	// Request parameters and headers cannot override server-held credentials or
	// increase the fixed validity, and no client secret is required.
	req, err := http.NewRequest(http.MethodGet, ts.URL+"/api/applemusic/token?teamId=CLIENTTEAM&keyId=CLIENTKEY1&privateKey=client-secret&expiresIn=999999999", nil)
	if err != nil {
		t.Fatal(err)
	}
	req.Header.Set("Origin", "https://music.example")
	res, err := client.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK || res.Header.Get("Content-Type") != "application/json" {
		t.Fatalf("token response status = %d, content type = %q", res.StatusCode, res.Header.Get("Content-Type"))
	}
	if res.Header.Get("Cache-Control") != "no-store" {
		t.Fatal("token response must not be stored by HTTP caches")
	}
	if res.Header.Get("Access-Control-Allow-Origin") != "https://music.example" {
		t.Fatal("token endpoint must use existing CORS policy")
	}
	var body map[string]json.RawMessage
	if err := json.NewDecoder(res.Body).Decode(&body); err != nil || len(body) != 2 {
		t.Fatal("response must contain exactly token and expiresAt")
	}
	var token string
	var expiresAt int64
	if err := json.Unmarshal(body["token"], &token); err != nil || token == "" {
		t.Fatal("response must contain a nonempty token string")
	}
	if err := json.Unmarshal(body["expiresAt"], &expiresAt); err != nil || expiresAt <= before {
		t.Fatal("expiresAt must be an epoch-seconds number")
	}
	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		t.Fatal("response token is not a JWT")
	}
	claimsJSON, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil {
		t.Fatal("invalid claims encoding")
	}
	var claims struct {
		Issuer    string `json:"iss"`
		IssuedAt  int64  `json:"iat"`
		ExpiresAt int64  `json:"exp"`
	}
	if err := json.Unmarshal(claimsJSON, &claims); err != nil {
		t.Fatal("invalid claims")
	}
	if claims.Issuer != "TEAM123456" || claims.IssuedAt < before || claims.IssuedAt > time.Now().Unix() ||
		claims.ExpiresAt != expiresAt || claims.ExpiresAt-claims.IssuedAt != 3600 {
		t.Fatal("JWT must use server credentials and match the documented expiry")
	}
	signature, err := base64.RawURLEncoding.DecodeString(parts[2])
	if err != nil || len(signature) != 64 {
		t.Fatal("invalid JOSE signature")
	}
	digest := sha256.Sum256([]byte(parts[0] + "." + parts[1]))
	if !ecdsa.Verify(&key.PublicKey, digest[:], new(big.Int).SetBytes(signature[:32]), new(big.Int).SetBytes(signature[32:])) {
		t.Fatal("HTTP token is not signed by the configured key")
	}

	cachedRes, err := client.Get(ts.URL + "/api/applemusic/token")
	if err != nil {
		t.Fatal(err)
	}
	defer cachedRes.Body.Close()
	var cached applemusic.DeveloperToken
	if err := json.NewDecoder(cachedRes.Body).Decode(&cached); err != nil || cachedRes.StatusCode != http.StatusOK ||
		cached.Token != token || cached.ExpiresAt != expiresAt {
		t.Fatal("repeated HTTP requests must return the cached token and expiry")
	}

	postRes, err := client.Post(ts.URL+"/api/applemusic/token", "application/json", strings.NewReader("{}"))
	if err != nil {
		t.Fatal(err)
	}
	defer postRes.Body.Close()
	if postRes.StatusCode != http.StatusMethodNotAllowed {
		t.Fatalf("POST status = %d, want 405", postRes.StatusCode)
	}
}

func TestAppleMusicTokenUnavailable(t *testing.T) {
	path, _ := appleMusicTestKey(t)
	invalidPath := filepath.Join(t.TempDir(), "secret-invalid-key.p8")
	if err := os.WriteFile(invalidPath, []byte("PRIVATE secret material must not be disclosed"), 0600); err != nil {
		t.Fatal(err)
	}
	cases := []struct {
		name, teamID, keyID, path string
	}{
		{"unconfigured", "", "", ""},
		{"team only", "TEAM123456", "", ""},
		{"missing file", "TEAM123456", "KEY1234567", ""},
		{"missing team", "", "KEY1234567", path},
		{"missing key ID", "TEAM123456", "", path},
		{"invalid ID", "bad-team", "KEY1234567", path},
		{"missing key file", "TEAM123456", "KEY1234567", filepath.Join(t.TempDir(), "secret-missing.p8")},
		{"invalid key file", "TEAM123456", "KEY1234567", invalidPath},
	}
	var logs bytes.Buffer
	oldWriter := log.Writer()
	log.SetOutput(&logs)
	defer log.SetOutput(oldWriter)
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			configureAppleMusicTest(t, tc.teamID, tc.keyID, tc.path)
			handler := newAppleMusicTokenHandler()
			for i := 0; i < 2; i++ {
				rec := httptest.NewRecorder()
				handler(rec, httptest.NewRequest(http.MethodGet, "/api/applemusic/token", nil))
				if rec.Code != http.StatusServiceUnavailable || rec.Header().Get("Content-Type") != "application/json" ||
					rec.Header().Get("Cache-Control") != "no-store" {
					t.Fatal("disabled or invalid configuration must produce a non-cacheable JSON 503")
				}
				var body map[string]string
				if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil || len(body) != 2 ||
					body["error"] != "applemusic_unavailable" || body["message"] != "Apple Music developer token is unavailable." {
					t.Fatal("503 must use the same generic error without credentials, key paths, tokens, or parser details")
				}
			}
		})
	}
	if logs.Len() != 0 {
		t.Fatal("token endpoint must not log configuration, parser errors, or credentials")
	}
}

func TestAppleMusicTokenRateLimit(t *testing.T) {
	path, _ := appleMusicTestKey(t)
	configureAppleMusicTest(t, "TEAM123456", "KEY1234567", path)
	oldTrustProxy := config.TrustProxy
	config.TrustProxy = false
	t.Cleanup(func() { config.TrustProxy = oldTrustProxy })
	mux := http.NewServeMux()
	mux.HandleFunc("GET /api/applemusic/token", newAppleMusicTokenHandler())
	mux.HandleFunc("GET /api/time", handleTime)
	mux.HandleFunc("GET /healthz", handleHealthz)

	for i := 0; i < 31; i++ {
		req := httptest.NewRequest(http.MethodGet, "/api/applemusic/token", nil)
		req.RemoteAddr = "192.0.2.1:" + strconv.Itoa(10000+i)
		// An untrusted forwarding header must not bypass the existing IP policy.
		req.Header.Set("X-Forwarded-For", "198.51.100."+strconv.Itoa(i+1))
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, req)
		wantStatus := http.StatusOK
		if i == 30 {
			wantStatus = http.StatusTooManyRequests
			if rec.Header().Get("Retry-After") != "60" || rec.Header().Get("Cache-Control") != "no-store" {
				t.Fatal("429 must include retry guidance and must not be cached")
			}
			var body map[string]string
			if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil || body["error"] != "rate_limited" || len(body) != 2 {
				t.Fatal("rate limit response must contain only a safe JSON error")
			}
		}
		if rec.Code != wantStatus {
			t.Fatalf("request %d: status = %d, want %d", i+1, rec.Code, wantStatus)
		}
	}

	for _, path := range []string{"/api/time", "/healthz", "/api/applemusic/token"} {
		req := httptest.NewRequest(http.MethodGet, path, nil)
		req.RemoteAddr = "192.0.2.1:20000"
		if path == "/api/applemusic/token" {
			req.RemoteAddr = "192.0.2.2:20000"
		}
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, req)
		if rec.Code != http.StatusOK {
			t.Fatal("token rate limit must not affect other routes or another IP")
		}
	}
}

func TestAppleMusicTokenUnavailableDoesNotBreakOtherRoutes(t *testing.T) {
	configureAppleMusicTest(t, "", "", "")
	ts := setupTestServer()
	defer ts.Close()
	client := ts.Client()
	client.Timeout = 5 * time.Second
	for _, tc := range []struct {
		path   string
		status int
	}{
		{"/api/applemusic/token", http.StatusServiceUnavailable},
		{"/healthz", http.StatusOK},
		{"/api/time", http.StatusOK},
	} {
		res, err := client.Get(ts.URL + tc.path)
		if err != nil {
			t.Fatal(err)
		}
		_, _ = io.Copy(io.Discard, res.Body)
		res.Body.Close()
		if res.StatusCode != tc.status {
			t.Fatalf("GET %s: status = %d, want %d", tc.path, res.StatusCode, tc.status)
		}
	}
}
