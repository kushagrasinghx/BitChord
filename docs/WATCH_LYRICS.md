# BitChord – Wear OS synced-lyrics feature: handoff and working instructions

> Paste this whole file as context. The user speaks **Turkish**: answer in Turkish, keep code/comments in English.
> You will NOT be able to compile or run anything unless your environment has Gradle + Android SDK. The user builds in
> **GitHub Codespaces (4-core)** and **GitHub Actions**; never claim code "works" before the user's compile/CI says so.

## 0. START HERE (instructions for the assistant)
**Situation.** The user's fork may be half-broken (some files were damaged by mistake). Do not trust its current state: rebuild it from scratch, then work on ONE branch.
1. Ask the user to run, in the repo (Codespace, or Ubuntu inside Termux `proot-distro`), with `pr_bundle.zip` uploaded next to it:
   `unzip -o pr_bundle.zip && bash restart_fork.sh --code <N> --yes` then `git push --force-with-lease origin main`.
   `restart_fork.sh` = fresh `upstream/main` + this feature (via `make_clean_pr.py`) + the fork's extras (CI workflow, version bump, Turkish strings, `docs/`), all in `main`. The old main is kept as `main-before-restart`. `<N>` must be higher than the versionCode installed on the user's phone (default 50).
2. Work **only on `main`**. Do **not** create or maintain a separate PR branch. The repo files are the source of truth; this document only describes them (`BITCHORD_SOURCES.md` is a snapshot for chats without repo access).
3. **When BitChord updates** (new upstream release): `git fetch upstream && git merge upstream/main` on `main`. The feature touches existing upstream files in only a few places (§5, hooks): if a merge conflict or a changed anchor appears there, re-apply the hook by hand. Then run the compile command below, fix errors, bump the version, push.
4. **Small additions** the user asks for later: edit in place, verify, commit with explicit paths (never `git add -A`), push `main`, and let Actions build the APK. Bump `extra["bitchordVersionCode"]` and `extra["bitchordBetaSuffix"]` in the root `build.gradle.kts` for every build the user will install (the phone offers a new watch app only when the version differs).
5. **Upstream pull request** (only if the user asks): `bash export_pr.sh` builds a throwaway branch `pr-export` from `upstream/main` with only the feature (no CI, version bump, Turkish strings or docs) and prints what to push. If the maintainer merges the feature, the user drops the fork and uses upstream releases (uninstall first: different signing key).
6. **Style.** Small steps. Full replacement files for anything large. Always tell the user the exact command that verifies the step. Never say something compiles or works without evidence from the user's build. The user speaks Turkish: reply in Turkish.
7. **Compile check** (what upstream CI runs): `./gradlew testDevDebugUnitTest --console=plain 2>&1 | grep -E '^e: |BUILD|FAILED'`; also `:wearos:compileProdDebugKotlin :app:compileProdDebugKotlin :tv:compileProdDebugKotlin`.

