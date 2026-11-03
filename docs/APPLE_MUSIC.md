# Apple Music integration (phone only)

Tracks [kushagrasinghx/BitChord#1222 — Native Apple Music Account Integration via MusicKit](https://github.com/kushagrasinghx/BitChord/issues/1222).

## Current status

**Native Apple Music is available in SDK-equipped dev builds for device testing;
production remains gated until subscribed-device validation is complete.** A normal checkout builds with stub adapters. The phone
now has account connection/error handling, Sources/provider controls, library and
catalog browsing, main Songs-search integration, local playlist import, and a
fake-tested speaker coordinator. Native authentication/playback adapter source is
compiled against Apple's official MediaPlayback 1.1.4 and MusicKitAuth 1.1.5 AARs. Missing credentials cannot
be substituted with fixtures for real sign-in or DRM playback.

**Current blocker:** no real Apple Music developer token or MusicKit signing key
is available. Provisioning the MusicKit-enabled key requires enrollment in the
paid Apple Developer Program. The implementation can be reviewed and tested with
offline fixtures, but live authorization, Apple library access and DRM playback
remain unverified until those server-side credentials and a subscribed test
account are supplied. This work relates to issue #1222; it does not establish that
the issue's live integration requirements are complete.

TV, Wear OS, web and desktop do not use this integration. TV reuses phone sources
with explicit disabled availability flags and the no-SDK adapters. Shared additions remain
JVM-compatible. The phone build's existing packaging task also builds its embedded
watch APK, without changing the watch integration.

Implemented:

- Backend-only ES256 developer-token minting, in-memory caching, safe errors, and
  a separately rate-limited `GET /api/applemusic/token` endpoint.
- Shared REST metadata APIs for catalog song search/ISRC lookup, storefront,
  library playlists/songs/albums/artists/recently added, album tracks, and catalog
  playlists. Pagination is bounded, cancellation-aware, and restricted to HTTPS
  `api.music.apple.com/v1/` URLs. Developer credentials are checked between pages;
  changing the user session aborts pagination rather than mixing accounts.
- Phone token storage using the existing `EncryptedPrefs` helper, backup
  exclusions, and a memory-only, refreshable developer-token cache. As with the
  app's other credentials, the helper falls back to private plain preferences
  when the device Keystore is unusable; both storage files are excluded from
  cloud backup and device transfer.
- Strict catalog matching with a storefront-scoped persistent LRU, ISRC evidence,
  duration checks, video rejection, short-lived negative hits, and prefetching of
  up to three tracks. Lock waiting and live catalog lookup share a 2.5-second
  timeout; network failures and timeouts do not become negative cache entries.
- Guarded playback-provider settings and external-row markers. `am:` means
  pending; `am-miss:<catalogId>` retains a direct Apple catalog ID and is **not**
  treated as a missing Spotify row. The phone blocks pending rows and disables
  Apple-only rows when disconnected or using another provider.
- Activity-owned sign-in controller, candidate storefront validation, cancellation,
  missing-app Play Store action, subscription/expired-subscription errors, and
  disconnect resetting the provider to YouTube Music.
- Sources/provider controls with fallback enabled by default, account health check,
  and a hint explaining which ExoPlayer audio effects do not apply to MusicKit.
- Playlists, songs, albums, artists and artist albums, recently added, and catalog
  song search. Matching uses six workers and seeds the playback matcher directly.
  Favorite Songs never guesses a localized name or fabricated ID; when Apple does
  not identify that collection, the user chooses their actual playlist.
- Main Songs search appends connected Apple catalog results, deduplicates YouTube
  counterparts, and keeps account-bound results separate from YouTube's page cache.
  Home, recommendations, radio and autoplay continue using YouTube Music.
- Library/pasted-link import into LocalPlaylistStore, retaining Apple-only catalog
  rows and reporting unavailable uploads. Connected YouTube users may also import
  matching songs into a private YouTube playlist; the complete local copy preserves
  Apple-only songs and serves as fallback if YouTube creation fails.
- RemoteSpeaker queue integration, priority below Cast/watch and above configured
  audio sources, bounded match/load timeouts, seek/pause/skip/repeat, fallback,
  lyrics ISRC hints, clock/source reporting, local volume routing, and audio-cache/
  crossfade skips. Native transport source prepares one song without auto-start and
  recreates its controller per load to reject stale callbacks and cached credentials.

`BuildConfig.APPLE_MUSIC_AVAILABLE` and `APPLE_MUSIC_AUTH_AVAILABLE` are true in
the `dev` flavor when the official AAR pair is present. The `prod` flavor keeps
both false pending live account/playback QA. `APPLE_MUSIC_SDK_PRESENT` reports
whether both local SDK files were found. Unsupported ABIs or native loading
failures keep the transport unavailable. Compilation and native controller
creation do not establish subscription entitlement or DRM playback readiness.

## Backend setup

You need an Apple Developer account, a MusicKit-enabled key, its Key ID, and your
Team ID. Follow the [backend configuration and security instructions](../backend/README.md#apple-music-developer-tokens-optional)
for these **server-only** environment variables:

- `APPLE_MUSIC_TEAM_ID`
- `APPLE_MUSIC_KEY_ID`
- `APPLE_MUSIC_PRIVATE_KEY_FILE`

Keep the `.p8` outside the repository and mount it as a restricted server secret.
Never place it in `app/`, resources, assets, Gradle properties, or an APK. Neither
a developer token nor a private key belongs in `local.properties` or BuildConfig.
There is no client-side signing or static developer-token fallback.

The HTTPS endpoint returns:

```json
{"token":"JWT","expiresAt":1800003600}
```

`expiresAt` is a Unix timestamp **in seconds**, matching the JWT `exp`. The backend
issues one-hour tokens and refreshes its cache within five minutes of expiry.
The phone keeps the developer token only in memory, refreshing within 60 seconds
of expiry. Token responses use `Cache-Control: no-store`; do not log their bodies.

The endpoint is public and does not require a user token. Anyone who can reach it
can obtain a developer token; CORS is not access control. The built-in per-IP
limit is not a substitute for suitable edge limits or deployment restrictions.
Apple catalog/library requests go directly to Apple; the backend never receives
the Music User Token or proxies the user's library.

## Phone build configuration

In the repository-root, ignored `local.properties`, add only the public backend
address (replace the example with your deployment):

```properties
APPLE_MUSIC_TOKEN_URL=https://your-backend.example/api/applemusic/token
```

Alternatively, supply `APPLE_MUSIC_TOKEN_URL` in the Gradle process environment.
`local.properties` takes precedence. The URL must use HTTPS, end in
`/api/applemusic/token`, and contain no credentials, query, or fragment. Redirects
are not followed. An empty value is supported: the app still starts and ordinary
playback works, but developer-token requests fail with a configuration error.
Changing this value requires rebuilding the app.

Do not add Apple Team IDs, Key IDs, `.p8` paths, developer tokens, or Music User
Tokens to the client build configuration.

### Optional local MusicKit SDK

After reviewing Apple's terms and GPLv3 compatibility, obtain the official
**MusicKit for Android** download from Apple Developer. Keep exactly one of each
file in the ignored directory:

```text
app/libs/applemusic/
  MusicKitAuth-release-1.1.5.aar
  MediaPlayback-release-1.1.4.aar
```

Use the versions supplied in the same official SDK distribution. SDK 1.1.4 ships
the two versions shown above and adds native 16 KB page support. Filename
detection accepts Apple's official casing. Both files or neither must be
present. Duplicates or an incomplete pair fail Gradle configuration clearly.
Never commit or redistribute Apple's binaries without permission.

Real/stub authentication and playback adapter source is under `app/src/appleMusic/`
and `app/src/appleMusicStub/`. Dropping in the AARs selects the real sources and
enables the dev flavor for testing; production stays gated. This implementation
uses the interfaces and lifecycle instructions in the official SDK archive. The
flow requires the Apple Music Android app and an active subscription.

Obtain the archive from [Apple Developer downloads](https://developer.apple.com/download/all/?q=Android%20MusicKit).
The local archive, extracted SDK directory, AARs, and macOS archive metadata are
ignored by Git. Only the AARs are needed under `app/libs/applemusic/`.

## Internal integration contracts

- `AppleMusicLibrary.auth` is installed by `BitChordApplication`. Catalog APIs
  use only the developer token; `/me/` APIs also require a Music User Token.
  Never forward the user token to catalog endpoints or the token backend.
- `AppleMusicTokens.saveUserToken(token, storefront, playbackStorefront)` persists the authorized
  user session. Fetch storefront from `/me/storefront`, not the device locale.
  Treat `connected` as locally stored credentials, not proof of a valid
  subscription or a live Apple authorization check. Preserve MusicKitAuth's
  encoded `appleMusicStorefrontId` separately from the REST country code;
  MediaPlayback's `TokenProvider.getAppleMusicStoreFrontId()` needs that value.
  Clearing a native controller also notifies the SDK with null credentials;
  releasing the adapter clears the SDK's persisted playback state.
- The auth UI updates the settings storefront after a successful
  connection. Disconnect through `AppSettings.disconnectAppleMusic()` to clear
  credentials/storefront and select YouTube Music. Cancel in-flight library and
  auth jobs on disconnect/account switch; they must not republish stale data.
- Construct and use a persistent `AppleMusicMatcher` off the main thread. Share
  one instance per cache file in private app storage. Cache file I/O is bounded
  and best-effort but synchronous; coroutine deadlines cannot preempt disk I/O.
- Apple library IDs and catalog IDs are different. Library uploads may have no
  catalog mapping; never fabricate one from a library ID. Do not invent a
  Favorite Songs playlist ID or identify it solely by its localized name.
- Apple Music is **not** a `MusicSource`: DRM audio must be played by MusicKit,
  never by returning an Apple URL to ExoPlayer.

## Remaining implementation

1. Enroll in the paid Apple Developer Program, create a MusicKit-enabled key,
   configure the Go backend with the Team ID, Key ID and server-only `.p8`, and
   supply its public HTTPS endpoint as `APPLE_MUSIC_TOKEN_URL`. No real developer
   token or signing key is currently available.
2. Test sign-in, subscription errors, DRM playback, transport and fallback using a
   subscribed account with Apple Music installed on a compatible device. Verify
   Cast handoff, focus interruptions, lyrics, notifications, scrobbling, widgets,
   Android Auto, sleep timer and Listen Together. Emulator UI checks and fake
   transport tests cannot establish Apple entitlement or DRM support.
3. Test release shrinking, native ABI and Android 16 KB page-size
   compatibility before changing the independent availability gates.

## Validation

Use the project's pinned Gradle wrapper and **JDK 17**. Android Studio's project
runtime is configured in `.gradle/config.properties`; on the audited machine this
is Temurin 17. Its bundled JDK 25 is incompatible with the pinned Gradle wrapper.

From the repository root:

```sh
./gradlew :shared:jvmTest :app:testDevDebugUnitTest :app:assembleDevDebug --console=plain
```

To reproduce the Android and TV workflow checks, including all module unit tests:

```sh
./gradlew :shared:jvmTest testDevDebugUnitTest assembleDevDebug --console=plain
```

The Check, TV and Web workflows also support manual dispatch on a feature branch
so their GitHub runner results can be checked before opening a pull request.
Check runs the shared JVM suite as well as Android unit tests and builds the dev
APKs; TV builds its dev APK; Web runs its existing type checks, tests and builds.

For only the phone's Apple Music/settings tests:

```sh
./gradlew :app:testDevDebugUnitTest --tests '*AppleMusic*' --tests '*PlaybackProviderTest' --console=plain
```

From `backend/`:

```sh
go test ./... -count=1 -timeout=90s
go test -race ./... -count=1 -timeout=90s
```

These tests use offline fixtures/fakes and do not prove live Apple authorization,
API entitlement, native SDK compatibility, or device playback. Those checks need
the real SDK, a configured Apple key/backend, and a subscribed test account.

The SDK-equipped dev APK was compiled and its native-load instrumentation test
passed on the arm64 Pixel 9 emulator. `zipalign -c -P 16 4` passed for that APK.
The emulator has no Apple Music app or configured token backend, so actual sign-in
and DRM playback remain unverified. Run the native smoke test after supplying AARs:

```sh
./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.music.bitchord.MusicKitRuntimeTest
```
