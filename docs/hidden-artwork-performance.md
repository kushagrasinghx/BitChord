# Pause hidden artist artwork and skip unused player captures

Baseline: `347c826a`, the user-tested scroll-recovery build. This batch preserves the liquid-glass renderer, scrolling physics and animation timing.

## Artist video visibility

An artist's motion header is outside its LazyColumn and stays composed after the image scrolls offscreen. Its decoder previously kept playing for as long as the app was foregrounded, including behind Now Playing.

The page now receives the app's artwork-motion permission, restricted to the current detail destination. Its header also derives visibility from the same position used to place the image: `headerTop + artHeight > 0`. This handles an image that has scrolled away while the rest of list item zero remains visible. Only changes to that visibility boolean reach composition; list placement continues to read the pixel offset independently. Opening the artist's shelf also pauses the outgoing header during its fade.

Hidden artwork uses the existing pause control. The video session, position and last rendered texture remain retained while composed; the change does not explicitly detach the surface, release the decoder or reset the artwork fade. Returning to the visible header resumes playback. Existing app-foreground gating remains in the player component. Pausing an already prepared player does not eliminate its retained resources or guarantee that startup performs no decoding.

## Unused Now Playing captures

Fullscreen Spotify artwork and the tablet artwork backdrop do not consume the hero video's palette bitmap. The tablet backdrop also does not consume the sleeve video's bitmap. These modes now pass a null frame callback instead of asking for a first-frame texture readback and discarding its result. Periodic refresh eligibility and intervals remain unchanged.

Queue, lyrics and docking transitions continue to use the existing capture pause. They do not toggle callback presence just for a temporary pause, avoiding an extra first-frame request after an interrupted transition. A real switch from a no-consumer presentation to a frame-consuming backdrop still requests its initial palette capture. Visible motion, rendered-state notifications and frame consumers that need a palette are retained.

## Validation scope

Independent source review checked visibility boundaries, outgoing destinations, retained video lifetime and capture eligibility. No emulator, automated phone gestures or candidate performance capture was used. The user has accepted this batch; specific artist-header resume behavior has not been independently verified on a physical display. Source review and JVM regressions do not establish a frame-rate gain.

All **60 focused JVM regressions pass**, covering the existing glass/cache, shadow, player lifecycle/layout/motion, overscroll, frame-measurement and playback-persistence cases. These checks do not exercise the new artist visibility gate on a physical display.

## Test APK

The optimized [ARM64 production APK](../output/BitChord-1.9-hidden-artwork-arm64.apk) builds successfully and passes signature verification with the same certificate as the preceding test build. Package `com.music.bitchord`, version `1.9-beta1`, code 30; not debuggable. The build used one worker, a 3 GB Java heap and in-process Kotlin compilation. Existing Kotlin/R8/lint metadata warnings remain. User changes to `gradle.properties` are preserved.

Size: 67,778,180 bytes. SHA-256: `cc9fc37c0523a47b89545edc720782604ba38bccd47cec8a990e28f29f0c5125`. [Build metadata](../output/hidden-artwork-build.json) records the exact source hashes.

The APK was checksum-verified after transfer and replacement-installed successfully over wireless ADB at `:37901`, preserving app data. Installation only; the app was not launched or driven automatically. The user subsequently accepted retaining the changes.

Suggested manual check: on an artist with motion artwork, scroll the image completely away and back, open/close Now Playing, and open/close an artist shelf. Check that visible artwork resumes without a black flash. For video-backed Now Playing artwork, check fullscreen Spotify playback and any enabled artwork-mesh backdrop after returning from queue/lyrics. This is a test checklist, not a claim that these device behaviors have been verified.

## User result and decision

The user reported that the remaining changes were good and solid, and approved keeping this batch after clarifying that the artist-page header is separate from the artwork that docks into the mini-player. The user explicitly chose to leave the pre-existing docking pause rule unchanged. No idle grace period or additional transition-video changes were made after the installed build. No quantified frame-delivery improvement is claimed for this batch.
