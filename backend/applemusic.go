package main

import (
	"net/http"
	"time"

	"github.com/KabirSinghBhatia/BitChord/backend/applemusic"
	"github.com/KabirSinghBhatia/BitChord/backend/config"
)

func newAppleMusicTokenHandler() http.HandlerFunc {
	// Missing and invalid configuration intentionally have the same HTTP result.
	// Never log the key, token, file path, or underlying parser/signing errors.
	provider, _ := applemusic.NewTokenProvider(config.AppleMusicTeamID, config.AppleMusicKeyID, config.AppleMusicPrivateKeyFile)
	// Keep this public endpoint's allowance and bounded IP table independent of
	// party creation and presence, even when those limits are disabled.
	limiter := newIPRateLimiter(time.Minute, 30, 10000)
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "no-store")
		if !limiter.Allow(clientIP(r)) {
			w.Header().Set("Retry-After", "60")
			jsonError(w, http.StatusTooManyRequests, "rate_limited", "Too many token requests. Please try again shortly.")
			return
		}
		token, err := provider.Token()
		if err != nil {
			jsonError(w, http.StatusServiceUnavailable, "applemusic_unavailable", "Apple Music developer token is unavailable.")
			return
		}
		jsonResponse(w, http.StatusOK, token)
	}
}
