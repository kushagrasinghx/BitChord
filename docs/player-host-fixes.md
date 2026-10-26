# Player host and reopening work

The preceding decoder/lifecycle APK was only marginally better in the user's report; that remains an unconfirmed improvement. This batch targets the repeated dialog-window and player-layout setup observed in the physical traces.

## Changes

Normal Now Playing now uses `PlayerOverlayHost` inside the activity's existing window. A Material3 `BottomSheetScaffold` keeps the existing sheet/scroll interaction, while `PlayerSheetMotion` retains the mini-player pull and artwork docking spring. The player inherits the activity's refresh-rate policy and does not allocate a separate Android dialog window on every opening. The existing reduced-animation presentation keeps its modal host.

After the first visit, the host uses `ReusableContentHost` to retain eligible UI/layout nodes between visits. Deactivation forgets remembered effects and video sessions, so this is not a continuously running hidden player. Subcomposed or platform-view content can have its own reuse policy; this does not guarantee that every player node or composition survives. The whole closed host is left unplaced, preventing its transparent Material surfaces from drawing or intercepting Home touches.

Underlying page semantics and keyboard focus are disabled while the overlay is visible. The overlay supplies a pane title, accessibility dismissal and a back handler registered before its child panel handlers. Direct/widget opens use the same opening motion as mini-player taps; repeated attachment is idempotent, and each opening waits for fresh layout readiness. The sheet shape still permits artwork to fly above its edge into the mini player. Dock coordinates remain in screen space for both host types.

Root-page glass contrast sampling pauses while this full-screen overlay covers the page, then resumes when it closes. Glass geometry/materials are retained; this stops the hidden root sampler's GPU readback and pixel analysis. Detail pages can own independent samplers, so this is specifically a root-sampler change.

Diagnostic builds add `BitChord.playerShown`, `BitChord.playerHidden` and `BitChord.playerMeasure` trace sections. The normal player is now in the **main** recorded window. It should not produce a separate `nowPlaying` window in a native recording; the reduced-animation modal still can. First-draw filtering and per-window totals therefore differ from older captures. Compare actions and presentation timestamps, rather than treating old/new per-window percentiles as identical populations.

## Validation and installation

- Release compilation and ARM64 production assembly pass, including the Wear companion.
- **38 focused tests pass:** the previous 34 plus four player-motion tests. New cases cover a direct open waiting for layout, attaching without restarting the tap/settled animation, fresh readiness after reuse, and closing before initial layout without a later reopening. Tests use a virtual frame clock and explicit snapshot notifications; they do not substitute for Android UI checks.
- APK signing matches the previous installed builds. Package `com.music.bitchord`, version `1.9-beta1`, versionCode 30. The optimized root remains below 10,000 code units at **9,316 / 210 registers**; no smoothness claim is inferred from that count.
- The APK was transferred with a matching SHA-256 and installed successfully on the physical POCO over wireless ADB using replacement installation, preserving app data. The user requested installation only and will test later; no automated UI interaction or new performance capture was performed. No emulator was used.
- The user's Gradle settings and scratch document are excluded from this work.

[APK](../output/BitChord-1.9-player-host-arm64.apk), [build metadata](../output/player-host-build.json), [DEX sizes](../output/player-host-dex-sizes.json).

SHA-256: `804bf2145e1f24838d200fd1ac6efc47d9c60b4b0426dde0d26592f4410b0bb2`.

When testing, check repeated open/close, back and pull gestures, artwork docking, Home interaction after closing, lyrics/queue navigation and background/return. Rotation, keyboard and accessibility behavior also need a device check because the window hosting changed.

This eliminates the normal player's per-opening Android window creation path, but initial player composition/layout still exists. The cold 71 ms Vulkan pipeline event has not been attributed to a specific draw/effect. The user subsequently reported noticeably smoother Home scrolling and mini-player transitions, together with a dismissal crash and remaining vertical scrolling / player endpoint interruptions. See [the crash and scrolling follow-up](player-dismissal-scroll-fixes.md). A fresh trace should separate first visit, warm reopen, scrolling and background/return before deciding the next graphics change.
