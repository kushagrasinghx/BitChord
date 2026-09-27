# BitChord — Listen Together

The party server behind **Listen together**: create a six-character code, share
it, and up to five signed-in devices listen to the same thing at the same time.
Anyone in the party can control the music.

Go + a WebSocket per device, no database, deployed as a single Render web
service.

```bash
go run .
go test -v ./...
```

---

## How devices stay in time

This is the part worth reading before changing anything. Nothing here is
synchronised by broadcasting "play now" — a frame that arrives 40 ms late on one
phone and 300 ms late on another would start them a quarter of a second apart,
and re-sending it more often would not fix that, only re-spread it.

Instead the server holds **a position and the server time that position was true
at**:

```jsonc
{
  "positionMs": 42000,          // where the party was...
  "anchorMs": 1757630001234,    // ...at this instant on the server's clock
  "isPlaying": true
}
```

Each device knows its own offset from that clock, so it can answer "where should
I be *right now*" locally, whenever it likes, without another round trip:

```
serverNow   = deviceNow + clockOffset
playhead    = positionMs + max(0, serverNow - anchorMs)      // while playing
playhead    = positionMs                                      // while paused
```

A delayed frame carries an anchor that is correspondingly further in the past,
so it still lands the device in exactly the right place. That is the whole
trick, and four things protect it:

1. **The clock offset is measured, not assumed.** Each device sends
   `{"type":"ping","clientMs":…}`; the server replies with that stamp untouched
   plus its own. Offset is `serverMs - (t0 + t1) / 2`, error is bounded by half
   the round trip, and keeping the sample with the *smallest* round trip is what
   makes this accurate over mobile data. Same idea as NTP. `GET /api/time` gives
   a client a first estimate before it has a socket.
2. **The server clock never jumps.** `clock/clock.go` reads the wall clock once
   and advances it monotonically after that, so an NTP step on the host cannot
   rewrite the anchor under a room full of phones at once.
3. **Resuming is scheduled slightly ahead.** Play and seek anchor
   `JAM_PLAY_LEAD_MS` (default 350 ms) into the future, so every device aims at
   one common instant and has time to buffer, instead of each starting whenever
   its own packet landed. Before that instant, `PositionAt` holds at the
   position rather than running backwards.
4. **The truth is re-stated on a timer.** Every `JAM_STATE_HEARTBEAT_MS`
   (default 5 s) each party is told again where it is, unprompted. Lost frames,
   a phone coming back from doze, an offset that has wandered — none of those
   announce themselves, so correctness must not depend on anyone asking.

The server's state is the truth. Devices *report* their real playhead
(`{"type":"report"}`), but that is only logged — a party is never averaged
towards a straggler on a bad connection.

## Identity, and what it is worth

Creating or joining requires `userId`, `deviceId` and `displayName`, which the
app takes from the signed-in Google/YouTube profile. That is what puts real
names and avatars in the member list on every device.

**The server cannot verify that claim**, and is written on the assumption it
never will: it holds no Google credential, and an Innertube round trip per join
would be both slow and another way to get rate-limited. What *is* server-held is
the **token** minted on join — unguessable, returned exactly once, and required
on every call afterwards. So a member can lie about who they are; they cannot
act as a member they are not, or join a party whose code they were not given.

If that trade ever stops being acceptable, the place to change it is
`Party.Join`: have the client send a short-lived proof from the account layer and
verify it there. Nothing above that function would need to move.

## Capacity and lifecycle

- **Five devices** (`JAM_MAX_MEMBERS`), counted per device rather than per
  account — the same person on a phone and a tablet is two streams.
- **Rejoining reclaims a slot.** Reinstall, force-stop, a socket the grace
  period outlived: all come back through the same path, so a party of five does
  not fill with ghosts of the same five devices.
- **A dropped socket is not a departure.** The membership survives
  `JAM_DISCONNECT_GRACE_MS` (default 45 s), which is what makes a tunnel or a
  screen-off invisible to everyone else.
