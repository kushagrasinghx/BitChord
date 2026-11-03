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
3. Open the address in any browser on the same Wi-Fi, enter the PIN once
   (it is traded for a session token; the PIN itself is never kept by the
   page), and control playback.

The server starts automatically on app launch if the switch was left on,
and stops when the switch is turned off. The PIN is regenerated every
time the server starts.

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
  polls `/api/state` once a second.

### API

| Route | Auth | What |
| --- | --- | --- |
| `GET /` | – | the player page |
| `POST /api/unlock` `{pin}` | – | → `{token}`; send it as `X-Remote-Token` afterwards |
| `GET /api/state` | token | playing, position/duration, current track, queue (≤200), shuffle/repeat, volume |
| `POST /api/command` | token | `{action}`: `play` `pause` `toggle` `next` `prev` `seek{ms}` `playIndex{index}` `playSong{song}` `addSong{song}` `addSongNext{song}` `volume{level 0..1}` `shuffle` `repeat` |
| `GET /api/search?q=` | token | YouTube Music song search via `YtMusicRepository` → `{results: [song]}` |

A `song` is `{videoId, title, artist, thumbnailUrl, durationText}`.

## Notes and limits

- Same-LAN only by design. Treat the PIN as a lock against strangers on
  shared Wi-Fi, not as real security: traffic is plain HTTP.
- The server lives in the app process. If Android kills BitChord (app
  swiped away and playback stopped), the remote stops with it.
- State updates are polled (1 s), not pushed; good enough for a remote,
  and it keeps the server trivially simple.
