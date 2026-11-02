# AutoMix

The DJ engine in this folder is ported from **AutoMix**
(https://github.com/heydavidf/automix), Copyright (C) 2026 David Finnegan,
licensed under the GNU Affero General Public License v3.0 (`./LICENSE`).
BitChord is GPL-3.0; GPLv3 §13 permits combining it with AGPLv3 code, and the
files here stay under the AGPL. Because the web player is served over a
network, its users are offered the source (the repository is public).

AutoMix's analysis uses **essentia.js** (AGPL-3.0, Music Technology Group,
Universitat Pompeu Fabra) and **idb** (ISC, Jake Archibald).

## What is verbatim

`analysis/energy.ts`, `analysis/camelot.ts`, `analysis/testtones.ts`,
`analysis/essentia.d.ts`, `engine/timeline.ts`, `engine/eq.ts` and
`engine/selector.ts` are AutoMix's files with only this attribution header
added (plus `!` where BitChord's `noUncheckedIndexedAccess` needs one).
`engine/transition-scheduler.ts` changes only how it reaches the AudioContext.

## What changed in the analysis, and why

Measured on real YouTube tracks (2026-10-23), AutoMix's beat grids were
almost never usable, so nearly every mix took its plain-crossfade fallback:

- **`fitGrid` (analysis/beatgrid.ts)** numbered essentia's ticks
  consecutively. A beatless breakdown leaves a gap in the ticks, so every
  later tick got the wrong beat number: Daft Punk's One More Time (a steady
  123 BPM) fitted at 117.9 BPM with a residual of ~850 ms. It is now a
  phase-coherence fit (the period on which the ticks line up best), which no
  gap can mis-number. `beatgrid.test.ts` has the case; the old fit gives
  90 BPM and a 15 s residual on it.
- **Head and tail grids (analysis.worker.ts, types.ts, engine/grid.ts).**
  One More Time's breakdown comes back half a beat off, so no single grid
  fits the whole song. The worker runs AutoMix's rhythm step twice: over the
  first 100 s (the grid a track is mixed *in* on, in the usual fields) and
  over the last 120 s (`tailGrid`, the grid it is mixed *out* on). The mix
  loop reads the playing track through `asOutgoing()`, so the scheduler is
  unchanged.
- **Confidence.** AutoMix divided essentia's beat confidence by 5.32, its
  maximum; essentia documents 1.5–3.5 as "good", so a well-tracked song
  scored ~0.3 before any other factor and almost nothing cleared the 0.2
  gate. It is read against 3.5 now, and the residual is counted from the
  ~20 ms essentia's own tick wobble always leaves. Measured: steady pop and
  dance tracks score 0.25–0.75, Hello's rubato opening 0.00, DNA.'s tempo
  change 0.06.

`ANALYSIS_VERSION` is 3 for all of this.

## What is adapted to the player, and why

AutoMix plays whole decoded files through `signalsmith-stretch` worklets.
BitChord streams YouTube through Shaka into two `<audio>` decks, which the
lyrics, video, Listen Together and Media Session code all read. So:

- `engine/deck.ts` keeps AutoMix's Deck interface (Timeline mirror,
  3-band EQ, gain, `playAt` / `pauseAt` / `setRate`, scheduled changes posted
  when imminent) over a media element routed through Web Audio. Tempo
  matching uses the element's pitch-preserving `playbackRate`; a phase-lock
  servo holds it on the timeline in place of the worklet's sample-accurate
  scheduling (measured: within ~10 ms of the beat a second into the blend).
- `analysis/analyzer.ts` downloads the track's own audio from YouTube
  (`fetchAudio.ts`, the direct URL Shaka plays) instead of reading a local
  file.
- `engine/autodj.ts` follows BitChord's queue for the next track and hands
  over at the blend's midpoint, as the phone does. Only the playing and the
  next song are analysed; until both are, nothing is planned or drawn, and
  if they aren't ready in time the song plays to its end and crossfades
  there. AutoMix's harmonic selector (`engine/selector.ts`) is kept but not
  used: the user asked for only the next song to be analysed.
- `engine/transition-scheduler.ts` adds the phone's DJ_FILTER for a pair
  that can't be beatmatched (no incoming grid, or tempos too far apart:
  the next song slowed past 10% or sped up past 25%): phrase-aligned on the outgoing beat, a rising high-pass out, an
  opening low-pass in, the bass handed over at the midpoint. The plain
  crossfade is left for an outgoing song with no grid at its end. The
  outgoing deck is never rate-nudged; the incoming one follows its actual
  position.
- `analysis/models.ts` ports the phone's Beat This! and open-unmix front
  ends and runs them with onnxruntime-web. Beat This! uses a fresh export of
  the official final0 checkpoint (`public/models/beat_this_final0_q8.onnx`,
  MIT weights); the phone's `beat_this_int8.onnx` finds no beats.
- `db.ts` caches analyses in IndexedDB by YouTube video id.

## The loop mix (BitChord)

`engine/loopDeck.ts` is AutoMix's signalsmith-stretch deck again, playing a
decoded region of a song, and `engine/loopMix.ts` plans and schedules a DJ
loop mix on two of them:
- instrumental 4-bar loops picked with the open-unmix vocal mask;
- a master tempo ramped beat by beat between any two BPMs;
- handovers to and from the player's `<audio>` elements.

`engine/stretch-types.d.ts` is AutoMix's type file, verbatim.