- **The host is privileged only if it says so.** By default anyone may control
  the music and the role just decides who inherits it, passing on when the host
  leaves rather than ending the party. A host may also set `hostOnlyControl`,
  after which the server refuses every playback and queue action from anybody
  else — see the control actions below. The setting belongs to the party, so a
  new host inherits it rather than being locked out of a party they now own.
- Parties are swept when nobody has been connected for
  `JAM_EMPTY_PARTY_TTL_MS`, and unconditionally after `JAM_PARTY_MAX_AGE_MS`.

## API

All bodies and frames are camelCase — the only client is the Android app, and
matching Kotlin's naming keeps `@SerialName` off every field.

### REST

| | |
|---|---|
| `GET /healthz` | Render's health check. |
| `GET /api/time` | `{"serverMs":…}` — a clock sample before the socket exists. |
| `POST /api/parties` | Create a party and join it as host. Body: `userId`, `deviceId`, `displayName`, `avatarUrl?`. → `201` with `code`, `token`, `you`, `party`. Creation is capped per IP and by the service-wide party limit. |
| `POST /api/parties/{code}/join` | Same body. `404` unknown code, `409` full, `422` no identity. The code is normalised first, so lower case, spaces, and `O`/`I`/`L` typed for `0`/`1` all work. |
| `GET /api/parties/{code}` | Full snapshot. Needs `Authorization: Bearer <token>`. |
| `POST /api/parties/{code}/leave` | Give up the slot. Needs the bearer token. |

### WebSocket — `/ws/parties/{code}`

Pass the party token in `Authorization: Bearer <token>` during the WebSocket
handshake. Tokens are intentionally not accepted in the URL, which prevents
them from being recorded in common HTTP access logs.

Client → server:

```jsonc
{"type": "ping",    "clientMs": 1757630000000}
{"type": "sync"}                                   // re-send me the state
{"type": "syncQueue"}                              // my queue copy is stale
{"type": "report",  "positionMs": 42210, "isPlaying": true}
{"type": "control", "action": "play",        "positionMs": 42000}
{"type": "control", "action": "pause",       "positionMs": 42000}   // positionMs optional
{"type": "control", "action": "seek",        "positionMs": 90000}   // required
{"type": "control", "action": "setTrack",    "track": {…}, "positionMs": 0, "isPlaying": true}
{"type": "control", "action": "setQueue",    "queue": [{…}], "queueIndex": 0}
{"type": "control", "action": "queueAdd",    "tracks": [{…}], "playNext": false}
{"type": "control", "action": "queueRemove", "videoId": "…"}
{"type": "control", "action": "queueClear"}
{"type": "control", "action": "queueMove",   "fromIndex": 2, "toIndex": 5, "videoId": "…"}
{"type": "control", "action": "next"}
{"type": "control", "action": "previous"}
{"type": "control", "action": "setHostOnlyControl", "enabled": true}   // host only
```

`setMaxMembers`, `kick` and `setHostOnlyControl` are host-only and answer
`403 host_only` to anybody else. While `hostOnlyControl` is on, so is every
action above them in that list — `play`, `pause`, `seek`, `setTrack`,
`setQueue`, `queueAdd`, `queueRemove`, `queueClear`, `queueMove`, `next`,
`previous` and `setAutoplay` — which is what makes the restriction real rather
than a matter of the app hiding its own buttons.

The flag travels on the `members` frame, alongside `maxMembers`, and in the
snapshot; it is deliberately not on the state frame, which rides the heartbeat.
Absent from either — an older server — it reads as `false`, which is the
behaviour the feature shipped with.

A `bye` frame carries a `reason`: `left` when a member gives up their own slot,
`kicked` when the host removes them. The server holds no grudge — a removed
device may rejoin immediately as far as it is concerned — but the reason lets a
client shut its own door, which is what the Android app does for 24 hours.

