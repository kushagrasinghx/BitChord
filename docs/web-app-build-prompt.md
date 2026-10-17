# Build BitChord Web: a React + Vite web player with a companion browser extension

You are building the web version of BitChord, an existing Android/desktop music player whose code is in this repository. The web app streams from YouTube Music and from user-added HTTP addons, has Listen Together and Replay, and has YouTube Music, Spotify and Discord integrations. A companion browser extension does the networking and sign-in work that a web page isn't allowed to do. The UI replicates the Apple Music web player (music.apple.com).

This document records decisions that were already made. Don't reopen them; where something is genuinely undecided, it says so.

---

## 0. Ground rules

- **Where the code goes:** this repo, under `web/`:
  - `web/app`: the Vite + React site.
  - `web/extension`: the browser extension.
  - `web/shared`: TypeScript types and protocol code used by both.
  - Use one package manager (pnpm workspaces) across all three.
- **The Kotlin code is the specification.** Every feature below has a working implementation in `shared/`, `app/` or `backend/`. Read it before writing the TypeScript version.
  - Port the reference behaviour faithfully first. Simplify only once you have evidence a piece is unnecessary. In this codebase, lines that looked redundant have more than once turned out to be the actual fix.
- **Probe real responses before writing a parser.** Before writing parsers for InnerTube, addons, lyric providers or Spotify, capture a real response and save it under `web/fixtures/`.
  - A lenient JSON parser turns a wrong envelope into an empty list instead of an error, so test against those captured fixtures.
- **Read the project memory index first** (`MEMORY.md` in the Claude project memory). The relevant entries are:
  - Listen Together party server and its sync bugs.
  - Lyric clock dates readings by sample time.
  - am-lyrics is the lyric benchmark.
  - lrc.red is Bini's backend.
  - vivi's YouTube is InnerTubeX.
  - Sources are user-added HTTP addons.
  - Say "addon", never "Eclipse".
  - UI must feel native.
  - Coil inline ImageRequest restarts: the same lesson applies to React effects.
- **Naming:** the source protocol is always called an **addon** in code and on screen.
- **Commits:** no Co-Authored-By trailer and no "Generated with Claude Code" line in commits or PRs.
- **Work in phases (section 11).** Each phase ends in something that runs. Don't build UI against mocked data you haven't probed for real.

---

## 1. Stack and architecture

| Piece | Decision |
|---|---|
| Framework | **Vite + React 19 + TypeScript (strict)**. Not Next.js: the app is entirely a client-side player, so server rendering adds nothing. |
| PWA | **No.** No service worker, no manifest-based install, for now. |
| Routing | React Router (data router). Every route is lazy-loaded (section 3). |
| State | Zustand for player and queue state. TanStack Query for every fetch: caching, dedupe, retries. |
| Styling | CSS Modules plus CSS custom properties taken from Apple Music's own tokens (section 2). No component library; Apple Music's look doesn't come out of one. |
| Playback | **Shaka Player** for YouTube (SABR) and for DASH-manifest addon streams. A plain `<audio>` element for progressive addon URLs. Route both through one Web Audio graph for volume, crossfade and EQ (section 5.5). |
| Hosting | Static build on Vercel or Cloudflare Pages (free). |
| Backend | **None for streaming.** The only server is the existing Go Listen Together server (section 8). |
| Extension | One Manifest V3 codebase built for Chromium (Chrome, Edge, Brave) and Firefox (section 4). |

### Compute budget (a hard constraint)

The only server is an Oracle Always Free `VM.Standard.E2.1.Micro`: 1/8 OCPU, 1 GB RAM, roughly 50 Mbps.

- **No audio and no YouTube API traffic may ever go through our servers.** At 1000 simultaneous users, routing audio through the server needs about 150 Mbps. It would also put thousands of YouTube requests a minute behind one datacenter IP, and YouTube blocks that.
- All YouTube traffic leaves from the user's browser, from their own IP, through the extension.
- Do not build an audio proxy. A capped proxy for users without the extension is explicitly out of scope.

### Without the extension

