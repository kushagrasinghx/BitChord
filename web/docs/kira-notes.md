# Kira and ytc-bridge: how the reference web client plays YouTube

Read on 2026-10-15.

- **Kira**: github.com/LuanRT/kira at `7a41cdc` (2025-10-29). Vue + Vite, `youtubei.js` ^16, `googlevideo` ^4.0.4, `bgutils-js` ^3.1.3, `shaka-player` ^4.16.4 (patched by a postinstall script).
- **ytc-bridge**: github.com/LuanRT/ytc-bridge at `8f53620` (2025-09-06), v1.2.0. Kira's companion extension.

Both are MIT. Nothing is vendored. This file records how they work so phase 3 can port the approach.

## Page ↔ extension

ytc-bridge is three pieces:

1. **Static DNR ruleset** (`rules.json`):
   - **`*.googlevideo.com`, XHR only, any initiator except youtube.com:**
     - Request: set `Origin` and `Referer` to `https://www.youtube.com`.
     - Response: set `Access-Control-Allow-Origin: *`, `Access-Control-Allow-Credentials: true`, `Access-Control-Allow-Methods: GET, HEAD, POST, …`, and `X-Content-Type-Options: nosniff`.
   - **`ggpht.com` and `ytimg.com` (XHR and image):** request `Origin`/`Referer` only.
   - Effect: SABR media requests go **straight from the page**, and media bytes never cross the messaging channel.
2. **Injected main-world script** (`injected.ts`, loaded by the content script as a web-accessible resource at `document_start`):
   - Defines `window.ytcBridge = { installed: true }`; this is the detection flag.
   - Defines `window.proxyFetch(url, init)`, which posts `PROXY_FETCH_REQUEST {requestId, url, options}` to the window and resolves on `PROXY_FETCH_RESPONSE`.
   - Bodies are base64-encoded; abort is emulated.
3. **Background worker** (`background.ts`), on `{action: 'fetch'}`:
   - Adds a **session** DNR rule for that exact URL, setting every requested header plus `origin: https://www.youtube.com`. A worker `fetch` can't set forbidden headers itself.
   - Then `fetch(url, {headers: {}, credentials: 'omit'})`, removes the rule, and returns status, headers and body (JSON, text, or base64 for binary).
   - Known bug: rule ids can collide (the counter starts at 4).

Kira's `fetchFunction` (handed to `Innertube.create({ fetch })`):

- Uses `proxyFetch` for everything except `initplayback`. Those requests, and all of googlevideo, go out with a plain `fetch`, relying on the static rules.
- Without the extension, it rewrites the URL to a user-configured proxy (`__host`, `__headers` query parameters). **We drop this path**: no proxy (prompt §1).
- It adds `$fields=playerConfig,storyboards,captions,playabilityStatus,streamingData,…` to `/v1/player` to shrink the response.

### What BitChord changes

