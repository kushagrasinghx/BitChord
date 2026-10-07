# Lyrics translation

## Overview

BitChord can translate the currently displayed lyrics into the language selected
inside the app. The translation keeps the original lyric timestamps, scroll
position, line activation and playback-driven highlighting. A compact button in
the full lyrics view switches between the original and translated versions.

This contribution is intentionally limited to lyrics translation. It does not
change Automix, playback transitions, source replacement or audio processing.

## User flow

1. Open the full lyrics view.
2. Tap the translation button beside the source credit.
3. BitChord resolves the active application locale and requests a translation.
4. If the detected source language matches the app language, the original lyrics
   remain visible and BitChord explains that no translation is needed.
5. Otherwise, the translated text appears with a short particle transition.
6. Tap the same button again to return immediately to the original lyrics.

The original lyrics are always retained in memory and are never overwritten.
While the translated version is selected, the compact current-lyric strip above
the playback scrubber uses that version too. Closing the full lyrics view does
not switch the strip back to the source language.

## Destination language

The destination comes from
`AppCompatDelegate.getApplicationLocales()`, which is the language explicitly
selected in BitChord's language dialog. When the application locale list is
empty, BitChord follows Android's effective configuration locale, matching the
app's normal “follow system” behaviour.

The translation therefore follows the language shown by BitChord, not an
unrelated device default. Language tags are normalized to their base language,
including legacy aliases such as `iw` → `he` and `in` → `id`.

## Translation pipeline

`LyricsTranslation` performs translation on demand:

- Blank instrumental lines are preserved and never sent.
- Section labels and background vocals are translated as independent text slots.
- Text slots are batched up to 3,500 characters using private-use delimiters,
  which preserves the exact mapping between the response and lyric lines.
- At most two short requests run concurrently, avoiding contention with the
  audio stream while keeping long lyrics responsive.
- The source language is automatically detected by the translation response.
- Requests have a 12-second call deadline and are cancelled if the song, lyrics
  or application language changes.
- A partial or malformed response is discarded; original lyrics stay on screen.

The current implementation uses Google's lightweight web translation endpoint
over HTTPS with `sl=auto`. It does not install an on-device translation model or
require an API key. This endpoint is not a versioned public API, so it may need to
be replaced by a maintainer-selected provider in the future. The provider is
isolated inside `LyricsTranslation.kt` to keep that replacement small.

## Timing and lyric animation

Every translated `LyricLine` retains:

- `timeMs`
- `sungUntilMs`
- background-vocal placement
- instrumental gaps
- the original list position

Exact source-language word boundaries cannot describe a translated sentence
because word order and word count can change. For word-synced lyrics, BitChord
projects the original character progress onto the translated text. The sweep
therefore follows the original holds, pauses and pace changes instead of running
uniformly from start to end. Bloom also uses the source vocal envelope. This is
an approximate reading guide, not semantic word alignment or phoneme timing in
the target language. Line-synced lyrics remain line-synced. Cached translations
are rebuilt with this timing on load; no cache deletion or download is needed.

The renderer interpolates within glyph bounds, avoiding next-row caret positions
that could send the highlight backwards at a line wrap. Right-to-left paragraphs
use a reversed reveal and glow direction.

## Visual response

Switching versions keeps the existing lyrics list and playback clock mounted.
For 540 ms, small particles drift from actual glyph positions in the active lyric
and its immediate neighbours (up to 18 per voice). Unsynchronized lyrics use the
first four lines. Positions and drift vectors are cached at text layout time.
A single animation clock is read only in the draw phase, so it does not
recompose, rescale or clip the lyrics list on every frame. Soft halos use two
circles rather than extra blur layers; particles fade in and out continuously.

The effect only animates draw properties and does not intercept touches.
Enabling BitChord's **Reduce animations** preference turns it into an immediate
text swap. Compose's animator respects the platform duration scale. Going into
the background cancels decoration, and returning or reopening the panel does
not replay an old toggle. Rapid toggles replace the previous animation.

## Storage and performance

No language packs or ML translation models are downloaded.

Translated text uses a two-level cache:

- 12 entries in memory for instant toggling and recently played songs;
- GZIP-compressed JSON files in Android's reclaimable `cacheDir`;
- a hard 2 MiB disk limit, trimmed least-recently-used first;
- SHA-256 keys derived from track, destination language and source lyrics;
- a cache format version so incompatible entries are ignored safely.

A typical cached song occupies only a few kilobytes. Clearing the application's
cache removes all persisted translations without affecting downloaded music or
settings.

## Failure behaviour

- Network failure: a localized message is shown and the original stays visible.
- Same source and destination language: no translated view is created.
- Song or locale changes: the pending request is cancelled and UI state resets.
- Invalid line mapping: the whole response is rejected instead of displaying
  translations against the wrong timestamps.
- Repeated toggle after success: no network request; it uses the in-memory result.

## Files

- `app/src/main/java/com/music/bitchord/data/lyrics/LyricsTranslation.kt`
  contains batching, source detection, timing reconstruction and bounded caching.
- `app/src/main/java/com/music/bitchord/ui/player/NowPlayingScreen.kt`
  contains the toggle, state handling and motion response.
- `app/src/main/res/values-*/strings.xml` localizes the new UI messages for every
  language currently offered by BitChord.

## Manual verification checklist

- Translate English lyrics with BitChord set to Spanish.
- Translate the same song again and verify the cached response is immediate.
- Switch back and forth without losing the active line or scroll position.
- Check held notes, vocal pauses and two-line phrases: the translated sweep
  should follow the source progress without reversing near a line wrap.
- Toggle repeatedly, close/reopen the panel, and background/restore the app;
  particles should settle completely and old transitions should not replay.
- Use Spanish lyrics while BitChord is set to Spanish and verify no translation
  view is created.
- Change BitChord to another supported language and verify the new destination.
- Change tracks while a translation is loading and verify no stale text appears.
- Enable **Reduce animations** and verify the particle transition is skipped.
- Clear the app cache and verify original lyrics continue to work normally.
