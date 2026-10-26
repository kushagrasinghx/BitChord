# Player dismissal crash and scroll interruption

The user tested the player-host APK (`d83da20f`) on the physical POCO and reported noticeably smoother Home scrolling and mini-player expansion/collapse. Remaining interruptions occur during vertical scrolling and at the fully expanded/collapsed player states; movement between intermediate player states feels smooth. This is subjective device feedback, not a new measured frame-rate result.

## Dismissal crash

Wireless ADB crash logs contain six production BitChord crashes with `IllegalArgumentException: measure is called on a deactivated node`. The latest saved stack is [the October 26 dismissal crash](../output/phone-traces/bitchord-player-dismissal-crash-20261026.log). It enters `BoxMeasurePolicy.measure` from `PlayerOverlayHost`'s player-measure modifier.

`ReusableContentHost(active = false)` retains its layout nodes while deactivating them. The former enclosing `Box` still tried to measure these nodes when the scaffold remeasured after dismissal. Leaving the outer overlay unplaced prevents drawing and input, but does not prevent measurement.

The content now has an explicit `PlayerContentMeasurePolicy`. When inactive it returns the host's constrained size without measuring or placing any retained children. When active it keeps the former Box's top/start placement and child minimum constraints. The existing activity-window host, node reuse, artwork lifecycle and docking animation remain in use.

Two JVM regression tests cover open → deactivate → repeated hidden measurement with changing constraints → reactivate, and closing a host without children. The inactive test child throws the same exception if it is measured. These exercise the actual measure policy, not Android's full composition/layout lifecycle.

## Overscroll ownership

The custom `IosOverscrollEffect` started its return spring after `performFling` completed, without retaining a cancellation handle. In the app's resolved Compose Foundation 1.10.5 source, `onScrollStopped` calls the overscroll wrapper around `doFlingAnimation`; the latter owns the list's scroll mutation only until it returns. A subsequent drag can cancel a list fling while leaving this later spring running. Both the finger and old spring can then write the same pull offset.

A virtual-frame-clock test reproduced this: after starting a bounce and beginning a new drag, the old spring remained active. That test failed on the previous implementation and passes with explicit spring cancellation on new user input. A generation check also prevents an old fling from starting a delayed bounce after a newer drag or fling has taken over. The cancellation is limited to the overscroll animation's child scope.

Four tests cover a drag interrupting a bounce, a stale fling completing after a new drag, ordinary interior scrolling/flinging passing through unchanged, and an edge fling returning to rest. The factory remains global, so the ownership fix applies to vertical pages as well as shelves. This establishes an edge/repeated-gesture defect; it does not establish the cause of every interior scrolling hitch or player endpoint stall. No decay, snap, refresh-rate or visual-material settings are changed.

Primary references: [Compose Foundation 1.10.5 sources](https://dl.google.com/dl/android/maven2/androidx/compose/foundation/foundation-android/1.10.5/foundation-android-1.10.5-sources.jar), [ReusableContentHost contract](https://github.com/androidx/androidx/blob/androidx-main/compose/runtime/runtime/src/commonMain/kotlin/androidx/compose/runtime/Composables.kt).

## Validation

All **44 focused JVM tests pass**, covering the six new cases and the previous player motion, video lifecycle, frame measurements and playback persistence cases. A physical-device check remains necessary for dismissal/reopening and the perceived scrolling behavior. No emulator is used.

Optimized ARM64 production assembly and signing verification pass. The certificate matches the installed player-host build. The [APK](../output/BitChord-1.9-dismissal-scroll-arm64.apk) was transferred over wireless ADB with a matching SHA-256 and replacement-installed successfully on the physical POCO, preserving app data. Installation only; no automated UI interaction, benchmark or new performance capture was performed. [Build metadata](../output/dismissal-scroll-build.json).

Package `com.music.bitchord`, version `1.9-beta1`, code 30. Size: 67,794,560 bytes. SHA-256: `d515bfebc7cbdc25f549299c7e3d25ee09dd432bb724e2c32d42e4f0e8fac9e2`.
