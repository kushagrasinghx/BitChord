package syncapi

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strings"
	"time"
)

const maxRequestBytes = 8 << 20

// NewHandler exposes the v1 account-sync API. Its caller is responsible for
// binding the returned handler to a TLS-capable deployment/reverse proxy.
type HandlerOptions struct {
	TrustedProxy         bool
	AllowProfileCreation bool
}

func NewHandler(store *Store, options ...HandlerOptions) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", handleHealth)
	var opts HandlerOptions
	if len(options) > 0 {
		opts = options[0]
	}
	api := &handler{
		store:                store,
		trustProxy:           opts.TrustedProxy,
		allowProfileCreation: opts.AllowProfileCreation,
		createLimiter:        newIPRateLimiter(createProfileLimit, time.Minute),
		claimLimiter:         newIPRateLimiter(pairingClaimLimit, time.Minute),
		pairLimiter:          newIPRateLimiter(pairingCreateLimit, time.Minute),
	}
	mux.HandleFunc("POST /v1/profiles", api.createProfile)
	mux.HandleFunc("POST /v1/profiles/{profileID}/pairings", api.createPairing)
	mux.HandleFunc("POST /v1/pairings/claim", api.claimPairing)
	mux.HandleFunc("GET /v1/profiles/{profileID}/devices", api.listDevices)
	mux.HandleFunc("DELETE /v1/profiles/{profileID}/devices/{deviceID}", api.revokeDevice)
	mux.HandleFunc("DELETE /v1/profiles/{profileID}", api.deleteProfile)
	mux.HandleFunc("GET /v1/profiles/{profileID}/snapshot", api.getSnapshot)
	mux.HandleFunc("PUT /v1/profiles/{profileID}/snapshot", api.putSnapshot)
	return mux
}

type handler struct {
	store                *Store
	trustProxy           bool
	allowProfileCreation bool
	createLimiter        *ipRateLimiter
	claimLimiter         *ipRateLimiter
	pairLimiter          *ipRateLimiter
}

func handleHealth(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

func (h *handler) createProfile(w http.ResponseWriter, r *http.Request) {
	if !h.allowProfileCreation {
		writeError(w, http.StatusForbidden, "profile_creation_disabled", "Sync profile creation is disabled by the operator.")
		return
	}
	var request struct{}
	if !decodeRequestJSON(w, r, &request) {
		return
	}
	if allowed, retryAfter := h.createLimiter.allow(clientIP(r, h.trustProxy), time.Now()); !allowed {
		rateLimitResponse(w, retryAfter)
		return
	}
	profileID, deviceID, token, err := h.store.createProfile()
	if errors.Is(err, errProfileLimit) {
		writeError(w, http.StatusServiceUnavailable, "profile_capacity", "Sync service has reached its profile capacity.")
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, "store_error", "Could not create sync profile.")
		return
	}
	writeJSON(w, http.StatusCreated, map[string]any{
		"profileId":   profileID,
		"deviceId":    deviceID,
		"deviceToken": token,
		"revision":    0,
	})
}

func (h *handler) createPairing(w http.ResponseWriter, r *http.Request) {
	profileID := r.PathValue("profileID")
	token := bearerToken(r.Header.Get("Authorization"))
	if _, ok := h.authenticate(w, r, profileID); !ok {
		return
	}
	var request struct{}
	if !decodeRequestJSON(w, r, &request) {
		return
	}
	if allowed, retryAfter := h.pairLimiter.allow(clientIP(r, h.trustProxy), time.Now()); !allowed {
		rateLimitResponse(w, retryAfter)
		return
	}
	pairingToken, expiresAt, err := h.store.createPairing(profileID, token)
	if errors.Is(err, errNotFound) {
		writeError(w, http.StatusNotFound, "profile_not_found", "Sync profile was not found.")
		return
	}
	if errors.Is(err, errUnauthorized) {
		writeError(w, http.StatusUnauthorized, "unauthorized", "Device token is not valid for this profile.")
		return
	}
	if errors.Is(err, errPairingLimit) {
		w.Header().Set("Retry-After", "300")
		writeError(w, http.StatusTooManyRequests, "pairing_limit", "Too many active pairing codes; wait for one to expire.")
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, "store_error", "Could not create device pairing.")
		return
	}
	writeJSON(w, http.StatusCreated, map[string]any{"pairingToken": pairingToken, "expiresAt": expiresAt})
}

