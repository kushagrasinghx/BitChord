# Reuse glass shadows and skip unused artwork copies

Baseline: `0005b5a1`, the no-readback glass build the user reported as noticeably smoother. This batch preserves the existing rim, glow, color dodge, blur, refraction and material settings.

## Repeated work removed

Each glass shadow previously recreated its blur mask filter and re-recorded an offscreen layer on every redraw. It now retains the existing graphics-layer recording until its shape, size, density, font scale, layout direction, color, radius or offset changes. Alpha and blend mode remain live properties. State read by a mutable shape's outline is observed and invalidates the recording. Detach/reset clears recording state. Existing shadow drawing, clear cutout, offsets and offscreen compositing are preserved.

Artwork videos previously captured their first rendered TextureView frame even when no caller consumed it. Frame capture is now explicitly optional, so Home cards, album/artist artwork and other no-consumer videos render without an unused bitmap copy. Actual Now Playing palette consumers retain their first-frame and requested periodic captures. Captures are suspended while the app is backgrounded or the artwork is paused for a transition. The rendered-state notification remains independent of bitmap capture. A pending first-frame request survives a pause, while resuming an already captured clip does not add another one-shot copy.

## Current-build trace

After the user confirmed readiness, a [30-second physical-phone trace](../output/phone-traces/bitchord-20261027-034608.pftrace) was captured on the committed baseline. The requested actions were Home/detail scrolling and player open/close; the recording has no page labels that establish each action individually. [Analysis](../output/phone-traces/bitchord-20261027-034608-analysis.txt) and [focused sections](../output/phone-traces/bitchord-20261027-034608-focus.txt) retain the full capture duration. One app window has 3,018 recorded frames: 54 carry an app-deadline-missed classification and 10 are classified dropped. Buffer stuffing alone is not counted as a dropped frame.

Main-thread frame work has a 5.14 ms median and 8.05 ms p95; RenderThread DrawFrames has a 7.02 ms median and 9.52 ms p95. Frame phases overlap and these numbers must not be added together. The older long player-cleanup stalls are absent in this run. The trace does not establish how much shadow reuse or artwork capture gating will improve frame delivery.

A separate 15.36 ms lazy-prefetch measurement includes an 8.98 ms emoji-font load ([subtree](../output/phone-traces/bitchord-20261027-034608-prefetch.txt)); this isolated load is not treated as the cause of persistent rendering cost. No emulator was used.

## Validation

Four new JVM tests cover retained-shadow lifetime, pixel-input changes, live alpha/blend changes, observed mutable shape changes and equal replacement shapes. These verify recording decisions and state observation rather than native image output. Source review checked shadow drawing equivalence and artwork callback/lifecycle behavior. The trace above is from the preceding baseline; candidate frame delivery has not been quantitatively measured.

The user has tested this batch and authorized keeping it after code review.

All **55 focused JVM tests pass** on the final source, including the four shadow-recording cases and the existing glass, player, frame-measurement and persistence regressions. The Android hardware-test source compiles; native image comparisons have not run for this batch. The optimized ARM64 production assembly and signing verification pass. Existing R8/Kotlin metadata warnings remain.

## APK

[Test APK](../output/BitChord-1.9-shadow-artwork-arm64.apk), production package `com.music.bitchord`, version `1.9-beta1`, code 30; not debuggable. Certificate matches prior production test builds.

Size: 67,778,176 bytes. SHA-256: `c39f5df78607e5e0221574780d803873cdce3924a3224ac612264501e6c1a9ca`. [Build metadata](../output/shadow-artwork-build.json).

The APK was transferred over wireless ADB with a matching SHA-256 and replacement-installed successfully on the POCO at `:37901`, preserving app data. Installation only; the app was not launched or driven automatically. The subsequent user result is recorded below.

## User result and decision

The user reported similar smoothness or a possible marginal improvement, and authorized keeping the changes if code review was clean. A final source review found no high- or medium-severity correctness issues. This is recorded as a code-efficiency improvement without a demonstrated large frame-delivery gain. The user also reports that glass controls and mini-player expansion/collapse are now very smooth, including on content-heavy pages. Remaining discomfort is described as heavy or throttled vertical scrolling; that is a separate follow-up investigation.
