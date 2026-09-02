# Audio-output validation

This checklist distinguishes the format BitChord asks Android to use from the
format the device mixer and DAC finally negotiate. Run it on every supported
phone and USB DAC before shipping an output-engine change.

## Test material

Use a local lossless track whose source properties are known: 24-bit/96 kHz or
24-bit/192 kHz FLAC, stereo, no DSP. Do not use a YouTube stream for this test:
it is lossy and normally 16-bit PCM after decode.

## AudioTrack 16-bit mode

1. In **Settings → Playback → Output precision**, choose **16-bit PCM**.
2. Change the option while audio is playing. Confirm playback resumes at the
   same queue item and position without restarting the app, then play the test
   file from the beginning.
3. Confirm sound, seeking, pause/resume, background playback, and a complete
   track play without underruns.
4. Capture `adb logcat` while playing and retain the `DECODE` line plus the
   device, Android version, wired/USB/Bluetooth route, and DAC indicator.

## AudioTrack 32-bit-float mode

1. Connect a USB DAC that explicitly advertises PCM-float support and enable
   **Prefer USB DAC**. Bluetooth and the built-in speaker are intentionally
   not valid float routes.
2. Choose **32-bit float** while audio is playing. Confirm the same queue item
   and position survive the live sink replacement.
3. Play the same known 24-bit test file. The setting makes Media3 configure
   `DefaultAudioSink` with float output; it is not a source-quality label.
4. Verify normal playback, seek, crossfade, volume changes, headphone unplug,
   and a 30-minute continuous run. Watch for `AudioTrack` initialization or
   underrun errors in logcat.
   On affected Samsung devices, `AUDIO_OUT` must name a non-SEC FLAC decoder
   (normally `c2.android.flac.decoder`) for a float session.
5. Verify the DAC's own status page/indicator reports the expected sample rate.
   Its bit-depth display must be interpreted as its negotiated input format;
   Android/OEM mixers may still resample a stream, so it is the authoritative
   final-hop reading.
6. Repeat without the DAC. The single output line must say **16-bit fallback**,
   and the same lossless FLAC must remain clean through an in-track upgrade.

## USB routing

1. Attach the DAC, enable **Prefer USB DAC**, and start a new playback session.
2. Confirm **Output status** names the DAC and shows the USB badge.
3. Unplug the DAC during playback: Android must reroute to the default device
   without a crash. Reattach and repeat.
4. Disable the preference and confirm Android again chooses its normal route.

## Required evidence for each device

- BitChord version and git revision.
- Device model, Android build, DAC model, and connection type.
- Test-file source properties.
- Settings selected, `DECODE`/AudioTrack logcat excerpt, and DAC sample-rate
  indication where available.
- Pass/fail results for playback, seek, crossfade, background playback, unplug,
  and 30-minute underrun-free playback.

AAudio, Oboe, and direct USB-class routes are separate native backends. They
require this same test matrix and must report their actual negotiated format
and fallback reason before being exposed as selectable production routes.
