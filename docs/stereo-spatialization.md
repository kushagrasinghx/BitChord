# Stereo Spatialization

Settings → Spatial audio → **Spatialize** turns stereo tracks into spatial audio for headphones. The track is upmixed
to 5.1, and the six channels play through virtual speakers in a listening room, rendered with measured head-related
impulse responses. Everything runs inside BitChord's own float DSP chain (`playback/spatializer/`), before the
equaliser. **Widen**, the original mid/side widener, is still there for speakers.

## Signal chain

```
stereo ──► upmixer (STFT, 2 → 5.1: L R C LFE Ls Rs) ──► 6 × 2 convolution (virtual speakers + room) ──► −8.3 dB ──► peak limiter ──► EQ ─► …
```

| Stage | File | What it does |
|---|---|---|
| Upmixer | `SurroundUpmixer.kt`, `UpmixerTables.kt` | sqrt-Hann STFT (2048 / hop 1024 up to 48 kHz). Per-bin left/right covariance, smoothed over 0.27 octave and 130 ms; the ambience is the smaller eigenvalue. Steering gains from lookup tables split the direct sound between the front channels and a phantom centre. The ambience goes to the surrounds through three all-pass combs and an EQ. Everything below 80 Hz (Linkwitz-Riley) plus the centre feeds the LFE channel. The tables are generated at runtime from ~25 parameters. |
| Virtual speakers | `PartitionedConvolver.kt`, `SpeakerResponses.kt` | Uniformly partitioned overlap-save convolution with the shipped 6 × 2 responses (`assets/spatializer/speakers_<rate>.bin`). Speakers sit at L/R ±49° / −10°, C 0° / −10°, Ls/Rs ±130°; the right-side speakers are exact mirror images of the left-side ones. The LFE is a dry 150 Hz low-passed feed. The room is mixed in 13 dB below the direct sound (measured over both ears the direct sound carries ≈ 15 dB more energy than the room); it reverberates for ≈ 290 ms (T30) with little bass. |
| Output | `PeakLimiter.kt`, `StereoSpatializer.kt` | −8.3 dB headroom (the upmix adds peaks of up to ~9 dB, mostly bass), then a stereo-linked look-ahead limiter at −0.3 dBFS with a 150 ms release. It turns the whole mix down, bass included, so it is tuned to act as little as possible. |

**Latency.** Output lags input by ≈ 2 hops + 5 ms, about 48 ms at 44.1/48 kHz. `PrecisionAudioSink` reports the
position that is audible (that much behind the frames played), so synced lyrics and PartySync stay in time. At end
of stream the sink pushes that much silence plus the room tail through the chain (`PrecisionAudioSink.drainTail`),
so track endings are not cut. Gapless tracks in the same format keep the spatializer running across the boundary; when
the next track changes sample rate or channel count, the sink holds the new configuration back until it has played
out the delayed end of the old track and 100 ms of its room, faded, as `DefaultAudioSink` does with its own
processors. A version swap seeks the incoming player ahead by the same delay, so both versions line up in the fade.

**Switching.** While spatial audio is on, Widen runs with the same delay as Spatialize, so switching between them is
a 60 ms crossfade between time-aligned signals; the incoming effect is started first and only faded in once it is
producing settled output. Turning spatial audio on or off changes the delay, so it is a short 20 ms crossfade, under
which ~50 ms of audio is skipped (off) or repeated (on); it never clicks.

**Loudness.** The spatialized output plays about 6 dB quieter than the stereo source: that is the headroom that
keeps the limiter idle on most music. On loud, dense masters it reduces by more than 0.1 dB about a tenth of the
time (more than 1 dB well under 1 %), which costs the bass ~0.1 dB in the loudest passages.

**Sample rates.** Responses ship for 44.1 and 48 kHz and match each other to within 0.03 dB up to 20 kHz; both
are prepared at service start, off the audio thread. Other rates from 22.05 to 192 kHz are resampled once, from the
matching family, with a polyphase windowed-sinc that keeps each response's level and frequency response, and
cached. Above 48 kHz the STFT frame doubles per octave, so its time and frequency resolution stays the same.

## Bit-perfect and lossless

Stereo Spatialization is an effect, so while it runs the output is new samples by definition. What it guarantees:

* **Off means untouched.** With spatial audio off, the spatial stage returns before reading a sample, adds no latency
  and no end-of-stream audio, and the sink hands the delegate the decoder's own bytes (16-bit stays 16-bit, 24-bit
  travels exactly as float). When it has nothing to act on, the same holds with it switched on: mono and
  multichannel files, rates it has no responses for (outside 22.05–192 kHz, e.g. 352.8/384 kHz DXD), Dolby Atmos
  tracks, and after it is switched off (once its 20 ms crossfade has run). `PrecisionAudioSinkTest` asserts this
  on the bytes handed to the delegate.
* **The readout says what is happening.** The Audio Pipeline dialog's bit-exact verdict and stereo row come from
  what the spatial stage is actually doing to the playing track, published live by the audible sink
  (`AudioOutputStatus.spatialEffect` / `spatialBypass`), not from the setting: "No — Stereo Spatialization"
  only while it is altering samples, and "100% (not stereo)" or "100% (no speaker responses at 352.8 kHz)" when
  it is on but passing audio through.
* **Native rate, full precision.** Audio is never resampled: the spatializer runs at the stream's own rate
  (hi-res 88.2–192 kHz included, with the responses resampled once, level-exact), on Float32 blocks like the rest
  of the chain. The convolution runs in double precision; measured against exact double-precision convolution its
  error is the Float32 rounding of its own output (≈ -150 dB below the signal; Float32 transforms left ≈ -147
  dBFS RMS and -130 dBFS peaks). The upmixer is Float32 by design.
* **Output format is the listener's.** The effect never changes the output encoding; with float output the
  spatialized signal reaches AudioTrack as Float32, with 16-bit output it is quantized like any other DSP stage's.

**Tests.** `StereoSpatializerTest` checks:

* the full chain on a deterministic signal, fed in awkward block sizes, against expected output computed offline by
  a separate floating-point implementation of the same chain (`src/test/resources/spatializer/golden_<rate>.bin`).
  The two must agree to better than −80 dB at 44.1 and 48 kHz;
* that hard-left, hard-right and centred sources land on the correct side, left and right as exact mirror images;
* that responses resampled to 22.05–192 kHz keep their level and frequency response (within 0.1 dB);
* that switching effects and turning them on and off never jumps from one sample to the next, and the reported
  latency follows;
* that a NaN or infinite input sample does not silence the spatializer;
* that the convolver matches exact double-precision convolution to better than -140 dB;
* the FFT (Float32 and double), the limiter and the end-of-stream tail.

`PrecisionAudioSinkTest` covers the end-of-stream drain (including under backpressure), the audible position, bit-exact
output whenever spatial audio has nothing to do, and holding a sample-rate change until the held audio has played.

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
ear, then made left/right symmetric: the right-side speakers' responses are mirror images of the left-side ones; the
centre speaker reaches both ears identically for its first 30 ms (direct sound and early reflections, which decide
where it is heard), and after that each ear keeps its own room, energy-matched to the other ear in time and frequency. The 44.1 kHz set is resampled from
the 48 kHz one with a linear-phase filter flat to 20 kHz. They are distributed under CC BY 4.0, with the attribution
above.
