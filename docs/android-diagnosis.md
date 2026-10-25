# Android diagnosis: 1.9-beta1

Investigated on 2026-10-25 using source history, APK resources, and DEX inspection. No emulator or device was launched. These are static findings, not measured frame timings.

## The build matters

The reported APK is:

```text
/tmp/claude-1000/-home-rtx-Desktop-BitChord/9690a072-c287-4c48-aa02-4290a30749f8/scratchpad/BitChord-1.9-prod-release-arm64.apk
package: com.music.bitchord
versionCode: 30
versionName: 1.9-beta1
debuggable: false
SHA-256: 419cc939197bbac8c4fe7ac070bdb8a83db425eb864025b153d2a956c902936d
```

The working branch, `feat/apple-glass-rim-and-vibrancy` at `fa1b9a89`, still declares version `1.8.1-beta1`. Its existing dev benchmark APK is a different build from the reported prod APK. The newer local branch, `feat/liquid-glass-apple-fidelity` at `ace7240a`, contains the 1.9 version change and the feature removal described below. The exact source commit used to build the prod APK was not independently established.

## 1. Missing global music-only preference: confirmed source removal

Commit `ba81c409e2d5a99e8ecacc5e0c02d41790af7ad5`, **full video support**, dated 2026-10-22, removed all of these:

- The Settings → Playback row and switch in `SettingsSheet.kt`.
- `AppSettings.preferMusicOnly`, its setter, preference key, and initialization.
- The automatic current-track audio conversion effect in `MainActivity.kt`.
- The preference-aware next-track prefetch and settings observer in `PlaybackService.kt`.
- The preference's string resources.

That commit is in the newer branch's ancestry and is absent from the current working branch's ancestry. The reported prod APK contains neither the `prefer_music_only` resource/key nor the `preferMusicOnly` property or setter. The local dev benchmark APK contains them. Its compiled Playback settings group registers the preference as its first row.

This explains the observed difference. It is not a dev/prod feature flag or a preference that starts hidden. The two APKs contain different source revisions. The newer build retains a per-track **Convert to audio / Convert to video** menu action, which does not replace the global preference.

**Repair:** restore the preference and its playback/prefetch behavior on the 1.9 source line while retaining video playback. Adding only the switch would leave the automatic behavior missing. The current working branch already has the old implementation, so adding another row here would not repair the reported APK.

Reproduce the history check:

```sh
git show ba81c409 -- app/src/main/java/com/music/bitchord/ui/screens/SettingsSheet.kt
git show ba81c409 -- app/src/main/java/com/music/bitchord/data/settings/AppSettings.kt
git show ba81c409 -- app/src/main/java/com/music/bitchord/MainActivity.kt
git show ba81c409 -- app/src/main/java/com/music/bitchord/playback/PlaybackService.kt
```

## 2. Root UI exceeds ART's normal compilation limit

Measured directly from the two APKs' DEX code items:

| Method | Reported prod 1.9 APK | Local dev benchmark 1.8.1 APK |
| --- | ---: | ---: |
| `MainActivityKt.BitChordApp-o3XDK20` | 15,000 code units; 269 registers | 17,518 code units; 286 registers |
| `NowPlayingScreenKt.NowPlayingScreen-cTH8XuI` | 5,975 code units | 5,776 code units |

The prod root count was independently cross-checked with the Android SDK's `dexdump`, which reports `insns size : 15000 16-bit code units`. Both APKs have R8-generated code; the reported prod APK is non-debuggable. Both also contain packaged baseline profile assets.

Android 16 ART's [compiler options](https://android.googlesource.com/platform/art/+/refs/heads/android16-release/compiler/driver/compiler_options.h) set the default huge-method threshold to 10,000 code units. Its [graph builder](https://android.googlesource.com/platform/art/+/refs/heads/android16-release/compiler/optimizing/builder.cc) skips methods above that threshold unless the compiler filter is `everything`.

Under those defaults, this root method cannot receive normal native compilation, even in the optimized release APK. This is a strong CPU performance suspect, not proof of a particular frame's duration. OEM/runtime flags and the actual compilation state on the user's phone were not inspected.

The source concentrates navigation state, playback state, many screen-specific flow collections, effects, callbacks, and library transformations in one root. Changes to any state read there can rerun that large composition. Even a simple visible page still participates in this root. The older dev artifact also exceeds the limit, so the problem is not unique to the latest glass implementation. This does not mean the entire UI recomposes on every scroll frame.

