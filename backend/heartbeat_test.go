package main

import (
	"runtime"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

// SEC-13: the heartbeat fan-out and its scheduling, driven deterministically
// instead of waiting for the production interval.
func TestHeartbeatFanOutDoesNotWaitForAStalledParty(t *testing.T) {
	release := make(chan struct{})
	otherPartyWritten := make(chan time.Time, 1)

	began := time.Now()
	go heartbeatFanOut([]string{"AAAAAA", "BBBBBB"}, func(code string) {
		switch code {
		case "AAAAAA":
			<-release // a socket waiting out its write deadline
		case "BBBBBB":
			otherPartyWritten <- time.Now()
		}
	})

	select {
	case at := <-otherPartyWritten:
		if waited := at.Sub(began); waited > 200*time.Millisecond {
			t.Fatalf("the second party's heartbeat waited %v for the stalled one", waited)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("a stalled party stopped another party's heartbeat")
	}

	close(release)
}

func TestHeartbeatPassesDoNotOverlap(t *testing.T) {
	var active int32
	var maxActive int32
	var passes int32

	pass := func() {
		n := atomic.AddInt32(&active, 1)
		for {
			old := atomic.LoadInt32(&maxActive)
			if n <= old || atomic.CompareAndSwapInt32(&maxActive, old, n) {
				break
			}
		}
		time.Sleep(40 * time.Millisecond)
		atomic.AddInt32(&active, -1)
		atomic.AddInt32(&passes, 1)
	}

	ticker := func() { runHeartbeatPasses(10*time.Millisecond, 5, pass) }
	ticker()

	if got := atomic.LoadInt32(&maxActive); got != 1 {
		t.Fatalf("heartbeat passes overlapped: %d were running at once", got)
	}
	if got := atomic.LoadInt32(&passes); got != 5 {
		t.Fatalf("ran %d passes, want 5", got)
	}
}

func TestHeartbeatFanOutTerminatesAndDoesNotAccumulate(t *testing.T) {
	before := runtime.NumGoroutine()
	var hits int32

	heartbeatFanOut(make([]string, 50), func(string) { atomic.AddInt32(&hits, 1) })

	if got := atomic.LoadInt32(&hits); got != 50 {
		t.Fatalf("beat %d parties, want 50", got)
	}
	// Every fan-out goroutine has called Done by the time the call returns, so
	// the count must come back down; allow for runtime noise from other tests.
	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		if runtime.NumGoroutine() <= before+2 {
			return
		}
		time.Sleep(10 * time.Millisecond)
	}
	t.Fatalf("goroutines grew from %d to %d and did not come back", before, runtime.NumGoroutine())
}

func TestHeartbeatFanOutContainsOnePartysPanic(t *testing.T) {
	var written []string
	var mu sync.Mutex

	heartbeatFanOut([]string{"AAAAAA", "BBBBBB"}, func(code string) {
		if code == "AAAAAA" {
			panic("socket write blew up")
		}
		mu.Lock()
		written = append(written, code)
		mu.Unlock()
	})

	mu.Lock()
	defer mu.Unlock()
	if len(written) != 1 || written[0] != "BBBBBB" {
		t.Fatalf("a panic in one party's heartbeat stopped the others: %v", written)
	}
}
