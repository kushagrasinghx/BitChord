package main

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/KabirSinghBhatia/BitChord/backend/config"
)

// A forwarded chain is append-only, so only the last entry was written by the
// proxy we trust. Everything before it came from the client [SEC-12].
func TestForwardedClientIPTakesTheProxyAppendedHop(t *testing.T) {
	cases := []struct {
		name   string
		header string
		want   string
	}{
		{"single proxy", "203.0.113.7", "203.0.113.7"},
		{"client wrote its own first", "1.2.3.4, 203.0.113.7", "203.0.113.7"},
		{"many forged entries", "9.9.9.9, 8.8.8.8, 203.0.113.7", "203.0.113.7"},
		{"port stripped", "203.0.113.7:51234", "203.0.113.7"},
		{"whitespace tolerated", "  1.2.3.4 ,   203.0.113.7  ", "203.0.113.7"},
		{"trailing comma ignored", "203.0.113.7, ", "203.0.113.7"},
		{"ipv6", "2001:db8::1", "2001:db8::1"},
		{"garbage nearest hop is not trusted", "203.0.113.7, not-an-ip", ""},
		{"empty header", "", ""},
	}

	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if got := forwardedClientIP(c.header); got != c.want {
				t.Fatalf("forwardedClientIP(%q) = %q, want %q", c.header, got, c.want)
			}
		})
	}
}

// With the proxy trusted, a request carrying a forged chain is attributed to
// the hop the proxy appended, not to the address the client chose.
func TestClientIPIgnoresAForgeableFirstHop(t *testing.T) {
	previous := config.TrustProxy
	config.TrustProxy = true
	defer func() { config.TrustProxy = previous }()

	request := httptest.NewRequest(http.MethodGet, "/api/parties", nil)
	request.RemoteAddr = "10.0.0.1:40000"
	request.Header.Set("X-Forwarded-For", "1.2.3.4, 203.0.113.7")

	if got := clientIP(request); got != "203.0.113.7" {
		t.Fatalf("clientIP = %q, want the proxy-appended hop 203.0.113.7", got)
	}
}

// An unusable header must not be trusted at all: the socket address wins.
func TestClientIPFallsBackToTheSocketOnGarbage(t *testing.T) {
	previous := config.TrustProxy
	config.TrustProxy = true
	defer func() { config.TrustProxy = previous }()

	request := httptest.NewRequest(http.MethodGet, "/api/parties", nil)
	request.RemoteAddr = "10.0.0.1:40000"
	request.Header.Set("X-Forwarded-For", "definitely not an address")

	if got := clientIP(request); got != "10.0.0.1" {
		t.Fatalf("clientIP = %q, want the socket address 10.0.0.1", got)
	}
}

// With no proxy configured the header is ignored entirely, which is what stops
// a direct client from evading a per-IP limit by setting it.
func TestClientIPIgnoresTheHeaderWhenNoProxyIsTrusted(t *testing.T) {
	previous := config.TrustProxy
	config.TrustProxy = false
	defer func() { config.TrustProxy = previous }()

	request := httptest.NewRequest(http.MethodGet, "/api/parties", nil)
	request.RemoteAddr = "10.0.0.1:40000"
	request.Header.Set("X-Forwarded-For", "203.0.113.7")

	if got := clientIP(request); got != "10.0.0.1" {
		t.Fatalf("clientIP = %q, want the socket address when no proxy is trusted", got)
	}
}
