# Fixtures

Real responses, captured by `node web/scripts/capture-fixtures.mjs [innertube|lyrics]`. Parsers are tested against these files, never against hand-written JSON (build prompt §0).

`index.json` records, for every capture:

- the URL and status;
- `acao`: the `Access-Control-Allow-Origin` header a page at `http://localhost:5173` receives.

A failed capture never replaces an earlier good one: the good file stays, and the failure is noted as `lastFailure` on its entry.

All captures are anonymous: no cookies, no account. The probe track is Radiohead, "Let Down" (*OK Computer*, 4:59).

## InnerTube (`innertube/`, WEB_REMIX)

| File | Request |
|---|---|
| `browse-home.json`, `browse-explore.json`, `browse-new-releases.json`, `browse-moods-and-genres.json` | `browse` with `FEmusic_*` |
| `search-suggestions.json` | `music/get_search_suggestions` with `input: "radioh"` |
| `search-all.json`, `search-songs.json`, `search-albums.json` | `search`, with no filter / with the `SearchFilter` params from `shared/.../model/Models.kt` |
| `browse-artist.json`, `browse-album.json`, `browse-playlist.json` | `browse` with the first `UC…` / `MPREb_…` / `VL…` id found in the searches |
| `next-radio.json` | `next` with `playlistId: RDAMVM<videoId>`, `isAudioOnly` |
| `player-web-remix-no-potoken.json` | `player` with **no PO token**: `UNPLAYABLE`, "The page needs to be reloaded." The baseline that phase 3 has to beat |

### How the browser must call youtubei

- youtubei answers a CORS **preflight** with **400**, even with a YouTube `Origin`. DNR can rewrite headers but not a status code, so a preflighted request can never succeed.
- A CORS **simple** request does work: `POST` with `Content-Type: text/plain;charset=UTF-8` and no non-safelisted headers returns `200` JSON. The capture script sends exactly that.
- So in the browser:
  - visitor data and the client name/version travel in the JSON body's `context.client`, never as `X-Goog-Visitor-Id` / `X-YouTube-Client-*` headers;
  - the extension's DNR rule sets `Origin`, `Referer` and `X-Origin`, and adds `Access-Control-Allow-Origin` to the response.

## Lyric providers (`lyrics/`)

The **Route** column is how the web app reaches each provider:

- **direct**: the response carries a usable ACAO, so a plain `fetch` works with no extension.
- **extension**: `lyrics.fetch` through the extension, after the host is granted once in lyric settings.

| # | Provider | File(s) | Status | ACAO | Route |
|---|---|---|---|---|---|
| 1 | lrc.red | `lrc-red-search.json`, `lrc-red-document.ttml` (by ISRC from Bini) | 200 | `*` | direct |
| 2 | BiniLyrics | `binilyrics.json` (gives `isrc` and `lyricsUrl` → lrc.red) | 200 | `*` | direct |
| 3 | BetterLyrics | `betterlyrics.json` | 200 | echoes origin | direct |
| 4 | BetterLyrics Portato | `betterlyrics-portato.json` | 200 | echoes origin | direct |
| 5 | PaxSenix (Apple) | `paxsenix-apple.json` (by Apple id `1097861834`) | 200 | `*` | direct for the lyrics. The Apple id search goes to `amp-api.music.apple.com` with a token scraped from the Apple web player and `Origin: music.apple.com`: **extension** |
| 6 | PaxSenix: Spotify | (not captured) | | | needs the user's API key |
| 7 | PaxSenix: Musixmatch | (not captured) | | | needs the user's API key |
| 8 | LyricsPlus | `lyricsplus-lyricsplus.prjktla.my.id.json` (first mirror that answered) | 200 | `*` | direct; the other mirrors are untested until one fails |
| 9 | Unison | `unison.json` | 200 | echoes origin | direct |
| 10 | YouTube captions | (captured with the player in phase 3: `get_transcript` needs a playable session) | | | extension (youtubei rules) |
| 11 | YouTube Music lyrics tab | `youtube-music-lyrics-tab.json` (`MPLYt…` browse from `next`) | 200 | none | extension (youtubei rules) |
| 12 | KuGou | `kugou-song-search.json` (0 hits for `"Radiohead - Let Down"`), `kugou-lyric-search.json`, `kugou-download.json` | 200 | `acsing.kugou.com` | extension |
| 13 | LRCLIB | `lrclib-get.json`, `lrclib-search.json` | 200 | `*` | direct. It rate-limits by IP (429s during capture), so the client must back off |
| 14 | Musixmatch | (not captured: needs a usertoken signed with a secret scraped from its web JS; capture when the provider is ported, phase 8) | | | extension |
| 15 | Genius | `genius-search.json` | 200 | none | extension |
| — | SimpMusic (hidden) | `simpmusic.json` | 403 | none | extension |
| — | Google Translate | `google-translate.json` (429) | 429 | none on the 429 | the script got 429 on every run while plain curl got 200; possibly the `Origin` header. Re-check in phase 8 |

The table above describes **this** capture. Rerun the script before porting a provider: hosts change their CORS headers without notice.

## Not captured yet, and why

- **A SABR exchange** (`application/vnd.yt-ump`). It needs a PO token from BotGuard and a playable `player` response. Both run in the browser (BotGuard has to run in a real browser), so the exchange is captured from the page in **phase 3**, with the extension's googlevideo rule in place.
- **An addon manifest, search and stream.** Addons are user-supplied, and the maintainer's own addon URL is personal and token-bearing, so it must never be committed. Capture by running the addon capture with the base URL from an environment variable, then commit only the responses after checking they hold no token.

## Spotify (`spotify/`)

Captured with an anonymous web-player token (no `sp_dc`), minted the way `extension/lib/spotify.ts` mints one: the TOTP secret and the query hashes are read from the live web player bundle. The token is never saved.

| File | Request |
|---|---|
| `web-player.json` | what the bundle declared: client version, secret versions, hashes for `libraryV3`, `fetchPlaylist`, `fetchLibraryTracks`, `canvas`, `searchTracks` |
| `fetch-playlist.json` | `fetchPlaylist` for "This Is Kanye West" (25 tracks), an editorial playlist anyone can read |
| `search-tracks.json` | `searchTracks` for "Let Down Radiohead" (the web player's own search; `api.spotify.com/v1/search` answers 429) |
| `canvas-anonymous.json` | `canvas` for "Espresso": `null` anonymously. Signed in, the same query returns a `canvaz.scdn.co` mp4 |

The library, Liked Songs and profile shapes were read from the user's own session in the browser and match `SpotifyLibrary.kt`'s parsers. They are not saved here, because they hold the user's data.
