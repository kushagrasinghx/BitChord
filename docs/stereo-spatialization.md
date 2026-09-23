# Stereo Spatialization

Settings → Spatial audio → **Spatialize** turns stereo tracks into spatial audio for headphones. The track is upmixed
to 5.1, and the six channels play through virtual speakers in a listening room, rendered with measured head-related
impulse responses. Everything runs inside BitChord's own float DSP chain (`playback/spatializer/`), before the
equaliser. **Widen**, the original mid/side widener, is still there for speakers.

## Signal chain

```
stereo ──► upmixer (STFT, 2 → 5.1: L R C LFE Ls Rs) ──► 6 × 2 convolution (virtual speakers + room) ──► −8 dB ──► peak limiter ──► EQ ─► …
```

| Stage | File | What it does |
|---|---|---|
| Upmixer | `SurroundUpmixer.kt`, `UpmixerTables.kt` | sqrt-Hann STFT (2048 / hop 1024 up to 48 kHz). Per-bin left/right covariance, smoothed over 0.27 octave and 130 ms; the ambience is the smaller eigenvalue. Steering gains from lookup tables split the direct sound between the front channels and a phantom centre. The ambience goes to the surrounds through three all-pass combs and an EQ. Everything below 80 Hz (Linkwitz-Riley) plus the centre feeds the LFE channel. The tables are generated at runtime from ~25 parameters. |
| Virtual speakers | `PartitionedConvolver.kt`, `SpeakerResponses.kt` | Uniformly partitioned overlap-save convolution with the shipped 6 × 2 responses (`assets/spatializer/speakers_<rate>.bin`). Speakers sit at L/R ±49° / −10°, C 0° / −10°, Ls/Rs ±130°; the right-side speakers are exact mirror images of the left-side ones. The LFE is a dry 150 Hz low-passed feed. The room is mixed in 13 dB below the direct sound (measured over both ears the direct sound carries ≈ 15 dB more energy than the room); it reverberates for ≈ 290 ms (T30) with little bass. |
| Output | `PeakLimiter.kt`, `StereoSpatializer.kt` | −8 dB headroom (the upmix adds peaks of up to ~9 dB, mostly bass), then a stereo-linked look-ahead limiter at −1 dBFS with a 250 ms release. It only has work to do on rare peaks, so the bass is not squashed. |

**Latency.** Output lags input by ≈ 2 hops + 5 ms, about 48 ms at 44.1/48 kHz. `PrecisionAudioSink` reports the
position that is audible (that much behind the frames played), so synced lyrics and PartySync stay in time. At end
of stream the sink pushes that much silence plus the room tail through the chain (`PrecisionAudioSink.drainTail`),
so track endings are not cut. Gapless tracks in the same format keep the spatializer running across the boundary.

**Switching.** While spatial audio is on, Widen runs with the same delay as Spatialize, so switching between them is
a 60 ms crossfade between time-aligned signals; the incoming effect is started first and only faded in once it is
producing settled output. Turning spatial audio on or off changes the delay, so it is a short 20 ms crossfade, under
which ~50 ms of audio is skipped (off) or repeated (on); it never clicks.

**Loudness.** The spatialized output plays about 6 dB quieter than the stereo source: that is the headroom that
keeps the limiter idle. On loud masters it still reduces by more than 1 dB only a fraction of a percent of the time
(by more than 0.1 dB up to about a fifth of the time, because of its slow release).

**Sample rates.** Responses ship for 44.1 and 48 kHz and match each other to within 0.03 dB up to 20 kHz; both
are prepared at service start, off the audio thread. Other rates from 22.05 to 192 kHz are resampled once, from the
matching family, with a polyphase windowed-sinc that keeps each response's level and frequency response, and
cached. Above 48 kHz the STFT frame doubles per octave, so its time and frequency resolution stays the same.

**Tests.** `StereoSpatializerTest` checks:

* the full chain on a deterministic signal, fed in awkward block sizes, against expected output computed offline by
  a separate floating-point implementation of the same chain (`src/test/resources/spatializer/golden_<rate>.bin`).
  The two must agree to better than −80 dB at 44.1 and 48 kHz;
* that hard-left, hard-right and centred sources land on the correct side, left and right as exact mirror images;
* that responses resampled to 22.05–192 kHz keep their level and frequency response (within 0.1 dB);
* that switching effects and turning them on and off never jumps from one sample to the next, and the reported
  latency follows;
* that a NaN or infinite input sample does not silence the spatializer;
* the FFT, the limiter and the end-of-stream tail.

`PrecisionAudioSinkTest` covers the end-of-stream drain, including under backpressure, and the audible position.

## Credits

* **Design.** The design (upmix to 5.1, then virtual speakers in a room) follows the Spatialize Stereo feature in
  macOS, and so does its tuning: the upmixer's parameters, the diffuse-field target and the room's decay statistics.
  Its behaviour was analysed and reimplemented from scratch for BitChord. No Apple code, HRTFs or room responses are
  included.
* **HRTFs:** Meta Reality Labs Research, SS2 dataset, head-and-torso simulator measurement (`SS2_HATS051123_1`),
  licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Modified for this use: diffuse-field
  equalised; the measured interaural phase is kept below ~1.3–2.2 kHz, and above that both ears share one
  minimum-phase response; truncated to 128 taps.
* **Room:** synthesised from the same HRTFs, using image sources plus stochastic reflections with a late tail.

The shipped `speakers_<rate>.bin` files are these HRTFs and this room rendered into one response per speaker and
ear, then made left/right symmetric: the right-side speakers' responses are mirror images of the left-side ones, and
the centre's direct sound is the same in both ears (each ear keeps its own room). The 44.1 kHz set is resampled from
the 48 kHz one with a linear-phase filter flat to 20 kHz. They are distributed under CC BY 4.0, with the attribution
above.
