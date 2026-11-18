# BitChord cross-device sync protocol (draft v1)

Status: **server API implemented locally; client protocol, encryption and sync are not implemented yet. Do not enable this service for users until clients encrypt snapshots locally.**

## Security model

- The API does not authenticate with Google/YouTube and must never receive a Google password, cookie, or OAuth credential. New profile creation is operator-disabled by default; an operator must explicitly enable `SYNC_ALLOW_PROFILE_CREATION=true` after deciding registration and retention policy.
- The first device creates a random 256-bit profile key locally. Encrypt each serialized sync snapshot with AES-256-GCM using a fresh 96-bit nonce. Bind `profileId`, protocol version, and snapshot schema version as authenticated associated data. Use a vetted platform crypto provider; do not implement crypto primitives manually.
- Store the profile key in Android Keystore-backed storage and the desktop OS credential store. The API receives only Base64 ciphertext and revision metadata. Verify encryption/decryption, authentication failure, key mismatch, and profile binding before client rollout.
- Device pairing: an authenticated device requests an API pairing token (5-minute expiry, one use), then shows a QR containing the HTTPS sync base URL, profile ID, pairing token, and profile key. The second device reads the QR locally; it sends only `profileId` and `pairingToken` to `/v1/pairings/claim`, then stores the received device token and profile key securely. Never include the profile key in an API request, URL, HTTP header, log, crash report, or analytics event.
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

## Client snapshot and merge rules (planned)

- Wrap the allowlisted account profile in a schema-versioned envelope. Keep a monotonic profile revision from the API separate from each field's logical version.
- Scalar settings: compare `(logicalCounter, deviceId)` lexicographically; deterministic tie-break by device ID. This avoids relying on wall-clock synchronization.
- Append-only listening events: stable event IDs; merge by set union and deduplicate by ID.
- Ordered lists/playlists: stable item IDs plus ordering metadata/tombstones; never merge by replacing the whole list with whichever snapshot arrived last.
- Queue/resume/live playback: only the designated active device owns mutable playback state. A server-backed active-device lease and handoff endpoint are not implemented yet; do not encode presence by racing whole-profile uploads.
- Ignore unknown fields only when the schema remains compatible; reject unknown major versions without replacing local state. Keep a previous local encrypted snapshot until a downloaded snapshot passes authentication, schema validation, and merge validation.
- Sync disabled by default. Show last-sync/error state; retry boundedly; never block playback while network sync runs.

## Acceptance gates

1. API contract, rate limits, persistence, restart, stale-CAS, device revoke/delete, request-size and malformed-store tests pass.
2. Android and desktop independently pass encryption/merge tests using the same language-neutral fixtures.
3. Two phones and desktop pair to one profile; settings/source order/history converge after online and offline edits; local-only hardware settings do not move.
4. Revoked device cannot fetch/upload; stale requests cannot overwrite newer snapshots; only the active device may publish Rich Presence after a lease/handoff mechanism exists.
5. Upstream design review, privacy review, storage/operator documentation, and deploy verification complete before enabling a public service.