- Detect the extension on startup (section 4.2).
- If it's missing:
  - YouTube playback, YouTube sign-in, Spotify and Discord show an install prompt instead of their features. Style it like Apple Music's own "sign in" banners, not as an error.
  - Addons and lyric providers that allow cross-site requests (CORS) still work.
- On mobile browsers, which can't install extensions, show a banner pointing to the Android app.

---

## 2. UI: replicate the Apple Music web player

The UI should replicate **music.apple.com** as closely as possible: layout, spacing, type scale, colours, motion, and hover, focus and empty states.

### 2.1 Investigate the live DOM before writing any component

Open https://music.apple.com in the browser tools you have (the built-in browser pane, or Claude in Chrome if the user prefers). **Don't sign in.** The browse, album, artist, playlist and search pages render signed out.

For each surface below:

- Take a screenshot at 1440×900, 1024×768 and 390×844.
- Read the DOM and accessibility tree.
- Run `getComputedStyle` on the key elements.

Surfaces:

1. App shell: left sidebar (search field, nav sections, library list), top player chrome (transport, now-playing tile with artwork, scrubber, volume, lyrics and queue toggles), main scroll area.
2. Home/Browse: hero, shelves, horizontal scrollers with arrow buttons, square and wide lockups, hover play buttons, "See All".
3. Album page: header (artwork, title, artist link, metadata line, Play/Shuffle buttons, "…" menu), track list rows (number turning into a play button on hover, explicit badge, duration, row menu), footer (release date, label, "More by").
4. Artist page: hero image, top songs grid, albums/singles shelves.
5. Playlist page.
6. Search: suggestions dropdown, top results, category tiles.
7. Lyrics view and Up Next queue panel: their open and close animation, and full-screen versus side panel.
8. Context menus, toasts, modals, focus rings, keyboard shortcuts.
9. Light and dark appearance, which follows `prefers-color-scheme`. Check both.

Write the findings to `web/docs/apple-music-dom.md` **before** building components. Cover:

- Element hierarchy per surface.
- Every CSS custom property Apple defines on `:root`/`body` (dump them with a script in the page), plus the colour, radius, spacing and shadow values actually in use.
- The type scale (font sizes, weights, line heights, letter spacing).
- Breakpoints, and what changes at each one.
- Animation durations and easing curves.
- Lockup and artwork sizes per breakpoint, and grid column counts.
- Scroll behaviour (sticky headers, shelf scroll-snap).

Build the design tokens in `web/app/src/styles/tokens.css` from this document.

### 2.2 What not to copy

- Apple's logos, wordmark, app icon and editorial artwork.
- The SF Pro font files. **Use DM Sans from Google Fonts on every platform** (decided 2026-10-15; replaces the earlier SF/Inter stack). Keep Apple's type scale; only the family changes.
- SF Symbols, which are licensed for Apple platforms only. Use an open icon set that's visually close (Lucide or Phosphor at matching stroke weights). Redraw any glyph that doesn't match well enough.
- Layout, spacing, motion and interaction patterns should match exactly. Branding is BitChord's own.

---

## 3. Lazy loading and skeletons, at every step

- **Routes:** every route component uses `React.lazy` inside `Suspense`. The fallback is a skeleton of that page's real layout, not a spinner.
- **Data:** every TanStack Query–backed section renders its own skeleton while loading. That includes each shelf, the album header, the track list, artist sections, search results, lyrics, the queue and the Replay cards. One slow shelf must not hold back the page.
- **Skeletons match the final layout exactly:** same artwork sizes, line counts and row heights, so nothing shifts when content arrives. Use a subtle shimmer, and turn it off under `prefers-reduced-motion`.
- **Images:** a fixed aspect-ratio box with a neutral placeholder. Use `loading="lazy"` and `decoding="async"`, and fade in on load. For artwork, request the size actually displayed, using YouTube's `=wNNN-hNNN` thumbnail URL parameters.
- **Long lists** (big playlists, the library, search, the queue) are virtualized.
- **Heavy modules load on first use:** Shaka Player, the am-lyrics component, the Replay charts and the bot-check token (PO token) generator.
- **Prefetch** a route's chunk and its first query on link hover or focus.

