# Full-sync state inventory (Android survey, v1.9.2)

Status: preliminary source inventory. This is a data-classification aid, not an authorization to sync every field. The field allowlist must be reviewed against current `AppSettings` keys and upstream privacy decisions before a client uploads anything.

| Data source | Storage/API | Proposed classification | Notes / policy |
|---|---|---|---|
| User-facing preferences | `app/src/main/java/com/music/bitchord/data/settings/AppSettings.kt`; SharedPreferences `bitchord_settings`; existing `exportPrefs()`, `importPrefs()`, `reload()` | `account-sync` only via an explicit allowlist | Do not mirror the entire preference file. Sync portable choices (appearance, playback preferences, etc.) only after mapping each key; retain device-local overrides. |
| Listening aggregates | `app/src/main/java/com/music/bitchord/data/stats/ListeningStats.kt`; monthly JSON under `filesDir/listening`; `exportAll()` / `importAll()` | `account-sync`, separate opt-in | Contains media IDs, titles, artists/albums, artwork URLs, timestamps, day/hour totals and play counts. Treat as personal data; require explicit category consent and deletion controls. |
| Search history | persisted in `bitchord_settings` and included in current manual export | `account-sync`, separately consented or excluded | Do not inherit inclusion from settings export. User may want preference sync without query history. |
| Source/add-on configuration | `app/src/main/java/com/music/bitchord/data/sources/SourceRegistry.kt`; separate `bitchord_sources` store; `configs`, `add`, `update`, `remove`, `setEnabled`, `reorderAddons` | `secret-excluded` by default; optional distinct category after review | URLs and metadata can reveal private hosts or carry credentials in paths/queries. Add a registry export/replace API rather than accessing its private preference handle. Never sync credentials embedded in URL/userinfo/query. |
| Credential/session stores | `app/src/main/java/com/music/bitchord/auth/AuthStore.kt`; `EncryptedPrefs` | `secret-excluded` | Includes third-party session/tokens and account credentials. Keep per-device; require re-authentication on each device. |
| Android encrypted preferences | `app/src/main/java/com/music/bitchord/auth/EncryptedPrefs.kt` | device-local key storage only | AndroidX EncryptedSharedPreferences uses Keystore-backed AES, but falls back to plain SharedPreferences when secure storage is unavailable. Do not use it as the sole fail-closed guarantee for a new sync key. |
| Android attestation key | `app/src/main/java/com/music/bitchord/data/sources/addon/AddonAuth.kt` | device-local, never exported | AndroidKeyStore device-bound key is for addon attestation, not portable profile encryption. |
| Downloads and download inventory | `AppSettings.DEVICE_LOCAL`, local download stores/collections | `device-local` | Files and local inventory do not move to another device. Do not sync downloaded media or cache metadata. |
| Storage/access paths | SAF folder URI and related local file handles; `AppSettings.DEVICE_LOCAL` | `device-local` | URIs/permissions are device-bound and must stay local. |
| Local playback/output state | runtime flows, audio session IDs, selected device/DAC/output | `device-local` | Hardware and OS state can differ across phones and PC; preserve local overrides. |
| Build/version/onboarding state | last-version code and onboarding-complete state (`AppSettings.DEVICE_LOCAL`) | `device-local` | Replaying these across devices may skip required local setup or trigger migration mistakes. |

## Existing behavior and integration points

- Manual Android export/import lives in `app/src/main/java/com/music/bitchord/data/stats/Backup.kt`; it exports settings plus listening buckets and replaces data on import. It is not live sync and does not include `SourceRegistry` configuration.
- Settings UI lives in `app/src/main/java/com/music/bitchord/ui/screens/SettingsSheet.kt`; the existing **Your data** section and document pickers are the natural location for sync settings.
- Settings routing is in `app/src/main/java/com/music/bitchord/ui/navigation/AppNavigator.kt` and `app/src/main/java/com/music/bitchord/MainActivity.kt`.
- Android cloud/device backup policy is controlled by `app/src/main/res/xml/backup_rules.xml` and `app/src/main/res/xml/data_extraction_rules.xml`; do not change those to include sync tokens or profile keys.

## Upstream privacy coordination

- Open PR #1715 provides optional WebDAV/S3 backup with manual restore; it is backup/replace, not multi-device merge. Avoid duplicating its backup transport/settings flow.
- Open PR #1801 changes backup/device-transfer policy and secure storage behavior. Reconcile any sync-key persistence or opt-in history sync with the maintainers’ privacy design before merging; do not assume unmerged code exists in this checkout.

## Before implementing Android sync

1. Inspect the exact `AppSettings` key lists (`SECRETS`, `DEVICE_LOCAL`) and write a versioned sync allowlist test.
2. Test old/missing/malformed settings, per-device overrides and concurrent edits; sync must never replace the whole preference file with a stale snapshot.
3. Decide separately whether to sync listening history and custom media-source definitions; both need explicit UI controls.
4. Implement a fail-closed portable profile-key workflow and test import rejection without damaging the current local profile.
5. Add an integration test through the real Settings screen and verify no network requests occur while sync is disabled.