- **Permissions.** ytc-bridge asks for `<all_urls>` host permission and injects into every site. We scope rules with `initiatorDomains: [<site origin>, localhost]`, run the content script only on our origins, and use `externally_connectable` on Chromium (prompt §4.2).
- **InnerTube calls.**
  - Kira routes InnerTube through the worker (`proxyFetch`).
  - The prompt wants plain anonymous InnerTube calls to go **directly from the page** with DNR CORS rules on `*/youtubei/*`.
  - **Risk to verify first in phase 3:** a page `POST` with `Content-Type: application/json` triggers a CORS preflight. youtubei has to answer `OPTIONS` with a 2xx for the DNR response headers to rescue it.
    - googlevideo evidently does (Kira's SABR POSTs work through the static rule).
    - If youtubei doesn't, the fallback is to send `Content-Type: text/plain` (a "simple" request; youtubei parses the body regardless) or to route through `yt.fetch` as Kira does.
    - Record which one works in a fixture note.
- **Signed-in calls** go through the worker with cookies and SAPISIDHASH (prompt §5.3). ytc-bridge uses `credentials: 'omit'`, so it never does this.

## InnerTube session

- `Innertube.create({ cache: new UniversalCache(true), fetch })`. The session (visitor data, client version from `/sw.js_data`) is persisted in IndexedDB (`youtubei.js` database).
- If `/sw.js_data` fails on the **first** run, YouTube.js silently falls back to a *locally generated* session, which causes playback failures. Kira deletes the `youtubei.js` database in that case so the next start retries. **Port this.**
- `Platform.shim.eval` is replaced with a `new Function(...)` runner that executes the player's signature and n-parameter functions (`exportedVars.nFunction` / `sigFunction`). Consequence for us: the page CSP needs `'unsafe-eval'`, or the decipher has to run in a sandboxed iframe or in the extension.
  - **Decision for phase 3:** keep it in the page, and allow `'unsafe-eval'` only in the dev CSP until the sandboxed-iframe variant is proven.
- On init, Kira also preloads `https://redirector.googlevideo.com/initplayback?source=youtube&itag=0&…` and caches the redirect target in localStorage (`googlevideo_redirector`), to save a hop on the first SABR request.

## PO token (BotGuard, `services/botguard.ts`)

1. `POST buildURL('Create', true)` with body `["O43z0dpjhgX20SCx4KAo"]` (the request key), headers `content-type: application/json+protobuf`, `x-goog-api-key: GOOG_API_KEY`, `x-user-agent: grpc-web-javascript/0.1`.
2. `BG.Challenge.parseChallengeData`. The interpreter JS is injected as an inline `<script id=<interpreterHash>>`, which also needs the CSP to allow it.
3. `BG.BotGuardClient.create({ globalObj: globalThis, globalName, program })`, then `snapshot({ webPoSignalOutput })`.
4. `POST buildURL('GenerateIT', true)` with `[requestKey, botguardResponse]`, which returns an integrity token. Then `BG.WebPoMinter.create({ integrityToken }, webPoSignalOutput)`.
5. Per video: `mintColdStartToken(videoId)` right away (an immediate placeholder), then `minter.mintAsWebsafeString(videoId)` in the background. **The content binding is the videoId** for the GVS (streaming) token.
6. SABR's `onMintPoToken` returns the minted token, or the cold-start one. For VOD it never blocks; for live it waits.

BitChord's Android version binds the token to the session's visitor data for the player request, and caches the tokens per session and visitor data (`innertube/potoken/`). Port that caching on top of this flow.

## SABR through Shaka

- `new SabrStreamingAdapter({ playerAdapter: new ShakaPlayerAdapter(), clientInfo: { osName, osVersion, clientName: CLIENT_NAME_IDS[name], clientVersion } })`.
- After the `player` response:
  - `setStreamingURL(await session.player.decipher(streaming_data.server_abr_streaming_url))`
  - `setServerAbrFormats(adaptive_formats.map(buildSabrFormat))`
  - `setUstreamerConfig(player_config.media_common_config.media_ustreamer_request_config.video_playback_ustreamer_config)`
- Manifest: `data:application/dash+xml;base64,` + `videoInfo.toDash({ manifest_options: { is_sabr: true, captions_format: 'vtt', include_thumbnails: false } })`. Then `player.load(manifestUri, startTime)`.
- `onReloadPlayerResponse(ctx)`: refetch `player` with `playbackContext.reloadPlaybackContext = ctx`, then reset the streaming URL and the ustreamer config.
- `ShakaPlayerAdapter` registers a custom Shaka networking scheme:
  - Requests whose response is `application/vnd.yt-ump` are read by `SabrUmpProcessor`.
  - The processor extracts the segment Shaka asked for, then **aborts** the rest of the response to save bandwidth.
  - It has a request timeout and abort handling, and `makeResponse` maps 401/403 to CRITICAL errors and the rest to RECOVERABLE.
- **Audio only for BitChord:** load the same SABR manifest but restrict Shaka to audio (`player.configure({ ... })` with video disabled, or filter the video adaptation sets out of the generated MPD). Prefer itag 251 (Opus), fall back to 140 (AAC).
  - The minimal example `googlevideo/examples/sabr-shaka-example` is the smallest starting point.
  - We render into an `<audio>` element.

## Player request parameters (`fetchVideoInfo`)

```
{ videoId, contentCheckOk: true, racyCheckOk: true,
  playbackContext: { adPlaybackContext: { pyv: true },
                     contentPlaybackContext: { signatureTimestamp: session.player.signature_timestamp } },
  startTimeSecs?, playbackContext.reloadPlaybackContext? }
```

**Don't copy `adPlaybackContext: { pyv: true }`.** With it, googlevideo cuts every stream off at ~70 s (measured in phase 3; see `youtube-playback.md`). BitChord's player request leaves it out.

Kira first tries **Onesie** (an encrypted player request through a googlevideo edge, `services/onesie.ts`), and falls back to `innertube.actions.execute('/player', { ...params, parse: false })`. For phase 3 we start with the plain `/player` call. Onesie is an optimisation; consider it once plain playback works.

## Playback tracking

Kira reports `videostats_playback_url` once, then `videostats_watchtime_url` every 30s (`innertube.actions.stats`), so YouTube's recommendations learn from what's played. For signed-in users, this is what feeds YouTube Music history. **Signed-in only, behind a setting** (the Android app's equivalent is its history-sync toggle).
