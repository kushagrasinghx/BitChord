package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"strings"
	"time"

	"github.com/gorilla/websocket"

	"github.com/KabirSinghBhatia/BitChord/backend/clock"
	"github.com/KabirSinghBhatia/BitChord/backend/config"
	"github.com/KabirSinghBhatia/BitChord/backend/hub"
	"github.com/KabirSinghBhatia/BitChord/backend/party"
	"github.com/KabirSinghBhatia/BitChord/backend/protocol"
)

var (
	store    = party.NewPartyStore()
	hubInst  = hub.NewHub()
	upgrader = websocket.Upgrader{
		CheckOrigin: func(r *http.Request) bool {
			return true // Handled by CORS middleware or allowed for mobile client
		},
	}
)

func main() {
	go startHeartbeatTicker()

	mux := http.NewServeMux()

	// REST endpoints
	mux.HandleFunc("GET /", handleRoot)
	mux.HandleFunc("GET /healthz", handleHealthz)
	mux.HandleFunc("GET /api/time", handleTime)
	mux.HandleFunc("POST /api/parties", handleCreateParty)
	mux.HandleFunc("POST /api/parties/{code}/join", handleJoinParty)
	mux.HandleFunc("GET /api/parties/{code}", handleGetParty)
	mux.HandleFunc("POST /api/parties/{code}/leave", handleLeaveParty)

	// WebSocket endpoint
	mux.HandleFunc("GET /ws/parties/{code}", handleWebSocket)

	handler := corsMiddleware(mux)

	addr := fmt.Sprintf("0.0.0.0:%d", config.Port)
	log.Printf("BitChord Listen Together (Go) starting on %s...", addr)
	if err := http.ListenAndServe(addr, handler); err != nil {
		log.Fatalf("Server stopped: %v", err)
	}
}

func corsMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization")
		if r.Method == "OPTIONS" {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func jsonResponse(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(data)
}

func jsonError(w http.ResponseWriter, status int, errCode, msg string) {
	jsonResponse(w, status, map[string]string{
		"error":   errCode,
		"message": msg,
	})
}

func parseBearerToken(r *http.Request) string {
	auth := r.Header.Get("Authorization")
	parts := strings.SplitN(auth, " ", 2)
	if len(parts) == 2 && strings.EqualFold(parts[0], "Bearer") {
		return strings.TrimSpace(parts[1])
	}
	return ""
}

// REST Handlers

func handleRoot(w http.ResponseWriter, r *http.Request) {
	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"service":    "bitchord-listen-together",
		"maxMembers": config.MaxMembers,
		"parties":    store.Len(),
		"serverMs":   clock.NowMs(),
	})
}

func handleHealthz(w http.ResponseWriter, r *http.Request) {
	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"ok":       true,
		"serverMs": clock.NowMs(),
	})
}

func handleTime(w http.ResponseWriter, r *http.Request) {
	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"serverMs": clock.NowMs(),
	})
}

func handleCreateParty(w http.ResponseWriter, r *http.Request) {
	var req protocol.JoinRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		jsonError(w, http.StatusUnprocessableEntity, "invalid_json", "Malformed JSON body.")
		return
	}
	if err := req.Validate(); err != nil {
		jsonError(w, http.StatusUnprocessableEntity, "validation_error", err.Error())
		return
	}

	p, err := store.Create()
	if err != nil {
		if pe, ok := err.(*party.PartyError); ok {
			jsonError(w, pe.Status, pe.Code, pe.Message)
			return
		}
		jsonError(w, http.StatusInternalServerError, "server_error", "Failed to create party.")
		return
	}

	p.Lock()
	m, err := p.Join(req.UserId, req.DeviceId, req.DisplayName, req.AvatarUrl)
	if err != nil {
		p.Unlock()
		store.Drop(p.Code)
		if pe, ok := err.(*party.PartyError); ok {
			jsonError(w, pe.Status, pe.Code, pe.Message)
			return
		}
		jsonError(w, http.StatusInternalServerError, "server_error", err.Error())
		return
	}
	partyWire := p.ToWire()
	youWire := m.ToWire()
	token := m.Token
	code := p.Code
	p.Unlock()

	jsonResponse(w, http.StatusCreated, map[string]interface{}{
		"code":  code,
		"token": token,
		"you":   youWire,
		"party": partyWire,
	})
}