A `track` is `{videoId, title, artist, thumbnailUrl, durationMs, fromAutoplay}` — enough to
identify and to *show* a song, and nothing more. Stream URLs, sources, quality
and download state stay each device's own business, so two people in a party can
be on different sources at different bitrates and still be in the same place.

Playback state also carries `startedBy` and `startedByName`, identifying the
member who selected the current track. They change only with the track;
`updatedBy` continues to identify the most recent play, pause, seek, or track
control. Keeping the name in the state preserves the attribution if that member
leaves before the song ends.

Server → client:

```jsonc
{"type": "welcome", "you": {…}, "party": {…}, "serverMs": …}
{"type": "pong",    "clientMs": …, "serverMs": …}
{"type": "state",   "playback": {…}, "serverMs": …}
{"type": "queue",   "queue": {"seq": 3, "index": 1, "items": [{…}]}, "serverMs": …}
{"type": "members", "members": [{…}], "maxMembers": 5, "serverMs": …}
{"type": "error",   "error": "rate_limited", "message": "…"}
{"type": "bye",     "reason": "left"}
```

Every mutation bumps `playback.seq`. **Clients must ignore any state whose `seq`
is not greater than the last one they applied** — that is what makes two people
hitting pause at the same moment settle instead of oscillate. A control is
broadcast to the sender too, so the controlling device re-anchors off the same
frame as everyone else rather than running on a state it predicted locally.

### The queue travels separately

**`state` does not contain the queue.** It carries `queueSeq`, `queueIndex` and
`queueLength`, and that is all. The list itself arrives:

- **whole, in a snapshot** — `welcome` and `GET /api/parties/{code}` both carry
  `party.queue`, because a device that has just arrived has no other way to
  learn it;
- **on change** — a queue control broadcasts a `queue` frame *before* the
  `state` frame, so nobody ever holds a state pointing at an index in a list
  they have not been given;
- **on request** — a client whose `queue.seq` disagrees with the `queueSeq` in a
  state frame sends `syncQueue`. That is the self-healing half: a missed queue
  broadcast is noticed on the very next heartbeat rather than lived with.

This split is the single biggest thing keeping the service cheap. The state
frame goes to every device every few seconds forever, and the queue is the one
field in it that is both large and almost never different — a 50-track queue on
the heartbeat was roughly twenty times the bytes of everything else combined,
paid continuously, on other people's mobile data. Adding a field to `ToWire`
that grows with the queue puts all of that straight back; put it in
`QueueToWire` instead.

### Shared queue limits and delta moves

To keep memory, bandwidth, and latency strictly bounded:

- **Upcoming limit** (`JAM_MAX_UPCOMING_QUEUE`, default 25): A party holds at
  most 25 upcoming songs (excluding the active track). Additions past 25 are
  clamped or rejected with `queue_full`.
- **Delta actions**: Rather than replacing the whole array on every gesture,
  clients send lightweight deltas (`queueAdd`, `queueRemove`, `queueClear`,
  `queueMove`).
- **Race condition resilience**: `queueMove` carries an optional `videoId`
  alongside indices. If concurrent network lag (e.g. 800 ms mobile latency) or
  a track completion shifts indices, the server resolves `fromIndex` from the
  track's `videoId` under the party lock, preventing misplaced drops.

## Deploying to Render

The blueprint is `render.yaml`, but Render reads blueprints from the repository
root and this one lives in `backend/`. Either copy it up a level, or create the
service by hand — which is one screen:

- **New → Web Service**, connect this repo
- **Root Directory** `backend`
- **Runtime** Go
- **Build Command** `go build -o server .`
- **Start Command** `./server`
- **Health check path** `/healthz`
- **Instances** 1

Then point the app at `https://<service>.onrender.com`, by adding this to
`local.properties` at the repository root:

```properties
LISTEN_TOGETHER_SERVER=https://<service>.onrender.com
```