## 1. Goal
Add a **synced-lyrics screen to the Wear OS companion** of the open-source Android music app **BitChord**
(`kushagrasinghx/BitChord`, YouTube-Music-style client, Kotlin, Compose, KMP). User's fork: `Myrtle-tr/BitChord`.
Target device: Samsung Galaxy Watch 8 (round AMOLED) + an Android phone. Final aim: a **clean upstream pull request**
(PR #1048 was closed by the maintainer: "deletes the release workflow, adds duplicate root files, helper scripts and a
version bump. Open a clean PR with only the feature code"). User also builds a personal APK from the fork.

## 2. Repository and branches
Upstream: `kushagrasinghx/BitChord` (v1.9 at the time of writing). Fork: `Myrtle-tr/BitChord`.
| Branch | Purpose |
|---|---|
| `upstream/main` | the maintainer's; never push there |
| fork `main` | **the only working branch**: upstream + the feature + `.github/workflows/build-apk.yml` + version bump + `*.bak` in `.gitignore` + Turkish strings (`values-tr`) + `docs/WATCH_LYRICS.md` |
| `main-before-restart` | backup of the old fork main (history only) |
| `pr-export` | throwaway, created by `export_pr.sh` only when a PR is wanted |
PR #1048 was closed by the maintainer ("deletes the release workflow, adds duplicate root files, helper scripts and a version bump. Open a clean PR with only the feature code"): that is why `export_pr.sh` keeps CI, version and helper files out. Rules: never `git add -A`; do not edit `.github/` of upstream's own workflows; helper files in the repo root (zip, scripts) are never committed.

## 3. Project facts
- Modules: `:app` (phone, Media3 `PlaybackService`), `:shared` (KMP: `LyricLine`, `LyricsRepository`, …), `:sharedUi`, `:wearos` (watch), `:tv`, `:desktopApp`, `:desktopUi`.
- Versions: Gradle 8.11.1, **JDK 17** (Java 25 breaks the Kotlin DSL), AGP 8.10.1, Kotlin 2.3.20, compileSdk 36/37, minSdk 30 (watch). Compose Multiplatform 1.10.3. Wear Compose **Material3** 1.6.2 (+ foundation, navigation). **No Material 1.**
- Flavors `dev` (`com.dev.bitchord`) / `prod` (`com.music.bitchord`). Watch and phone must share applicationId **and** signing key for the Wearable Data Layer.
- `:app` builds the watch APK and **bundles it** (`bundleWearApk` → `assets/wear/BitChordWear.apk`); the in-app "Wear OS" installer sideloads it by wireless ADB. `wearos/protocol/` is a srcDir shared by `:app` and `:wearos` (holds the project's `WearProtocol.kt`); our protocol files live in `wearos/protocol/.../wear/lyrics/`.
- Phone already publishes `NowPlaying` to the watch (`WearBridge`, DataClient): `mediaId`, `positionMs`, `positionAt` (**phone wall clock**), `advancing`, `speed`, `durationMs`, colours. Watch side: `PhoneLink` → `NowPlaying` flow; `NowPlaying.positionAt(wallMs)` extrapolates. Watch commands already exist: `link::previous/next/togglePlay/seekTo(Long)`.
- Ambient/AOD: the app's own `Ambient.kt` (`LocalAmbient`, `AmbientPlayer`) covers every screen; our lyrics screen does not implement its own AOD.
- TV module compiles the phone's sources and has no Wear libs: it drops app files that have a **same-path file under `tv/src/main/java`** (rule-based `exclude { el -> ... }`, line ~494 of `tv/build.gradle.kts`). Hence the no-op `tv/.../wear/WearLyricsSync.kt` stub. Upstream CI (`check.yml`: Desktop Linux/Windows/macOS, Android `testDevDebugUnitTest`, Go) builds `:tv` too.

## 4. Architecture of the feature
**Phone** `app/.../wear/WearLyricsSync.kt` (object, started/stopped by `PlaybackService`):
- `publish(context, mediaId, title, artist, durationMs, lines: List<LyricLine>?, album, fromUi)` builds a `SyncedLyricsPayload` and puts it on the Data Layer (gzip JSON **Asset**). Identical repeats skipped via `lastPutKey`; the item must always hold the latest lyrics (an earlier "already delivered" gate was a bug).
- Lyrics the player picked/synced itself (`fromUi = true`) win over the service's own lookup for that track.
- Answers watch requests: lyrics re-send, translation/romanization (phone's `LyricsTranslation.translate/romanize`, target language **sent by the watch**), source list (probes every enabled source: 10 parallel, list "done" after ≤9 s, quiet retry for misses, debounced puts, prefetch when the watch screen is open), source selection, clock pings (reply includes the phone's real playhead, read on the main thread), mirrors `AppSettings.lyricsOffsetMs`.
**Watch** `wearos/.../wear/ui/`: `LyricsViewModel` (state, clock, DataClient/MessageClient), `LyricsScreen` (UI), `LyricLineItem` (line, gap dots, background vocal), `LyricsCache` (offline LRU, gzip files, 80 entries/12 MB).

### Protocol (`WearSyncPaths`, package `com.music.bitchord.wear.lyrics`)
| Dir | API | Path | Content |
|---|---|---|---|
| phone→watch | Data | `/bitchord/lyrics/payload` | `trackId`, `hash`, `lyricsAsset`, `updatedAt`; hash `"none"` = no synced lyrics |
| phone→watch | Data | `/bitchord/lyrics/extra` | `trackId`, `kind` (translation/romanization/original), `status` (ok/same/unavailable), `lang`, `texts[]` aligned to payload lines |
| phone→watch | Data | `/bitchord/lyrics/offset` | `offsetMs` (positive = lyrics later; shown position = playhead − offset) |
| phone→watch | Data | `/bitchord/lyrics/sources` | `trackId`, `ids[]`, `labels[]`, `kinds[]` (word/line), `current`, `done` |
| watch→phone | Msg | `/bitchord/lyrics/request` | `trackId` |
| watch→phone | Msg | `/bitchord/lyrics/transform` | `"trackId\nmode\nlang"` |
| watch→phone | Msg | `/bitchord/lyrics/sources/request` | `trackId` |
| watch→phone | Msg | `/bitchord/lyrics/select` | `"trackId\nsourceId"` |
| watch→phone | Msg | `/bitchord/lyrics/clock/ping` | 8 bytes: watch send time |
| phone→watch | Msg | `/bitchord/lyrics/clock/pong` | 29 bytes: t0 echo(8) + phone wall(8) + phone position(8) + advancing(1) + speed(4) |

### Payload JSON (compact keys)
top: `v,id,ti,ar,d(duration),h(hash),l[]`; line: `i(index),x(text),s,e,c(chorus),g(gap),o(sourceIndex or -1),y[](syllables)`, optional `bt,bs,be` (background vocal text/start/end); syllable: `t,s,e,a(charStart),b(charEnd)`.
`SyncedLyricsPayload.build(...)`: sorts lines, derives syllable char ranges (falls back to word timing for line-timed lyrics), flags chorus (repeated normalized text), keeps explicit blank lines as gaps and **invents gaps**: where a line's end is stated, silence ≥ 5 s; where it is not (line-timed sources) the sung time is estimated (110 ms/char, 2.5–8 s) and ≥ 6 s silence is required; intro gap ≥ 4 s. Hash = first 8 bytes of SHA-256 of the lines JSON.

### Clock / position (the hardest part)
Problem seen: lyrics ran a line ahead while playing but were right when paused → phone and watch **wall clocks differ** and `NowPlaying.positionAt` compares the phone's stamp with the watch's clock. Fix in `LyricsViewModel`: ping bursts (8 on open, 6 on track change, 4 on resume, then every 15 s while playing) → each pong is a **fix** on the phone's real playhead (taken at the middle of the round trip); position = median of the ≤5 lowest-RTT fixes run on to now; a fix off by > 800 ms from the held ones means a seek → start over; a stamp within 150 ms of "now" at arrival is a local PhoneLink stamp (no clock conversion). Fallback: min-RTT wall-clock offset. Visible position is blended (2.5 s) when the correction ≤ 500 ms, else snapped. Final position = base − `lyricsOffsetMs`. Ticker 60 fps while playing & visible, 1 Hz in ambient.

### UI behaviour
`ScalingLazyColumn` (anchor ItemCenter, autoCentering), crown scroll with 4 s snap-back, swipe left = next (right stays system back), tap a line = seek (`line.startMs + offset`), long press **900 ms** (via `LocalViewConfiguration`) = panel: close ✕, keep screen on (per song; also off after 30 s paused), text size, **lyrics source picker**, auto-sync status, app version. Controls row `[translate][prev][play][next][Latin]`: middle three at the bottom, the two outer ones raised 6 % of the height to follow the round rim. Fading uses `graphicsLayer { compositingStrategy = ModulateAlpha }` + top padding (plain `Modifier.alpha` made an offscreen layer that **clipped the raised buttons and their touch area**). Haptics through `view.performHapticFeedback` (no VIBRATE permission). Active line: neon-cyan syllable sweep, spring scale, 38 % alpha for others; translation lines sweep with the ORIGINAL line's progress; background vocal small under the line; gap = three dots that fill across the gap.

## 5. Files (final package names)
| Path | Role |
|---|---|
| `wearos/protocol/.../wear/lyrics/WearSyncPaths.kt` | protocol constants |
| `wearos/protocol/.../wear/lyrics/SyncedLyricsPayload.kt` | model, builder, JSON/gzip |
| `wearos/src/main/java/com/music/bitchord/wear/ui/LyricsViewModel.kt` | watch brain |
| `.../wear/ui/LyricsScreen.kt`, `LyricLineItem.kt`, `LyricsCache.kt` | UI + cache |
| `app/src/main/java/com/music/bitchord/wear/WearLyricsSync.kt` | phone sender |
| `tv/src/main/java/com/music/bitchord/wear/WearLyricsSync.kt` | no-op stub (TV) |
| `wearos/src/main/res/{values/strings_lyrics.xml, drawable/ic_lyrics*.xml}` | 21 strings (English; Turkish only on fork main), 3 icons |

Hooks in existing upstream files (all small, anchored text replacements; see `make_clean_pr.py`):
`PlaybackService.kt` (import; `onCreate`: `WearLyricsSync.start` + `playhead` lambda; `onDestroy`: `stop`; `loadLyricsForCurrentTrack`: two `publish` calls incl. `album = currentSong.albumName`), `MainViewModel.kt` (4 `wearPublishLyrics` calls + helper using `currentLyricsRequest`, `fromUi = true`), `WearApp.kt` (LYRICS route + `openLyrics`, `onSeek = link::seekTo`), `NowPlayingScreen.kt` (`PlayerActions.openLyrics`, 4th chip, smaller chips), `wearos/build.gradle.kts` (+`lifecycle-viewmodel-compose` same version as `lifecycle-runtime-compose`).

## 6. Workflow used with the assistant (lessons)
- The user is on a phone/tablet browser. **Pasting large files into the Codespaces editor crashes it** and corrupts indentation. Delivery that works: bundle all files in **one zip**, upload it to GitHub (`main`, commit message `[skip ci]`), then in the Codespace `git fetch origin && git show origin/main:pr_bundle.zip > pr_bundle.zip && unzip -o pr_bundle.zip`.
- `make_clean_pr.py` puts the feature on a checkout identical to `upstream/main` (moves the files, patches the hooks, writes resources + TV stub); `restart_fork.sh` calls it and adds the fork extras. Later edits: edit in place, or `bash update_files.sh` for files delivered in a zip (moves them, no logic).
- Verify before pushing: `./gradlew testDevDebugUnitTest --console=plain 2>&1 | grep -E '^e: |BUILD|FAILED'` (what upstream CI runs), plus `:wearos:compileProdDebugKotlin :app:compileProdDebugKotlin :tv:compileProdDebugKotlin`.
- Personal APK: Actions workflow `build-apk.yml` (fork `main` only): Temurin 17, **explicit `sdkmanager` NDK install with retries** (AGP's auto-download once delivered a corrupt zip), optional signing from secrets `KEYSTORE_B64/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD` (→ `assembleProdRelease`/`Dev`), else debug key; input `flavor` Prod|Dev and `force_debug`; artifact = phone APK containing the watch APK. Dev flavor installs next to the official app; prod needs the official one uninstalled (different signature).

## 7. Errors met and their fixes
| Symptom | Cause | Fix |
|---|---|---|
| Kotlin script `IllegalArgumentException: 25.0.4.1` | Java 25 | JDK 17 |
| `aarMetadata` unresolved | library-only block in an application module | removed |
| "Unexpected `plugins` block", unbalanced braces | build file pasted twice | restore upstream file; never overwrite `wearos/build.gradle.kts` (flavors, signing, versions live there) |
| `Unresolved reference 'shared'` | `:wearos` has no dependency on `:shared` by design | put protocol files in `wearos/protocol/` |
| Files named `%E2%80%8B…` | zero-width characters from copy/paste | strip from names and contents |
| Gradle daemon vanished | `-Xmx768m` from `~/.gradle/gradle.properties` | fresh 4-core Codespace; repo's own `-Xmx4096m` |
| Obsolete `WearLyricsBridge.kt` errors | leftover first design | deleted (phone already publishes NowPlaying; reuse it) |
| CI: `setup-android` "Failed to find package 'tools'" | action outdated | use runner SDK, install NDK by `sdkmanager` |
| CI: NDK "unknown archive" | corrupt download | explicit install with retries |
| CI: keystore password incorrect | secrets from two keystore generations | regenerate, test with `keytool -list`, update all 4 secrets |
| APK "not installed" | same package, other signature | uninstall official or use `dev` flavor |
| `semantics` / `contentDescription` unresolved | imports dropped | re-added |
| Material 1 imports, duplicate ambient, VIBRATE | didn't match project | Material3, `LocalAmbient`, view haptics |
| Lyrics a line ahead only while playing | wall-clock skew phone↔watch | ping fixes (§4) |
| Source switch not reaching watch | "already delivered" gate | item always holds latest; skip only identical repeat |
| New buttons never appeared | watch APK same versionCode → no update offered | bump version on fork `main` |
| Raised buttons clipped / untappable | `Modifier.alpha` offscreen layer + parent bounds | `ModulateAlpha` + top padding |
| No dots in mid-song instrumentals | line-timed sources don't state line ends | estimate sung time from text length |
| PR #1048 closed | workflow deleted, root files, scripts, bump | clean branch from upstream, `make_clean_pr.py` |
| Fork CI "Desktop · Windows": `invalid path '.github/workflows/build.yml\n'` | a workflow file whose NAME ends in a newline (created in GitHub web UI) | remove it; gone after resetting `main` to `lyrics-bridge` |
| Fork CI "Android": `:tv:compileDevDebugKotlin` unresolved Wearable | TV compiled the phone-only `WearLyricsSync` | TV stub at the same path (rule-based exclude drops the app's copy) |

## 8. Status and TODO (as of the last session)
1. `pr_bundle.zip` = 7 Kotlin files with final package + `make_clean_pr.py` (handles the TV rule) + `restart_fork.sh` + `export_pr.sh` + `update_files.sh` + `build-apk.yml` + `strings_lyrics_tr.xml` + this doc as `WATCH_LYRICS.md`. All scripts were tested on a **mock repo only**; no Kotlin compiler was available to the previous assistant, so the code is **unverified by a real build** until the user's CI/compile passes.
2. **First task:** rebuild the fork (§0.1), run the compile check, fix whatever the compiler reports. Likely trouble spots: a hook anchor that changed in upstream v1.9 (`make_clean_pr.py` names the failing hook), the TV exclusion rule, flavor names in the compile command.
3. Device checks still open: raised controls no longer clipped and tappable; three dots during a mid-song instrumental of a **line-timed** song (thresholds may need tuning); source list speed (prefetch); the auto-sync line in the panel shows a fix count; tap-to-seek; keep-screen-on; background vocals.
4. Ideas not done (need extra libraries/manifest): Wear **Tile** and **complication** for now-playing, lyrics in AOD (the app's `AmbientPlayer` covers the screen).
5. Unknowns: display labels for every `LyricsSource` (a `when` on enum names with a prettified fallback is used), gap thresholds, behaviour on the watch's `dev` flavor.

## 9. Rules for the next assistant
Keep the PR minimal (feature files + small hooks). Do not rename the package `com.music.bitchord.wear.lyrics`. Material3 only. No new permissions. Do not edit upstream files beyond the listed hooks. Always provide **full replacement files** (not diffs) for anything large, and tell the user the exact compile command to verify. Be honest about what was not compiled.
