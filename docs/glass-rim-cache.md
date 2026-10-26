# Retain glass rim drawing resources

The rim now uses a stable modifier node. Recomposition with the same shape retains its paths, blur filters, gradients and paints; changing only the opacity callback requests a redraw. The delegated Compose draw cache still rebuilds for shape, size, density, layout direction and observed geometry-state changes. Unchanged opacity also skips redundant paint updates.

The drawing sequence remains glow color dodge, glow additive white, rim color dodge, rim additive white, then the outside outline. Geometry, blur radii, gradients, gains, clipping and draw order are preserved. This removes some CPU setup and native paint updates; it does **not** reduce the four lighting draws or establish a visible smoothness improvement. The contrast sampler is unchanged.

## Why the combined renderer is absent

An experimental Android 16 custom blender and cached coverage masks were explored, but their hardware visual comparisons were not completed. Combining four draws also changes how partially covered clip-edge pixels are blended; pre-rasterized masks can differ when scaled or moved by fractional pixels. That path, its bitmap cache and its asynchronous preparation jobs have been removed from the application source.

The last installation command during that experiment targeted `com.dev.bitchord` (BitChord Dev), a debuggable, unoptimized build for offscreen image tests. The user reported considerably worse choppiness after trying the installed app. APK inspection confirms the debug flag; that comparison cannot isolate the renderer's effect from the different build configuration. The command did not replace the existing `com.music.bitchord` production package. The device is now disconnected for charging, so no installed-app inspection or new device test was performed.

## Validation

- All 44 focused JVM regression tests pass for playback persistence, frame measurements, player lifecycle/motion/measurement and overscroll ownership.
- The retained-paint hardware comparison test compiles. It checks repeated opacity values and fade-out/restore against fresh drawing resources, without opening an Activity. It has **not run** on the disconnected phone.
- No emulator, performance trace or benchmark was used for this change.

Cache lifecycle behavior was checked against the project's [Compose UI 1.10.5 source](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui/1.10.5/ui-1.10.5-sources.jar), specifically `DrawModifier.kt` and `NodeKind.kt`.

## APK

The optimized [ARM64 production APK](../output/BitChord-1.9-rim-cache-arm64.apk) builds successfully and passes signing verification. Its certificate matches the previous production APK. Package `com.music.bitchord`, version `1.9-beta1`, code 30; not debuggable. It has **not been installed**, because the phone is disconnected. The [previous production APK](../output/BitChord-1.9-dismissal-scroll-arm64.apk) remains available for comparison.

Size: 67,794,560 bytes. SHA-256: `b459bb102dc61fc30a0261083344d04efcb15701dc66554fd451f787a4796b44`. [Build metadata](../output/rim-cache-build.json).

The build used one Gradle worker, a 3 GB heap and in-process Kotlin compilation through command-line overrides. Existing user changes to `gradle.properties` were not edited or included in the commit. The build emits the same R8/Kotlin metadata warnings as earlier successful production builds.