That becomes `BuildConfig.LISTEN_TOGETHER_SERVER` and seeds the address box on
the Listen Together screen. It is only a default — anything typed into that box
wins and persists, so running your own copy of this server never means editing
the build. `local.properties` is gitignored, so a fresh checkout builds with an
empty default and simply asks for an address the first time the screen is
opened.

Two things about Render specifically:

- **Keep it to one instance.** A party lives in the memory of the instance
  holding its WebSockets, so a second instance is a second, disjoint set of
  parties, and a join lands in whichever one the load balancer picked. Outgrowing
  that is a Redis pub/sub swap behind `PartyStore` — nothing above it moves.
- **The free plan spins down after ~15 minutes idle**, which closes every
  WebSocket and drops every party, and the next request then waits ~30 s for a
  cold start. Fine for development; for real use this wants the paid instance
  type, where the process stays up (or a free uptime monitor pinging `/healthz`
  every 12 minutes).

### Environment

Every one of these is optional — `config/config.go` carries the same defaults.

| Variable | Default | |
|---|---|---|
| `JAM_MAX_MEMBERS` | `5` | Devices per party. |
| `JAM_STATE_HEARTBEAT_MS` | `5000` | How often the truth is re-stated. |
| `JAM_PLAY_LEAD_MS` | `350` | How far ahead a resume is scheduled. |
| `JAM_MAX_UPCOMING_QUEUE` | `25` | Maximum upcoming tracks in shared queue. |
| `JAM_MAX_QUEUE_LENGTH` | `26` | Hard limit on total queue length (`1 + MaxUpcomingQueue`). |
| `JAM_DISCONNECT_GRACE_MS` | `45000` | How long a lost socket keeps its slot. |
| `JAM_EMPTY_PARTY_TTL_MS` | `120000` | How long an empty party survives. |
| `JAM_PARTY_MAX_AGE_MS` | `43200000` | Hard ceiling on a party's life. |
| `JAM_CONTROL_RATE_PER_SECOND` | `25` | Per-member control ceiling. |
| `JAM_FRAME_RATE_PER_SECOND` | `30` | Per-member WebSocket frame ceiling, including ping and sync frames. |
| `JAM_MAX_PARTIES` | `50` | Maximum concurrent parties. Conservative default for Render Free (250 sockets at five devices each). |
| `JAM_CREATE_RATE_PER_MINUTE` | `2` | Maximum new parties per client IP per minute. |
| `JAM_RATE_LIMIT_MAX_ENTRIES` | `10000` | Maximum tracked client IPs before new creations are rejected, preventing the limiter itself from growing without bound. |
| `JAM_REQUEST_MAX_BYTES` | `16384` | Maximum REST request size. |
| `JAM_WEBSOCKET_MAX_BYTES` | `16384` | Maximum incoming WebSocket message size. |
| `JAM_CONNECTION_IDLE_MS` | `900000` | Close a WebSocket that sends no message for 15 minutes. |
| `JAM_ALLOWED_ORIGINS` | *(none)* | Comma-separated browser Origin allowlist. Native clients send no Origin and remain supported. |
| `JAM_TRUST_PROXY` | `false` | Read `X-Forwarded-For` for rate limiting only when a trusted proxy terminates requests. |
| `PORT` | `8000` | Port the server listens on. |

## Layout

```
clock/clock.go        The one clock, monotonic so anchors never jump
codes/codes.go        Six-character codes, and reading a mistyped one charitably
config/config.go      Environment knobs
hub/hub.go            Who holds a socket, and how a frame reaches everyone
party/party.go        Party, Member, PlaybackState — the sync rule, in-memory state
protocol/protocol.go  Action and frame-type names
main.go               REST routes, the socket loop, the heartbeat ticker
main_test.go          REST endpoints and WebSocket integration tests
party/party_test.go   Capacity limits, queue limits, delta moves, step
```
