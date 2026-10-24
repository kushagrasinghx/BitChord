package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
	"time"

	"github.com/gorilla/websocket"

	"github.com/KabirSinghBhatia/BitChord/backend/clock"
	"github.com/KabirSinghBhatia/BitChord/backend/config"
	"github.com/KabirSinghBhatia/BitChord/backend/party"
	"github.com/KabirSinghBhatia/BitChord/backend/presence"
	"github.com/KabirSinghBhatia/BitChord/backend/protocol"
)

func setupTestServer() *httptest.Server {
	// Each test server starts with its own party-creation allowance.
	createLimiter = newIPRateLimiter(time.Minute, config.CreateRatePerMinute, config.RateLimitMaxEntries)
	mux := http.NewServeMux()
	mux.HandleFunc("GET /{$}", handleRoot)
	mux.HandleFunc("GET /healthz", handleHealthz)
	mux.HandleFunc("GET /api/time", handleTime)
	mux.HandleFunc("POST /api/parties", handleCreateParty)
	mux.HandleFunc("POST /api/parties/{code}/join", handleJoinParty)
	mux.HandleFunc("GET /api/parties/{code}", handleGetParty)
	mux.HandleFunc("POST /api/parties/{code}/leave", handleLeaveParty)
	mux.HandleFunc("POST /api/presence", handlePresence)
	mux.HandleFunc("GET /api/stats/live", handleLiveStats)
	mux.HandleFunc("GET /api/stats/live/badge.svg", handleLiveBadge)
	mux.HandleFunc("GET /invite/{code}", handleInviteLanding)
	mux.HandleFunc("GET /ws/parties/{code}", handleWebSocket)

	return httptest.NewServer(corsMiddleware(mux))
}

