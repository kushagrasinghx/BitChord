package applemusic

import (
	"crypto/ecdsa"
	"crypto/ed25519"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"math/big"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"
)

const (
	testTeamID = "TEAM123456"
	testKeyID  = "KEY1234567"
)

func newTestKey(t *testing.T, curve elliptic.Curve) *ecdsa.PrivateKey {
	t.Helper()
	key, err := ecdsa.GenerateKey(curve, rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	return key
}

func testKeyPEM(t *testing.T, key any) []byte {
	t.Helper()
	der, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		t.Fatal(err)
	}
	return pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: der})
}

func writeTestKeyFile(t *testing.T, contents []byte) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "AuthKey_test.p8")
	if err := os.WriteFile(path, contents, 0600); err != nil {
		t.Fatal(err)
	}
	return path
}

func newTestProvider(t *testing.T) (*TokenProvider, *ecdsa.PrivateKey) {
	t.Helper()
	key := newTestKey(t, elliptic.P256())
	path := writeTestKeyFile(t, testKeyPEM(t, key))
	provider, err := NewTokenProvider(testTeamID, testKeyID, path)
	if err != nil {
		t.Fatal(err)
	}
	return provider, key
}

func verifyTestToken(t *testing.T, token DeveloperToken, key *ecdsa.PublicKey, now time.Time) {
	t.Helper()
	parts := strings.Split(token.Token, ".")
	if len(parts) != 3 {
		t.Fatal("JWT must have three segments")
	}
	decoded := make([][]byte, 3)
	for i, part := range parts {
		var err error
		decoded[i], err = base64.RawURLEncoding.DecodeString(part)
		if err != nil || strings.Contains(part, "=") {
			t.Fatalf("segment %d must use unpadded base64url", i)
		}
	}
	var header map[string]string
	if err := json.Unmarshal(decoded[0], &header); err != nil {
		t.Fatal("invalid JWT header")
	}
	if len(header) != 3 || header["alg"] != "ES256" || header["typ"] != "JWT" || header["kid"] != testKeyID {
		t.Fatal("unexpected JWT header fields")
	}
	var claims struct {
		Issuer    string `json:"iss"`
		IssuedAt  int64  `json:"iat"`
		ExpiresAt int64  `json:"exp"`
	}
	if err := json.Unmarshal(decoded[1], &claims); err != nil {
		t.Fatal("invalid JWT claims or non-numeric timestamps")
	}
	var fields map[string]json.RawMessage
	if err := json.Unmarshal(decoded[1], &fields); err != nil || len(fields) != 3 {
		t.Fatal("JWT must contain only iss, iat, and exp")
	}
	if claims.Issuer != testTeamID || claims.IssuedAt != now.Unix() {
		t.Fatal("unexpected JWT issuer or issued-at timestamp")
	}
	if claims.ExpiresAt != now.Unix()+3600 || token.ExpiresAt != claims.ExpiresAt {
		t.Fatal("expiry must match the response and be exactly one hour after iat")
	}
	if claims.ExpiresAt-claims.IssuedAt > 180*24*60*60 {
		t.Fatal("token validity exceeds six months")
	}
	if len(decoded[2]) != 64 {
		t.Fatalf("ES256 JOSE signature length = %d, want 64", len(decoded[2]))
	}
	r := new(big.Int).SetBytes(decoded[2][:32])
	s := new(big.Int).SetBytes(decoded[2][32:])
	digest := sha256.Sum256([]byte(parts[0] + "." + parts[1]))
	if !ecdsa.Verify(key, digest[:], r, s) {
		t.Fatal("JWT signature does not verify with the configured public key")
	}
	tampered := sha256.Sum256([]byte(parts[0] + "." + parts[1] + "tampered"))
	if ecdsa.Verify(key, tampered[:], r, s) {
		t.Fatal("signature accepted tampered claims")
	}
}

func TestTokenSignatureAndClaims(t *testing.T) {
	provider, key := newTestProvider(t)
	now := time.Unix(1800000000, 987654321)
	provider.now = func() time.Time { return now }
	token, err := provider.Token()
	if err != nil {
		t.Fatal(err)
	}
	verifyTestToken(t, token, &key.PublicKey, now)
}

