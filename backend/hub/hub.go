package hub

import (
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

// SafeConn wraps *websocket.Conn with a mutex to prevent concurrent write panic.
type SafeConn struct {
	mu   sync.Mutex
	conn *websocket.Conn
}

func NewSafeConn(conn *websocket.Conn) *SafeConn {
	return &SafeConn{conn: conn}
}

func (s *SafeConn) WriteJSON(v interface{}) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	_ = s.conn.SetWriteDeadline(time.Now().Add(5 * time.Second))
	return s.conn.WriteJSON(v)
}

func (s *SafeConn) Close() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.conn.Close()
}

// Hub coordinates active WebSocket connections across rooms.
type Hub struct {
	mu      sync.RWMutex
	sockets map[string]map[string]*SafeConn // code -> memberId -> SafeConn
}

func NewHub() *Hub {
	return &Hub{
		sockets: make(map[string]map[string]*SafeConn),
	}
}

func (h *Hub) Attach(code, memberId string, conn *websocket.Conn) *SafeConn {
	sc := NewSafeConn(conn)
	h.mu.Lock()
	room, ok := h.sockets[code]
	if !ok {
		room = make(map[string]*SafeConn)
		h.sockets[code] = room
	}
	prev := room[memberId]
	room[memberId] = sc
	h.mu.Unlock()

	// Close previous connection outside lock so it doesn't stall other parties
	if prev != nil && prev != sc {
		_ = prev.Close()
	}
	return sc
}

func (h *Hub) Detach(code, memberId string, sc *SafeConn) {
	h.mu.Lock()
	defer h.mu.Unlock()

	room, ok := h.sockets[code]
	if !ok {
		return
	}
	if room[memberId] == sc {
		delete(room, memberId)
	}
	if len(room) == 0 {
		delete(h.sockets, code)
	}
}

func (h *Hub) DropParty(code string) {
	h.mu.Lock()
	room := h.sockets[code]
	delete(h.sockets, code)
	h.mu.Unlock()

	if room != nil {
		for _, sc := range room {
			_ = sc.Close()
		}
	}
}

func (h *Hub) MembersOnline(code string) []string {
	h.mu.RLock()
	defer h.mu.RUnlock()

	room := h.sockets[code]
	if room == nil {
		return nil
	}
	members := make([]string, 0, len(room))
	for mid := range room {
		members = append(members, mid)
	}
	return members
}

func (h *Hub) ActiveCodes() []string {
	h.mu.RLock()
	defer h.mu.RUnlock()

	codes := make([]string, 0, len(h.sockets))
	for c := range h.sockets {
		codes = append(codes, c)
	}
	return codes
}

func (h *Hub) Send(code, memberId string, payload interface{}) {
	h.mu.RLock()
	room := h.sockets[code]
	var target *SafeConn
	if room != nil {
		target = room[memberId]
	}
	h.mu.RUnlock()

	if target != nil {
		_ = target.WriteJSON(payload)
	}
}

func (h *Hub) CloseMember(code, memberId string) {
	h.mu.RLock()
	room := h.sockets[code]
	var target *SafeConn
	if room != nil { target = room[memberId] }
	h.mu.RUnlock()
	if target != nil { _ = target.Close() }
}

func (h *Hub) Broadcast(code string, payload interface{}, skipMemberId string) {
	h.mu.RLock()
	room := h.sockets[code]
	if room == nil || len(room) == 0 {
		h.mu.RUnlock()
		return
	}
	targets := make([]*SafeConn, 0, len(room))
	for mid, sc := range room {
		if mid != skipMemberId {
			targets = append(targets, sc)
		}
	}
	h.mu.RUnlock()

	// Concurrently write to all targets so one slow mobile network does not delay others
	var wg sync.WaitGroup
	for _, sc := range targets {
		wg.Add(1)
		go func(conn *SafeConn) {
			defer wg.Done()
			_ = conn.WriteJSON(payload)
		}(sc)
	}
	wg.Wait()
}