func handleJoinParty(w http.ResponseWriter, r *http.Request) {
	code := r.PathValue("code")
	var req protocol.JoinRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		jsonError(w, http.StatusUnprocessableEntity, "invalid_json", "Malformed JSON body.")
		return
	}
	if err := req.Validate(); err != nil {
		jsonError(w, http.StatusUnprocessableEntity, "validation_error", err.Error())
		return
	}

	p, err := store.Get(code)
	if err != nil {
		if pe, ok := err.(*party.PartyError); ok {
			jsonError(w, pe.Status, pe.Code, pe.Message)
			return
		}
		jsonError(w, http.StatusNotFound, "no_such_party", "No party with that code.")
		return
	}

	p.Lock()
	m, err := p.Join(req.UserId, req.DeviceId, req.DisplayName, req.AvatarUrl)
	if err != nil {
		p.Unlock()
		if pe, ok := err.(*party.PartyError); ok {
			jsonError(w, pe.Status, pe.Code, pe.Message)
			return
		}
		jsonError(w, http.StatusInternalServerError, "server_error", err.Error())
		return
	}
	partyWire := p.ToWire()
	youWire := m.ToWire()
	token := m.Token
	pCode := p.Code
	p.Unlock()

	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"code":  pCode,
		"token": token,
		"you":   youWire,
		"party": partyWire,
	})
}

func handleGetParty(w http.ResponseWriter, r *http.Request) {
	code := r.PathValue("code")
	token := parseBearerToken(r)
	if token == "" {
		jsonError(w, http.StatusUnauthorized, "unauthorized", "Missing bearer token.")
		return
	}

	p, err := store.Get(code)
	if err != nil {
		if pe, ok := err.(*party.PartyError); ok {
			jsonError(w, pe.Status, pe.Code, pe.Message)
			return
		}
		jsonError(w, http.StatusNotFound, "no_such_party", "No party with that code.")
		return
	}

	p.Lock()
	_, err = p.Authenticate(token)
	if err != nil {
		p.Unlock()
		if pe, ok := err.(*party.PartyError); ok {
			jsonError(w, pe.Status, pe.Code, pe.Message)
			return
		}
		jsonError(w, http.StatusUnauthorized, "unauthorized", "Bad token.")
		return
	}
	partyWire := p.ToWire()
	p.Unlock()

	jsonResponse(w, http.StatusOK, partyWire)
}

func handleLeaveParty(w http.ResponseWriter, r *http.Request) {
	code := r.PathValue("code")
	token := parseBearerToken(r)
	if token == "" {
		jsonError(w, http.StatusUnauthorized, "unauthorized", "Missing bearer token.")
		return
	}

	p, err := store.Get(code)
	if err != nil {
		if pe, ok := err.(*party.PartyError); ok {
			jsonError(w, pe.Status, pe.Code, pe.Message)
			return
		}
		jsonError(w, http.StatusNotFound, "no_such_party", "No party with that code.")
		return
	}

	p.Lock()
	member, err := p.Authenticate(token)
	if err != nil {
		p.Unlock()
		if pe, ok := err.(*party.PartyError); ok {
			jsonError(w, pe.Status, pe.Code, pe.Message)
			return
		}
		jsonError(w, http.StatusUnauthorized, "unauthorized", "Bad token.")
		return
	}

	memberId := member.MemberId
	p.Remove(memberId)
	membersFrame := membersFrame(p)
	p.Unlock()

	// Notify room and drop leaving member socket
	hubInst.Send(p.Code, memberId, map[string]interface{}{
		"type":   protocol.FrameBye,
		"reason": "left",
	})
	hubInst.Broadcast(p.Code, membersFrame, memberId)

	jsonResponse(w, http.StatusOK, map[string]bool{"ok": true})
}

// WebSocket Handler

func handleWebSocket(w http.ResponseWriter, r *http.Request) {
	code := r.PathValue("code")
	token := r.URL.Query().Get("token")
	if token == "" {
		http.Error(w, "missing token", http.StatusUnauthorized)
		return
	}

	p, err := store.Get(code)
	if err != nil {
		http.Error(w, "no such party", http.StatusNotFound)
		return
	}

	p.Lock()
	member, err := p.Authenticate(token)
	if err != nil {
		p.Unlock()
		http.Error(w, "bad token", http.StatusUnauthorized)
		return
	}
	p.MarkConnected(member, true)
	p.Unlock()

	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("WebSocket upgrade failed: %v", err)
		return
	}

	sc := hubInst.Attach(p.Code, member.MemberId, conn)

	// Send welcome frame
	p.Lock()
	welcome := map[string]interface{}{
		"type":     protocol.FrameWelcome,
		"you":      member.ToWire(),
		"party":    p.ToWire(),
		"serverMs": clock.NowMs(),
	}
	membersF := membersFrame(p)
	p.Unlock()

	_ = sc.WriteJSON(welcome)
	hubInst.Broadcast(p.Code, membersF, "")

	// Read loop
	defer func() {
		hubInst.Detach(p.Code, member.MemberId, sc)
		_ = sc.Close()

		p.Lock()
		p.MarkConnected(member, false)
		mFrame := membersFrame(p)
		p.Unlock()

		hubInst.Broadcast(p.Code, mFrame, "")
	}()

	for {
		var raw map[string]interface{}
		if err := conn.ReadJSON(&raw); err != nil {
			break
		}
		handleSocketFrame(p, member, sc, raw)
	}
}