func TestTokenCacheAndRefresh(t *testing.T) {
	provider, key := newTestProvider(t)
	now := time.Unix(1800000000, 0)
	provider.now = func() time.Time { return now }
	first, err := provider.Token()
	if err != nil {
		t.Fatal(err)
	}

	for _, advance := range []time.Duration{0, time.Minute, tokenValidity - refreshSlack - time.Second} {
		now = time.Unix(1800000000, 0).Add(advance)
		cached, err := provider.Token()
		if err != nil || cached != first {
			t.Fatal("token changed before the refresh boundary")
		}
	}

	now = time.Unix(first.ExpiresAt, 0).Add(-refreshSlack)
	refreshed, err := provider.Token()
	if err != nil || refreshed.Token == first.Token {
		t.Fatal("token was not refreshed exactly at the slack boundary")
	}
	verifyTestToken(t, refreshed, &key.PublicKey, now)

	// Returned values cannot mutate the cache.
	copyOfToken := refreshed
	copyOfToken.Token = "changed by caller"
	copyOfToken.ExpiresAt = 0
	cached, err := provider.Token()
	if err != nil || cached != refreshed {
		t.Fatal("caller changed the cached value")
	}

	now = time.Unix(refreshed.ExpiresAt+1, 0)
	afterExpiry, err := provider.Token()
	if err != nil || afterExpiry.Token == refreshed.Token {
		t.Fatal("expired token was returned")
	}
	verifyTestToken(t, afterExpiry, &key.PublicKey, now)

	now = now.Add(-10 * time.Minute)
	afterClockJump, err := provider.Token()
	if err != nil || afterClockJump.Token == afterExpiry.Token {
		t.Fatal("token with a future iat was reused after a backwards clock jump")
	}
	verifyTestToken(t, afterClockJump, &key.PublicKey, now)
}

func TestTokenConcurrentCache(t *testing.T) {
	provider, key := newTestProvider(t)
	now := time.Unix(1800000000, 0)
	provider.now = func() time.Time { return now }
	var previous DeveloperToken
	for round := 0; round < 2; round++ {
		const callers = 64
		tokens := make(chan DeveloperToken, callers)
		errors := make(chan error, callers)
		var wg sync.WaitGroup
		for i := 0; i < callers; i++ {
			wg.Add(1)
			go func() {
				defer wg.Done()
				token, err := provider.Token()
				tokens <- token
				errors <- err
			}()
		}
		wg.Wait()
		close(tokens)
		close(errors)
		for err := range errors {
			if err != nil {
				t.Fatal(err)
			}
		}
		var first DeveloperToken
		for token := range tokens {
			if first.Token == "" {
				first = token
			}
			if token != first {
				t.Fatal("concurrent callers received different cached tokens")
			}
		}
		verifyTestToken(t, first, &key.PublicKey, now)
		if round > 0 && first == previous {
			t.Fatal("concurrent callers did not refresh the token")
		}
		previous = first
		now = time.Unix(first.ExpiresAt, 0).Add(-refreshSlack)
	}
}

func TestTokenProviderConfiguration(t *testing.T) {
	key := newTestKey(t, elliptic.P256())
	path := writeTestKeyFile(t, testKeyPEM(t, key))
	cases := []struct {
		name, teamID, keyID, path string
	}{
		{"disabled", "", "", ""},
		{"team only", testTeamID, "", ""},
		{"key ID only", "", testKeyID, ""},
		{"file only", "", "", path},
		{"missing team", "", testKeyID, path},
		{"missing key ID", testTeamID, "", path},
		{"missing file", testTeamID, testKeyID, ""},
		{"whitespace", " \t", "\n", " "},
		{"short team", "TEAM", testKeyID, path},
		{"lowercase team", "team123456", testKeyID, path},
		{"invalid team character", "TEAM12345!", testKeyID, path},
		{"long key ID", testTeamID, "KEY12345678", path},
		{"invalid key ID", testTeamID, "KEY123456!", path},
		{"nonexistent file", testTeamID, testKeyID, filepath.Join(t.TempDir(), "missing-secret.p8")},
		{"directory", testTeamID, testKeyID, t.TempDir()},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			provider, err := NewTokenProvider(tc.teamID, tc.keyID, tc.path)
			if err != ErrUnavailable || provider != nil {
				t.Fatal("invalid configuration must return only ErrUnavailable and no provider")
			}
			if token, err := provider.Token(); err != ErrUnavailable || token != (DeveloperToken{}) {
				t.Fatal("unavailable provider must never return a token")
			}
		})
	}

	t.Run("trimmed valid configuration", func(t *testing.T) {
		provider, err := NewTokenProvider(" "+testTeamID+"\n", "\t"+testKeyID+" ", " "+path+" ")
		if err != nil {
			t.Fatal(err)
		}
		now := time.Unix(1800000000, 0)
		provider.now = func() time.Time { return now }
		token, err := provider.Token()
		if err != nil {
			t.Fatal(err)
		}
		verifyTestToken(t, token, &key.PublicKey, now)
	})
}

