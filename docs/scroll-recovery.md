# Reverse-fling recovery and phantom overscroll

Baseline: `d059b7d2`. The user reports that glass controls and mini-player transitions are now very smooth, while ordinary vertical scrolling can feel heavy or throttled. This batch changes the global overscroll wrapper, retaining its touch rubber-band curve and return-spring settings.

## Reproduced behavior

An existing 120px edge pull followed by a reverse fling step of 80px delivered zero movement to the list: all 80px paid down the pull, leaving 40px of stretch. Subsequent fling steps continued spending their decay on the effect before the list could move.

Compose Foundation 1.10.5's native Android overscroll deliberately unwinds stretch four times faster for `NestedScrollSource.SideEffect`. Its source explains the otherwise delayed start of content movement. The custom effect now follows that recovery rule: the same 120px pull uses 30px of the 80px fling step, delivering the remaining 50px to the list immediately. Consumed movement is converted back to the original input units. Ordinary movement with no pull remains unchanged.

The effect also accepted every nonzero unconsumed remainder as new edge tension. A regression with repeated 0.125px leftovers created pull state during otherwise consumed list movement; the next reverse gesture had to clear that phantom pull. New pulls now require a remainder above 0.5px, the noise threshold used by Foundation. Once a pull exists, subpixel touch movement continues to update it continuously.

Primary reference: the project's [Compose Foundation 1.10.5 sources](https://dl.google.com/dl/android/maven2/androidx/compose/foundation/foundation-android/1.10.5/foundation-android-1.10.5-sources.jar), `AndroidOverscroll.android.kt`, including `destretchMultiplier`, `FlingDestretchFactor` and the rounding-error handling in `applyToScroll`.

## Scope

The root navigation-bar scroll connection consumes zero scroll delta and has no fling override. The page viewport is not resized by its collapse. Queue/lyrics containment is limited to player overlays. A separate bottom-bar hit guard consumes gestures beginning in its bottom strip; that behavior was not changed without evidence linking it to this report.

No changes are made to list decay, fling velocity, shelf snapping, glass rendering, animation duration, the touch rubber-band curve or bounce spring constants. Faster reverse-fling recovery does change how the existing stretch returns to rest under fling input. Weak reverse-fling pre-absorption remains a possible separate investigation.

## Validation

Before the production change, nine focused overscroll tests produced [three failures](../output/scroll-recovery-reproduction.txt): reverse-fling recovery, per-axis/partial-consumption accounting, and phantom tension from tiny remainders. Touch continuity and the previous gesture-cancellation tests already passed. The new tests exercise the actual `IosOverscrollEffect`, including consumed and forwarded deltas, retained pull state and direction reversal.

This establishes concrete interaction defects. It does not establish that every reported interior scrolling interruption comes from overscroll, or quantify an improvement in physical-device frame delivery. No emulator was used.

All **60 focused JVM tests pass** after the fix, including all nine overscroll cases and the previous glass, shadow, player, frame-measurement and persistence checks. Independent source review found no high- or medium-severity correctness issues. The subsequent physical-device result is recorded below.

## APK

The optimized [ARM64 production APK](../output/BitChord-1.9-scroll-recovery-arm64.apk) builds successfully and passes signing verification. Package `com.music.bitchord`, version `1.9-beta1`, code 30; not debuggable. The certificate matches the preceding production build.

Size: 67,778,176 bytes. SHA-256: `c06d62055a9020e5b84e2437eb99945a7d1d46cddadce93cc0dd9475acf569e7`. [Build metadata](../output/scroll-recovery-build.json).

The APK was replacement-installed successfully over wireless ADB at `:37901` with a verified transfer checksum, preserving app data. Installation only; no automatic app launch or gestures. The user subsequently tested this APK and authorized committing it.

## User-tested result

The user reports improved scrolling and that it no longer feels hijacked. Comparing similar text-heavy content in other apps, the user finds the current scroll feel on par or effectively identical, while expressing some uncertainty about whether every remaining rough edge is resolved. The user authorized committing this batch. This is a positive manual device result, not a quantified frame-rate measurement or proof that every remaining stall is fixed.