func handleSocketFrame(p *party.Party, member *party.Member, sc *hub.SafeConn, frame map[string]interface{}) {
	kind, _ := frame["type"].(string)
	if kind == "" {
		return
	}

	p.Lock()
	defer p.Unlock()

	member.LastSeenMs = clock.NowMs()

	switch kind {
	case protocol.FramePing:
		_ = sc.WriteJSON(map[string]interface{}{
			"type":     protocol.FramePong,
			"clientMs": frame["clientMs"],
			"serverMs": clock.NowMs(),
		})

	case protocol.FrameSync:
		_ = sc.WriteJSON(stateFrame(p))

	case protocol.FrameSyncQueue:
		_ = sc.WriteJSON(queueFrame(p))

	case protocol.FrameReport:
		// Playhead position report from client for monitoring
		if posNum, ok := frame["positionMs"].(float64); ok && p.Playback.IsPlaying {
			drift := int64(posNum) - p.Playback.PositionAt(clock.NowMs())
			if drift > 1500 || drift < -1500 {
				log.Printf("party %s: %s drifted %dms", p.Code, member.DisplayName, drift)
			}
		}

	case protocol.FrameControl:
		if !p.SpendControlBudget(member) {
			_ = sc.WriteJSON(map[string]interface{}{
				"type":    protocol.FrameError,
				"error":   "rate_limited",
				"message": "Too many controls at once.",
			})
			return
		}

		queueBefore := p.Playback.QueueSeq
		action, _ := frame["action"].(string)
		success, errCode, errMsg := applyControl(p, member, action, frame)
		if success {
			p.Touch()
			// If queue changed, broadcast queue first
			if p.Playback.QueueSeq != queueBefore {
				hubInst.Broadcast(p.Code, queueFrame(p), "")
			}
			hubInst.Broadcast(p.Code, stateFrame(p), "")
		} else if errCode != "" {
			_ = sc.WriteJSON(map[string]interface{}{
				"type":    protocol.FrameError,
				"error":   errCode,
				"message": errMsg,
			})
		}
	}
}