func TestTokenProviderRejectsInvalidKeys(t *testing.T) {
	key := newTestKey(t, elliptic.P256())
	valid := testKeyPEM(t, key)
	sec1, err := x509.MarshalECPrivateKey(key)
	if err != nil {
		t.Fatal(err)
	}
	publicKey, err := x509.MarshalPKIXPublicKey(&key.PublicKey)
	if err != nil {
		t.Fatal(err)
	}
	_, otherKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	cases := []struct {
		name string
		pem  []byte
	}{
		{"empty", nil},
		{"not PEM", []byte("secret invalid key material")},
		{"bad DER", pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: []byte("secret invalid DER")})},
		{"wrong curve", testKeyPEM(t, newTestKey(t, elliptic.P384()))},
		{"wrong key type", testKeyPEM(t, otherKey)},
		{"SEC1 instead of PKCS8", pem.EncodeToMemory(&pem.Block{Type: "EC PRIVATE KEY", Bytes: sec1})},
		{"public key only", pem.EncodeToMemory(&pem.Block{Type: "PUBLIC KEY", Bytes: publicKey})},
		{"encrypted PEM", pem.EncodeToMemory(&pem.Block{Type: "ENCRYPTED PRIVATE KEY", Bytes: []byte("encrypted secret")})},
		{"PEM headers", pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Headers: map[string]string{"Secret": "hidden"}, Bytes: sec1})},
		{"multiple keys", append(append([]byte{}, valid...), valid...)},
		{"trailing data", append(append([]byte{}, valid...), []byte("secret trailing data")...)},
		{"leading data", append([]byte("secret prefix\n"), valid...)},
		{"oversized", []byte(strings.Repeat("x", maxKeyBytes+1))},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			path := writeTestKeyFile(t, tc.pem)
			provider, err := NewTokenProvider(testTeamID, testKeyID, path)
			if err != ErrUnavailable || provider != nil {
				t.Fatal("invalid key must fail closed with a generic error")
			}
		})
	}

	t.Run("PEM whitespace", func(t *testing.T) {
		path := writeTestKeyFile(t, append(append([]byte("\n \t"), valid...), []byte("\n \t")...))
		provider, err := NewTokenProvider(testTeamID, testKeyID, path)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := provider.Token(); err != nil {
			t.Fatal(err)
		}
	})
}

func TestDisabledTokenProvider(t *testing.T) {
	for _, provider := range []*TokenProvider{nil, {}} {
		if token, err := provider.Token(); err != ErrUnavailable || token != (DeveloperToken{}) {
			t.Fatal("disabled provider must return a generic error and no token")
		}
	}
}

func TestTokenKeyLoadedOnce(t *testing.T) {
	key := newTestKey(t, elliptic.P256())
	path := writeTestKeyFile(t, testKeyPEM(t, key))
	provider, err := NewTokenProvider(testTeamID, testKeyID, path)
	if err != nil {
		t.Fatal(err)
	}
	if err := os.Remove(path); err != nil {
		t.Fatal(err)
	}
	now := time.Unix(1800000000, 0)
	provider.now = func() time.Time { return now }
	for i := 0; i < 2; i++ {
		token, err := provider.Token()
		if err != nil {
			t.Fatal("signing must use the key loaded at startup, not re-read the file")
		}
		verifyTestToken(t, token, &key.PublicKey, now)
		now = time.Unix(token.ExpiresAt+1, 0)
	}
}
