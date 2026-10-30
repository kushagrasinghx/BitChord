# Live Vocal Remover (StemSep engine port) — working documentation

> **AGENT / CONTEXT PROTOCOL**
> This file is the source of truth for the live vocal remover feature.
> **After every context compaction, session restart, or before continuing this task, re-read this whole file first**, then check the *Status log* at the bottom and continue from the first unchecked item. Update the Status log after each completed step. Do not trust chat summaries over this file.

## Original request (verbatim, Polish, 2026-10-29)

> zobacz dostepne pliki aka co tu jest
>
> https://github.com/kushagrasinghx/BitChord pobierz brancha v1.8.2 oraz https://github.com/penetratorcwl/StemSepw 2 osobne foldery, upewnij sie bys nie zapisal w temp
>
> folder wyzej masz folder StemSep, z tamtad bedziemy sie wzorowac by dodaj funkcje plan jest taki, implementujemy vocal remover do bitchorda, backend (logike) mozesz skopiowac z aplikacji, jedyne czym to sie rozni wsm to to ze utwor to ten co aktualnie leci, nie rob nic na skroty, ma byc zrobione raz a porzadnie, zadnego dsp, ma byc osobna zakladka/strona w ustawieniach w ktorej ma byc pobieranie modelu oraz wklejanie linki do pobierania innego jak ktos chce, ustawianie liczby rdzeni cpu albo auto i to chyba tyle w ustawieniach, ma sie to pojawiac w lyrics screen i ma byc przycisk do tego zamiast przycisku do tlumaczenia gdyz on ma byc po lewej stronie, po kliknieciu przycisku morphuje sie w kapsulke (jak w apple music) i mozna sterowac glosnoscia, chce by na razie byl gdzies debug info widoczne ktore bedzie tymczasowe z info o wszystkim, jak testowalem w roznych telefonach to trzeba okolo 4-4.5s na zrobienie 5s chunka bez wokalu, daj sobie czas, rob dokumentacje, zapisz tego prompta w niej i niech po kazdym kompaktowaniu czytal dokumentacje robiona, dla jasnosci chce by to bylo irl odsluch, ze w trakcie jak utwor leci to na zywo usuwa wokal i jak jest np w polowie utwor to od polowy powinno robic

Restated as requirements:

1. Port StemSep's backend (separation logic) into BitChord as a **live vocal remover** for the **currently playing track**.
2. **Real audio audition (irl odsłuch)**: while the track plays, vocals are removed live. If enabled mid-track, processing starts from the current position.
3. **No shortcuts, do it properly once. No cheap DSP** (no phase-cancel tricks) — the ML model (UVR MDX-Net ONNX) does the removal. Copy the logic from StemSep rather than reinventing.
4. **Settings: a dedicated tab/page** containing:
   - model download,
   - pasting a custom download URL for a different model,
   - CPU thread/core count or Auto,
   - (that's all for settings).
5. **Lyrics screen**: a vocal-remover button **replacing the translation button position (right side)**; the translation button moves to the **left side**.
6. Tapping the button **morphs it into a capsule** (Apple Music style) with **volume control**.
7. **Temporary debug info** visible somewhere, containing "everything" (performance, pipeline state).
8. Expected performance on test phones: **~4–4.5 s per 5 s chunk** (StemSep README: ~1.8 s/chunk on SM-S931B big cores, 4 threads).
9. Take the time needed; maintain this documentation; re-read it after each compaction.

Reference clone locations: StemSep at `/workspaces/opencode-cs/StemSep` (repo root, `master` @ `d1e12df`), BitChord at this repo (branch `v1.8.2` @ `77634fb6`). StemSep's `AGENTS.md` explicitly states the separation engine was designed to be reused by BitChord.

## Architecture decision (why not a DspChain processor)

BitChord's existing live-PCM hook is `DspChain` inside `PrecisionAudioSink` (`app/src/main/java/com/music/bitchord/playback/audio/`): synchronous, allocation-free, 4096-frame blocks on the renderer thread, bypassed on the bit-perfect path. MDX-Net inference needs ~1.8–4.5 s per 5.92 s chunk — it cannot run inside that synchronous chain without stalls, and a look-ahead buffer inside the sink would fight the two-player crossfade/Automix handoff. That would be the "shortcut" the request forbids.

**Chosen design — parallel live pipeline (faithful StemSep port):**

```
toggle ON (lyrics screen capsule)
  → VocalRemoverController (:app playback) captures song + positionMs
  → resolve stream URL (StreamResolver, already TTL-cached by playback)
  → download stream body to temp file via OkHttp (with media headers; opus webm/mp4 ≈ 3–10 MB)
  → MediaExtractor + MediaCodec decode (ported StemSep AudioDecoder, file-based)
      → stereo float32 → StereoResampler → 44.1 kHz → full-track f32 PCM file (frameCount known)
  → LiveSeparationSession (port of MdxStreamingSeparator loop, chunk-clamped to [startFrame, end])
      MDX STFT → ONNX (OnnxMdxEngine, CPU, AutoThreadController lead target 5 s) → iSTFT
      → vocals.f32 + instrumental.f32 flushed per chunk (BlockStereoSink)
  → LiveStemPlayer (ported as-is): one AudioTrack, reads growing stem files, plays from
      startFrame = current position; waits while playhead catches up with available frames
  → ExoPlayer + spare player setVolume(0f) while remover active (stays the position clock/seek source)
sync: player position drives the UI/lyrics; engine keeps LiveStemPlayer aligned to player position
      (periodic drift check; re-seek on user seek / track change / pause / stop)
toggle OFF: release LiveStemPlayer + session, restore player volumes, delete temp files
```

Consequences handled explicitly:
- **Seek** → re-anchor separation start to new position (PCM already decoded; only the chunk loop start moves; in-flight chunk result for old position is dropped via generation counter — StemSep invariant "seek uses a generation counter").
- **Track change** → full restart of the pipeline for the new track at its current position.
- **Pause** → stem player pauses; ExoPlayer paused normally by the service.
- **Automix/true crossfade** → while the remover is active, mute applies to whichever player is/becomes active (both `player` and `spare` kept at 0; re-applied in `adoptPlayer`/`setSessionOwner` paths). Overlap regions of two tracks are out of scope — the remover follows the new track.
- **Audio focus** remains with the media session (ExoPlayer). The stem AudioTrack uses the same stream type; it is the only audible output while active.
- **Buffer exhaustion** (phone slower than realtime): playhead waits at `availableFrames` (StemSep `LiveStemPlayer` behavior — `lock.wait(50)`), i.e. audible stall rather than desync. This matches the user's stated perf expectations.
- **No DSP path**: when the remover is off, nothing in the audio chain changes.

## Port map (StemSep → BitChord)

Kept **as-is** (packages stay `app.stemsep.*` for provenance and future sync; modules added to `settings.gradle.kts`):

| StemSep | BitChord destination | Notes |
|---|---|---|
| `core-dsp/**` (MdxConfig, MdxStft, Windows, PcmInterfaces, StereoResampler, WavIO, FloatPcmFileSource/Writer, BlockStereoSink, StemMixer) | `:core-dsp` module | pure JVM, JTransforms dep |
| `MdxStreamingSeparator.kt` | `:core-dsp` | **not** reused directly (whole-file assumptions); see new file below |
| `CoreDspTest.kt` | `:core-dsp` tests | must keep passing |
| `separation/` ModelCatalog, ModelDownloader, OnnxMdxEngine, ComputeBackend, AutoThreadController (+ their tests) | `:separation` module | ModelDownloader cache dir → BitChord `filesDir/models` |
| `separation/LiveStemPlayer.kt`, `LiveStemPreview.kt` | `:separation` module | play growing stem files |
| `separation/AudioDecoder.kt` | `:separation` — adapted | URI→File variant (MediaExtractor on fd); HTTP download happens in `:app` via OkHttp so YouTube headers apply |
| `separation/SeparationPipeline.kt` | `:separation` — replaced by `LiveSeparationSession.kt` | live pipeline: startFrame clamp, per-chunk flush, preview publish, lead callback, cancel/generation |
| `separation/StemStorage.kt`, `StemExporter.kt`, `StemPlayer.kt`, `SeparationWorker.kt`, WorkManager | **not ported** | BitChord needs no export/completed-session; temp dir lifecycle owned by the controller |
| StemSep UI (MainViewModel prefs, ComputeCard) | replicated in BitChord settings page | SharedPreferences keys via BitChord `AppSettings` |

New BitChord-side files (as actually implemented):
- `app/.../playback/vocalremover/VocalRemover.kt` — singleton engine orchestrator (`: VocalRemoverController`): `VocalRemoverHost` service interface, session lifecycle, stream fetch (OkHttp + StreamResolver headers) or local file, decode, separation from playhead, LiveStemPlayer audition, 250 ms sync loop (mute re-assert, drift seek, jump restart, track/pause mirroring), temp-dir ownership, debug state.
- `sharedUi/.../ui/player/VocalRemoverUi.kt` — UI contract (phase enum, state + debug data classes, `VocalRemoverController` interface, `LocalVocalRemover` composition local; null → control hidden, e.g. desktop). Types moved out of `:app` so sharedUi never depends on it.
- `sharedUi/.../ui/player/VocalRemoverCapsule.kt` — the disc→capsule morph: 34.dp mic disc (spinner while preparing, error icon on failure) expanding to a 216.dp Apple-Music-style pill with the vocal-level slider (0 = removed → 1 = mixed back) and an ⓘ button opening the TEMPORARY monospace debug popup (all of `VocalRemoverDebug`).
- `SettingsCategory.VOCAL_REMOVER` page in `SettingsSheet.kt`: model card (name, size, download/cancel with LinearProgressIndicator, verified/unverified+recorded-hash status), custom HTTPS URL + optional SHA-256 dialog ("Use built-in" reset), CPU threads Auto switch + 1..cores slider. Own `ModelDownloader` instance so the model can be fetched before the service ever ran.
- `AppSettings` keys: `vocalRemoverModelUrl/Sha/Size`, `vocalRemoverCpuThreads` (0 = Auto).

## Settings page spec (new `SettingsCategory.VOCAL_REMOVER`)

- Model section: current model card (default `mdx-9482`, 29.7 MB, pinned SHA-256), **Download model** button with progress (LinearProgressIndicator pattern from `DownloadManagerSheet`), state (not downloaded / downloading x% / downloaded+verified).
- Custom model: URL text field (paste), optional SHA-256 + expected size; **Download & use**. HTTPS only. If no hash provided, compute and display SHA-256 after download (shown in debug info; documented trade-off: unverified custom model).
- CPU threads: Auto toggle + slider 1..coreCount (core count from `Runtime.getRuntime().availableProcessors()` / `ComputeCapabilityDetector`), same semantics as StemSep (Auto = start min(4, max), dynamic at chunk boundaries, lead target ≥5 s).
- Persistence in `AppSettings` (SharedPreferences, StateFlow fields) — BitChord pattern, not DataStore.

## Lyrics screen UI spec

- In the lyrics status row: `translationToggle` moves to the **left** slot (next to romanization), the vocal-remover button takes the **right** slot (34.dp circle style like `TranslationToggleButton`, icon `Icons.Rounded.` person/wave style from material icons; keep `LocalPlayerTint`).
- Tap → button morphs into an **Apple-Music-style capsule**: rounded pill containing an enable indicator + slider(s):
  - **Vocal level** slider (0 → vocals fully removed; 100 → vocals mixed back at full level) — implemented by `StemMixer` gains (instrumental 1.0, vocal = level), same mixer the player uses (StemSep invariant: one AudioTrack, software mix).
  - The capsule collapses back to the button on tap of its close/enable control; toggle off disables the remover and restores original audio.
- Applies in both portrait `NowPlayingScreen` (sharedUi) and landscape `LandscapePlayer` (and `PlayerSidePanels` desktop pane which reuses those composables).
- Morph animation: rect `lerp` + `animateFloatAsState` precedent (FloatingTabBar inline→expanded); respect `AppSettings.reduceAnimation`.

## Debug info (temporary, clearly marked)

A small always-visible panel while the remover is ON (inside/below the capsule or a toggleable overlay): enabled state, phase, track videoId + position, available/playhead frames + lead seconds, chunk index/start, per-chunk inference ms + average, active CPU threads (requested/effective), model id + sha (truncated), decode/download progress, session temp paths, device cores, player volumes (main/spare), drift vs ExoPlayer position, stalls count. Text block in monospace; marked `// DEBUG - TEMPORARY, remove before release polish` in code (one component, easy to delete).

## Invariants carried from StemSep (must hold)

- Stereo 44.1 kHz; periodic Hann, hop 1024, chunk 261,120 samples, edge trim 3072, overlap 0.10, compensation 1.035, tensor `[1,4,2048,256]`, channel order L-Re, L-Im, R-Re, R-Im, zero first 3 freq bins.
- Instrumental = mixture − compensated vocals; stems sum to mixture.
- Do not change STFT/chunk/overlap math without updating `CoreDspTest` and `SEPARATOR_VERSION`.
- One `AudioTrack` for both stems; seek via generation counter; model inference never on main thread.
- Model weights never committed; downloads HTTPS + pinned SHA-256 (custom URL: hash optional per spec above).
- ONNX: `onnxruntime-android 1.28.0` (already in BitChord `:app`; the `:separation` module should use `compileOnly`/`api` against the same version — keep single runtime).

## BitChord integration anchors (from exploration)

- `PlaybackService.kt` (`com.music.bitchord.playback`): `buildPlayer` :3379, `adoptPlayer` :3405, `setSessionOwner` :3457, `player` :569 / `spare` :576, resolver :1392, `StreamResolver.resolve(videoId)` (shared, TTL cache), `mediaHeadersFor` :585, scope :710.
- State for UI: `PlayerConnection.rememberPlayerState()` (`PlayerState.song/isPlaying/position/durationMs`), `MainViewModel`.
- Settings: `SettingsSheet.kt` — `SettingsCategory` :2591, section blocks `shows()` (add one near :1492 LANGUAGE), `ChoiceGroup` :2755, `SliderRow` :2481, `SettingsRow` :2321; `AppSettings.kt` object with StateFlows + prefs setters.
- Lyrics toggles row: `LandscapePlayer.kt` `LandscapeLyricsPane` Row :545–566 (left romanization :553, right translation :565); `NowPlayingScreen.kt` :2084–2140 (`TranslationToggleButton` usage :2109); `PlayerLyrics.kt` `TranslationToggleButton` :1451–1498.
- Build: no version catalog; inline deps; `:app` minSdk 26 / compileSdk 37 / flavors dev+prod; modules `:shared`, `:sharedUi` via `settings.gradle.kts` :18–42; ONNX 1.28.0 at `app/build.gradle.kts:400`.
- Temp files: `context.cacheDir` precedent exists; stems session dir `cacheDir/vocalremover-session` (delete on toggle off / track release).

## Performance budget

Chunk 5.92 s. Phone measurements quoted by the user: 4–4.5 s/chunk (≈1.3–1.5× realtime ahead of consumption only when threads ≥4 and big-core; on weaker SoCs the playhead will periodically stall at the buffer edge — that is expected behavior, not a bug to "fix" with DSP). AutoThreadController keeps ≥5 s lead by raising intra-op threads at chunk boundaries. First chunk latency after toggle ≈ download + decode + 1 inference.

## Test plan

- `:core-dsp:test` — ported StemSep tests (7) + new test: chunk-loop start-frame clamp produces identical samples to full-file run sliced from that frame.
- `:separation:testDebugUnitTest` — AutoThreadControllerTest, ComputeBackendTest, ModelCatalog custom-spec test.
- `:app:assembleDebug` with `ANDROID_HOME=/opt/android-sdk` (JDK 17). Local SDK: platforms 36/37, build-tools 35–37 available.
- Manual (device, by user): mid-track enable, seek, track change, pause/resume, capsule slider audible effect, debug panel values.

## Status log (update every step)

- [x] 2026-10-29 — Repos cloned (BitChord `v1.8.2`, StemSep `master`). Full exploration of both codebases (playback, settings, lyrics UI, core-dsp/separation APIs, live preview mechanics).
- [x] 2026-10-29 — This document + `AGENTS.md` protocol created. Build env verified (JDK 17, `/opt/android-sdk`).
- [x] 2026-10-29 — Port `:core-dsp` module (as-is + `startFrame` clamp param on `MdxStreamingSeparator`; new test proves clamped start == sliced full run bit-exactly; 8/8 tests pass).
- [x] 2026-10-29 — Port `:separation` subset (ModelCatalog + `custom()` specs w/ optional hash, ModelDownloader + sidecar-hash verification, OnnxMdxEngine, ComputeBackend, AutoThreadController, LiveStemPlayer, LiveStemPreview, file-based AudioDecoder, new LiveSeparationSession). Exporter/StemPlayer/StemStorage/SeparationPipeline deleted. `compileDebugKotlin` + `testDebugUnitTest` pass.
- [x] 2026-10-29 — Gradle wiring: `:core-dsp` unconditional in `settings.gradle.kts`, `:separation` inside the SDK-gated block; `app/build.gradle.kts` has `implementation(project(":separation"))`; single ONNX runtime 1.28.0.
- [x] 2026-10-29 — `VocalRemover.kt` (`:app`, package `playback.vocalremover`) + PlaybackService integration: `VocalRemoverHost`, attach in `onCreate`, detach in `onDestroy`, every ExoPlayer volume site gated via `removerGated()` (crossfade/adopt/buildPlayer covered), `EVENT_MEDIA_ITEM_TRANSITION` → `onMediaChanged()`, 250 ms sync loop re-asserts mute/seeks/restart-on-jump. `:app:compileDevDebugKotlin` BUILD SUCCESSFUL. AppSettings keys added (model URL/sha/size, CPU threads; 0 = Auto).
- [ ] Settings page (model download, custom URL, CPU threads/Auto) UI in `SettingsSheet.kt`.
- [ ] Lyrics screen: translation button → left, remover button → right slot; capsule morph + vocal level slider; debug panel.
- [ ] Final verification: `:core-dsp:test`, `:separation:testDebugUnitTest`, `:app:assembleDevDebug` + docs update.

Unverified/open risks:
- Exact `:shared`/`:sharedUi` exposure of `StreamResolver` to `:app` (agent said shared module; confirm import path when coding).
- `ResolvingDataSource`/`mediaHeadersFor` reuse for the standalone download (headers required, TTL ok).
- Opus-in-webm decoding via MediaCodec on device — StemSep decoded SAF files; BitChord's own pipeline already plays these formats so decoder exists; port keeps the same MediaCodec path.
- Dual AudioTrack coexistence (muted ExoPlayer track + stem track) — standard, but not yet exercised on device.
