# Liquid glass without contrast readback

The glass contrast sampler has been removed from the root page and detail pages. Glass foregrounds use black in the light theme and white in the dark theme. Artwork no longer causes labels or icons to invert. Unusually bright or dark artwork can therefore still reduce foreground contrast.

The removal includes the sampling coroutine, probe graphics layers, hardware-bitmap copies, CPU pixel extraction and luminance sorting, sample notifications, per-control bounds tracking and foreground crossfades. The diagnostics screen no longer offers the obsolete readback toggle. Artwork video capture is a separate feature and is unaffected.

## Rendering work retained and reused

The sharp rim, soft glow, both color-dodge operations, additive highlights, outline, blur, saturation, depth refraction and chromatic dispersion retain their previous rendering equations and settings. `GlassRim.kt` and the backdrop shader source are byte-identical to the preceding commit. Liquid glass resolution remains `0.33`; blur remains `8dp` before scaling. Lighting draw count is unchanged.

Unchanged built-in color, blur and lens stages now reuse their RenderEffects. Each node retains one result per stage rather than a history of animation frames. Lens keys contain the upstream effect, dimensions, padding, four corner radii, refraction height/amount, depth and dispersion flags. Changes replace the corresponding snapshot and dependent downstream effects. Removed stages are discarded, and node reset clears both shader and effect caches.

Effect rebuilding is deferred to drawing with current geometry, avoiding an eager rebuild with old or unspecified dimensions followed by another rebuild in the draw. Animated inputs remain observed and trigger redraws. Live backdrops continue to be recorded; effect reuse does not freeze the background.

Snapshot lifetime was checked against Android 16's [RenderEffect JNI implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/libs/hwui/jni/RenderEffect.cpp) and [Skia runtime image filter](https://android.googlesource.com/platform/external/skia/+/refs/heads/android16-release/src/effects/imagefilters/SkRuntimeImageFilter.cpp), which retains a copy of the supplied shader builder.

The full-resolution glass fill also retains its outline until shape, size, density, font scale or layout direction changes. Its existing fill colors, blend modes and outline drawing are preserved.

## Validation scope

The regression checks cover effect-chain reuse, changing lens inputs, upstream changes, conditional-stage removal/re-enabling, reset, and cached outline equivalence across rendering environments. These establish cache behavior and invalidation; they do not execute Android's renderer or measure device frame delivery.

Source review checked the removed sampler call sites, material/shader invariants, bounded cache lifetime and animated invalidation. No emulator was used.

All **51 focused JVM tests pass**, and the existing Android hardware render-test source compiles. Hardware image checks have not run on a phone for this batch.

## APK

The optimized [ARM64 production APK](../output/BitChord-1.9-glass-no-readback-arm64.apk) builds successfully and passes signing verification with the same certificate as prior production builds. Package `com.music.bitchord`, version `1.9-beta1`, code 30; not debuggable. Packaged DEX inspection confirms the sampler classes and glass-readback trace string are absent.

Size: 67,778,184 bytes. SHA-256: `e4bce395cee5e9897f6b56e7c272cf6c0d8fd7bf237b2f35fe72e0186a3b7c8c`. [Build metadata](../output/glass-no-readback-build.json). The user installed and tested this APK after delivery.

Build limits: one worker, 3 GB Java heap and in-process Kotlin compilation. Existing user changes to `gradle.properties` were preserved and excluded from this commit.

## User-tested baseline

On October 27, 2026, the user tested the optimized APK and reported that the smoothness improvement is definitely noticeable. The user then authorized committing this batch before further optimization. This is positive physical-device feedback, not a measured frame-rate result; the effects of sampler removal and effect reuse have not been separated.

Wireless ADB reconnected to the Android 16 phone at port 37901. The current source diff and local APK match the build metadata recorded before the user's test. The installed APK also matches the recorded SHA-256. No automated UI interaction, new benchmark or system trace was performed while recording this baseline.