func (h *handler) claimPairing(w http.ResponseWriter, r *http.Request) {
	var request struct {
		ProfileID    string `json:"profileId"`
		PairingToken string `json:"pairingToken"`
	}
	if !decodeRequestJSON(w, r, &request) {
		return
	}
	if request.ProfileID == "" || request.PairingToken == "" {
		writeError(w, http.StatusBadRequest, "invalid_pairing", "Profile ID and pairing token are required.")
		return
	}
	if allowed, retryAfter := h.claimLimiter.allow(clientIP(r, h.trustProxy), time.Now()); !allowed {
		rateLimitResponse(w, retryAfter)
		return
	}
	deviceID, token, err := h.store.claimPairing(request.ProfileID, request.PairingToken)
	switch {
	case errors.Is(err, errNotFound):
		writeError(w, http.StatusNotFound, "profile_not_found", "Sync profile was not found.")
	case errors.Is(err, errUnauthorized):
		writeError(w, http.StatusUnauthorized, "invalid_pairing", "Pairing token is invalid, expired, or already used.")
	case errors.Is(err, errDeviceLimit):
		writeError(w, http.StatusConflict, "device_limit_reached", "Sync profile already has the maximum number of devices.")
	case err != nil:
		writeError(w, http.StatusInternalServerError, "store_error", "Could not pair device.")
	default:
		writeJSON(w, http.StatusCreated, map[string]string{
			"profileId":   request.ProfileID,
			"deviceId":    deviceID,
			"deviceToken": token,
		})
	}
}

func (h *handler) listDevices(w http.ResponseWriter, r *http.Request) {
	profileID := r.PathValue("profileID")
	token := bearerToken(r.Header.Get("Authorization"))
	if _, ok := h.authenticate(w, r, profileID); !ok {
		return
	}
	devices, err := h.store.listDevices(profileID, token)
	switch {
	case errors.Is(err, errNotFound):
		writeError(w, http.StatusNotFound, "profile_not_found", "Sync profile was not found.")
	case errors.Is(err, errUnauthorized):
		writeError(w, http.StatusUnauthorized, "unauthorized", "Device token is not valid for this profile.")
	case err != nil:
		writeError(w, http.StatusInternalServerError, "store_error", "Could not list sync devices.")
	default:
		writeJSON(w, http.StatusOK, map[string]any{"devices": devices})
	}
}

func (h *handler) deleteProfile(w http.ResponseWriter, r *http.Request) {
	profileID := r.PathValue("profileID")
	token := bearerToken(r.Header.Get("Authorization"))
	if _, ok := h.authenticate(w, r, profileID); !ok {
		return
	}
	err := h.store.deleteProfile(profileID, token)
	switch {
	case errors.Is(err, errNotFound):
		writeError(w, http.StatusNotFound, "profile_not_found", "Sync profile was not found.")
	case errors.Is(err, errUnauthorized):
		writeError(w, http.StatusUnauthorized, "unauthorized", "Device token is not valid for this profile.")
	case err != nil:
		writeError(w, http.StatusInternalServerError, "store_error", "Could not delete sync profile.")
	default:
		w.WriteHeader(http.StatusNoContent)
	}
}

func (h *handler) revokeDevice(w http.ResponseWriter, r *http.Request) {
	profileID := r.PathValue("profileID")
	requesterID, ok := h.authenticate(w, r, profileID)
	if !ok {
		return
	}
	err := h.store.revokeDevice(profileID, requesterID, r.PathValue("deviceID"))
	switch {
	case errors.Is(err, errCannotRevokeSelf):
		writeError(w, http.StatusBadRequest, "cannot_revoke_self", "A device cannot revoke itself.")
	case errors.Is(err, errNotFound):
		writeError(w, http.StatusNotFound, "device_not_found", "Device was not found.")
	case errors.Is(err, errUnauthorized):
		writeError(w, http.StatusUnauthorized, "unauthorized", "Device token is not valid for this profile.")
	case err != nil:
		writeError(w, http.StatusInternalServerError, "store_error", "Could not revoke device.")
	default:
		w.WriteHeader(http.StatusNoContent)
	}
}

func (h *handler) getSnapshot(w http.ResponseWriter, r *http.Request) {
	profileID := r.PathValue("profileID")
	token := bearerToken(r.Header.Get("Authorization"))
	if _, ok := h.authenticate(w, r, profileID); !ok {
		return
	}
	revision, value, err := h.store.getSnapshot(profileID, token)
	if errors.Is(err, errNotFound) {
		writeError(w, http.StatusNotFound, "profile_not_found", "Sync profile was not found.")
		return
	}
	if errors.Is(err, errUnauthorized) {
		writeError(w, http.StatusUnauthorized, "unauthorized", "Device token is not valid for this profile.")
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, "store_error", "Could not read sync snapshot.")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"revision": revision, "snapshot": value})
}

