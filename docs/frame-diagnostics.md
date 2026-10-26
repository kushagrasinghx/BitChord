# Physical-phone frame investigation

The previous refactor is committed as `5ff3551c` on `perf/android-smoothness`. Its smaller root and storage tests did **not** yield a noticeable smoothness improvement in the user's test. The remaining jank has been reproduced by the user on a POCO X8 Pro Max and a Galaxy S25 Ultra, both on Android 16. No emulator is used in this investigation. The POCO subsequently connected over wireless ADB for system tracing.

## Findings to isolate

These are source-confirmed behaviors, not measured explanations for the user's dropped frames:

- `GlassContrast.kt` records and reads back a scaled copy of the page every 150 ms while glass controls have readers and the activity is resumed. Pixel copying and per-region luminance calculations happen on its composition coroutine. It also samples unchanged content. This is a recent glass-specific suspect and cannot explain jank from before glass was enabled.
- `TopFadeBlur.kt` runs a progressive 24 dp Haze blur even when Liquid Glass is disabled. Ordinary frosted controls can also use Haze independently of the glass switch. **Liquid Glass off is not a blur-free comparison.**
- `CanvasArtworkPlayer.kt` uses `TextureView.getBitmap()` on the UI coroutine for frame-derived artwork colors. Small capture dimensions limit bytes copied but do not establish how long a GPU readback waits on either phone.
- The legacy mesh runs a full-surface live blur during its drift; the modern artwork backdrop uses a precomputed bitmap. The combined light-rendering experiment chooses the modern backdrop.
- `MainActivity.applyPerformanceMode()` sets the activity's window and Compose view. The modal Now Playing window receives neither that policy nor an explicit high-rate vote. The actual rate on each window needs measurement. Android's [adaptive refresh-rate documentation](https://developer.android.com/develop/ui/views/animations/adaptive-refresh-rate) describes per-View votes and the default normal-rate category; a fast display does not by itself prove that this app renders at its maximum rate.
- `AppSettings.setHighPerformanceMode(true)` clears Reduce dynamic blur and Reduce animation. Enabling either reduction turns High performance mode off. This couples render cost to the refresh-rate request and confounds comparisons made through those settings. The diagnostic presets bypass the coupling without persisting settings. The refresh-rate implementation and high-performance setting were introduced by `be5de5d4` on 2026-08-29, before the recent glass work.

The installed APK resolves Compose runtime/UI/foundation to 1.10.5, Material3 to 1.3.1, and ProfileInstaller to 1.4.1. The older BOM declaration is not evidence that the installed Compose UI is 1.7.6. Dependency upgrades are not part of this diagnostic pass.

The APK contains a baseline profile, but its presence alone does not establish that Android has compiled that profile on the installed phone. The report asynchronously records ProfileVerifier's result code, compiled/enqueued state and embedded-profile flag. This is an installation-level check, not proof that a particular root method has native code. See Android's [baseline profile verification guidance](https://developer.android.com/topic/performance/baselineprofiles/debug-baseline-profiles).

## Diagnostic APK

Build with `-Pbitchord.performanceDiagnostics=true`. Ordinary builds default to false: no diagnostic settings row, activity access, window observers or render overrides. This is a non-debuggable, R8-optimized production variant with the same package/certificate as the prior APK. It also permits shell profiling for optional ADB traces.

1. Install the diagnostic APK over the existing build.
2. Open **Settings → Performance → Frame diagnostics**.
3. Start **Baseline**, wait three seconds, then repeat the actions that show the problem for 45 seconds. Include scrolling, opening/closing Now Playing, minimizing and returning. Use an optional label to distinguish scenarios.
4. Return to diagnostics and repeat the same sequence with **Light rendering + high refresh**, then **Highest refresh rate only**. Keep the same track/page and avoid changing Settings during each run.
5. Use **Share recordings** to export a ZIP containing the recent runs. Additional presets can separately disable glass readback, dynamic blur, motion artwork or UI animation if the first comparisons identify a useful direction.

Overrides affect in-memory settings only and reset when a recording ends. UI transitions remain enabled in the combined light-rendering mode unless the original Reduce animation setting was already on. There is no live FPS overlay generating additional app draws during capture. The native diagnostic control activity is outside the recorded windows.

## What the report measures

The main and Now Playing windows are observed independently. [FrameMetrics](https://developer.android.com/reference/android/view/FrameMetrics) supplies each frame's total duration, its actual deadline, GPU duration, UI/render phases, first-draw flag and intended/actual VSync timestamps. The report includes raw samples, per-window summaries, rendering settings, window frame-rate requests, battery-saver/thermal status, lifecycle events and timed glass/artwork readback spans.

`uiNs` sums the pre-frame unknown delay, input, animation, layout/measure and draw phases. This is wall time, not a sampled CPU-busy measurement. Unknown delay and VSync lateness remain separate raw fields so scheduling delays can be distinguished from expensive layout/draw work. The ADB trace adds actual thread scheduling and framework/app trace sections; identifying an untraced hot method may still require CPU sampling.

First draws remain in the raw recording and are separated from steady-frame percentiles. Missing platform metrics remain unknown. Lost metric callbacks are reported as **lost reports**, not dropped display frames. A frame exceeding its app deadline is not a complete diagnosis of presentation behavior; [FrameTimeline](https://perfetto.dev/docs/data-sources/frametimeline) distinguishes app work, SurfaceFlinger and display presentation. Phase durations can overlap, and a readback overlapping a late frame is correlation rather than proof of cause.

The recorder preallocates no full-screen bitmaps, runs metrics collection/report writing on a worker, bounds raw storage to 30,000 frames, and writes no report file during capture. It observes the app's windows; it does not measure the launcher's animation after the app is minimized.

Analyze exported JSON or ZIP files locally:

```sh
python3 scripts/perf/analyze_frames.py /path/to/bitchord-frames.zip --details
```

Compare steady frame percentiles, deadline misses, main/player cadence, first-draw spikes and timed readback spans across modes. Differences need a repeatable action sequence, sufficient samples, and comparable temperature/power state. Cadence excludes gaps over 100 ms and is informative during continuous motion, not a claim about a static page's FPS.

## Optional wireless ADB trace

For minimize/launcher transitions, or to locate busy/blocking intervals on the UI thread or RenderThread, capture the physical phone's scheduler and system FrameTimeline. The script requires an explicit serial and rejects emulator targets. It neither launches the app nor drives its UI.

```sh
python3 scripts/perf/record_phone_trace.py --serial IP:PORT --seconds 45
```

Use `--adb /path/to/adb` if it is not on PATH. Reproduce the issue during capture. The `.pftrace` is pulled into `output/phone-traces`; the phone-side copy remains under `/data/misc/perfetto-traces`. Configuration can be inspected without a device using `--print-config` and is saved alongside each trace. This path follows Perfetto's [local Android recording](https://perfetto.dev/docs/getting-started/local-android-trace-recording) and [FrameTimeline](https://perfetto.dev/docs/data-sources/frametimeline) documentation.

The recorder now streams compressed data to the phone's trace file every second, and places process metadata/FrameTimeline in a separate buffer. The first capture retained roughly 100,000 sections per second, predominantly Skia internals, adding overhead and overwriting a 64 MiB non-streaming buffer. **Removing the `gfx` category did not remove those Skia sections on this phone.** `--presentation-only` therefore disables ftrace/atrace entirely for an independent presentation check. The default retains scheduler, View/Compose, window, input and runtime tracing; `--verbose-graphics` additionally requests the `gfx` category. See Perfetto's [buffer configuration](https://perfetto.dev/docs/concepts/config) and [file streaming/compression fields](https://perfetto.dev/docs/reference/trace-config-proto).

Analyze a pulled trace with the official Perfetto trace processor:

```sh
trace_processor query -f scripts/perf/analyze_phone_trace.sql output/phone-traces/TRACE.pftrace
```

The SQL checks retained coverage, overwritten data and final-flush status before reporting per-window rendering/presentation, jank classifications, presentation intervals and UI/RenderThread sections. App frame duration is not on-screen latency. Dropped app/display frames are excluded from presentation timestamps. Buffer stuffing alone is not counted as a dropped frame; idle or window-hidden gaps must not be interpreted as scrolling frame drops. First-observed frames are not necessarily first draws when a window existed before tracing started.


## Validation and artifact

- [Diagnostic ARM64 APK](../output/BitChord-1.9-frame-diagnostics-arm64.apk), same `com.music.bitchord` package, version `1.9-beta1` / code 30 and signing certificate as the previous build.
- SHA-256: `43046d6f8687bda5fe4bf69899e5d1d66ee548d00e2b522cbf73b96fd40f5e84`.
- Optimized production release assembled successfully, including the Wear companion. Manifest inspection confirms diagnostics enabled, shell profiling enabled, no debuggable flag, and no test-only installation restriction. Signing verification passes.
- The original APK passed 23 focused tests. After the physical-recording data-quality correction, all **26** focused tests pass: 19 persistence tests plus 7 frame-measurement tests. These include conflicting duplicate reports, sentinel-like budgets and distinct samples sharing an intended timestamp. The collector correction has not yet been rebuilt into the linked APK; exported raw data remains usable through the corrected analyzer.
- The instrumented root remains below the normal huge-method limit at **9,140 code units / 211 registers**. The prior uninstrumented performance build was 8,890 units; no frame-time improvement is inferred from either number.
- No emulator was launched. The user exercised the diagnostic APK on the physical POCO; system tracing subsequently used that phone over wireless ADB.
- [Build metadata](../output/frame-diagnostics-build.json) and [DEX measurements](../output/frame-diagnostics-dex-sizes.json).


## First physical recording: 2026-10-26, POCO

Input: `bitchord-frames-20261026-054325.zip`, run `20261026-054129-690`, **HIGH_REFRESH** only, 45 seconds. The user identifies the screens as Home and Now Playing. This run does not establish whether the refresh override improves the original build; it has no baseline or light-rendering comparison.

### Data quality correction

The file contains 9,192 callback reports but only **4,596 distinct timing samples** when reports identical in all measured timing fields except deadline/report-loss count are collapsed. Distinct timing samples sharing an intended timestamp are retained. There are 4,594 groups with conflicting deadlines. Most Home deadlines grow into seconds, and one value is near the signed 64-bit sentinel range. No dependable whole-window late-frame percentage can be derived from this capture. The diagnostic APK's displayed near-zero percentage must be disregarded.

The analyzer now preserves raw input, normalizes repeat reports, marks conflicting or >1s deadline values unavailable, and withholds aggregate percentages and budget percentiles when deadline coverage is incomplete. The collector source now copies each platform FrameMetrics object before extracting fields; its summary applies the same quality checks. Regression tests reproduce the conflicting-report pattern, sentinel-like budgets and legitimate distinct samples at a shared timestamp. These collector changes are source changes for a future build; the existing APK can still provide raw timing data for the remaining preset comparison.

Corrected local outputs: [analysis](../output/phone-recording-20261026-analysis.txt), [normalized samples](../output/phone-recording-20261026-cleaned.json), [findings](../output/phone-recording-20261026-findings.json). Raw ZIP remains unchanged. The duplicate/conflicting platform reports are observed facts; their framework/vendor cause has not been established.

### Measured timings

All values below are milliseconds. Total frame latency is not an FPS estimate or a dropped-frame count; UI, RenderThread and GPU stages may overlap.

| Window / stage | Median | 95th percentile |
| --- | ---: | ---: |
| Home total frame duration | 16.87 | 30.73 |
| Home UI phases including unknown delay | 6.05 | 18.45 |
| Home pre-frame unknown delay | 0.72 | 11.41 |
| Home draw/display-list work | 4.29 | 5.86 |
| Home command issue | 5.13 | 6.76 |
| Home GPU duration | 4.93 | 7.27 |
| Now Playing total, excluding first draws | 7.39 | 18.34 |
| Glass sampling call wall time | 12.39 | 19.46 |

Both windows requested 120 Hz and the display reported 120 Hz. Active-frame intended-VSync cadence is approximately 8.30 ms. The run therefore does not support a simple missing 120 Hz request as the sole explanation for these stalls. Battery saver was off, thermal status was 0 at both endpoints, and ProfileVerifier reported a compiled profile with an embedded/enqueued profile. This remains an installation-level check, not method-specific native-code verification.

Two player openings are visible:

| Opening, seconds into capture | Initial total | Initial layout | Initial GPU | Following frame total | Following pre-frame delay |
| --- | ---: | ---: | ---: | ---: | ---: |
| 3.928 | 101.29 | 43.56 | 4.64 | 131.57 | 90.13 |
| 28.111 | 96.92 | 44.44 | 6.02 | 119.62 | 83.94 |

Both windows also show an approximately 83 ms VSync timestamp shift after each opening. This is evidence of shared UI-thread delay around player creation; a pure GPU-time explanation for those opening spikes is insufficient. It does not identify the expensive composable, initialization call or wait without a trace.

### Readback association and next comparison

There are 357 instrumented glass-sampling calls (including any early-return/canceled attempts), with a maximum wall time of 25.32 ms. Calls consume 4.43 seconds of summed wall time across the run; that is **not** a CPU-busy measurement because the coroutine can suspend. Two artwork captures take 7.19 and 8.16 ms.

Among Home timing samples whose UI/delay interval overlaps a glass-sampling call, median pre-frame delay is 12.25 ms and p95 is 21.36 ms (345 samples). Without that overlap, they are 0.69 and 5.21 ms (3,837 samples). That is a strong association worth isolating; motion or rendering load can affect both, so this alone is not proof that readback causes all delay. The callbacks do not include page/scroll markers, and there is no system trace of thread stacks or presentation.

The most useful next run is **Light rendering + high refresh** with the same Home/Now Playing actions. It retains the same explicit high-refresh requests while disabling glass sampling, dynamic blur, motion artwork and the live legacy backdrop. The existing HIGH_REFRESH run is usable as that comparison; it does not need to be repeated merely to obtain an initial contrast. If delays remain, the next investigation needs a physical scheduler/FrameTimeline trace, especially around player opening. There is no direct measurement of launcher/minimize behavior in this file.

## Second physical recording: light rendering

Input: `bitchord-frames-20261026-061839.zip`, new LIGHT run `20261026-061727-078`. The ZIP also contains the unchanged first HIGH_REFRESH run. The user reports that this run was **still similarly janky**, despite the lower recorded rendering times.

| Steady window / stage | HIGH_REFRESH p95, ms | LIGHT p95, ms |
| --- | ---: | ---: |
| Main total frame duration | 30.73 | 9.10 |
| Main UI phases including unknown delay | 18.45 | 3.84 |
| Main pre-frame unknown delay | 11.41 | 1.10 |
| Main GPU duration | 7.27 | 3.53 |
| Now Playing total | 18.34 | 8.89 |

The new run contains 4,357 distinct Main timing samples and 2,651 player samples after duplicate normalization. It records 14 player openings versus two in the first run, so action mix and cache warmth differ. Liquid Glass and motion artwork were already disabled before the new preset started; this is a broader settings comparison than toggling the LIGHT preset alone. Both windows requested/reported 120 Hz, with intended-VSync cadence around 8.30 ms. Battery saver was off and thermal status was 0 at both endpoints.

There were no glass/artwork readback operations. The duplicate/conflicting-deadline problem persisted, so it cannot be attributed solely to readback. Late-frame percentages from this APK remain unusable. Each initial player frame still took **34.97–42.48 ms**, including **16.41–20.85 ms** of initial layout and **28.12–36.85 ms** of UI phases. Initial GPU work was only 0.91–3.32 ms. Most following frames had another 26–32 ms of pre-frame delay. These recurring opening stalls remain a useful target even with effects disabled.

Lower app render timings did not establish smoother on-screen delivery. The next measurement is actual FrameTimeline presentation and scheduling, with normal settings, rather than inferring the user's experience from these stage percentiles. Corrected exported-report analysis is saved in [the light comparison](../output/phone-recording-20261026-light-analysis.txt).

## First wireless system trace: retained final six seconds

Artifact: [trace](../output/phone-traces/bitchord-20261026-063712.pftrace), [SQL analysis](../output/phone-traces/bitchord-20261026-063712-analysis.txt). The user reproduced Home, player and minimize/return with normal app settings during a requested 45-second recording. The non-streaming 64 MiB buffer overwrote **414,707,712 bytes**, retaining only **6.325 seconds**, beginning 38.696 seconds after tracing started. Initial process names were also overwritten; window names and surviving thread/PID data identify BitChord. The retained segment contains only the Main app surface and a brief launcher gesture surface, not the earlier player/minimize transitions.

Of 567 retained app frames, FrameTimeline classifies 46 as App Deadline Missed and three as Dropped Frame. Another 475 have Buffer Stuffing alone, with 33 of the deadline misses also carrying that state. **Buffer Stuffing is extra queue latency and can remain smooth; it is not 508 dropped frames.** All predictions are valid. The 565 retained SurfaceFlinger display frames report no compositor deadline miss.

Matching non-dropped app frames to their SurfaceFlinger display token gives median/p95 frame-start-to-presentation latency of **29.54/36.95 ms**. Consecutive presentations have median/p95 spacing of **8.31/16.68 ms**, with 45 intervals between 12.5 and 100 ms. Three longer gaps require interaction context and are not counted as scrolling drops. Rendering completion alone has median/p95 **15.26/19.33 ms**.

The UI and RenderThread ran for 2.54 and 3.94 seconds of the retained scheduling coverage; runnable-but-not-running time was only 53 and 33 ms. This segment does not suggest widespread CPU scheduling starvation. There are 64 hardware-bitmap readbacks on the UI thread (p95 2.14 ms, max 8.64 ms), substantial full-screen rendering, and repeated UI frames above one display interval. However, the verbose graphics category itself emits very large numbers of nested Skia events and can perturb timings. These are measurements under that trace configuration, not clean baseline percentages or proof of one expensive application method.

The corrected repeat omits verbose graphics logging, streams to disk and isolates metadata/presentation buffers. No app setting or rendering implementation was changed between these system traces.

## Complete detailed system trace

Artifacts: [30-second trace](../output/phone-traces/bitchord-20261026-064307.pftrace), [SQL analysis](../output/phone-traces/bitchord-20261026-064307-analysis.txt), [ExoPlayer lifecycle log](../output/phone-traces/exoplayer-lifecycle.log). Coverage is 30.018 seconds with no buffer overwrites, process names intact, and successful final flush. Trace Processor reports that omitting `flush_period_ms` forces full in-memory sorting; that is a host-side analysis memory issue, not evidence of missing events. The recorder now supplies a five-second flush period.

Although `gfx` was omitted, this capture still contains extensive Skia instrumentation. Its frame classifications locate problems, but its timing distributions must be checked against a presentation-only trace before treating them as normal app performance.

### Player dismissal blocks the UI thread

Six player windows open during this run. Their first rendering frames take 34.36–59.35 ms. Five complete dismissals contain long `Compose:onForgotten` sections, including these four:

| Time into trace, seconds | Cleanup wall time, ms | UI CPU running, ms | UI sleeping/waiting, ms |
| --- | ---: | ---: | ---: |
| 13.371 | 77.83 | 4.70 | 72.95 |
| 15.938 | 45.30 | 6.44 | 38.49 |
| 21.137 | 55.53 | 5.95 | 49.23 |
| 27.472 | 44.01 | 5.31 | 38.42 |

These intervals are predominantly waits inside player cleanup. Their duration cannot be explained by expensive Compose CPU work alone. During the largest one, a video playback thread is still creating/configuring an HEVC decoder: `createCodec:c2.mtk.hevc.decoder` takes 108.97 ms. The UI thread sleeps for 48.41 ms, later another 17.47 ms, within the same cleanup.

The source has two relevant synchronous paths in [CanvasArtworkPlayer.kt](../app/src/main/java/com/music/bitchord/ui/player/CanvasArtworkPlayer.kt):

- The TextureView listener forwards `onSurfaceTextureDestroyed` to Media3 on the UI thread.
- The composition's `onDispose` directly calls `player.release()` on that thread. Git blame dates this pattern to `4fba5c093` on **2026-08-20**, before Liquid Glass.

Media3 1.11.0 [clears the video output from its TextureView destruction callback](https://github.com/androidx/media/blob/1.11.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayerImpl.java), and its [internal surface-detach and release operations wait for playback-thread completion](https://github.com/androidx/media/blob/1.11.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayerImplInternal.java). ExoPlayer lifecycle log entries, aligned using the trace's MONOTONIC/BOOTTIME snapshots, fall within the nested cleanup sections. Release-to-surface-callback-cleanup logging spans approximately 17–25 ms for these cases, consistent with the measured second wait. This establishes a blocking decoder lifecycle contribution; without method stacks or explicit app spans, not every millisecond of the outer cleanup can be assigned to one Media3 method.

The existing application Looper is the main thread. Moving only `release()` to an arbitrary coroutine dispatcher would violate Media3's thread ownership. A fix must address surface ownership and all commands/callbacks consistently, or retain/reuse the player and surface across transitions with a safe bounded lifetime.

### RenderThread pipeline creation stalls both windows

At 13.898 seconds, `CreateGraphicsPipeline` on the app RenderThread takes **71.01 ms** inside a Skia FillRect operation. The enclosing RenderThread frame takes **96.62 ms**. The UI thread's `postAndWait` lasts **89.89 ms**, producing a **95.27 ms** UI frame. This is a graphics pipeline creation stall; it is separate from ART/baseline-profile compilation.

There are 116 pipeline creation sections in the run, totaling 106.35 ms. The one 71 ms event dominates; the trace does not prove pipeline creation causes every scrolling miss or identify which composable/effect generated that draw. Glass, blur or another effect cannot be singled out solely from the `FillRectOp` label.

### Additional source findings

[SearchPlayingBars](../sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/components/Common.kt) reads all three infinite-animation heights during composition and changes three layout heights each frame. Its animation path dates to September 29. A small playing indicator can therefore continue composing/measuring when the rest of a page appears still. Reading animation values during drawing would avoid that work; its exact share of the measured stalls has not been isolated.

[PlayerSheetHost](../app/src/main/java/com/music/bitchord/MainActivity.kt) creates a fresh modal window and player composition on every opening and removes them on dismissal. The repeated initial layout/rendering spikes are consistent with that lifecycle. The previous root split retained this behavior.

The LIGHT preset deliberately retains UI animations, including the vector station/mood artwork on Home. Home's card-video lookup also bypasses the Now Playing `rememberCanvasArtwork` setting gate. The preset's absence of captured readback operations does not establish that every animated element was disabled.

## Presentation-only confirmation

Artifacts: [trace](../output/phone-traces/bitchord-20261026-071656.pftrace), [SQL analysis](../output/phone-traces/bitchord-20261026-071656-analysis.txt). Configuration contains only FrameTimeline and initial process metadata, with no app, scheduler or graphics tracing. The 30-second request produced a **335,923-byte** file; frame events cover 29.212 seconds. Both buffers report zero overwrites. The final-flush success flag is 0, so final-tail completeness is not asserted. The findings below concern well-populated earlier windows/intervals rather than an assumed complete final second.

The run begins outside BitChord and includes a launcher transition into it, followed by Home/player activity. It is a confirmation with a different action mix, not a controlled quantitative before/after comparison with the detailed run. The app's in-memory rendering settings were not exported for this system-only capture.

| Observed app surface | Frames | App Deadline Missed | Dropped Frame |
| --- | ---: | ---: | ---: |
| Main | 1,913 | 159 | 9 |
| Player opening 1 | 135 | 14 | 0 |
| Player opening 2 | 162 | 13 | 0 |
| Player opening 3 | 380 | 24 | 0 |

All app-frame predictions are valid. Another 1,321 Main frames carry Buffer Stuffing alone; these are extra latency, not 1,321 dropped frames. The classification and app deadline misses persist without the detailed trace's Skia instrumentation.

Main rendering duration has median/p95 **12.25/19.97 ms**, while non-dropped frame-start-to-presentation latency is **29.40/34.41 ms**. Adjacent presentation spacing has median/p95 **8.31/16.63 ms**, with 158 intervals between 12.5 and 100 ms. Longer gaps are not automatically called dropped frames because the app is not continuously animating during every interval.

The first player frames take **142.84, 76.88 and 67.99 ms** to finish rendering; presentation follows at **153.60, 87.80 and 78.83 ms** from those frame starts. All three miss the app deadline. Two windows also have a shared ~125–129 ms stall around 11.812 seconds. The newly observed Main surface itself has a 146.41 ms initial rendering frame after entering from the launcher. These are real app presentation stalls with detailed tracing off, rather than an inference from the broken native deadline percentages.

This trace also includes system/launcher presentation events, but it does not identify a separate BitChord minimize/return gesture cleanly enough to assign all reported minimize jank to one app operation. System attribution remains a separate limit from the directly measured player opening/closing and Main-frame misses.

## Implementation priorities from the traces

1. Remove synchronous video-surface/decoder waits from UI transitions, with consistent Media3 Looper and resource ownership. Instrument surface destruction, player release and creation separately to verify which waits disappear. This targets an observed pre-glass problem.
2. Reduce repeated expanded-player creation/layout work and retain expensive prepared state across openings where its lifecycle can be bounded. Test initial open, repeated open/close, track changes and background/return separately.
3. Identify the draw/effect behind the cold Vulkan pipeline creation, then cache/prewarm or simplify that specific path. The trace does not justify a blanket dependency upgrade or claiming that one effect explains all jank.
4. Move the playing-bars animation out of composition/layout and stop unnecessary animation/readback work on covered screens. Confirm the result through actual presentation, with comparable actions and settings.

The investigation supplies a measured diagnosis and corrected capture/analyzer tools on `perf/android-smoothness`. SQL analysis was executed successfully on all three saved traces, Python tools pass syntax/config checks, and `git diff --check` passes. The user's `gradle.properties` and scratch document remain outside this diagnostic work.

The subsequent [first fix batch](video-lifecycle-fixes.md) moves Canvas decoder construction, surface changes and teardown to a dedicated control Looper, retains textures until safe release, and moves the playing-bars animation out of composition/layout. Its optimized APK builds and all 34 focused tests pass. Native behavior and presentation improvement still require a phone comparison; the modal layout and cold-pipeline targets remain open.
