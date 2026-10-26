# First fixes after the physical frame traces

Branch: `perf/android-smoothness`. The preceding traces identify 44–78 ms player-cleanup waits, repeated initial-player spikes and a separate cold Vulkan pipeline stall. This batch targets the decoder lifecycle contribution and the playing indicator's continuous layout work. Smoothness on the phone still needs measurement after installation.

## Changes

`CanvasVideoSession` owns silent artwork players on one dedicated `canvas-control` Looper. Construction, preparation, play/pause commands, repaint seeks, surface changes and release all execute there. Media3 callbacks deliver values back to the main thread before touching Compose state or TextureView transforms. This keeps Media3's application-thread contract intact while removing its synchronous waits from the UI thread. The shared executor starts without waiting for `HandlerThread.looper` on the UI thread.

The TextureView listener now supplies an explicitly owned `Surface`. Destruction enqueues detachment and returns false, transferring responsibility for releasing the SurfaceTexture to the control thread. A lease holds the Surface and texture until Media3 has acknowledged detachment or player release. If the player closes while its view is still attached, only the Surface is released; the texture stays owned by the view until its destruction callback. A late callback for an old view cannot clear a replacement's output. Rapidly canceled startup cannot create a decoder after closure.

The Canvas composable is keyed to its clip, so an AndroidView factory/listener cannot retain the previous clip's state or surface ownership. Existing view transforms, fade, still-artwork fallback, URL fallback, cache-backed networking and foreground pause behavior remain in place. This is a change to the silent Canvas/motion-artwork decoder; the separately implemented `MusicVideoEngine` and the audio service retain their own ownership.

`SearchPlayingBars` now uses a single infinite transition and reads its three animated values inside Canvas drawing. The same heights, timing, plate, spacing and rounded bars are drawn without composing and remeasuring the indicator for each animation frame. This is an independently identified source inefficiency; no numerical smoothness gain is claimed from it without a phone comparison.

Diagnostic trace sections now work during an ADB system trace even when the in-app recorder is inactive. They distinguish `BitChord.canvasCreate`, `canvasPrepare`, `canvasSetSurface` and `canvasRelease` on the control thread from `canvasDispose` and `canvasSurfaceDestroyed` on the UI thread. Timing/report work is gated out of ordinary builds. This APK also includes the FrameMetrics copying and duplicate/conflicting-deadline corrections described in [frame-diagnostics.md](frame-diagnostics.md).

## Validation

- Optimized ARM64 production APK assembled successfully, including the Wear companion. Non-debuggable, non-test-only, shell-profileable, with diagnostics enabled.
- **34 focused tests pass:** 8 video lifecycle tests, 7 frame-measurement tests and 19 persistence tests. Video tests cover asynchronous commands, destruction/replacement ordering, stale callbacks, close-before-start, calls after close, failure cleanup and a decoder wait running concurrently with a caller that remains free. Textures are asserted to survive the blocked detach.
- Signing verification passes, matching the installed test build's certificate. Package `com.music.bitchord`, version `1.9-beta1`, versionCode 30.
- Packaged DEX contains the new `canvas-control` owner and lifecycle trace sections. Baseline-profile assets and the embedded Wear APK are present. The release root remains below 10,000 code units at **9,144 / 211 registers**; no performance gain is inferred from its size.
- Python capture/analyzer tools and the trace SQL were checked during the diagnosis. `git diff --check` passes. No emulator was used.
- The build still emits the repository's existing Kotlin-metadata compatibility diagnostics from lint/R8, while completing successfully. No dependency or compiler upgrade is included in this batch.

## APK and physical check

[Installable ARM64 APK](../output/BitChord-1.9-video-lifecycle-arm64.apk)

SHA-256: `cfc356d6032107cb92580029aca8e030b0b02b162c19672f883d61beebfa4bc0`.

[Build metadata](../output/video-lifecycle-build.json), [manifest inspection](../output/video-lifecycle-manifest.txt), [DEX measurements](../output/video-lifecycle-dex-sizes.json).

Install over the preceding diagnostic APK. With normal settings and a track that has motion artwork, repeat player open/close, minimize/return, track changes and rotation. Check that the clip appears, returns after backgrounding, retains the proper crop/fade, and falls back to still artwork on a failed clip. Home card videos share this Canvas implementation and should also be checked while scrolling.

A detailed system trace can now verify that decoder waits occur on `canvas-control`, while the two UI teardown sections are brief. A presentation-only run provides the lower-overhead check for visible delivery. Compare similar actions and settings; the earlier normal and LIGHT recordings used different action mixes. Actual improvement and native surface behavior remain unverified until this phone check.

Repeated modal-window/player-layout construction and the exact draw responsible for cold Vulkan pipeline creation remain subsequent work. This batch moves decoder construction and lifecycle waits off the UI thread; it does not cache the expanded player UI or replace its modal window.