func (h *handler) putSnapshot(w http.ResponseWriter, r *http.Request) {
	profileID := r.PathValue("profileID")
	token := bearerToken(r.Header.Get("Authorization"))
	if _, ok := h.authenticate(w, r, profileID); !ok {
		return
	}
	var request struct {
		ExpectedRevision *int64 `json:"expectedRevision"`
		SchemaVersion    int    `json:"schemaVersion"`
		Ciphertext       string `json:"ciphertext"`
	}
	if !decodeRequestJSON(w, r, &request) {
		return
	}
	if request.ExpectedRevision == nil || *request.ExpectedRevision < 0 || request.SchemaVersion != 1 || request.Ciphertext == "" {
		writeError(w, http.StatusBadRequest, "invalid_snapshot", "Snapshot envelope is invalid.")
		return
	}
	revision, err := h.store.putSnapshot(profileID, token, *request.ExpectedRevision, snapshot{
		SchemaVersion: request.SchemaVersion,
		Ciphertext:    request.Ciphertext,
	})
	switch {
	case errors.Is(err, errNotFound):
		writeError(w, http.StatusNotFound, "profile_not_found", "Sync profile was not found.")
	case errors.Is(err, errUnauthorized):
		writeError(w, http.StatusUnauthorized, "unauthorized", "Device token is not valid for this profile.")
	case errors.Is(err, errRevisionStale):
		writeJSON(w, http.StatusConflict, map[string]any{"error": "revision_conflict", "revision": revision})
	case errors.Is(err, errInvalidSnapshot):
		writeError(w, http.StatusBadRequest, "invalid_snapshot", "Snapshot envelope is invalid.")
	case err != nil:
		writeError(w, http.StatusInternalServerError, "store_error", "Could not persist sync snapshot.")
	default:
		writeJSON(w, http.StatusOK, map[string]int64{"revision": revision})
	}
}

func (h *handler) authenticate(w http.ResponseWriter, r *http.Request, profileID string) (string, bool) {
	token := bearerToken(r.Header.Get("Authorization"))
	if token == "" {
		writeError(w, http.StatusUnauthorized, "unauthorized", "A device bearer token is required.")
		return "", false
	}
	deviceID, err := h.store.authorizedDevice(profileID, token)
	switch {
	case errors.Is(err, errNotFound):
		writeError(w, http.StatusNotFound, "profile_not_found", "Sync profile was not found.")
		return "", false
	case errors.Is(err, errUnauthorized):
		writeError(w, http.StatusUnauthorized, "unauthorized", "Device token is not valid for this profile.")
		return "", false
	case err != nil:
		writeError(w, http.StatusInternalServerError, "store_error", "Could not authorize device.")
		return "", false
	default:
		return deviceID, true
	}
}

func bearerToken(value string) string {
	parts := strings.SplitN(strings.TrimSpace(value), " ", 2)
	if len(parts) != 2 || !strings.EqualFold(parts[0], "Bearer") {
		return ""
	}
	return strings.TrimSpace(parts[1])
}

func decodeRequestJSON(w http.ResponseWriter, r *http.Request, target any) bool {
	if r.Body == nil {
		writeError(w, http.StatusBadRequest, "invalid_json", "A JSON body is required.")
		return false
	}
	r.Body = http.MaxBytesReader(w, r.Body, maxRequestBytes)
	defer r.Body.Close()
	decoder := json.NewDecoder(r.Body)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		var maxBytesErr *http.MaxBytesError
		if errors.As(err, &maxBytesErr) {
			writeError(w, http.StatusRequestEntityTooLarge, "request_too_large", "Request body exceeds the 8 MiB limit.")
		} else {
			writeError(w, http.StatusBadRequest, "invalid_json", "Malformed or unsupported JSON body.")
		}
		return false
	}
	if err := decoder.Decode(&struct{}{}); err != io.EOF {
		writeError(w, http.StatusBadRequest, "invalid_json", "Request body must contain one JSON value.")
		return false
	}
	return true
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

func writeError(w http.ResponseWriter, status int, code, message string) {
	writeJSON(w, status, map[string]string{"error": code, "message": message})
}
