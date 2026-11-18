package syncapi

import (
	"net"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	createProfileLimit = 5
	pairingClaimLimit  = 15
	pairingCreateLimit = 10
	maxTrackedClients  = 10000
)

type rateWindow struct {
	started time.Time
	count   int
}

type ipRateLimiter struct {
	mu      sync.Mutex
	limit   int
	window  time.Duration
	clients map[string]rateWindow
	sweptAt time.Time
}

func newIPRateLimiter(limit int, window time.Duration) *ipRateLimiter {
	return &ipRateLimiter{limit: limit, window: window, clients: make(map[string]rateWindow)}
}

func (l *ipRateLimiter) allow(key string, now time.Time) (bool, time.Duration) {
	l.mu.Lock()
	defer l.mu.Unlock()
	if now.Sub(l.sweptAt) >= time.Second*30 {
		for client, entry := range l.clients {
			if now.Sub(entry.started) >= l.window {
				delete(l.clients, client)
			}
		}
		l.sweptAt = now
	}
	entry, exists := l.clients[key]
	if !exists {
		if len(l.clients) >= maxTrackedClients {
			return false, l.window
		}
		l.clients[key] = rateWindow{started: now, count: 1}
		return true, 0
	}
	if now.Sub(entry.started) >= l.window {
		l.clients[key] = rateWindow{started: now, count: 1}
		return true, 0
	}
	if entry.count >= l.limit {
		return false, l.window - now.Sub(entry.started)
	}
	entry.count++
	l.clients[key] = entry
	return true, 0
}

func clientIP(r *http.Request, trustProxy bool) string {
	if trustProxy {
		forwarded := strings.Split(r.Header.Get("X-Forwarded-For"), ",")
		if len(forwarded) > 0 {
			candidate := strings.TrimSpace(forwarded[0])
			if net.ParseIP(candidate) != nil {
				return candidate
			}
		}
	}
	if host, _, err := net.SplitHostPort(r.RemoteAddr); err == nil {
		return host
	}
	return r.RemoteAddr
}

func rateLimitResponse(w http.ResponseWriter, retryAfter time.Duration) {
	seconds := int(retryAfter.Round(time.Second).Seconds())
	if seconds < 1 {
		seconds = 1
	}
	w.Header().Set("Retry-After", strconv.Itoa(seconds))
	writeError(w, http.StatusTooManyRequests, "rate_limited", "Too many requests; retry later.")
}
