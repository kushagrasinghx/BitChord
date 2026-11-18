# BitChord cross-device sync protocol (draft v1)

Status: **The API backend is committed. The shared Kotlin snapshot model, typed allowlist, AES-GCM codec/vault, Ktor client, and CAS coordinator are implemented; `:shared:jvmTest` passes 170 tests. Android now has a Keystore-backed vault, conditional settings bridge, opt-in profile/pairing manager, settings page, device listing/revocation, recovery reset, and WorkManager sync; the full `:app:testDevDebugUnitTest` run passes 1311 tests with 1 skipped. The Android UI/Keystore has not been exercised on a device, no public service is deployed, desktop integration remains blocked on Rashid’s repo URL/branch, and the operator must enable profile registration explicitly.**

## Security model

- The API does not authenticate with Google/YouTube and must never receive a Google password, cookie, or OAuth credential. New profile creation is operator-disabled by default; an operator must explicitly enable `SYNC_ALLOW_PROFILE_CREATION=true` after deciding registration and retention policy.
- The first device creates a random 256-bit profile key locally. `shared/src/jvmSharedMain/kotlin/com/music/bitchord/sync/SyncSnapshotCrypto.kt` serializes a versioned snapshot and encrypts it with AES-256-GCM (`AES/GCM/NoPadding`), a fresh 96-bit `SecureRandom` nonce, and a 128-bit tag. The Base64 ciphertext payload is `formatVersion:u8(1) || schemaVersion:u32be || nonce:12 bytes || ciphertext+tag`; authenticate AAD `"bitchord-sync-snapshot" || 0x00 || protocolVersion:u8(1) || profileIdUtf8Length:u32be || profileIdUtf8 || schemaVersion:u32be`. The codec caps plaintext at 5 MiB. It receives the 32-byte profile key as an argument and does not persist or log it. Keep using a vetted platform crypto provider; do not implement primitives manually.
- `shared/src/jvmSharedMain/kotlin/com/music/bitchord/sync/EncryptedSyncVault.kt` seals the profile key, device token, server URL, revision and settings baseline with AES-GCM. Android's `app/src/main/java/com/music/bitchord/data/settings/AndroidSyncVault.kt` wraps that blob under a per-install Android Keystore key in `bitchord_sync_vault.xml`; the file is excluded from Android cloud backup and device transfer. Keystore failure is fail-closed—there is no plaintext fallback. A confirmed recovery action can clear an unreadable local vault and cancel scheduled work, but cannot revoke the orphaned server-side device entry; another linked device must revoke it when possible. JVM vault tests and Android compilation pass, but actual Android Keystore behavior has not been device-tested. A desktop OS credential-store adapter is still required. The API receives only Base64 ciphertext and revision metadata.
- Device pairing: an authenticated device requests an API pairing token (5-minute expiry, one use), then shows a QR and selectable invitation code containing the HTTPS sync base URL, profile ID, pairing token, and profile key. The invitation is held in transient, non-saveable UI state and can be dismissed. The joining device accepts the pasted code locally; there is no in-app camera scanner yet. It sends only `profileId` and `pairingToken` to `/v1/pairings/claim`, then stores the received device token and profile key securely. If the claim succeeds but secure local storage fails, the joiner is told to revoke that device from another linked device and request a fresh invitation. Never include the profile key in an API request, URL, HTTP header, log, crash report, or analytics event.
- The server stores SHA-256 hashes of random device/pairing tokens, encrypted snapshot ciphertext, and revision metadata. Use HTTPS off localhost. Do not trust `X-Forwarded-For` unless the service is behind a proxy that overwrites it.
- Revoking a device blocks future server requests, but cannot erase keys/ciphertext already held by an offline device. A later key-rotation/recovery workflow must be designed before promising revocation of previously copied data.
- V1 excludes OAuth/session tokens, API keys and media-source credentials by default. Downloaded files, caches, local paths, OS permissions, audio outputs, DAC settings and other hardware-specific preferences remain device-local. Allowlist syncable fields; never serialize all `SharedPreferences` blindly.

## HTTP API

Base URL example: `https://sync.example.invalid/sync` when a reverse proxy strips `/sync` before forwarding to the API listener.

- `GET /healthz` → `200 {"status":"ok"}`.
- `POST /v1/profiles` with `{}` → `201 {profileId, deviceId, deviceToken, revision: 0}`. `deviceToken` is returned once.
- `POST /v1/profiles/{profileId}/pairings` with `{}` and bearer auth → `201 {pairingToken, expiresAt}`. Token is one-use, expires in five minutes; at most five active pairings and ten devices per profile.
- `POST /v1/pairings/claim` with `{profileId, pairingToken}` → `201 {profileId, deviceId, deviceToken}`. No bearer token required; pairing token is the one-use capability.
- `GET /v1/profiles/{profileId}/devices` with bearer auth → `{devices:[{deviceId, createdAt, current}]}`.
- `DELETE /v1/profiles/{profileId}/devices/{deviceId}` with bearer auth → `204`; self-revocation is rejected.
- `DELETE /v1/profiles/{profileId}` with bearer auth → `204`; removes server-side profile data.
- `GET /v1/profiles/{profileId}/snapshot` with bearer auth → `{revision, snapshot}`; a new profile has revision `0` and `snapshot:null`.
- `PUT /v1/profiles/{profileId}/snapshot` with bearer auth and body `{"expectedRevision":0,"schemaVersion":1,"ciphertext":"<Base64>"}` → `{revision:1}`. `expectedRevision` is mandatory. A stale revision returns `409 {error:"revision_conflict",revision:<current>}`; clients fetch, decrypt, merge, and retry. Server accepts Base64 but cannot tell whether plaintext was encrypted; only clients can enforce the encryption requirement.

