# Automix models and song detection

Automix measures audio, not a song title or a catalog BPM. Android/desktop use
the shared Beat This! tracker; the web worker uses the same front end and
postprocessing. Open-Unmix estimates vocal presence, while the existing DSP
or Essentia pass supplies key, energy and structural cues.

## Bundled weights

`native/analyzer/model-manifest.json` is the source for the model filenames,
SHA-256 checksums, tensor contracts and postprocessing revision. Run
`python scripts/automix_models.py generate` after editing it. Generated Kotlin
and TypeScript bindings are checked into the repository so builds need no
Python or model download.

| Model | Checkpoint | Input | License |
| --- | --- | --- | --- |
| Beat This! | `final0`, MatMul-only dynamic int8 | Mono 22,050 Hz → `[1, time, 128]` log-mel | MIT weights |
| Open-Unmix vocals | `umxhq-vocals`, int8 export | Stereo 44,100 Hz → `[1, 2, 2049, 960]` magnitude STFT | MIT weights |

The model binaries are identical on Android and web. Android verifies an
extracted copy before opening it and replaces corrupt or stale files
atomically. The web verifies downloaded bytes before creating an ONNX session
and retries failed downloads on a later track, after one minute. Tensor names,
output lengths and finite values are checked; unavailable models retain the
existing fallback analysis.

## Detection changes

- Chunking follows [CPJKU's current inference implementation](https://github.com/CPJKU/beat_this/blob/main/beat_this/inference.py):
  six padded frames at the outer ends, discarded prediction borders, a full
  final chunk and the first prediction kept in overlaps.
- Flat adjacent peak runs produce one beat. Sub-frame interpolation remains,
  and non-finite predictions cannot become beat or vocal evidence.
- Confidence combines regular spacing, model evidence above the zero-logit
  decision boundary and support from the number of beats. Regular but barely
  positive peaks previously scored about 0.84; they now score near zero.
  This is a conservative mixing score, not a calibrated accuracy probability.
- Independently detected downbeats snap only within 40 ms (or one tenth of
  a beat at very high tempos). A detection in a beatless break cannot be
  moved to an unrelated beat.
- Android scores the opening and ending separately when stitching windows.
  A strong ending cannot raise a weak opening's confidence. The web also
  reduces trust in a constant grid whose fit has excessive timing residual.
- Persisted profiles include the model/postprocessing revision. Android uses
  SHA-256 track filenames and also checks the stored track ID; IDs with the
  same Java hash can no longer share a profile. Web cache lookup finishes
  before a download starts, and storage failures do not discard valid analysis.

Changed models or postprocessing invalidate earlier profiles automatically.
The Android storage schema is 4; the web analysis version is 9. Increment the
manifest's `postprocessingRevision` whenever detection semantics change.

## Choosing a replacement

[Upstream still defaults to `final0`](https://github.com/CPJKU/beat_this#available-models).
`final1` and `final2` use different training seeds and need comparison against
your listening library. The `small0`–`small2` checkpoints are candidates for
lower inference cost: their upstream checkpoint files are about 8.1 MB rather
than 78 MB. Compare beat/downbeat accuracy, latency and thermal behavior before
changing the default. The bundled quantized `final0` is about 23 MB.

Keep Open-Unmix's MIT `umxhq` weights unless a replacement's weights license,
front end and vocal-mask behavior have been verified. The code license alone
does not establish the pretrained weights' license.

Export Beat This! using `beat_this.inference.load_model`, returning `beat` and
`downbeat` tensors. Use input `input_spectrogram [1, time, 128]`, dynamic time
axes and opset 17. Quantize only `MatMul` with per-channel dynamic int8; the
retired export that also quantized convolutions produced no usable beats.

After evaluating a candidate export:

```sh
python -m pip install onnx
python scripts/automix_models.py install-beat candidate.onnx --checkpoint final1
python scripts/automix_models.py check --onnx
```

Installation validates the graph before modifying bundled files, copies the
candidate to both platforms, updates its checksum and generated bindings, and
removes the replaced binary. The filename includes the checksum to prevent
stale browser or extracted-file reuse. Commit the manifest, both model files
and both generated bindings together.

An Open-Unmix export with the same magnitude-STFT contract can be installed
with `install-vocal candidate.onnx --checkpoint umxhq-vocals` (or `umx-vocals`).
It must use input `mix_magnitude` and output `target_magnitude`, both float32
with shape `[1, 2, 2049, 960]`.

## Verification

```sh
python scripts/automix_models.py check
./gradlew :shared:jvmTest
pnpm --dir web/app test -- src/playback/automix
pnpm --dir web/app typecheck
```

The shared tests open the real shipped ONNX graphs. Regression tests cover
chunk coverage, weak/irregular evidence, dropped beats, plateau peaks,
downbeat alignment, independent head/tail confidence and cache identity.
These checks do not establish real-song accuracy or audible transition
quality; compare candidates on annotated music and listen on a device before
publishing a model change.
