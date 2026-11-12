# YouTube playback in the browser: what was measured

Phase 3, 2026-10-15. Chromium 145 (Playwright's build) and Firefox 158, both with the extension, signed out, run from `localhost:5173`. Tracks are the 20 in the Debug page's test set (`/debug`).

## Result

- **17 of 20 tracks play in both browsers**, each about 1 s to first audio (the first track of a session pays 2–3 s more for the YouTube.js session). All play Opus itag 251 from **VISIONOS direct URLs**.
- A seek to 60% of the track keeps playing, and so does natural playback past the point where streams used to stop.
- **The other 3 are age-gated.** Every client answers `LOGIN_REQUIRED: Sign in to confirm your age`. The player says "This track is age-restricted. Sign in to YouTube to play it." and doesn't ask again for 10 minutes (StreamResolver's verdict cache). The Android app gives the same answer signed out, so these wait for sign-in (phase 5).

## How a track plays

1. **Player request.** YouTube.js `player`, through the client walk in `resolver.ts`: VISIONOS → YTMUSIC (WEB_REMIX) → WEB → MWEB, at most 3 per track. A client that refuses a track sits out for 10 minutes.
2. **Direct URLs.** If the response has direct audio URLs (VISIONOS does), YouTube.js renders a plain DASH manifest over them, deciphering the signature and `n` parameters, and Shaka fetches by byte range. This is how Android plays: InnerTubeX runs with SABR off.
3. **SABR.** Without direct URLs, a googlevideo SABR session runs in front of Shaka (Kira's adapter). It is kept as a fallback; see "Open" below for why it isn't the main path.
4. **Refusal mid-track** (HTTP 401/403): that client is excluded and the track resumes on the next one at the same position.

## Findings that shaped the code

**`adPlaybackContext: { pyv: true }` caps the stream at ~70 s.**
- Kira sends it in its player request. With it, googlevideo refuses every range past ~1.3 MB, about 70 s of audio, whichever client minted the URL.
- Measured from Node: 4 of 4 VISIONOS URLs refused a range at 1.4 MB with it, 0 of 4 without it.
- In the browser, the same wall appeared over both SABR and direct URLs, for every client, until it was removed.
- `resolver.ts` no longer sends it.

**youtubei rejects CORS preflights** (400, even with a YouTube Origin).
- The page therefore sends InnerTube calls as simple requests: a `text/plain` body and no custom headers.
- The extension's declarativeNetRequest rule adds Origin, Referer and X-Origin.
- Client name, version and visitor data all travel in the JSON body.

**BotGuard's endpoints** (`www.youtube.com/api/jnn`) allow any origin, but refuse a YouTube Origin on the preflight.
- `Create` is a plain fetch from the page.
- `GenerateIT` goes through the extension (`yt.fetch`), so it carries YouTube's Origin, as in Kira, and as on Android, where BotGuard runs in a youtube.com WebView.

**Shaka's retries replay stale SABR requests.**
- googlevideo builds the SABR request body once, in a request filter, and Shaka retries reuse the same request object.
- Retries a minute later still carried the body built at 0.4 s, including the cold-start PO token.
- Shaka-level retries are therefore off. The failure callback restarts streaming, which builds a fresh body.

**PO tokens follow Android** (`PoTokenGenerator`):
- The token bound to the session's visitor data goes in the player request (`serviceIntegrityDimensions.poToken`), for the BotGuard clients only (YTMUSIC, WEB, MWEB).
- The token bound to the videoId is the streaming token: the SABR `streamerContext.poToken`, or `pot=` on direct URLs.
- VISIONOS takes neither, matching InnerTubeX.

**VISIONOS media requests carry no Origin or Referer.** InnerTubeX's `buildHeaders` sends nothing for VISIONOS, and the extension's rule 7 removes both for `c=VISIONOS` URLs.

**Shaka's DASH-only build has no `data:` URI plugin** (error 1000). The engine uses the compiled build.

## Open

**The web clients' streams still stop at ~60–70 s** (YTMUSIC over SABR, and YTMUSIC over direct URLs with `pot`).
- Their responses keep reporting stream protection status 2, "attestation pending", even though the minted token is real: 90 bytes, against 21 for a cold-start token.
- So our WebPO tokens aren't being accepted. Untested causes include the content binding (the player-request token is minted from the decoded visitor data), and whether YouTube now wants something beyond what Kira and googlevideo's example do. Kira itself (October 2025) no longer starts playback at all.
- This matters for signed-in playback in phase 5, where the web clients are the ones that carry an account.

**Age-restricted tracks need the user's own signed-in account** (confirmed by the user; phase 5). On the phone, InnerTubeX serves them through `WEB_REMIX_SABR`:
- authentication optional, ranked up (+18) for restricted content when the request is signed in;
- PO tokens: player request not required, streaming (GVS) optional.

So the signed-in path is WEB_REMIX with the user's cookies, which makes the web-client token problem above a phase 5 blocker.

**VISIONOS is "PROBE_ONLY" in InnerTubeX's catalog** (version 1.02). Android's automatic first choice is `VISIONOS_0_1`: version 0.1, osName `VISION_OS`, `RealityDevice14,1`, platform MOBILE, the music player endpoint. 1.02 works here today; switching to 0.1 needs YouTube.js to send a client version it doesn't define.

**SABR forward seeks past the buffer** were refused before the `pyv` fix. They weren't retested afterwards, because SABR no longer carries any of the 17 tracks.