---

## 4. The browser extension

### 4.1 What it is for

The extension is the networking layer and the identity layer:

1. **Network bridge.** It sets headers YouTube requires and adds CORS headers to responses, so the page can talk to YouTube directly. It also lets the page reach addons and lyric providers that don't allow cross-site requests.
2. **Integration 1, YouTube Music:** signed-in requests using the user's own browser session.
3. **Integration 2, Spotify:** library and playlist import plus Canvas, using the user's own open.spotify.com session.
4. **Integration 3, Discord:** "Listening to BitChord" rich presence.

All credentials stay inside the extension. The web page never receives cookies or tokens; it only gets results.

### 4.2 Structure

Use Manifest V3 and build with WXT (or a Vite-based MV3 setup) to get Chrome and Firefox targets from one source.

**Background service worker:** owns all credentialed requests.

**Page ↔ extension messaging:**
- Chromium: `externally_connectable` matching the site's origins (production domain plus `http://localhost:5173`), with `chrome.runtime.sendMessage(EXTENSION_ID, …)`.
- Firefox doesn't support `externally_connectable` for web pages. Use a content script on the site's origin that relays `window.postMessage` ↔ `runtime.sendMessage`.
- Hide both behind one `bridge` module in `web/shared`.
- The bridge contract is typed request/response pairs with a version number:
  - `ping`, which returns the version and capabilities. Use it for extension detection.
  - `yt.fetch`, `yt.authState`, `addon.fetch`, `lyrics.fetch`, `spotify.*`, `discord.*`.
  - Plus a streaming port for long-lived pushes (auth state changes, Discord connection status).

**Header and CORS rules** via `declarativeNetRequest` dynamic rules, scoped to requests initiated by the site's origin (`initiatorDomains`):
- On `music.youtube.com/youtubei/*`, `www.youtube.com/youtubei/*` and `*.googlevideo.com/*`: set `Origin` and `Referer` to the YouTube origin the request impersonates.
- On those responses: add `Access-Control-Allow-Origin: <site origin>`, `Access-Control-Allow-Credentials` where needed, and `Access-Control-Expose-Headers`.
- With those rules, Shaka's SABR requests and plain InnerTube calls go **directly from the page**. Audio bytes never pass through the messaging channel.
- Signed-in YouTube calls go through `yt.fetch` in the background worker instead, because they need cookies and the SAPISIDHASH header (section 5.3).

**Permissions:**
- Required: `music.youtube.com`, `www.youtube.com`, `*.googlevideo.com`, `*.youtube.com` (cookies), `open.spotify.com`, `api-partner.spotify.com`, `clienttoken.spotify.com`, `accounts.spotify.com`.
- Requested **at use time** through `optional_host_permissions`:
  - Each addon's origin, requested when the user adds that addon.
  - Each lyric provider host that doesn't allow cross-site requests, requested once in lyric settings.
  - Discord hosts, requested when the user turns Discord on.
- Request no broad `<all_urls>` permission at install.

**Popup:** the three integrations with their connected/disconnected state, account names and avatars, a sign-out button for each, the extension version, and a link to the site.

### 4.3 Store-review risk (decided: build it anyway; keep the risk contained)

- The Chrome Web Store bans extensions that download YouTube content. An extension that rewrites YouTube's headers to stream audio may be rejected or later removed.
- **Never** add a download feature for YouTube.
- Keep the listing's description about integrations and playback.
- Make Discord presence a separate build-time feature flag (section 7.3), so the store build can leave it out if review objects.
- Firefox's add-on store (AMO) is the fallback distribution channel. Keep the Firefox build fully working.

---

## 5. YouTube Music streaming

### 5.1 References (read these first)

