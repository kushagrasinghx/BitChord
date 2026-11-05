# Remote control (fork feature)

This fork adds a **LAN web remote** to BitChord: a small web server inside
the app that serves a player page any browser on the same Wi-Fi can open —
now playing, queue, transport, volume, and YouTube Music search — driving
this phone's player. It is the browser-shaped counterpart to the companion
app idea: instead of syncing playback to other phones, the music keeps
playing on this phone and the browser is just a remote.

Not to be confused with the built-in **Listen together**, which syncs
playback between BitChord installs through a hosted relay. The remote
needs no account, no relay and no internet — only the same LAN.

## Using it

1. In BitChord: **Settings → Remote control** → switch it on.
2. The row now shows the address and PIN, e.g.
   `Open http://192.168.1.20:8899 · PIN 4821`.
3. Open the address in any browser on the same Wi-Fi, enter the PIN and
   your name once (they are traded for a session token; the PIN itself
   is never kept by the page), and control playback.

The PIN is shared, not personal: everyone on the Wi-Fi can join with
the same PIN, each under their own name. Every connection gets its own
session, and songs queued from the web player are labelled with the
name that queued them — in the queue list and under the now-playing
track. (Songs queued in the app itself carry no label; the media
session has no per-item owner, so the labels live in the server and
last while the server runs.)

The queue is editable from the page: every row has **move up**,
**move down** and **remove** controls, and removing a song raises a
toast with **Undo** that puts it back at its old position, label
included. Because several people can edit at once, move and remove
name the song they mean, not just its position — if the queue has
shifted since the page last rendered, the server refuses the edit
rather than moving or removing the wrong song, and the page's next
poll shows the true queue. The restored label is the server's own
record of the removal, never a value the page sends back.

Search covers **songs, artists, albums and playlists**, grouped on
the page. Tapping an artist, album or playlist card opens its page —
the artist's songs (with their albums and singles as further cards),
the album's tracklist, the playlist's songs — and **Back** returns
to the results.

The ♪-lines button in the transport row opens a **lyrics viewer**
for the current track: the same lyrics the app finds (same sources,
same order), highlighted line by line as the song plays and
auto-scrolling with it when they are time-synced. Tapping a synced
line seeks there. Answers are cached per track while the server
runs; changing the lyrics sources or their order in Settings starts
the cache over.

On a wide screen — a laptop on the same Wi-Fi is a first-class
remote — the page lays out in two columns: player and lyrics on the
left, search and the queue on the right.

The server starts automatically on app launch if the switch was left on,
and stops when the switch is turned off. By default the PIN is
regenerated every time the server starts. Two settings change that,
both under the switch and both applied at the next start:

- **Remote PIN** — set your own PIN (4–8 digits). A custom PIN is
  always the PIN, so it never changes.
- **Persistent PIN** — with no custom PIN set, keep the generated PIN
  across restarts instead of minting a fresh one each time.

## How it works

- `remote/RemoteControlServer.kt` — a plain socket HTTP server (no new
  dependencies) on port **8899**, plus the `RemoteControl` lifecycle
  object. Serves the page and a small JSON API.
- `remote/RemoteBridge.kt` — a MediaController into `PlaybackService`,
  held while the server runs. Commands go through the media session —
  the same door the app's UI and the Wear bridge use — so a browser tap
  is indistinguishable from an in-app tap: Listen Together parties hear
  it, the notification follows, and queue/shuffle semantics stay the
  service's own (`playSongs`, `toggleShuffle`, `Song.toMediaItem()`).
- `assets/remote_player.html` — the whole player page in one file. It
  polls `/api/state` once a second. One column on phones; two columns
  (player + lyrics | search + queue) on screens 1080px and wider.
- The feature's pure decisions — the attribution walk, PIN choice,
  name sanitizing, browse-kind resolution — are unit-tested in
  `app/src/test/java/com/music/bitchord/remote/RemoteLogicTest.kt`.

### API

| Route | Auth | What |
| --- | --- | --- |
| `GET /` | – | the player page |
| `POST /api/unlock` `{pin, name}` | – | → `{token, name}`; send the token as `X-Remote-Token` afterwards (header only — tokens never travel in URLs). Each unlock mints its own session. Wrong PINs are throttled: 5 failures from one address within 10 minutes lock that address out for 10 minutes, 30 across all addresses lock everyone out for 5 (HTTP 429), and every failure is delayed |
| `GET /api/state` | token | playing, position/duration, current track, queue (≤200 shown, each item labelled `queuedBy` when a web user queued it; labels are tracked over the whole queue), shuffle/repeat, volume, `users` (names with songs in the queue), `you` (the caller's name) |
| `POST /api/command` | token | `{action}`: `play` `pause` `toggle` `next` `prev` `seek{ms}` `playIndex{index}` `playSong{song}` `addSong{song}` `addSongNext{song}` `addSongAt{index, song, beforeVideoId?}` `move{from, to, fromId?}` `remove{index, videoId?}` `volume{level 0..1}` `shuffle` `repeat`. The optional ids name the song the page means; on a mismatch the edit is refused, and for `addSongAt` the song is appended instead of landing mid-list |
| `GET /api/search?q=` | token | YouTube Music search via `YtMusicRepository` → `{songs: [song], artists: [card], albums: [card], playlists: [card]}` |
| `GET /api/browse?id=&kind=` | token | The page behind a card → `{kind, browseId, title?, subtitle?, songs: [song], sections: [{title, items: [card]}]}` (sections only on artist pages) |
| `GET /api/lyrics` | token | Lyrics for the current track → `{found, videoId, synced?, source?, lines: [{at, text}]}`; `pending: true` while the track's duration is still unknown |

A `song` is `{videoId, title, artist, thumbnailUrl, durationText}`.
A `card` is `{kind, browseId, title, subtitle, thumbnailUrl}`.

## Notes and limits

- Same-LAN only by design. Treat the PIN as a lock against strangers on
  shared Wi-Fi, not as real security: traffic is plain HTTP. The lock
  is at least a real one — wrong PINs are throttled and lock out (see
  the API table) — and request bodies are capped at 64 KB and refused
  before any auth check, so the port cannot be crashed or probed with
  oversized requests.
- The address shown in Settings is the Wi-Fi interface's, picked over
  any VPN tunnel interface, so the remote stays reachable on the LAN
  while a VPN is connected. (A VPN app that firewalls local traffic can
  still block it — that app needs its "allow LAN" option on.)
- The server lives in the app process. If Android kills BitChord (app
  swiped away and playback stopped), the remote stops with it.
- State updates are polled (1 s), not pushed; good enough for a remote,
  and it keeps the server trivially simple.
