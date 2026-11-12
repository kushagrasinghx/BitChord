# Spotify and Discord (phase 9)

Prompt §7. Both live in the extension; the page only gets results.

## Spotify

`extension/lib/spotify.ts`, with the app side in `app/src/data/spotify/`.

### Tokens

The user's own open.spotify.com session. `sp_dc` stays in the browser's jar; the tokens minted from it stay in the extension.

- `/api/token` is signed with the web player's TOTP, as `DesktopSpotifyToken` does it (SHA-1, 30 s steps, the secret's character codes XORed with `index % 33 + 9`).
- The secret is read from the web player's bundle, `{secret:'…',version:N}`, newest first. The desktop's table had fallen behind (v12; the live one was **v61** on 2026-10-16). The table stays as the fallback. A `totpVerExpired` answer rereads the bundle once.
- `sp_dc` is sent as a `Cookie` header from a session rule (id 303) that only matches the extension's own `/api/token` request, and only for as long as that request takes.
  - Measured: `credentials: 'include'` from the worker didn't carry it under any SameSite setting (Chromium headless). A bogus `sp_dc` sent this way is answered 401, so the header route provably reaches Spotify.
  - The same route works in Firefox.
- Every use checks that the cookie is still the one the token was minted from. `cookies.onChanged` didn't fire in testing, so this check, not the event, is what keeps an account switch safe.
- The client token is `SpotifyToken.clientToken`. It's best-effort; requests go without it if minting fails.
- Android mints in a WebView because a self-signed token was refused downstream there. From a real browser the self-minted token reads the library, liked songs and canvases (checked in the user's Chrome: 32 playlists, 94 liked songs, a canvas for "Espresso").

### Queries

All go through Pathfinder (`api-partner.spotify.com/pathfinder/v2/query`). Hashes are read live from the bundle; the Kotlin constants are the fallbacks.

| Operation | Use | Where its hash lives |
|---|---|---|
| `libraryV3` | the library's playlists (Liked Songs is added first, as on the phone) | main bundle |
| `fetchPlaylist` | a playlist's tracks and cover | main bundle |
| `fetchLibraryTracks` | Liked Songs | main bundle |
| `profileAttributes` | the account's name and avatar | main bundle |
| `canvas` | a track's Canvas (`SpotifyCanvasQuery`) | main bundle |
| `searchTracks` | finding a YouTube track's Spotify counterpart, for its canvas | the `xpui-routes-search` chunk, found through the bundle's chunk table |

- On 2026-10-16 the bundle declared new hashes for `libraryV3` and `fetchPlaylist`; the old library hash still answered.
- "PersistedQueryNotFound" rereads the bundle once.
- `api.spotify.com/v1/search`, which `DesktopSpotifyCanvas` used, answers the web player's token with 429, signed in or not. It isn't used.

### In the app

- **Sidebar.** A Spotify section, once signed in: All Spotify Playlists (`/spotify`), then each playlist.
- **A playlist** (`/spotify/playlist/:id`) is an ordinary playlist page, as `MainViewModel.openSpotifyPage` makes it.
  - Spotify's rows go up at once, marked as still matching.
  - Six at a time, each is matched to YouTube Music (`SpotifyImporter.matchTrack`): the `TrackMatcher` best of a Songs search for "title artist", else the first hit.
  - A row that finds nothing stays listed, dimmed, and can't be played; the queue skips it.
  - Rows are kept per account and playlist for the session. Matching pauses while the page is closed.
- **Import to YouTube Music** makes a private playlist of the matched songs (InnerTube `playlist/create`, as `YtMusicRepository.createPlaylist`). It asks once before writing to the account, and only appears when signed in to YouTube Music.
- **Canvas** plays behind the Lyrics panel, dimmed, muted and looping, and pauses with the music. It isn't looked up at all with reduced motion, on Save-Data or cellular connections, or with the Canvas setting off (Extension page → Spotify).
- **`app/src/data/match/trackMatcher.ts`** is a port of `TrackMatcher.kt`, tested with the cases from `SourcesTest.kt`. The addons phase will use it too.

## Discord

`extension/lib/discord.ts`, behind the build flag `DISCORD_PRESENCE`.

- **The flag.** On for the site's zips. A Web Store build (`BITCHORD_STORE_BUILD=1`) leaves it out unless `DISCORD_PRESENCE=1`. Without it there's no `scripting` permission, no discord.com optional permission, no connect page, and no `discord` capability.
- **Connecting.**
  - "Connect Discord" (the app's Extension page or the popup) opens `discord.html`.
  - Its button asks for `https://discord.com/*`.
  - It then reads the token from a discord.com tab, opening one if needed, with `DiscordLoginScreen`'s iframe trick in the page's own world. It polls for up to two minutes, which covers signing in from scratch.
  - Discord is asked who the token belongs to (`users/@me`), and the token is kept in `storage.local`. The page never sees it.
- **The gateway** is a port of Kizzy's `DiscordWebSocket`:
  - connect, pump, and back off from 1 s doubling to 60 s;
  - a heartbeat watchdog;
  - resume with replay of the last presence;
  - no resend of a presence the session already has;
  - close code 4004 ends retries.
- **Identity.** It identifies as Discord's **web** client (`browser: Chrome`/`Firefox`), not Android or desktop, since the socket carries the browser's own User-Agent.
- **Keeping the worker awake.** While the socket is up, an extension API call every 20 s keeps the MV3 worker from sleeping (Chrome 116+ also counts socket traffic).
- **The card** is `DiscordRPC.updateSong`:
  - details are the title (with a `[1.25x]` rate tag), state is the artist;
  - timestamps have the rate divided out;
  - the two buttons support `{song_name}`, `{artist_name}` and `{album_name}`;
  - artwork goes through Discord's external-assets mirror, keeping the raw URL if that fails;
  - `application_id` is set whenever there are buttons.
- **When it publishes** (`app/src/playback/discordPresence.ts`, the moments `PlaybackService` uses):
  - a track change, a resume, a seek, or an options change publishes;
  - a pause clears the card;
  - buffering and the gap between tracks are ignored.
- **When it stops.** When the last BitChord page has been gone for 30 s, the card is cleared and the socket closed. A cleared card with nothing new for 5 minutes closes the socket too.
- **Options** are kept in the app (Extension page → Discord): activity type, name, status, lead with the song, and the two buttons with their text. A one-line warning says the token use is against Discord's terms.

## Verified

- **Spotify, in the user's Chrome** (page context, before the extension had it): a self-minted token is non-anonymous and reads the library and a canvas.
- **Spotify, in Chromium and Firefox with the extension:**
  - the bridge reports both capabilities;
  - signed out reads as signed out;
  - a bogus `sp_dc` reaches Spotify (401) and is reported as an expired session;
  - the per-request cookie rule is gone afterwards.
- **Spotify pages, with the bridge stubbed from the captured "This Is Kanye West" playlist** (`fixtures/spotify/fetch-playlist.json`) and real YouTube Music search:
  - all 25 rows matched;
  - a double-click plays that row with the playlist as the queue;
  - the sidebar and the grid render.
- **Discord gateway, against a local stand-in gateway:**
  - identify;
  - the activity payload;
  - no duplicate push;
  - heartbeats acknowledged;
  - RECONNECT resumes and replays;
  - a dropped socket heals and replays;
  - clear and close.

## Not done or not verified

- **The real Spotify account through the extension** (playlists, matching, Canvas) waits for the user to reload the extension at 0.4.0.
- **Import to YouTube Music** writes to the account and wasn't run.
- **Discord against the real gateway:** connecting needs the user's own click and account.
- **Canvas** only shows in the Lyrics side panel; the full-screen player stands on the artwork's mesh instead.
- **Importing a public playlist by link** (`SpotifyImporter.extractPlaylistId` and the embed page) isn't built; the library covers the signed-in case.
