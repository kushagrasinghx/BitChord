# Lyrics (phase 8)

Prompt §10. Our providers decide the lyrics; **am-lyrics renders them, unmodified**.

## Providers

`app/src/lyrics/`, ported from `shared/…/data/lyrics/`:

| File | Port of |
|---|---|
| `sources.ts` | `LyricsSource.kt` (order and labels; SimpMusic and Megalobiz left out as hidden), `LyricsQuery.kt` |
| `model.ts` | `LyricLine.kt` (data only), `SyllableRuns.kt`, `LyricGaps.kt`, `BackgroundVocals.kt`, `LyricAlignments.kt` |
| `parse.ts` | `TtmlLyrics.kt`, `EnhancedLrc.kt`, `LrcLib.parseLrc`, `KaraokeLrc`, `ProviderLyrics.kt` |
| `providers.ts` | every provider file |
| `repository.ts` | `LyricsRepository.kt` (the race) |
| `settings.ts` | the desktop's lyric settings (order, on/off, prefer syllables, PaxSenix key) |
| `clock.ts` | `LyricClock.kt`, verbatim |
| `ttml.ts` | writes TTML from lines, for am-lyrics |

The race, as on the phone:
- Every enabled source is asked at once, and answers are taken in priority order.
- A word-timed answer wins.
- Otherwise the first timed one wins, or with "prefer syllable timing" the search carries on for a word-timed one.
- Genius is only asked if everything above it missed.
- BiniLyrics names the recording (an ISRC) first, within 2.5 s.
- Results are cached for 12 hours per track.

## Who can be reached from the page (measured 2026-10-16)

| Host | CORS | Route |
|---|---|---|
| lrc.red (search and `/s/{isrc}.ttml`), lyrics-api.binimum.org (307 to lrc.red, both `*`), lyrics.paxsenix.org, lrclib.net | `*` | direct |
| lyricsplus.prjktla.my.id, lyricsplus.binimum.org (the only LyricsPlus mirrors left; atomix.one, `.workers.dev` and both `.vercel.app` were dead on 2026-10-17 and dropped) | `*` | direct |
| lyrics-api.boidu.dev, unison.boidu.dev | echoes `localhost` only | direct, falling back to the extension elsewhere |
| KuGou, Musixmatch, Genius, music.apple.com and amp-api (PaxSenix's Apple search), api.paxsenix.org | none | extension |
| translate.googleapis.com (am-lyrics' translation) | `*` | direct |

How the extension route works:
- `lyrics.fetch` (`extension/lib/lyricsFetch.ts`) serves only the hosts in `lib/lyricHosts.ts`.
- Each host is an `optional_host_permissions` entry.
- Chrome only prompts in answer to a click inside the extension. So `lyrics.requestPermissions` opens `grant.html`, which asks.
- Until a host is granted, requests to it fail with `permission`. Lyric Settings offers "Allow"; the panel offers it too, but only when nothing was found.
- The grant happens in the extension's tab, so when BitChord's tab comes back (focus or visibility) the app asks `lyrics.permissions` about the refused hosts. Any that are allowed now leave the notice, the lyric cache is dropped, and the lookup runs again.
- Session rules give the extension's own requests:
  - Apple's Origin, for `amp-api`.
  - Musixmatch's `mxm_bab=AB` cookie.

## Rendering

- `shell/LyricsPanel.tsx` creates `<am-lyrics>` itself, with `ttml` set before it's attached.
  - am-lyrics starts loading on `connectedCallback`, and through the React wrapper the properties arrive after that.
  - That first load went to am-lyrics' own providers with no title, finished after ours, and wiped it (seen in testing: only the footer rendered).
  - The React wrapper and `@lit/react` aren't used for that reason.
- Apple TTML from a provider is passed through untouched (translations, transliterations, songwriters, song parts survive). Everything else is written by `ttml.ts`:
  - syllables as adjacent spans, a space between words;
  - `x-bg` for the answering vocal;
  - `v1`/`v2` voices for duets (`v2` typed `other`, so a song opening on the answering side starts on the right, by am-lyrics' own alternation rule);
  - untimed paragraphs for unsynced lyrics.
- am-lyrics' footer offers to fetch from its own providers. A stylesheet added to its shadow root hides it with `visibility: hidden`; the library file isn't patched (MPL-2.0 stays clean). Our provider picker sits above.
  - Not `display: none`: am-lyrics measures its scroll range to the footer, so a collapsed footer made the range zero and the lyrics never followed the song.
- Translate and Romanise are am-lyrics' own buttons (Google's endpoint, the same one `LyricsTranslation.kt` uses). They translate to English only.
- The clock (`LyricClock`, verbatim) reads `audio.currentTime` each animation frame, stamped with `performance.now()` at that moment. Seeks and track changes are announced; `line-click` seeks the player. The frame loop stops while paused.
- The panel's code (and am-lyrics, about 170 KB) loads the first time the panel opens.

## Full-screen player

The LCD's artwork opens it (`shell/FullScreenPlayer.tsx`). The lyrics column is the same `LyricsPanel`, with `variant="full"`: the element gets `data-full`, and a rule in the adopted stylesheet replaces am-lyrics' three container tiers with Apple's full-screen sizes (28 → 38 → 50 → 72px at 980 / 1320 / 1680px) and puts the current line 30% down. The side panel is hidden while it's open, so only one am-lyrics runs. The background is `MeshBackdrop`: the artwork averaged into a 4×4 grid (`lib/meshPalette.ts`), painted as drifting radial blobs on a 64×40 canvas at up to 30 fps and scaled up by the GPU; it stops while paused or with reduced motion.

## Verified

Playwright Chromium and stock Firefox, with the extension:
- Planet Her "Woman": lrc.red, 66 lines.
- After a seek the right line is active, and the element's time stays within 5–25 ms of the audio.
- Clicking a line seeks to it.
- The footer is hidden.
- Picking LRCLIB swaps the lyrics.
- Translate on Despacito: 75 translated lines.

## Not done or not verified

- The extension-only providers (KuGou, Musixmatch, Genius, PaxSenix's Apple search) end to end: granting needs a real click on the browser's permission prompt, which the headless harness can't give. Only the refusal path (`permission`, then the Allow offer) was seen.
- Choosing a translation language other than English.
