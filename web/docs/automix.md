# Automix

Settings → Playback → Crossfade → **Automix** (also in Up Next's ⋯ menu).
The engine is AutoMix by David Finnegan (AGPL-3.0), ported into
`app/src/playback/automix/`. What was kept, and what changed and why, is in
that folder's `NOTICE.md`.

## What it does

1. **Analysis.** The playing track and the next one are downloaded once more
   from YouTube (the direct Opus URL), decoded at 44.1 kHz and analyzed in a
   worker with essentia.js: tempo, a beat grid for the opening and one for
   the end, downbeat, key (Camelot), energy curve, intro and outro. About
   12–18 s per track; cached in IndexedDB (`bitchord-automix`).
2. **Choosing.** The queue decides what comes next; only the playing and the
   next song are analysed. Until both are, nothing is planned or drawn on
   the seek bar, and a song whose mix wasn't ready plays to its end and
   crossfades there (as on the phone).
3. **Planning.** The blend starts on a phrase (8 bars) before the outgoing
   track's outro, and the incoming track starts so its full-energy phrase
   lands as the blend ends. Blend length is the setting's maximum, shortened
   for weaker grids (8 bars under 0.6 confidence, 4 under 0.45).
4. **Mixing**, on the Web Audio clock:
   - the incoming track time-stretched to the outgoing tempo (pitch kept),
     gliding back to its own over 24 s afterwards;
   - an equal-power cross;
   - a bass swap over one bar at the midpoint (−12 dB on long blends, −6 dB on
     short ones);
   - a high-shelf cross over the whole blend.

   A pair that can't be beatmatched (no grid on the incoming song, or tempos
   too far apart: the next song is slowed down by at most 10% and sped up
   by at most 25%) gets the phone's filter mix instead, still on the
   outgoing song's phrase. Only an outgoing song with no grid at its end
   gets a plain 12 s equal-power crossfade.
   **The loop mix** is what a pair with usable beat grids gets now:
   - **Outgoing:** at the start of a 4-bar loop in the song's last minute, the
     `<audio>` element hands its sound to a stretch deck (AutoMix's own
     signalsmith-stretch deck, over the decoded song). The loop is the latest
     4 bars that are instrumental (≤ 10% sung by the vocal mask) and loud in
     every bar.
   - **Incoming:** the earliest instrumental 4 bars of the next song come in
     over 4 bars, bass cut, stretched to the outgoing tempo.
   - **Tempo:** one master tempo ramps from the outgoing BPM to the incoming
     one at up to 2.5 BPM per bar, over any gap. It folds to the incoming
     song's double or half time when that's closer (111 → 169 ramps to 84.5).
     Both decks follow it beat by beat, so they stay locked.
   - **Exit:** the bass swaps mid-ramp; the outgoing loop drains away through
     a rising high-pass; the incoming loop lets go at its own boundary into
     the rest of its song; two bars later its `<audio>` element takes over.
   - **Song length:** the loops lengthen the join by the ramp; that's
     intended.
   - **Handovers:** element → deck and deck → element, measured on a click
     track, land within 5–8 ms of the beat.
5. **Handoff** at the blend's midpoint: the queue, Now Playing, lyrics and
   Media Session move to the incoming track there, as on the phone.

Pause, seek, Next or picking another track cancels a blend in flight; the
track in front plays on.

## How it plays

The two `<audio>` decks are routed through one AudioContext the first time
Automix runs (a media element can only be routed once, so it stays routed
for the page's life). After that the elements sit at volume 1 and the graph
carries volume (`master`) and the plain crossfade (`fader` per deck). Each
deck is `MediaElementSource → 3-band EQ → AutoMix gain → fader → master`.

AutoMix's worklet started tracks sample-accurately; a media element can't.
`engine/deck.ts` posts each scheduled change when its time comes, starting
the element its measured start latency early. While a blend runs, a servo
compares the element's position with the timeline every 40 ms and nudges
`playbackRate`: up to 3% audible, up to 10% while the deck is still below
gain 0.3, or a seek past 250 ms.

## Measured (headless Chromium, 2026-10-23)

- Future Nostalgia (115 BPM) → Don't Start Now (124 BPM):
  - rate 0.9276, 4 bars (8.3 s), bass swap at −6 dB;
  - beat-phase error −82 ms at the first sample (gain 0.02), −8 ms one second
    later, and −9 to −14 ms from then on.
- Don't Start Now → Cool (grid 0.07) took the 12 s fallback, as it should.
- Grid confidence by song:

  | Songs | Confidence |
  |---|---|
  | Dua Lipa, Doja Cat, Daft Punk, Send My Love | 0.25–0.75 |
  | Hello's rubato opening | 0.00 |
  | DNA. (tempo change) | 0.06 |
  | Cool | 0.07 |

## Testing

- `window.__automixSelfTest()` (dev builds) analyzes a synthetic 124 BPM
  A-minor loop. Expect BPM ≈ 123.93 and Camelot 8A.
- Dev builds expose `window.__bcAutomix` (decks, context, the loop) and
  `window.__bcAnalyzer`.
- `beatgrid.test.ts` covers the grid fit.

Not checked yet:
- Firefox;
- a real (headed) listen;
- a SABR-only track: there's no file to fetch, so its analysis fails and it
  takes the fallback.

## Stats for nerds

Settings → Playback → Show Stats for Nerds adds two lines under the artist in
the full-screen player:
- the stream (`Opus · 48 kHz · 133 kbps · Stereo · itag 251 · VISIONOS direct`);
- with Automix on, a dimmer line with each song's analysis, then the planned
  mix and the reason a pair isn't beatmatched.

The planned mix also shows on both seek bars, pulsing on the beat while it
plays.

## Beat This! (beats and downbeats)

The loop mix cuts on Beat This!'s detected downbeats: each loop runs from a
detected beat 1 to the detected beat 1 four bars on. Essentia's grid is only
the fallback.

- **Model:** `public/models/beat_this_final0_q8.onnx` is the official `final0`
  checkpoint (MIT), exported to ONNX on 2026-10-23 with only its MatMuls
  quantized to int8 (23 MB). It matches the float model on 99–100% of beats
  and every downbeat.
- **Front end:** `analysis/models.ts` matches the official preprocessing to
  within 0.0002.
- **The phone's file is broken:** `beat_this_int8.onnx` also quantized the
  convolutions and finds no beats.
- **Measured** on two Pioneer DJ demo tracks (128 → 120 BPM): decks locked
  within 5 ms of each other, beats at loop seams within 2 ms of the rest, and
  a median beat spread of 3.5–5 ms.
- **To re-export:** `torch.onnx.export` of `beat_this.inference.load_model`,
  wrapped to return `(beat, downbeat)`, input `input_spectrogram [1, time,
  128]`, opset 17. Then
  `onnxruntime.quantization.quantize_dynamic(op_types_to_quantize=['MatMul'],
  per_channel=True)`.
- **Download note:** Python's bundled CA list rejects cloud.cp.jku.at; curl
  with the Windows store verifies it.