func TestRESTEndpoints(t *testing.T) {
	ts := setupTestServer()
	defer ts.Close()

	// 1. Health check
	res, err := http.Get(ts.URL + "/healthz")
	if err != nil || res.StatusCode != http.StatusOK {
		t.Fatalf("GET /healthz failed: status %v, err %v", res.StatusCode, err)
	}
	var health map[string]interface{}
	_ = json.NewDecoder(res.Body).Decode(&health)
	if health["ok"] != true {
		t.Fatalf("Expected ok: true in healthz")
	}

	// 1b. Non-existent subpaths must return 404, not root 200
	resBogusHealth, err := http.Get(ts.URL + "/1324/healthz")
	if err != nil || resBogusHealth.StatusCode != http.StatusNotFound {
		t.Fatalf("Expected 404 for /1324/healthz, got %v, err %v", resBogusHealth.StatusCode, err)
	}
	resBogusPath, err := http.Get(ts.URL + "/1324")
	if err != nil || resBogusPath.StatusCode != http.StatusNotFound {
		t.Fatalf("Expected 404 for /1324, got %v, err %v", resBogusPath.StatusCode, err)
	}

	// 2. Server time
	resTime, err := http.Get(ts.URL + "/api/time")
	if err != nil || resTime.StatusCode != http.StatusOK {
		t.Fatalf("GET /api/time failed: status %v, err %v", resTime.StatusCode, err)
	}
	var timeResp map[string]interface{}
	_ = json.NewDecoder(resTime.Body).Decode(&timeResp)
	if _, ok := timeResp["serverMs"].(float64); !ok {
		t.Fatalf("Expected numeric serverMs")
	}

	// 3. Create Party
	createBody := map[string]string{
		"userId":      "u_alice",
		"deviceId":    "d_alice",
		"displayName": "Alice",
	}
	b, _ := json.Marshal(createBody)
	createRes, err := http.Post(ts.URL+"/api/parties", "application/json", bytes.NewReader(b))
	if err != nil || createRes.StatusCode != http.StatusCreated {
		t.Fatalf("POST /api/parties failed: status %v, err %v", createRes.StatusCode, err)
	}
	var partyResp map[string]interface{}
	_ = json.NewDecoder(createRes.Body).Decode(&partyResp)

	code, _ := partyResp["code"].(string)
	token, _ := partyResp["token"].(string)
	if code == "" || token == "" {
		t.Fatalf("Expected code and token in response")
	}

	// 4. Join Party
	joinBody := map[string]string{
		"userId":      "u_bob",
		"deviceId":    "d_bob",
		"displayName": "Bob",
	}
	jb, _ := json.Marshal(joinBody)
	joinRes, err := http.Post(fmt.Sprintf("%s/api/parties/%s/join", ts.URL, code), "application/json", bytes.NewReader(jb))
	if err != nil || joinRes.StatusCode != http.StatusOK {
		t.Fatalf("POST /api/parties/%s/join failed: status %v", code, joinRes.StatusCode)
	}

	// 5. Get Party with token
	req, _ := http.NewRequest("GET", fmt.Sprintf("%s/api/parties/%s", ts.URL, code), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	getRes, err := http.DefaultClient.Do(req)
	if err != nil || getRes.StatusCode != http.StatusOK {
		t.Fatalf("GET /api/parties/%s failed with token: status %v", code, getRes.StatusCode)
	}

	// 6. Get Party without token should be 401
	unauthReq, _ := http.NewRequest("GET", fmt.Sprintf("%s/api/parties/%s", ts.URL, code), nil)
	unauthRes, err := http.DefaultClient.Do(unauthReq)
	if err != nil || unauthRes.StatusCode != http.StatusUnauthorized {
		t.Fatalf("Expected 401 Unauthorized, got %v", unauthRes.StatusCode)
	}
}

func TestWebSocketFlow(t *testing.T) {
	ts := setupTestServer()
	defer ts.Close()

	// Create Party
	createBody := map[string]string{
		"userId":      "u1",
		"deviceId":    "d1",
		"displayName": "Host",
	}
	b, _ := json.Marshal(createBody)
	res, _ := http.Post(ts.URL+"/api/parties", "application/json", bytes.NewReader(b))
	var pResp map[string]interface{}
	_ = json.NewDecoder(res.Body).Decode(&pResp)
	code := pResp["code"].(string)
	token := pResp["token"].(string)

	// Connect WebSocket
	u, _ := url.Parse(ts.URL)
	u.Scheme = "ws"
	u.Path = fmt.Sprintf("/ws/parties/%s", code)
	headers := http.Header{}
	headers.Set("Authorization", "Bearer "+token)
	ws, _, err := websocket.DefaultDialer.Dial(u.String(), headers)
	if err != nil {
		t.Fatalf("WebSocket connection failed: %v", err)
	}
	defer ws.Close()

	// 1. Should receive welcome frame
	var welcome map[string]interface{}
	_ = ws.SetReadDeadline(time.Now().Add(2 * time.Second))
	if err := ws.ReadJSON(&welcome); err != nil {
		t.Fatalf("Failed to read welcome frame: %v", err)
	}
	if welcome["type"] != protocol.FrameWelcome {
		t.Fatalf("Expected welcome frame, got: %v", welcome["type"])
	}

	// 2. May receive members frame from broadcast
	_ = ws.SetReadDeadline(time.Now().Add(500 * time.Millisecond))
	var nextFrame map[string]interface{}
	if err := ws.ReadJSON(&nextFrame); err == nil {
		if nextFrame["type"] != protocol.FrameMembers && nextFrame["type"] != protocol.FrameState {
			t.Logf("Received broadcast frame: %v", nextFrame["type"])
		}
	}

	// 3. Send Ping, expect Pong
	ping := map[string]interface{}{
		"type":     protocol.FramePing,
		"clientMs": 123456789,
	}
	if err := ws.WriteJSON(ping); err != nil {
		t.Fatalf("Failed to write ping: %v", err)
	}

	var pong map[string]interface{}
	for {
		_ = ws.SetReadDeadline(time.Now().Add(2 * time.Second))
		if err := ws.ReadJSON(&pong); err != nil {
			t.Fatalf("Failed to read pong: %v", err)
		}
		if pong["type"] == protocol.FramePong {
			break
		}
	}
	if pong["clientMs"] != float64(123456789) {
		t.Errorf("Expected pong clientMs to match, got %v", pong["clientMs"])
	}

	// 4. Send queueAdd control
	addControl := map[string]interface{}{
		"type":   protocol.FrameControl,
		"action": protocol.ActionQueueAdd,
		"track": map[string]interface{}{
			"videoId": "abc_song",
			"title":   "Test Song",
			"artist":  "Artist",
		},
	}
	if err := ws.WriteJSON(addControl); err != nil {
		t.Fatalf("Failed to send queueAdd: %v", err)
	}

	// Read until queue frame or state frame received
	var receivedQueue bool
	for i := 0; i < 5; i++ {
		_ = ws.SetReadDeadline(time.Now().Add(2 * time.Second))
		var f map[string]interface{}
		if err := ws.ReadJSON(&f); err != nil {
			break
		}
		if f["type"] == protocol.FrameQueue {
			receivedQueue = true
			qData, _ := f["queue"].(map[string]interface{})
			items, _ := qData["items"].([]interface{})
			if len(items) != 1 {
				t.Errorf("Expected 1 item in queue, got %d", len(items))
			}
			break
		}
	}
	if !receivedQueue {
		t.Fatalf("Expected queue broadcast after queueAdd")
	}
}

func TestCreateRateLimiter(t *testing.T) {
	limiter := newIPRateLimiter(time.Minute, 2, 100)
	if !limiter.Allow("203.0.113.10") || !limiter.Allow("203.0.113.10") {
		t.Fatal("expected the first two creations from an IP to be allowed")
	}
	if limiter.Allow("203.0.113.10") {
		t.Fatal("expected the third creation from an IP to be rate limited")
	}
	if !limiter.Allow("203.0.113.11") {
		t.Fatal("expected a separate IP to have its own allowance")
	}
}

func TestRateLimiterFreesExpiredEntriesWhenFull(t *testing.T) {
	limiter := newIPRateLimiter(time.Minute, 5, 1)
	if !limiter.Allow("203.0.113.20") {
		t.Fatal("expected the first IP to fit")
	}
	if limiter.Allow("203.0.113.21") {
		t.Fatal("expected a second IP to be refused while the table is full")
	}
	// Age the only entry past the window; a full table must sweep it to make room.
	entry := limiter.entries["203.0.113.20"]
	entry.started = entry.started.Add(-2 * time.Minute)
	limiter.entries["203.0.113.20"] = entry
	if !limiter.Allow("203.0.113.21") {
		t.Fatal("expected the expired entry to be swept for the new IP")
	}
}

func postPresence(t *testing.T, ts *httptest.Server, body string, origin string) *http.Response {
	t.Helper()
	req, _ := http.NewRequest(http.MethodPost, ts.URL+"/api/presence", strings.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	if origin != "" {
		req.Header.Set("Origin", origin)
	}
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("POST /api/presence failed: %v", err)
	}
	return res
}

func liveStats(t *testing.T, ts *httptest.Server) presence.Counts {
	t.Helper()
	res, err := http.Get(ts.URL + "/api/stats/live")
	if err != nil || res.StatusCode != http.StatusOK {
		t.Fatalf("GET /api/stats/live failed: status %v, err %v", res.StatusCode, err)
	}
	if got := res.Header.Get("Access-Control-Allow-Origin"); got != "*" {
		t.Fatalf("stats must be readable from any site, got Access-Control-Allow-Origin %q", got)
	}
	var counts presence.Counts
	if err := json.NewDecoder(res.Body).Decode(&counts); err != nil {
		t.Fatalf("decode stats: %v", err)
	}
	return counts
}

func TestPresence(t *testing.T) {
	presenceTracker = presence.NewTracker(6*time.Minute, 1000)
	presenceLimiter = newIPRateLimiter(time.Minute, 100, 100)
	ts := setupTestServer()
	defer ts.Close()

	const phone = "3f2b8c1e-9a4d-4e6f-8b7a-1c2d3e4f5a6b"
	const pc = "7c1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f"

	res := postPresence(t, ts, `{"id":"`+phone+`","platform":"android","open":true}`, "")
	if res.StatusCode != http.StatusOK {
		t.Fatalf("android ping: status %d", res.StatusCode)
	}
	var reply map[string]int
	_ = json.NewDecoder(res.Body).Decode(&reply)
	if reply["intervalSec"] != config.PresenceIntervalSec {
		t.Fatalf("expected intervalSec %d, got %v", config.PresenceIntervalSec, reply)
	}
	postPresence(t, ts, `{"id":"`+pc+`","platform":"pc","open":true}`, "")
	// The same install pinging again is still one install.
	postPresence(t, ts, `{"id":"`+phone+`","platform":"android","open":true}`, "")

	presenceTracker.Sweep(clock.NowMs())
	if got := liveStats(t, ts); got.Online != 2 || got.Android != 1 || got.PC != 1 {
		t.Fatalf("got %+v, want 2 online (1 android, 1 pc)", got)
	}

	postPresence(t, ts, `{"id":"`+pc+`","platform":"pc","open":false}`, "")
	presenceTracker.Sweep(clock.NowMs())
	if got := liveStats(t, ts); got.Online != 1 || got.PC != 0 {
		t.Fatalf("got %+v, want the closed pc gone", got)
	}

	// The web player counts as its own platform, from its allowed origin only.
	const web = "5a6b7c8d-1e2f-4a3b-9c4d-0e1f2a3b4c5d"
	const webOrigin = "https://web.example.test"
	prev := config.AllowedOrigins
	config.AllowedOrigins = []string{webOrigin}
	defer func() { config.AllowedOrigins = prev }()
	webPing := `{"id":"` + web + `","platform":"browser","open":true}`
	if res := postPresence(t, ts, webPing, webOrigin); res.StatusCode != http.StatusOK {
		t.Fatalf("browser ping: status %d", res.StatusCode)
	}
	presenceTracker.Sweep(clock.NowMs())
	if got := liveStats(t, ts); got.Online != 2 || got.Android != 1 || got.Browser != 1 {
		t.Fatalf("got %+v, want 2 online (1 android, 1 browser)", got)
	}
	for name, origin := range map[string]string{"no origin": "", "other origin": "https://example.com"} {
		if res := postPresence(t, ts, webPing, origin); res.StatusCode != http.StatusForbidden {
			t.Errorf("browser ping with %s: status %d, want 403", name, res.StatusCode)
		}
	}
	if res := postPresence(t, ts, `{"id":"`+pc+`","platform":"pc","open":true}`, webOrigin); res.StatusCode != http.StatusForbidden {
		t.Errorf("native platform from the web origin: status %d, want 403", res.StatusCode)
	}
	postPresence(t, ts, `{"id":"`+web+`","platform":"browser","open":false}`, webOrigin)
	presenceTracker.Sweep(clock.NowMs())
	if got := liveStats(t, ts); got.Online != 1 || got.Browser != 0 {
		t.Fatalf("got %+v, want the closed browser gone", got)
	}

	rejected := []struct {
		name   string
		body   string
		origin string
		status int
	}{
		{"unknown platform", `{"id":"` + pc + `","platform":"ios","open":true}`, "", http.StatusUnprocessableEntity},
		{"desktop is not a platform name", `{"id":"` + pc + `","platform":"desktop","open":true}`, "", http.StatusUnprocessableEntity},
		{"bad id", `{"id":"abc","platform":"pc","open":true}`, "", http.StatusUnprocessableEntity},
		{"missing open", `{"id":"` + pc + `","platform":"pc"}`, "", http.StatusUnprocessableEntity},
		{"from a browser", `{"id":"` + pc + `","platform":"pc","open":true}`, "https://example.com", http.StatusForbidden},
	}
	for _, c := range rejected {
		if res := postPresence(t, ts, c.body, c.origin); res.StatusCode != c.status {
			t.Errorf("%s: status %d, want %d", c.name, res.StatusCode, c.status)
		}
	}
	presenceTracker.Sweep(clock.NowMs())
	if got := liveStats(t, ts); got.Online != 1 {
		t.Fatalf("rejected pings changed the count: %+v", got)
	}
}

func TestPresenceRateLimit(t *testing.T) {
	presenceTracker = presence.NewTracker(6*time.Minute, 1000)
	presenceLimiter = newIPRateLimiter(time.Minute, 2, 100)
	ts := setupTestServer()
	defer ts.Close()

	body := `{"id":"3f2b8c1e-9a4d-4e6f-8b7a-1c2d3e4f5a6b","platform":"android","open":true}`
	postPresence(t, ts, body, "")
	postPresence(t, ts, body, "")
	if res := postPresence(t, ts, body, ""); res.StatusCode != http.StatusTooManyRequests {
		t.Fatalf("third ping in a minute: status %d, want 429", res.StatusCode)
	}
}

func TestLiveBadge(t *testing.T) {
	presenceTracker = presence.NewTracker(6*time.Minute, 1000)
	presenceLimiter = newIPRateLimiter(time.Minute, 100, 100)
	ts := setupTestServer()
	defer ts.Close()

	postPresence(t, ts, `{"id":"3f2b8c1e-9a4d-4e6f-8b7a-1c2d3e4f5a6b","platform":"android","open":true}`, "")
	presenceTracker.Sweep(clock.NowMs())

	res, err := http.Get(ts.URL + "/api/stats/live/badge.svg")
	if err != nil || res.StatusCode != http.StatusOK {
		t.Fatalf("GET badge failed: status %v, err %v", res.StatusCode, err)
	}
	// GitHub's camo proxy only refetches when the origin says not to cache.
	if got := res.Header.Get("Cache-Control"); !strings.Contains(got, "no-cache") {
		t.Fatalf("badge must not be cached, got Cache-Control %q", got)
	}
	var body bytes.Buffer
	_, _ = body.ReadFrom(res.Body)
	// shields.io's own render of "LISTENING NOW: 1", so the badge looks unchanged.
	want := `<svg xmlns="http://www.w3.org/2000/svg" width="156.5" height="28" role="img" aria-label="LISTENING NOW: 1"><title>LISTENING NOW: 1</title><g shape-rendering="crispEdges"><rect width="124.25" height="28" fill="#0d1117"/><rect x="124.25" width="32.25" height="28" fill="#fb4f67"/></g><g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" text-rendering="geometricPrecision" font-size="100"><text transform="scale(.1)" x="621.25" y="175" textLength="1002.5">LISTENING NOW</text><text transform="scale(.1)" x="1403.75" y="175" textLength="82.5" font-weight="bold">1</text></g></svg>`
	if body.String() != want {
		t.Fatalf("badge svg differs from shields:\n got %s\nwant %s", body.String(), want)
	}
}

func TestRequestOrigin(t *testing.T) {
	r := httptest.NewRequest("GET", "http://example.com/invite/ABC123", nil)
	r.Header.Set("X-Forwarded-Proto", "https")

	// Without JAM_TRUST_PROXY the forwarded proto is ignored.
	if got := requestOrigin(r); got != "http://example.com" {
		t.Errorf("Expected http://example.com, got %s", got)
	}

	config.TrustProxy = true
	t.Cleanup(func() { config.TrustProxy = false })
	if got := requestOrigin(r); got != "https://example.com" {
		t.Errorf("Expected https://example.com, got %s", got)
	}

	// An explicit public origin wins over request headers.
	config.PublicOrigin = "https://party.example.com"
	t.Cleanup(func() { config.PublicOrigin = "" })
	if got := requestOrigin(r); got != "https://party.example.com" {
		t.Errorf("Expected https://party.example.com, got %s", got)
	}

	// When unset it falls back to inference.
	config.PublicOrigin = ""
	if got := requestOrigin(r); got != "https://example.com" {
		t.Errorf("Expected fallback to https://example.com, got %s", got)
	}
}

func TestInviteDeepLinkUsesPublicOrigin(t *testing.T) {
	ts := setupTestServer()
	defer ts.Close()

	p, err := store.Create()
	if err != nil {
		t.Fatalf("Party creation failed: %v", err)
	}

	config.PublicOrigin = "https://party.example.com"
	t.Cleanup(func() { config.PublicOrigin = "" })

	res, err := http.Get(ts.URL + "/invite/" + p.Code)
	if err != nil {
		t.Fatalf("GET /invite/%s failed: %v", p.Code, err)
	}
	buf := new(bytes.Buffer)
	_, _ = buf.ReadFrom(res.Body)
	content := buf.String()

	if !strings.Contains(content, "server=https%3A%2F%2Fparty.example.com") {
		t.Errorf("Expected deep link to carry the public origin, got: %s", content)
	}
	if strings.Contains(content, "server=http%3A") {
		t.Errorf("Expected no http server URL in deep link, got: %s", content)
	}
}

func TestInviteLanding(t *testing.T) {
	ts := setupTestServer()
	defer ts.Close()

	// 1. Non-existent party
	res, err := http.Get(ts.URL + "/invite/ZZZZZZ")
	if err != nil {
		t.Fatalf("GET /invite/ZZZZZZ failed: %v", err)
	}
	if res.StatusCode != http.StatusNotFound {
		t.Errorf("Expected 404 for non-existent party, got %d", res.StatusCode)
	}

	// 2. Create an active party directly in store
	p, err := store.Create()
	if err != nil {
		t.Fatalf("Party creation failed: %v", err)
	}
	code := p.Code

	// 3. Active party landing page
	resActive, err := http.Get(ts.URL + "/invite/" + code)
	if err != nil {
		t.Fatalf("GET /invite/%s failed: %v", code, err)
	}
	if resActive.StatusCode != http.StatusOK {
		t.Errorf("Expected 200 for active party invite, got %d", resActive.StatusCode)
	}
	buf := new(bytes.Buffer)
	_, _ = buf.ReadFrom(resActive.Body)
	content := buf.String()

	expectedDeepLinkPrefix := "bitchord://party/" + code
	if !strings.Contains(content, expectedDeepLinkPrefix) {
		t.Errorf("Expected HTML content to contain deep link %s", expectedDeepLinkPrefix)
	}
	if !strings.Contains(content, code) {
		t.Errorf("Expected HTML content to contain party code %s", code)
	}
}

// A browser offers the token as a subprotocol (it cannot set Authorization on
// a WebSocket). The server answers with the protocol name alone, never the token.
func TestWebSocketTokenAsSubprotocol(t *testing.T) {
	ts := setupTestServer()
	defer ts.Close()

	b, _ := json.Marshal(map[string]string{"userId": "u1", "deviceId": "d1", "displayName": "Host"})
	res, _ := http.Post(ts.URL+"/api/parties", "application/json", bytes.NewReader(b))
	var created map[string]interface{}
	_ = json.NewDecoder(res.Body).Decode(&created)
	code := created["code"].(string)
	token := created["token"].(string)

	u, _ := url.Parse(ts.URL)
	u.Scheme = "ws"
	u.Path = fmt.Sprintf("/ws/parties/%s", code)

	dialer := websocket.Dialer{Subprotocols: []string{socketProtocol, socketTokenPrefix + token}}
	ws, resp, err := dialer.Dial(u.String(), nil)
	if err != nil {
		t.Fatalf("WebSocket with a subprotocol token failed: %v", err)
	}
	defer ws.Close()
	if got := resp.Header.Get("Sec-WebSocket-Protocol"); got != socketProtocol {
		t.Fatalf("expected the server to answer %q, got %q", socketProtocol, got)
	}
	var welcome map[string]interface{}
	_ = ws.SetReadDeadline(time.Now().Add(2 * time.Second))
	if err := ws.ReadJSON(&welcome); err != nil || welcome["type"] != protocol.FrameWelcome {
		t.Fatalf("expected a welcome frame, got %v (%v)", welcome["type"], err)
	}

	bad := websocket.Dialer{Subprotocols: []string{socketProtocol, socketTokenPrefix + "nottherighttoken"}}
	if _, resp, err := bad.Dial(u.String(), nil); err == nil || resp == nil || resp.StatusCode != http.StatusUnauthorized {
		t.Fatalf("expected 401 for a wrong subprotocol token, got %v", err)
	}
}

func joinTestParty(t *testing.T, ts *httptest.Server, code, who string) (string, string) {
	t.Helper()
	b, _ := json.Marshal(map[string]string{"userId": "u_" + who, "deviceId": "d_" + who, "displayName": who})
	path := ts.URL + "/api/parties"
	if code != "" {
		path = fmt.Sprintf("%s/api/parties/%s/join", ts.URL, code)
	}
	res, err := http.Post(path, "application/json", bytes.NewReader(b))
	if err != nil {
		t.Fatalf("join %s: %v", who, err)
	}
	defer res.Body.Close()
	var body map[string]interface{}
	_ = json.NewDecoder(res.Body).Decode(&body)
	c, _ := body["code"].(string)
	tok, _ := body["token"].(string)
	if c == "" || tok == "" {
		t.Fatalf("join %s: status %d", who, res.StatusCode)
	}
	return c, tok
}

func dialTestParty(t *testing.T, ts *httptest.Server, code, token string) *websocket.Conn {
	t.Helper()
	u, _ := url.Parse(ts.URL)
	u.Scheme = "ws"
	u.Path = "/ws/parties/" + code
	h := http.Header{}
	h.Set("Authorization", "Bearer "+token)
	ws, _, err := websocket.DefaultDialer.Dial(u.String(), h)
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	return ws
}

func TestLeftMemberSocketCannotAct(t *testing.T) {
	ts := setupTestServer()
	defer ts.Close()
	code, hostTok := joinTestParty(t, ts, "", "host")
	_, _ = joinTestParty(t, ts, code, "guest")
	ws := dialTestParty(t, ts, code, hostTok)
	defer ws.Close()

	req, _ := http.NewRequest("POST", fmt.Sprintf("%s/api/parties/%s/leave", ts.URL, code), nil)
	req.Header.Set("Authorization", "Bearer "+hostTok)
	if res, err := http.DefaultClient.Do(req); err != nil || res.StatusCode != http.StatusOK {
		t.Fatalf("leave failed: %v", err)
	}
	// Leaving closes the socket, so reads run out without hanging.
	for {
		_ = ws.SetReadDeadline(time.Now().Add(2 * time.Second))
		var f map[string]interface{}
		if err := ws.ReadJSON(&f); err != nil {
			if ne, ok := err.(net.Error); ok && ne.Timeout() {
				t.Fatalf("socket stayed open after leave")
			}
			break
		}
	}

	// Even a socket that outlived its member must be refused by the guard.
	p, _ := store.Get(code)
	ghost := &party.Member{MemberId: "ghost", IsHost: true}
	if handleSocketFrame(p, ghost, nil, map[string]interface{}{"type": protocol.FrameControl, "action": protocol.ActionPause}) {
		t.Fatalf("a removed member's frame must end the socket")
	}
}

// pipeListener serves http over in-memory pipes, which block on every write
// until the other side reads: a phone that has stopped reading, on demand.
type pipeListener chan net.Conn

func (l pipeListener) Accept() (net.Conn, error) {
	c, ok := <-l
	if !ok {
		return nil, net.ErrClosed
	}
	return c, nil
}
func (l pipeListener) Close() error   { return nil }
func (l pipeListener) Addr() net.Addr { return &net.TCPAddr{} }

func TestStalledListenerDoesNotHoldThePartyLock(t *testing.T) {
	ts := setupTestServer()
	defer ts.Close()
	code, hostTok := joinTestParty(t, ts, "", "host")
	_, stalledTok := joinTestParty(t, ts, code, "stalled")

	controller := dialTestParty(t, ts, code, hostTok)
	defer controller.Close()
	go func() {
		for {
			var f map[string]interface{}
			if controller.ReadJSON(&f) != nil {
				return
			}
		}
	}()

	pipes := make(pipeListener, 1)
	go (&http.Server{Handler: ts.Config.Handler}).Serve(pipes)
	dialer := websocket.Dialer{NetDial: func(string, string) (net.Conn, error) {
		server, client := net.Pipe()
		pipes <- server
		return client, nil
	}}
	h := http.Header{}
	h.Set("Authorization", "Bearer "+stalledTok)
	stalled, _, err := dialer.Dial("ws://pipe/ws/parties/"+code, h)
	if err != nil {
		t.Fatalf("pipe dial: %v", err)
	}
	defer stalled.Close()
	// Welcome and its own members frame; after that it never reads again.
	for i := 0; i < 2; i++ {
		var f map[string]interface{}
		if err := stalled.ReadJSON(&f); err != nil {
			t.Fatalf("stalled read: %v", err)
		}
	}

	if err := controller.WriteJSON(map[string]interface{}{"type": protocol.FrameControl, "action": protocol.ActionPause}); err != nil {
		t.Fatalf("send pause: %v", err)
	}
	time.Sleep(200 * time.Millisecond) // the broadcast is now stuck on the pipe

	start := time.Now()
	req, _ := http.NewRequest("GET", fmt.Sprintf("%s/api/parties/%s", ts.URL, code), nil)
	req.Header.Set("Authorization", "Bearer "+hostTok)
	res, err := http.DefaultClient.Do(req)
	if err != nil || res.StatusCode != http.StatusOK {
		t.Fatalf("GET party: %v", err)
	}
	if waited := time.Since(start); waited > time.Second {
		t.Fatalf("GET waited %v on the party lock behind a stalled socket", waited)
	}
}