func applyControl(p *party.Party, member *party.Member, action string, frame map[string]interface{}) (bool, string, string) {
	var posPtr *int64
	if pos, ok := frame["positionMs"].(float64); ok {
		pVal := int64(pos)
		posPtr = &pVal
	}

	switch action {
	case protocol.ActionPlay:
		p.Playback.Play(&member.MemberId, posPtr)
		return true, "", ""

	case protocol.ActionPause:
		p.Playback.Pause(&member.MemberId, posPtr)
		return true, "", ""

	case protocol.ActionSeek:
		if posPtr == nil {
			return false, "missing_position", "seek requires positionMs"
		}
		p.Playback.Seek(&member.MemberId, *posPtr)
		return true, "", ""

	case protocol.ActionSetTrack:
		trackMap, _ := frame["track"].(map[string]interface{})
		t := party.TrackFromWire(trackMap)
		if t == nil {
			return false, "invalid_track", "Track must include videoId"
		}
		pos := int64(0)
		if posPtr != nil {
			pos = *posPtr
		}
		isPlaying := true
		if pVal, ok := frame["isPlaying"].(bool); ok {
			isPlaying = pVal
		}
		var qIndexPtr *int
		if qIndex, ok := frame["queueIndex"].(float64); ok {
			qi := int(qIndex)
			qIndexPtr = &qi
		}
		p.Playback.SetTrack(&member.MemberId, t, pos, isPlaying, qIndexPtr, &member.DisplayName)
		return true, "", ""

	case protocol.ActionSetQueue:
		itemsRaw, _ := frame["queue"].([]interface{})
		var tracks []*party.Track
		for _, item := range itemsRaw {
			if m, ok := item.(map[string]interface{}); ok {
				if tr := party.TrackFromWire(m); tr != nil {
					tracks = append(tracks, tr)
				}
			}
		}
		qIdx := -1
		if idxNum, ok := frame["queueIndex"].(float64); ok {
			qIdx = int(idxNum)
		}
		p.Playback.SetQueue(&member.MemberId, tracks, qIdx)
		return true, "", ""

	case protocol.ActionQueueAdd:
		var tracksToAdd []*party.Track
		if singleMap, ok := frame["track"].(map[string]interface{}); ok {
			if tr := party.TrackFromWire(singleMap); tr != nil {
				tracksToAdd = append(tracksToAdd, tr)
			}
		} else if itemsRaw, ok := frame["tracks"].([]interface{}); ok {
			for _, item := range itemsRaw {
				if m, ok := item.(map[string]interface{}); ok {
					if tr := party.TrackFromWire(m); tr != nil {
						tracksToAdd = append(tracksToAdd, tr)
					}
				}
			}
		}
		playNext, _ := frame["playNext"].(bool)
		ok, reason := p.Playback.AddUpcoming(&member.MemberId, tracksToAdd, playNext)
		if !ok {
			if reason == "queue_full" {
				return false, "queue_full", fmt.Sprintf("Queue is full (maximum %d upcoming songs).", config.MaxUpcomingQueue)
			}
			return false, reason, "Could not add track to queue."
		}
		return true, "", ""

	case protocol.ActionQueueRemove:
		vid, _ := frame["videoId"].(string)
		if vid == "" {
			if idxNum, ok := frame["index"].(float64); ok {
				idx := int(idxNum)
				if idx >= 0 && idx < len(p.Playback.Queue) {
					vid = p.Playback.Queue[idx].VideoId
				}
			}
		}
		if vid == "" {
			return false, "missing_track", "queueRemove requires videoId or index"
		}
		if !p.Playback.RemoveUpcoming(&member.MemberId, vid) {
			return false, "not_found", "Track is not in the upcoming queue"
		}
		return true, "", ""

	case protocol.ActionQueueClear:
		if !p.Playback.ClearUpcoming(&member.MemberId) {
			return false, "no_upcoming", "No upcoming tracks to clear"
		}
		return true, "", ""

	case protocol.ActionQueueMove:
		fromNum, okFrom := frame["fromIndex"].(float64)
		toNum, okTo := frame["toIndex"].(float64)
		if !okFrom || !okTo {
			return false, "missing_indices", "queueMove requires fromIndex and toIndex"
		}
		var videoId string
		if v, ok := frame["videoId"].(string); ok {
			videoId = v
		}
		if !p.Playback.MoveUpcoming(&member.MemberId, int(fromNum), int(toNum), videoId) {
			return false, "invalid_move", "Invalid queue move indices"
		}
		return true, "", ""

	case protocol.ActionNext:
		if !p.Playback.Step(&member.MemberId, 1, &member.DisplayName) {
			return false, "end_of_queue", "Already at the end of the queue."
		}
		return true, "", ""

	case protocol.ActionPrevious:
		if !p.Playback.Step(&member.MemberId, -1, &member.DisplayName) {
			return false, "start_of_queue", "Already at the beginning of the queue."
		}
		return true, "", ""

	default:
		return false, "unknown_action", fmt.Sprintf("Unknown control action '%s'", action)
	}
}

// Frame builders

func stateFrame(p *party.Party) map[string]interface{} {
	return map[string]interface{}{
		"type":     protocol.FrameState,
		"playback": p.Playback.ToWire(clock.NowMs()),
		"serverMs": clock.NowMs(),
	}
}

func queueFrame(p *party.Party) map[string]interface{} {
	return map[string]interface{}{
		"type":     protocol.FrameQueue,
		"queue":    p.Playback.QueueToWire(),
		"serverMs": clock.NowMs(),
	}
}

func membersFrame(p *party.Party) map[string]interface{} {
	membersList := make([]map[string]interface{}, 0, len(p.Members))
	for _, m := range p.Members {
		membersList = append(membersList, m.ToWire())
	}
	return map[string]interface{}{
		"type":       protocol.FrameMembers,
		"members":    membersList,
		"maxMembers": config.MaxMembers,
		"serverMs":   clock.NowMs(),
	}
}

// Background Heartbeat Ticker

func startHeartbeatTicker() {
	interval := time.Duration(config.StateHeartbeatMs) * time.Millisecond
	if interval < 1*time.Second {
		interval = 1 * time.Second
	}
	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	for range ticker.C {
		now := clock.NowMs()
		for _, p := range store.All() {
			if len(hubInst.MembersOnline(p.Code)) > 0 {
				p.Lock()
				sf := stateFrame(p)
				p.Unlock()
				hubInst.Broadcast(p.Code, sf, "")
			}
		}

		changed := store.Sweep(now)
		for _, p := range changed {
			p.Lock()
			mf := membersFrame(p)
			p.Unlock()
			hubInst.Broadcast(p.Code, mf, "")
		}

		activeCodes := hubInst.ActiveCodes()
		liveParties := store.All()
		liveMap := make(map[string]bool)
		for _, lp := range liveParties {
			liveMap[lp.Code] = true
		}
		for _, code := range activeCodes {
			if !liveMap[code] {
				hubInst.DropParty(code)
			}
		}
	}
}
