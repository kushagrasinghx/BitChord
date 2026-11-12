# BitChord for Wear OS

A watch remote for the BitChord phone app: now playing over the player's mesh
backdrop, play/pause on the cover with a progress ring (hold it to seek), skip,
Love where the phone offers it, Up Next, shuffle, repeat, the phone's volume on
the crown or bezel, an always-on player, and an Output picker covering the
phone's outputs and **this watch**.

The watch is never a second player with a queue of its own. As the speaker it
is a receiver, exactly like a Cast device: the phone keeps the queue (paused)
and decides every track, the watch plays the one it is told plus the next two,
reports back, and the phone's app, notification, widgets and the watch UI all
show the same state. Audio is read through the phone's own resolver and cache
over a Wearable channel, so the watch needs no network or account. Tracks the
phone serves as a DASH manifest can't play on the watch yet.

The APK is about 4.5 MB (most of it Media3, for the speaker), R8 is on for every
build type, and the app listens only while it is on screen or playing.

| Path | What it is |
| --- | --- |
| `protocol/.../WearProtocol.kt` | Paths, keys and commands. Compiled into both `:app` and `:wearos` |
| `src/main/java/.../data/PhoneLink.kt` | The watch's view of the phone: Data Layer state in, messages out |
| `src/main/java/.../ui/**` | Now Playing, Up Next, More, connection screens |
| `src/main/java/.../playback/**` | The watch as the speaker: `Speaker`, its player service, the phone-backed data source |
| `../app/src/main/java/.../wear/WearBridge.kt` | Phone side: publishes state, runs the watch's commands, `WatchSpeaker`, the stream server |
| `../app/src/main/java/.../playback/cast/RemoteSpeaker.kt` | The receiver seam Cast and the watch share |
| `../tv/src/main/java/.../wear/WearBridge.kt` | No-op stand-in, so the TV build carries none of it |

## Dev and prod

The Wearable Data Layer only connects apps that have the same package name and
the same signing key. So the watch app takes the phone app's ids:

| Watch build | Package | Pairs with |
| --- | --- | --- |
| `devDebug` | `com.dev.bitchord` | the phone's `devDebug` (debug key) |
| `prodRelease` | `com.music.bitchord` | the phone's `prodRelease` (release key) |

A watch build only finds a phone build of the same flavor **and** signed with
the same key. Published phone APKs are signed in CI with the key from
`secrets.KEYSTORE_BASE64`, which is not the keystore in a local checkout, so
the prod watch APK for those phones has to come from CI too. `android.yml`
uploads it as `bitchord-wear-prod-release-signed`.

## Build and install

```bash
./gradlew :wearos:assembleDevDebug :wearos:assembleProdRelease
```

APKs land in `wearos/build/outputs/apk/<flavor>/<type>/BitChordWear-*.apk`.
To install on a Galaxy Watch, turn on Developer options → ADB debugging and
Wireless debugging on the watch, then:

```bash
adb pair <watch-ip>:<pairing-port>
adb connect <watch-ip>:<port>
adb -s <watch-ip>:<port> install -r wearos/build/outputs/apk/dev/debug/BitChordWear-dev-debug.apk
```

The phone needs a BitChord build that includes `WearBridge` (anything from this
change on), from the same flavor and signed with the same key.

Debug builds are shrunk and non-debuggable, because an un-R8'd, debuggable
Compose app is noticeably slower on a watch CPU. To get a debugger back, pass
`-Pbitchord.wearDebuggable=true`.
