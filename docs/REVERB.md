# Reverb

BitChord has a reverb of its own. It runs inside the app's audio pipeline,
before the samples reach the system, and works the same on every platform.

## Where it is

| Platform | Settings | Quick toggle |
|---|---|---|
| Android phone and tablet | Settings › Playback › **Reverb**. Turn it on and the controls show underneath. | Player › audio-output capsule › **Reverb** |
| Android TV | Settings › Sound › **Reverb** | The **waves** button on the Now Playing transport |
| Desktop (macOS, Linux, Windows) | Settings › Playback › **Reverb** | Player › audio-output capsule › **Reverb** |

The quick toggle and the settings switch are the same setting, so they can't
disagree. Everything is saved and restored on restart: on Android in the app's
preferences (backups include it), on desktop in the desktop preferences store.

## Controls

Four presets set the room:

| Preset | Decay (RT60) | Pre-delay | Damping |
|---|---|---|---|
| Room | 0.6 s | 5 ms | 6 kHz |
| Hall (default) | 2.2 s | 20 ms | 4.5 kHz |
| Plate | 1.5 s | 0 ms | 9 kHz |
| Cathedral | 4.0 s | 30 ms | 3 kHz |

- **Decay**: 0.2–6 s, the time the tail takes to fall 60 dB.
- **Pre-delay**: 0–200 ms of silence before the tail starts.
- **Damping**: 1–16 kHz, where the tail starts losing treble.
- **Mix**: 0–100% wet, blended as `(1 − mix)·dry + mix·wet`. Default 35%.
- **Width**: 0–100% stereo spread of the tail.

Picking a preset changes only the room (decay, pre-delay, damping). Mix and
width stay where you left them. **Reset to defaults** puts all five back to
Hall at 35% and full width.

## How it works

The DSP is one class, `shared/.../playback/ReverbProcessor.kt`, and both
engines use it:

- **Android and Android TV**: one instance per ExoPlayer, run as a stage of the
  Float32 `DspChain` inside `PrecisionAudioSink`. The order is spatial →
  equaliser → **reverb** → transition filter → loudness, so the loudness
  limiter sees the extra energy the tail adds. TV builds from the phone's
  sources, so it uses the same engine.
- **Desktop**: one instance on the engine's final output, after the crossfade
  blend and the speed stretch. The tail carries across track transitions, and a
  crossfade doesn't run a second reverb.

Rather than convolving against synthesised impulse responses, BitChord uses an
algorithmic tank in Freeverb's layout: per side, 8 damped feedback combs feed
4 allpasses. With this design:

- every slider applies on the next audio block, with no impulse to regenerate;
- it adds no latency and allocates nothing once the stream is configured;
- it costs a few dozen multiply-adds per frame.

Each comb's feedback is derived from the decay (`g = 10^(-3·d/RT60)`), and the
damping is a low-pass inside every loop. The wet signal is normalised to
roughly the input's energy, so each preset plays at about the same loudness.
Each preset is a size, distance and brightness of room rather than a
recorded impulse.

Turning reverb on or off, or moving the mix, glides the levels over about
20 ms, so changing it mid-song doesn't click or pause playback. Once a
switch-off has faded out, the stage passes audio through untouched. On
Android that keeps the bit-exact path intact, and the Audio Pipeline readout
reports "No — reverb" while it is on. A seek clears the tail, so the audio from
before the seek doesn't ring on.

## Limitations

- **Stereo is the main target.** Mono tracks get the sum of both tails. Tracks
  with more than two channels (5.1 beds, Dolby Atmos decoded to PCM) get the
  tail on the front left/right pair only. Every channel's dry level is scaled
  together, so the front-to-rear balance stays the same.
- **Android passthrough/offload and Cast**: no PCM passes through the app's
  sink on these paths, so the reverb doesn't run. The equaliser has the same
  limitation.
- **Android crossfades**: each player has its own tank. When a crossfade ends,
  the outgoing track's tail stops with that player, but by then the crossfade
  has already faded it out.
