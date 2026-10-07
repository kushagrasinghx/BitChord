// Package presence counts how many app installs are open right now.
//
// Each open app sends a small heartbeat every few minutes. The tracker keeps one
// fixed-size entry per install and forgets it once it has missed its window,
// so memory grows with open installs (about 50 bytes each), not with requests.
// Reads never take the lock: Sweep publishes the counts and Counts loads them.
package presence

import (
	"encoding/hex"
	"sync"
	"sync/atomic"
	"time"
)

type Platform uint8

const (
	Android Platform = iota + 1
	PC
)

// ParsePlatform accepts only the platforms the apps ship on.
func ParsePlatform(s string) (Platform, bool) {
	switch s {
	case "android":
		return Android, true
	case "pc":
		return PC, true
	}
	return 0, false
}

// ParseID accepts a canonical UUID (8-4-4-4-12 hex digits, either case).
func ParseID(s string) ([16]byte, bool) {
	var id [16]byte
	if len(s) != 36 || s[8] != '-' || s[13] != '-' || s[18] != '-' || s[23] != '-' {
		return id, false
	}
	compact := s[0:8] + s[9:13] + s[14:18] + s[19:23] + s[24:36]
	if _, err := hex.Decode(id[:], []byte(compact)); err != nil {
		return id, false
	}
	return id, true
}

type Counts struct {
	Online    int   `json:"online"`
	Android   int   `json:"android"`
	PC        int   `json:"pc"`
	UpdatedAt int64 `json:"updatedAt"`
}

type entry struct {
	lastMs   int64
	platform Platform
}

type Tracker struct {
	mu         sync.Mutex
	seen       map[[16]byte]entry
	windowMs   int64
	maxEntries int
	counts     atomic.Pointer[Counts]
}

// NewTracker counts an install as open for window after its last ping. Once
// maxEntries installs are tracked, new ones are ignored until old ones expire,
// so a flood of made-up IDs is capped in memory.
func NewTracker(window time.Duration, maxEntries int) *Tracker {
	t := &Tracker{
		seen:       make(map[[16]byte]entry),
		windowMs:   window.Milliseconds(),
		maxEntries: maxEntries,
	}
	t.counts.Store(&Counts{})
	return t
}

func (t *Tracker) Ping(id [16]byte, platform Platform, nowMs int64) {
	t.mu.Lock()
	defer t.mu.Unlock()
	if _, known := t.seen[id]; !known && t.maxEntries > 0 && len(t.seen) >= t.maxEntries {
		return
	}
	t.seen[id] = entry{lastMs: nowMs, platform: platform}
}

// Close forgets an install that told us it closed, instead of waiting out its window.
func (t *Tracker) Close(id [16]byte) {
	t.mu.Lock()
	delete(t.seen, id)
	t.mu.Unlock()
}

// Sweep drops installs that missed their window and publishes the new counts.
func (t *Tracker) Sweep(nowMs int64) {
	cutoff := nowMs - t.windowMs
	c := Counts{UpdatedAt: nowMs}
	t.mu.Lock()
	for id, e := range t.seen {
		if e.lastMs < cutoff {
			delete(t.seen, id)
			continue
		}
		switch e.platform {
		case Android:
			c.Android++
		case PC:
			c.PC++
		}
	}
	t.mu.Unlock()
	c.Online = c.Android + c.PC
	t.counts.Store(&c)
}

// Counts returns the figures from the last Sweep.
func (t *Tracker) Counts() Counts {
	return *t.counts.Load()
}
