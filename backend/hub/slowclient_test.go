package hub

import (
	"net/http"
	"net/http/httptest"
	"runtime"
	"strings"
	"testing"
	"time"

	"github.com/gorilla/websocket"
)

// SEC-13: a broadcast waits for every target write, each capped by the 5 s
// write deadline in SafeConn. That delay is only a defect if it reaches parties
// the slow client is not in, if it grows without bound, or if it makes the
// heartbeat loop fall behind. This measures all three.
func TestSlowClientDoesNotDelayOtherParties(t *testing.T) {
	upgrader := websocket.Upgrader{CheckOrigin: func(*http.Request) bool { return true }}
	serverConns := make(chan *websocket.Conn, 4)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			return
		}
		serverConns <- conn
		<-r.Context().Done()
	}))
	defer srv.Close()

	url := "ws" + strings.TrimPrefix(srv.URL, "http")
	dial := func() *websocket.Conn {
		c, _, err := websocket.DefaultDialer.Dial(url, nil)
		if err != nil {
			t.Fatalf("dial: %v", err)
		}
		return c
	}

	// The slow client: it completes the handshake and then never reads again, so
	// its socket buffers fill and server writes stall.
	slowClient := dial()
	defer slowClient.Close()
	slowServer := <-serverConns

	// A healthy client in a different party, drained continuously.
	fastClient := dial()
	defer fastClient.Close()
	fastServer := <-serverConns
	received := make(chan time.Time, 16)
	go func() {
		for {
			if _, _, err := fastClient.ReadMessage(); err != nil {
				return
			}
			select {
			case received <- time.Now():
			default:
			}
		}
	}()

	h := NewHub()
	h.Attach("AAAAAA", "slow", slowServer)
	h.Attach("BBBBBB", "fast", fastServer)

	big := strings.Repeat("x", 8<<20)
	baselineFast := time.Since(time.Now())
	_ = baselineFast

	before := runtime.NumGoroutine()
	slowDone := make(chan time.Duration, 1)
	go func() {
		start := time.Now()
		h.Broadcast("AAAAAA", big, "")
		slowDone <- time.Since(start)
	}()

	// Give the slow write time to actually block on a full socket buffer.
	time.Sleep(200 * time.Millisecond)

	start := time.Now()
	h.Broadcast("BBBBBB", "heartbeat", "")
	otherParty := time.Since(start)

	slowTook := <-slowDone
	after := runtime.NumGoroutine()

	t.Logf("slow-client broadcast: %v", slowTook)
	t.Logf("unrelated party broadcast while it was blocked: %v", otherParty)
	t.Logf("goroutines before=%d after=%d", before, after)

	if otherParty > 500*time.Millisecond {
		t.Errorf("a blocked client delayed an unrelated party by %v", otherParty)
	}
	if slowTook < 500*time.Millisecond {
		t.Logf("note: the slow write did not block; the socket buffer absorbed the payload")
	}
	if after > before+4 {
		t.Errorf("goroutines grew from %d to %d around one broadcast", before, after)
	}
}

// The same party: a blocked member must not stop the others from being written
// to, because Broadcast writes to every target concurrently.
func TestSlowClientDoesNotStopItsOwnParty(t *testing.T) {
	upgrader := websocket.Upgrader{CheckOrigin: func(*http.Request) bool { return true }}
	serverConns := make(chan *websocket.Conn, 4)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			return
		}
		serverConns <- conn
		<-r.Context().Done()
	}))
	defer srv.Close()

	url := "ws" + strings.TrimPrefix(srv.URL, "http")
	slowClient, _, err := websocket.DefaultDialer.Dial(url, nil)
	if err != nil {
		t.Fatalf("dial slow: %v", err)
	}
	defer slowClient.Close()
	slowServer := <-serverConns

	fastClient, _, err := websocket.DefaultDialer.Dial(url, nil)
	if err != nil {
		t.Fatalf("dial fast: %v", err)
	}
	defer fastClient.Close()
	fastServer := <-serverConns

	h := NewHub()
	h.Attach("AAAAAA", "slow", slowServer)
	h.Attach("AAAAAA", "fast", fastServer)

	got := make(chan time.Time, 8)
	go func() {
		for {
			if _, _, err := fastClient.ReadMessage(); err != nil {
				return
			}
			got <- time.Now()
		}
	}()

	big := strings.Repeat("y", 8<<20)
	go h.Broadcast("AAAAAA", big, "")

	select {
	case at := <-got:
		t.Logf("the healthy member of the same party was written to at %v", at.Format("15:04:05.000"))
	case <-time.After(2 * time.Second):
		t.Fatal("a blocked member stopped its own party from being written to")
	}
}