**Repair:** divide this into smaller, restartable, non-inline composables with narrower state subscriptions: page hosts, player host, menus/dialogs, and playback effects. Confirm the resulting release DEX sizes. An additional baseline profile alone does not bypass this threshold under the normal compiler filters.

## 3. Queue persistence can stall the UI and lifecycle transitions

Relevant source paths are unchanged between the checked-out and newer local branches:

- `playback/LastPlayed.kt`
- `playback/BoundedContextPersistence.kt`

`PlaybackService.reportProgress()` runs on the service's main-thread scope and calls `savePlaybackState()` every five seconds while playing. This updates both restart stores where applicable.

`BoundedContextPersistence` stores the full canonical context JSON in the same SharedPreferences file as the changing playhead. It permits up to **8 MB of JSON**. Although a progress update changes only a scalar and performs no JSON encoding itself, Android's [SharedPreferences implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/app/SharedPreferencesImpl.java) writes the complete preference map to XML and synchronizes the file. Updating the position therefore still rewrites the queue-containing file.

`LastPlayed.saveQueueImmediately()`, `LastPlayed.clearImmediately()`, and the equivalent bounded-context methods also use synchronous `commit()`. The playback service calls these at explicit queue boundaries on its main-thread path.

Android documents that pending [`apply()` writes can block the main thread during activity/service lifecycle transitions](https://developer.android.com/reference/android/content/SharedPreferences). That makes this a particularly relevant suspect for minimize/resume stutters. How often it causes a visible stall depends on the actual saved collection size, pending writes, and storage speed; none was measured on the phone.

**Repair:** separate the large context snapshot from frequently updated progress in storage, not just into different keys. Serialize and persist snapshots through a background writer, coalesce progress saves, and preserve the ordering/durability guarantees at queue replacement boundaries. Merely calling `apply()` from a worker does not remove its lifecycle flush behavior.

## 4. Rendering costs to isolate after those fixes

These are additional suspects, not measured GPU bottlenecks:

- The page uses `hazeSource`; the top-edge blur remains active when Liquid Glass is disabled unless **Reduce dynamic blur** is enabled. The progressive `TopFadeBlur` was introduced on October 23, so it can add cost but cannot by itself explain an earlier regression. Its sampling already uses a 0.33 scale.
- `GlassContrast.kt` samples the backdrop every **150 ms** while resumed and read by glass controls, even when the visible page is idle. It converts the sampled layer to a bitmap, copies hardware pixels when necessary, and reads pixels for CPU-side luminance calculations. Small dimensions reduce bandwidth but do not remove GPU synchronization. This is a recent glass-specific addition, separate from the older problems.
- `CanvasArtworkPlayer.kt` captures a `TextureView` bitmap on the main-thread composition scope. Non-Spotify motion-artwork modes request repeat captures every **3 seconds**. This is another possible periodic stall, limited to those artwork modes.
- Settings uses an eager `Column.verticalScroll`, so opening the screen composes and measures offscreen settings too. This is a screen-specific cost, not an explanation for all app-wide jank.

The ordinary player backdrop already has a precomputed small-image blur path. Playback position is already separated into `PlaybackPosition`, with a 500 ms poll that does not update the whole `PlayerState` on every tick. Those earlier performance problems should not be assumed to remain unchanged.

## Suggested order

1. Restore the complete music-only preference on the newer source line.
2. Split the root composition and verify release DEX sizes fall comfortably below ART's limit.
3. Decouple progress storage from the large queue snapshot and remove main-thread durable writes while retaining queue-boundary correctness.
4. Isolate blur and bitmap-readback costs on a physical phone using the release-equivalent build. Compare idle, scrolling, player opening/closing, and minimize/resume with a large saved playlist.

At the end of the diagnostic stage, only this document had been added; app source and existing user changes were untouched. The follow-up implementation is recorded below. No runtime frame-time improvement is claimed.

## Follow-up implementation

The user requested the first performance fixes and explicitly deferred the music-only preference. Work continued on `feat/liquid-glass-apple-fidelity` at `ace7240a`, carrying the existing user changes across from the older branch. The global music-only setting and the rendering implementations are unchanged.

### Composition

- Added a stable session state owner for ephemeral UI state. Navigation and Replay state retain their existing saveable lifetimes.
- Moved Home, Explore, Search, Library, History and lyrics subscriptions into the composables that consume them. Downloaded releases and local playlist transformations now run in the Library branch.
- Extracted the player sheet, Google login, settings dialogs, playlist/download dialogs, browse actions, sorting menus, update dialog, dismissal handlers and bottom bars into restartable composables. Queue notices now invalidate the bottom-bar host directly.
- Kept lyrics loading mounted independently so settings changes still reload the playing track when the player is closed.

### Playback persistence

`LastPlayed` and `BoundedContextPersistence` now use a shared serial background writer. The large JSON snapshot and small progress record occupy separate preference files. Queue conversion/encoding and disk commits run on that writer without holding the caller's state lock. Progress bursts are coalesced and do not encode or rewrite the queue snapshot. The radio restart capture converts only the existing bounded queue window.

Existing JSON and scalar preferences remain readable and migrate on the first write. A per-snapshot token prevents progress from an older queue being attached to a replacement if the process dies between the two file commits. Clears and replacements update memory immediately and keep disk ordering. Queue-boundary Media3 commands return a future that completes after the worker's durable barrier, preserving their acknowledgement semantics while leaving the main looper free. No `apply()` remains in these two restart stores.

### Validation

- No emulator or connected device was used during implementation and build validation. This work establishes code generation and storage behavior; physical-phone frame timings remain to be measured. Subsequent user testing is recorded below.
- All 19 persistence tests pass: 11 bounded-context tests, 3 LastPlayed integration tests and 5 writer tests. They cover legacy migration, bounded restoration, stale progress, coalescing, clear/replacement during encoding, durability barriers and an interrupted snapshot/progress pair.
- An earlier full unit run executed 1,110 tests: 16 failed and 1 was skipped. All failures were the existing `LocaleStringsTest` assertions for missing `direct_source_stream` and `find_your_mood` translations. The missing translations were confirmed in the unmodified branch; localization was left outside this change.
- The signed ARM64 production release builds successfully, including the Wear OS companion. Its root `Content-vz2T9sI` is **8,890 code units / 205 registers**, compared with the reported APK's original **15,000 / 269**. The `BitChordApp` wrapper is 310 units; no replacement giant method was created. All app methods inspected remain below 10,000 units, and the existing wildcard baseline profile includes the new hosts. Direct root flow subscriptions fell from 68 to 38.
- Build environment: JDK 21 from the existing local installation, cached dependencies in offline mode, and the existing SDK 37.0 package selected through a temporary Gradle init script. A temporary task-input override also accommodates the watch bundler's hard-coded output path when Android Studio ABI injection selects the intermediates directory. The local release signing override uses the same debug certificate as the reported APK and is not written into project build configuration. `android.injected.testOnly=false` is explicit so the delivered APK can be installed from a file manager.

### Physical-phone result (2026-10-26)

The user installed the delivered APK and reported no noticeable improvement in smoothness: jank and frame drops persist. Any slight difference was indistinguishable from noise. The smaller compiled root and passing persistence tests establish structural changes, not a demonstrated frame-time improvement; the primary jank remains unresolved.

These performance changes are retained on `perf/android-smoothness` for further investigation. The music-only preference remains deferred.

### Further physical-phone checks

Install the performance APK over the reported 1.9 build to retain the real saved queue and settings. Compare Home/Library scrolling, player opening and closing, and minimize/resume while a large playlist is playing. Exercise radio start/queue replacement, clear, shuffle/repeat and relaunch to check restart behavior. Leave the same blur and animation settings in place for the first comparison. Frame-time gains cannot be established by the unit tests or DEX inspection alone.


### Delivered artifact

- [BitChord-1.9-performance-arm64.apk](../output/BitChord-1.9-performance-arm64.apk): `com.music.bitchord`, version `1.9-beta1` / code 30, ARM64, non-debuggable, test-only flag disabled. Package and certificate match the reported APK, allowing an in-place update.
- SHA-256: `b16c08e431b5c50636ddca4db7381e6d1fadad4b223ac9434403cf53102cbc41`.
- [Build details](../output/performance-build.json) and [compiled DEX sizes](../output/performance-dex-sizes.json).