All bearer tokens go in `Authorization: Bearer …`, never in URLs. API bodies are capped at 8 MiB. The prototype state file is capped at 64 MiB and supports a single process/instance only; production hosting must use durable storage and TLS. The current Render free blueprint is not a durable sync deployment.

## Client snapshot and merge rules

Implemented in `shared/src/commonMain/kotlin/com/music/bitchord/sync/SyncSnapshot.kt` and `SyncPreferencePolicy.kt`. Android production adapters are in `app/src/main/java/com/music/bitchord/data/settings/SyncSettingsBridge.kt` and `AppSettingsSyncPort`; sync orchestration is in `AndroidSyncManager.kt` and `SyncWork.kt`.

- Schema v1 stores `fields: Map<String, SyncField>` where each field has a typed value and `FieldClock(counter, deviceId)`. A null value is a tombstone.
- `mergeSnapshots` picks the greater `(counter, deviceId)` lexicographically, preserving unknown fields. Different values with an identical clock are rejected as corruption rather than chosen silently.
- `recordLocalChanges` compares current settings with the last successfully synced baseline, bumps the counter above the highest known field clock, and stamps only changed keys. It must not treat an old local snapshot as a fresh edit after a conflict.
- The settings allowlist is explicit and type-checked. V1 keys are:

```text
audio_quality_wifi, audio_quality_cellular, upgrade_length_slack_seconds, audio_quality_download,
crossfade_seconds, smart_fade_enabled, sleep_timer_fade_out, automix_performance_mode, skip_silence,
loudness_normalization, direct_source_stream, youtube_premium_audio, equalizer_enabled, equalizer_mode,
equalizer_tone_x, equalizer_tone_y, equalizer_focused, equalizer_balance, equalizer_bands, playback_speed,
theme_mode, app_font, autoplay, shuffle_enabled, repeat_mode, show_nerd_stats, show_listen_together_button,
reduce_animation, hide_volume_bar, hide_status_bar_in_player, hide_song_status, hide_lyrics_preview,
lyrics_card_logo, show_queue_history, swipe_to_play_next, dont_repeat_suggestions, prefer_music_only,
hide_recents, hide_home_videos, hide_top_picks, hide_home_moods, square_video_thumbnails,
smart_version_alignment, reduce_dynamic_blur, disable_blend_effects, liquid_glass, glass_performance,
progressive_blur, lyrics_blur, lyrics_offset_ms, translation_language, animated_canvas, canvas_over_cellular,
disable_spotify_canvas, spotify_canvas_auto_hide, prioritize_spotify_canvas, full_bleed_artwork,
full_bleed_detail_artwork, show_total_duration, player_theme, last_player_screen, synced_lyrics,
lyrics_sources, lyrics_source_order, prioritize_syllable_sync, replay_genres, filter_non_music_audio,
library_sort, pinned_playlists
```

- `SyncPreferencePolicy.applySnapshot` changes only allowlisted keys, skips wrong-type values, applies explicit tombstones, and preserves unknown, secret, and device-local preferences. The current allowlist intentionally excludes cache limits, download inventories/paths, audio-output/DAC controls, performance/display hardware controls, integration endpoints and credentials, Discord identity/presence controls, search history, listening aggregates and custom media-source definitions.
- Listening history, search history and custom sources require separate category consent and are not in this v1 settings payload.
- `shuffle_enabled` and `repeat_mode` are synced as persistent playback-mode preferences and applied when the receiving device initializes its player. This does not sync queue contents, current item, playback position, active session or resume state; those remain device-local until a separate handoff protocol exists. Do not let every device race to publish Rich Presence.
- Preserve a previous local encrypted snapshot until downloaded ciphertext authenticates, validates schema and merges successfully. Unknown major schema versions are rejected.
- Sync remains disabled by default. `AndroidSyncManager` requires an explicit enable or pairing action, the settings page offers manual sync and pause/resume, and WorkManager runs a network-constrained one-time pass plus a six-hour periodic pass. A process-wide mutex serializes sync, pause/resume and local reset; CAS conflicts retry up to the shared coordinator limit. Transient I/O/Ktor timeouts, HTTP 408/425/429/5xx and exhausted CAS conflicts use WorkManager backoff. Durable last-success/error history and active-device Discord presence coordination are not yet implemented.

## Acceptance gates

1. API contract, rate limits, persistence, restart, stale-CAS, device revoke/delete, request-size and malformed-store tests pass.
2. Android and desktop independently pass encryption/merge tests using the same language-neutral fixtures.
3. Two phones and desktop pair to one profile; settings/source order/history converge after online and offline edits; local-only hardware settings do not move.
4. Revoked device cannot fetch/upload; stale requests cannot overwrite newer snapshots; only the active device may publish Rich Presence after a lease/handoff mechanism exists.
5. Upstream design review, privacy review, storage/operator documentation, and deploy verification complete before enabling a public service.
