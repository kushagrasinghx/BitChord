// Package applemusic issues server-signed MusicKit developer tokens. It does not
// handle user authorization or Music User Tokens.
package applemusic

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
	"errors"
	"io"
	"os"
	"strings"
	"sync"
	"time"
)

const (
	tokenValidity = time.Hour
	refreshSlack  = 5 * time.Minute
	maxKeyBytes   = 16 * 1024
)

// ErrUnavailable deliberately contains no configuration or signing details.
var ErrUnavailable = errors.New("Apple Music developer token is unavailable")

type DeveloperToken struct {
	Token     string `json:"token"`
	ExpiresAt int64  `json:"expiresAt"`
}

// TokenProvider loads its key once and shares one cached token across callers.
// Its zero value (and a nil provider) is disabled and safe to use.
type TokenProvider struct {
	mu       sync.Mutex
	key      *ecdsa.PrivateKey
	teamID   string
	keyID    string
	now      func() time.Time
	cached   DeveloperToken
	issuedAt int64
}

// NewTokenProvider accepts only an Apple .p8 (unencrypted PKCS#8 P-256) key file.
// Missing, partial, and invalid configuration all fail closed with the same error.
func NewTokenProvider(teamID, keyID, privateKeyFile string) (*TokenProvider, error) {
	teamID = strings.TrimSpace(teamID)
	keyID = strings.TrimSpace(keyID)
	privateKeyFile = strings.TrimSpace(privateKeyFile)
	if !validID(teamID) || !validID(keyID) || privateKeyFile == "" {
		return nil, ErrUnavailable
	}

	file, err := os.Open(privateKeyFile)
	if err != nil {
		return nil, ErrUnavailable
	}
	defer file.Close()
	keyPEM, err := io.ReadAll(io.LimitReader(file, maxKeyBytes+1))
	if err != nil || len(keyPEM) > maxKeyBytes {
		return nil, ErrUnavailable
	}
	keyPEM = bytes.TrimSpace(keyPEM)
	block, rest := pem.Decode(keyPEM)
	if block == nil || block.Type != "PRIVATE KEY" || len(block.Headers) != 0 ||
		len(bytes.TrimSpace(rest)) != 0 || !bytes.HasPrefix(keyPEM, []byte("-----BEGIN PRIVATE KEY-----")) {
		return nil, ErrUnavailable
	}
	parsed, err := x509.ParsePKCS8PrivateKey(block.Bytes)
	if err != nil {
		return nil, ErrUnavailable
	}
	key, ok := parsed.(*ecdsa.PrivateKey)
	if !ok || key.Curve != elliptic.P256() {
		return nil, ErrUnavailable
	}
	return &TokenProvider{key: key, teamID: teamID, keyID: keyID, now: time.Now}, nil
}

func validID(id string) bool {
	if len(id) != 10 {
		return false
	}
	for _, c := range id {
		if (c < 'A' || c > 'Z') && (c < '0' || c > '9') {
			return false
		}
	}
	return true
}

func (p *TokenProvider) Token() (DeveloperToken, error) {
	if p == nil || p.key == nil {
		return DeveloperToken{}, ErrUnavailable
	}
	p.mu.Lock()
	defer p.mu.Unlock()

	now := p.now().Unix()
	// A backwards clock jump must not serve a token whose iat is in the future.
	if p.cached.Token != "" && now >= p.issuedAt && now < p.cached.ExpiresAt-int64(refreshSlack/time.Second) {
		return p.cached, nil
	}

	expiresAt := now + int64(tokenValidity/time.Second)
	header, err := json.Marshal(struct {
		Algorithm string `json:"alg"`
		KeyID     string `json:"kid"`
		Type      string `json:"typ"`
	}{"ES256", p.keyID, "JWT"})
	if err != nil {
		return DeveloperToken{}, ErrUnavailable
	}
	claims, err := json.Marshal(struct {
		Issuer    string `json:"iss"`
		IssuedAt  int64  `json:"iat"`
		ExpiresAt int64  `json:"exp"`
	}{p.teamID, now, expiresAt})
	if err != nil {
		return DeveloperToken{}, ErrUnavailable
	}
	input := base64.RawURLEncoding.EncodeToString(header) + "." + base64.RawURLEncoding.EncodeToString(claims)
	digest := sha256.Sum256([]byte(input))
	r, s, err := ecdsa.Sign(rand.Reader, p.key, digest[:])
	if err != nil {
		return DeveloperToken{}, ErrUnavailable
	}
	// JOSE uses fixed-width R || S, not the ASN.1 encoding used by SignASN1.
	var signature [64]byte
	r.FillBytes(signature[:32])
	s.FillBytes(signature[32:])
	p.cached = DeveloperToken{
		Token:     input + "." + base64.RawURLEncoding.EncodeToString(signature[:]),
		ExpiresAt: expiresAt,
	}
	p.issuedAt = now
	return p.cached, nil
}
