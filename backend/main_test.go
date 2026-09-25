package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
	"time"

	"github.com/gorilla/websocket"

	"github.com/KabirSinghBhatia/BitChord/backend/config"
	"github.com/KabirSinghBhatia/BitChord/backend/protocol"
)

func setupTestServer() *httptest.Server {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /{$}", handleRoot)
	mux.HandleFunc("GET /healthz", handleHealthz)
	mux.HandleFunc("GET /api/time", handleTime)
	mux.HandleFunc("POST /api/parties", handleCreateParty)
	mux.HandleFunc("POST /api/parties/{code}/join", handleJoinParty)
	mux.HandleFunc("GET /api/parties/{code}", handleGetParty)
	mux.HandleFunc("POST /api/parties/{code}/leave", handleLeaveParty)
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

func TestRequestOrigin(t *testing.T) {
	r := httptest.NewRequest("GET", "http://example.com/invite/ABC123", nil)
	r.Header.Set("X-Forwarded-Proto", "https")

	// Without JAM_TRUST_PROXY the forwarded proto is ignored.
	if got := requestOrigin(r); got != "http://example.com" {
		t.Errorf("Expected http://example.com, got %s", got)
	}

	config.TrustProxy = true
	defer func() { config.TrustProxy = false }()
	if got := requestOrigin(r); got != "https://example.com" {
		t.Errorf("Expected https://example.com, got %s", got)
	}

	// An explicit public origin wins over request headers.
	config.PublicOrigin = "https://party.example.com"
	defer func() { config.PublicOrigin = "" }()
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
	defer func() { config.PublicOrigin = "" }()

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
