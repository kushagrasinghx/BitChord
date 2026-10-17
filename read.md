# BitChord — Simple Change Log

**Rule:** Add new dated notes at the bottom. Do not delete or rewrite older notes.

## 2026-10-08 — Radio no-repeat fix

**What changed:** With **Don’t repeat songs** on, AutoPlay now compares the main title without extra text in brackets. For example, “Savage Love (Laxed - Siren Beat)” and “Savage Love” count as the same song even when the artist credits differ. If the original and a remix appear together, the original is preferred.

**Files changed:**
- `shared/src/jvmSharedMain/kotlin/com/music/bitchord/playback/QueueBuilder.kt` — title matching and original-first ordering.
- `app/src/main/java/com/music/bitchord/playback/Autoplay.kt` — sends the no-repeat setting to the filter.
- `app/src/main/java/com/music/bitchord/MainActivity.kt` — applies the setting when starting radio.
- `app/src/main/java/com/music/bitchord/playback/PlaybackService.kt` — applies it when starting a station and refilling AutoPlay.

**Checks:** Added tests for the Savage Love example and other title cases. Shared JVM tests passed, and the Dev APK built successfully. The APK was not play-tested on a phone.

## 2026-10-09 — Code details for the no-repeat fix

### Queue filter (`QueueBuilder.kt`)

**Before:** A suggestion was rejected only when it matched the same recording (including artist credit).

```kotlin
if (taken.any { isSameRecording(it, candidate) }) continue
```

**After:** When **Don’t repeat songs** is on, it also rejects a matching base title when extra version text differs.

```kotlin
if (taken.any {
    isSameRecording(it, candidate) ||
        (avoidAlternateVersions && isSameSongAcrossMixVersions(it, candidate))
}) continue
```

The title-family check removes bracketed details before comparing:

```kotlin
title.replace(VERSION_PARENTHETICAL, " ")
```

So both `Savage Love (Laxed - Siren Beat)` and `Savage Love` reduce to `savage love`. Different artist credits do not let that version through. If the titles are already identical and have no version marker, the artist check stays, so unrelated songs called `Home` are not automatically blocked.

### Setting passed into the filter (`Autoplay.kt`)

**Before:**

```kotlin
QueueBuilder.extend(existing, related, limit)
```

**After:**

```kotlin
QueueBuilder.extend(existing, related, limit, avoidAlternateVersions)
```

`MainActivity.kt` and `PlaybackService.kt` pass the app’s **Don’t repeat songs** setting for a new radio and later AutoPlay refills.