- **YouTube.js** (https://github.com/LuanRT/YouTube.js, npm `youtubei.js`, MIT). A JavaScript InnerTube client.
  - Use its `music` namespace for browse, search, album, artist, playlist, up next/radio, related tracks and lyrics.
  - Docs: https://ytjs.dev. The browser-usage guide routes everything through a custom `fetch`; in our build that `fetch` targets the extension's header rules, not a proxy.
- **googlevideo** (https://github.com/LuanRT/googlevideo, npm `googlevideo`, MIT): handles SABR and UMP. Read its `/examples` folder and the API docs at https://ytjs.dev/googlevideo/api.
- **Kira** (https://github.com/LuanRT/kira, live at https://kira.ytjs.dev, MIT): the **primary reference for playback.**
  - It's a Vite web client that plays YouTube through YouTube.js + googlevideo + Shaka Player over SABR.
  - It includes the Shaka network scheme that intercepts `application/vnd.yt-ump` responses and a `SabrUmpProcessor`.
  - It also runs desktop users through an extension. Read how its extension and page cooperate, and reuse the approach.
  - The old YouTube.js browser example (dash.js + `toDash`) is marked outdated. Don't start from it.
- **BgUtils** (https://github.com/LuanRT/BgUtils, npm `bgutils-js`): generates bot-check tokens (PO tokens) by running Google's BotGuard script. Generate tokens in the page or extension context: the user's real browser is where BotGuard expects to run.
- **BitChord's own Kotlin implementation.** It's the behavioural reference for which client to use, what to retry, and when to fall back:
  - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/innertube/StreamResolver.kt`
  - `…/innertube/InnerTubeXResolver.kt`: client catalog and cipher tiers, from the InnerTubeX library that the vivi app also uses.
  - `…/innertube/PlayerClient.kt`
  - `…/innertube/potoken/`: PO token generation on Android (a WebView runs BotGuard).
  - `…/innertube/Innertube.kt`: WEB_REMIX context; the live client version read from `https://www.youtube.com/sw.js_data` with a fallback version; SAPISIDHASH signing; `X-Goog-AuthUser`; `X-Origin`.
  - `…/innertube/InnertubeParser.kt`
  - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/YtMusicRepository.kt`: which endpoints back which screens.
  - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/sources/{StreamFormat.kt,TrackMatcher.kt,SourceTrackKeys.kt}`

### 5.2 Playback pipeline

1. Resolve the track's video id. A YouTube Music `videoId` maps directly.
2. Get a PO token (BgUtils), cached per session and visitor data as BitChord does.
3. Call `player` through YouTube.js, with requests passing the extension's header rules.
4. Start a SABR session (googlevideo) and play **audio only** through Shaka. Prefer Opus (itag 251) and fall back to AAC (itag 140). Use AAC on Safari, which isn't a supported target but must not crash.
5. On a 403, a bot-check failure or a missing format, follow the fallback order in `StreamResolver.kt`/`InnerTubeXResolver.kt`. Retry with another client or refresh the PO token. Log each step to a debug panel (the web equivalent of `TrackLog`).
6. Media Session API: metadata, artwork, play/pause/next/previous/seek handlers, position state.

### 5.3 YouTube Music sign-in (Integration 1)

- There's no login screen. The extension reads the user's existing Google session cookies for `music.youtube.com`/`.youtube.com` with the `cookies` API.
  - The Android equivalent is `app/src/main/java/com/music/bitchord/auth/{YtMusicLoginScreen.kt,WebSession.kt,AuthStore.kt}`.
  - The **closest analogue** is the desktop app, which already reads cookies from installed browsers: `desktopApp/src/main/kotlin/com/music/bitchord/desktop/{DesktopBrowserCookies.kt,DesktopYouTubeAuth.kt,DesktopWebSignIn.kt}`.
- Signed-in requests run in the background worker:
  - Cookie header from the jar.
  - `Authorization: SAPISIDHASH <ts>_<sha1(ts + " " + SAPISID + " " + origin)>`, exactly as `Innertube.kt` builds it.
  - `X-Goog-AuthUser`, `X-Origin`, and the visitor data.
- Multiple Google accounts: list the jar's accounts and channels and let the user pick one, which sets `X-Goog-AuthUser` and the page id. The Android behaviour is `WebSessionMode.SWITCH_CHANNEL`.
- Signed-in features: home feed, library (playlists, albums, artists, liked songs, history), like/unlike, add to playlist, and personalised radio.
- Signed out: browse, search and playback still work anonymously.

---

## 6. Addons (same protocol as the app)

Reference implementation:
- `app/src/main/java/com/music/bitchord/data/sources/addon/AddonClient.kt`
- `…/addon/AddonModels.kt`
- `…/addon/SourceFormats.kt`
- `app/src/main/java/com/music/bitchord/data/sources/AddonSource.kt`

Port the request and response shapes, caching (shared calls with manifest and stream time-to-live caches), and matching **exactly**.

- **An addon is an HTTP server.** Nothing it sends is ever executed.
  - **`GET {base}/manifest.json`** returns `id`, `name`, `version`, `resources[]`, `settings[]` (`key`, `default`, `options[].value`), `allowDownloads`, `checkValidLossless`.
    - The addon is playable if `resources` is empty or includes `search`.
    - Send each declared setting's **default** as a query parameter on every request.
    - The `quality` setting gets the closest option to the requested tier (`LOSSLESS`/`HIGH`/`LOW`). See `settingsFor` and `matchTier`.
  - **`GET {base}/search?q=`** returns `tracks[]` with `id`, `title`, `artist`, `album`, `duration`, `artworkURL`, `albumArtworkURL`, `format`, `audioQuality`, `audioMode(s)`, `atmos`, and an optional `streamURL`.
  - **`GET {base}/stream/{id}`** returns `url` or `manifest`, plus format and codec fields, `sampleRate`, `bitDepth`, `bitrate`, `encrypted` and `error`.
  - The manifest is optional: identify an addon by probing its endpoints, as `SourceFormats.identify` does.
- **Matching** a YouTube or catalogue track to an addon result: port `TrackMatcher.kt` and `SourceTrackKeys.kt`.
- **Fetching:**
  - Try a direct `fetch` first.
  - If it fails on CORS, ask the user to grant that addon's origin to the extension, and from then on route the addon through `addon.fetch`.
  - Remember per addon which route works.
- **Playing:**
  - A progressive `url` plays in `<audio>`.
  - A DASH `manifest` (for example Tidal-style MPDs) plays in Shaka. Pick the FLAC rendition when the tier is LOSSLESS: these manifests list every rendition, HE-AAC first and FLAC last.
  - `encrypted` streams are skipped.
  - FLAC and ALAC-in-MP4 play natively in Chromium and Firefox. DSD, AC-4 and E-AC-3 are **unsupported** on the web: skip to the next source and say so in the debug panel.
- **`checkValidLossless`:** a browser can't detect a lossless-capable output, so skip such addons unless the user turns on "My output supports lossless" in settings.
- **`allowDownloads`:** there are no downloads on the web for now. Ignore it.
- **Web Audio caveat:** media from another origin is silent inside an `AudioContext` unless it's fetched with CORS. Set `crossOrigin="anonymous"` on the element. If the addon's CDN doesn't send CORS headers, either add a `declarativeNetRequest` response-header rule for that granted origin, or play the stream outside the Web Audio graph (no crossfade or EQ for that track).
- Addon settings UI: an add-by-URL field, a reorderable list, enable/disable, manifest details, and per-addon permission status.

---

## 7. The other two extension integrations

### 7.1 Spotify (Integration 2)

- Reference implementation:
  - `app/src/main/java/com/music/bitchord/auth/SpotifyLoginScreen.kt`: captures the `sp_dc` cookie.
  - `app/src/main/java/com/music/bitchord/data/canvas/{SpotifyToken.kt,SpotifyCanvas.kt}`: token from `sp_dc` (TOTP-based, via `open.spotify.com`/`accounts.spotify.com`) and a client token from `clienttoken.spotify.com/v1/clienttoken`.
  - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/canvas/SpotifyCanvasQuery.kt`
  - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/spotify/{SpotifyImporter.kt,SpotifyLibrary.kt}`: `api-partner.spotify.com/pathfinder` queries.
  - Desktop: `desktopApp/.../DesktopSpotifyCanvas.kt` and `DesktopIntegrationsUi.kt`.
- The extension reads `sp_dc` from the user's own open.spotify.com session and mints the tokens in the background worker. The page never sees `sp_dc` or the tokens.
- Features:
  - Import liked songs and playlists into BitChord, matched to YouTube Music tracks with the importer's matching.
  - Spotify Canvas (looping video) behind the now-playing artwork, as on the phone. Load it lazily, never autoplay it on metered connections, and respect reduced motion.
- If the TOTP secret scheme changes upstream, copy the current Kotlin implementation. Don't invent a fix.

### 7.2 Discord, setup

- Reference implementation:
  - `app/src/main/java/com/music/bitchord/auth/DiscordLoginScreen.kt`: token from discord.com `localStorage`.
  - `shared/src/jvmSharedMain/kotlin/com/my/kizzy/{gateway,rpc,utils}`: gateway at `wss://gateway.discord.gg/?v=9&encoding=json`, external assets via `discord.com/api/v9/applications/{id}/external-assets`.
  - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/discord/DiscordApplication.kt`: application id.
  - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/presence/Presence.kt`: what the activity shows.
- In the extension:
  - When the user connects Discord, read the token from an open discord.com tab's `localStorage`, through a content script that runs only on the user's explicit "Connect Discord" action.
  - Hold the gateway WebSocket in the background worker. MV3 service workers sleep, so keep it alive with an offscreen document or the Chrome 116+ WebSocket keep-alive.
  - Publish the same activity the phone does: track, artist, artwork via external assets, and timestamps.
  - The page only sends "now playing" updates through the bridge.

### 7.3 Discord, risk and the feature flag

- This is user-token automation, which Discord's terms prohibit, exactly as on the phone. The user has accepted that. Make it opt-in, with a one-line warning when it's turned on.
- Put it behind the build flag `DISCORD_PRESENCE` so the Chrome Web Store build can ship without it.
- Use Discord OAuth (`identify` scope) only if we ever need identity alone. It isn't part of this phase.

---

## 8. Listen Together

Server: `https://api.bitchord.kushagrasingh.in`, the Go server in `backend/`. Read `backend/README.md` in full first: the anchor-and-offset sync model is the whole design.

### 8.1 Server API
- REST:
  - `GET /api/time`
  - `POST /api/parties`
  - `POST /api/parties/{code}/join`
  - `GET /api/parties/{code}`
  - `GET /api/parties/{code}/preview`
  - `POST /api/parties/{code}/leave`
- Bearer token auth, as in `parseBearerToken`.
- WebSocket: `GET /ws/parties/{code}`.
- Frame types (defined in `backend/protocol/protocol.go`): `welcome`, `state`, `sync`, `members`, `queue`, `control` (`play`, `pause`, `seek`, `next`, `previous`), `ping`/`pong`, `activity`, `kick`, `bye`, `error`, `report`. Copy the field names from the Go structs.

### 8.2 Client
- Port `app/src/main/java/com/music/bitchord/data/listentogether/{ListenTogether.kt,JamInviteLink.kt}`, `app/src/main/java/com/music/bitchord/playback/PartySync.kt` and `shared/src/jvmSharedMain/kotlin/com/music/bitchord/playback/QueueTimeline.kt` (the shared queue model).
  - The memory index lists sync and queue bugs that were already fixed (the AutoPlay double-send and the duplicate-track index). Port the fixed behaviour.
- **Clock:**
  - Time readings come from `performance.timeOrigin + performance.now()`.
  - Offset = `serverMs - (t0 + t1) / 2`; keep the sample with the smallest round trip.
  - Playhead = `positionMs + max(0, serverNow - anchorMs)` while playing.
  - Honour the server's play lead (`JAM_PLAY_LEAD_MS`).
  - Subtract `AudioContext.outputLatency` (plus `baseLatency`) when computing where the audio *actually* is.
- **Autoplay policy:** joining from an invite link (`/invite/{code}` → web route) needs a user gesture. Show a "Tap to join" card that starts the `AudioContext`.

### 8.3 Server-side changes this needs (small; the user deploys them)
- Add the production site origin and `http://localhost:5173` to `JAM_ALLOWED_ORIGINS`.
  - The server deliberately rejects wildcards (`backend/config/config.go`).
  - Set it in `backend/deploy/` so `setup.sh` keeps it across rebuilds. Deploy runs on push to `main` (`.github/workflows/deploy-backend.yml`).
- **Do not** call `POST /api/presence` from the web. It rejects any request with an `Origin` header by design. If the user wants web users counted in live stats, that needs a separate, explicit server change. Ask first.

---

## 9. Replay

- Reference implementation:
  - `app/src/main/java/com/music/bitchord/data/stats/{ListeningRecorder.kt,ListeningStats.kt,ArtistFacts.kt,Backup.kt}`
  - `app/src/main/java/com/music/bitchord/ui/replay/{ReplayModel.kt,ReplayScreen.kt,ReplayStories.kt,ReplayPoster.kt,ReplayShareSheet.kt}`
- **Storage:** IndexedDB (via `idb`), with the same bucket model the phone keeps as JSON files: per-period buckets with track, artist and album entries, hourly and daily counters, and per-day stats (`StoredBucket`, `TrackEntry`, `NameEntry`, `DayStats`).
- **Recording rules** (what counts as a play, minimum listened time, skips, seeks): copy `ListeningRecorder.kt` exactly.
- **Import:** accept the phone's backup file (`Backup.kt` format) so a user's existing history comes with them.
- **UI:** the Replay summary, stories and a shareable poster (render to canvas, then download a PNG), styled to fit the Apple Music look.

---

## 10. Lyrics: the desktop provider set, rendered with am-lyrics

### 10.1 Providers

Use the same chain as the desktop app:
- `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopLyricsClient.kt`
- `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/LyricsRepository.kt`
- `…/lyrics/LyricsSource.kt`

The default priority is the declaration order of `LyricsSource`, minus the `hidden` entries (SimpMusic and Megalobiz). The user can reorder and disable providers, as on the desktop:

1. lrc.red (`lrc.red`): Apple timings by recording, syllable-level. See the lrc.red/Bini memory.
2. BiniLyrics (`lyrics-api.binimum.org`): matched on the recording; source of the ISRC.
3. BetterLyrics (`lyrics-api.boidu.dev/getLyrics`).
4. BetterLyrics Portato (`lyrics-api.boidu.dev/qq/getLyrics`).
5. PaxSenix (`lyrics.paxsenix.org`/`api.paxsenix.org`, with Apple catalogue search).
6. PaxSenix: Spotify (API key required).
7. PaxSenix: Musixmatch (API key required).
8. LyricsPlus (community mirrors; port the mirror list and failover from `LyricsPlus.kt`).
9. Unison (`unison.boidu.dev`).
10. YouTube captions (timed captions for the playing video).
11. YouTube Music (plain lyrics from the Lyrics tab).
12. KuGou.
13. LRCLIB.
14. Musixmatch.
15. Genius (plain-text fallback).

Translation and romanisation: `LyricsTranslation.kt` and `TranslationLanguages.kt`.

Port each provider from its Kotlin file, including query building (`LyricsQuery.kt`), the shared fetch (`LyricsHttp.kt`) and every parser:
- `TtmlLyrics.kt`
- `EnhancedLrc.kt`
- `SyllableRuns.kt`
- `BackgroundVocals.kt`
- `LyricSections.kt`
- `LyricGaps.kt`
- `LyricAlignments.kt`

For each provider:
- Test whether it allows cross-site requests (CORS).
- Ones that don't go through the extension's `lyrics.fetch`, after the user grants the host once in lyric settings.
- Ones that do, fetch directly.

### 10.2 Rendering: am-lyrics, as it is

- Use **am-lyrics** (https://github.com/binimum/am-lyrics, npm `@uimaxbai/am-lyrics`, MPL-2.0).
  - It's a Lit web component. Use its React wrapper: `import { AmLyrics } from '@uimaxbai/am-lyrics/react'`, which requires `@lit/react`.
  - Keep its syllable and word animation, interpolation, background-vocal handling and line-click seeking **exactly as they are**. Don't re-implement or restyle the animation.
  - MPL-2.0 is file-level copyleft: using it as a dependency is fine, but any file of it we patch stays MPL-2.0 and must be published.
- **Our providers decide the lyrics. am-lyrics only renders them.**
  - Pass our result through its `ttml` property, so it makes no requests of its own.
  - Convert every result to TTML first: word/syllable-timed results keep their timings, line-synced LRC becomes line-level TTML, and plain text is shown without timing. Port the conversion from `TtmlLyrics.kt` and `LrcWriter.kt`.
  - Hide its source footer (`hideSourceFooter`) and show our own provider picker, so switching providers matches the app.
- **Clock:** pass `currentTime` in ms from a **steered playback clock**, not raw `audio.currentTime`.
  - Read the media time, then advance it by `requestAnimationFrame` deltas between readings.
  - Stamp each reading with the time it was sampled.
  - Explicitly announce seeks so the clock jumps rather than glides.
  - This is the "lyric clock dates readings by sample time" lesson from the memory index; without it lyrics stick or step back and forth.
  - Set `interpolate` on.
- Handle `line-click` by seeking the player to `detail.timestamp`.
- Layout: the Apple Music lyrics side panel and the full-screen lyrics view from section 2.1, hosting the am-lyrics component.

---

## 11. Phases

Finish each phase, verify it in the browser and commit before starting the next.

1. **Investigation.**
   - Apple Music DOM report (section 2.1).
   - Read the Kira source and the extension code it relies on.
   - Fixtures: one each for InnerTube music browse, search, album, artist, `player`, a SABR exchange, an addon manifest/search/stream, and one response per lyric provider.
   - **Done when:** `web/docs/apple-music-dom.md` and `web/fixtures/` exist.
2. **Scaffold.**
   - The pnpm workspace, Vite app, tokens, app shell (sidebar and player chrome) with skeletons, the router with lazy routes, and the extension skeleton with the bridge and `ping`.
   - **Done when:** the site detects the extension in Chrome and Firefox.
3. **YouTube playback.**
   - Header rules, PO token, SABR through Shaka, audio-only, client fallbacks, Media Session.
   - **Done when:** 20 varied tracks (old, new, regional, explicit, age-restricted) play signed out, from localhost, with no server.
4. **Browse UI.** Home, search, album, artist and playlist through `yt.music`, replicating Apple Music, with skeletons everywhere.
5. **YouTube Music sign-in (Integration 1).** Library, likes, account switching.
6. **Queue and player.**
   - Up Next panel, shuffle and repeat, radio and autoplay, crossfade, keyboard shortcuts.
   - Port the queue semantics from `shared/src/jvmSharedMain/kotlin/com/music/bitchord/playback/{QueueBuilder.kt,QueueTimeline.kt,QueueWindow.kt,QueueSections.kt}`.
7. **Addons.** Full protocol, CORS fallback, DASH/FLAC playback, settings UI.
8. **Lyrics.** Provider chain, TTML conversion, am-lyrics, steered clock, translation.
9. **Spotify and Discord (Integrations 2 and 3).**
10. **Listen Together.**
    - **Done when:** a web member and an Android member in the same party stay within ~50 ms of each other across play, pause, seek, next and a queue edit.
11. **Replay.** Recording, IndexedDB, phone backup import, summary, stories, poster.

## 12. Out of scope for now

- PWA and service worker.
- An audio or API proxy server.
- Downloads and offline playback.
- Casting.
- DSD, AC-4 and E-AC-3.
- Choosing the audio output device.
- Safari extension.
- Mobile-browser playback (show the Android app banner instead).
- Discord OAuth.
- Counting web users in `/api/presence`.
