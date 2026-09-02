# Automix AI: electronic transition ranker

`Automix AI [BETA]` is an opt-in persisted preference. The first bundled model
is a small ONNX logistic ranker trained from UnmixDB electronic-music mix
annotations (5,424 positive transitions; held-out AUC 0.904). It is encoded as
an Android asset and loaded locally only when the switch is enabled.

The deterministic beat/key/phrase/vocal safety rules remain the source of
truth. The model never creates a transition: it only turns a low-scoring
already-safe `DJ_BLEND` into the existing conservative `DJ_FILTER` plan.

## Goal

The future model ranks already-safe transition candidates; it must not create
an unsafe transition or bypass the current fallback policy. The planner still
owns hard guards such as missing analysis, incompatible tempo stretch, and
simultaneous vocals.

The model should answer one compact question per prepared pair:

```text
Given candidate A and candidate B, which safe transition is preferable?
```

It should return a compatibility score and optional ranking among existing
plans (`beatmatched`, musical crossfade, or simple crossfade). It must never
run over a complete decoded track during playback.

## Bundled first model

The shipped ranker consumes four bounded plan facts: normalised BPM distance,
stretch distance, overlap duration and fade-duration difference (neutral for
the current one-overlap plan). Its artifact is 549 B. It is deliberately tiny
because the hard musical work remains in Automix's DSP and deterministic policy.

Future models can use a larger tabular ranker:

- Gradient-boosted trees (LightGBM/XGBoost) exported to ONNX, or
- an INT8 MLP with two hidden layers of 16–32 units.

Target APK contribution: under 0.5 MB; target inference: milliseconds once per
upcoming pair. Reuse the existing ONNX Runtime dependency. Do not add a second
ML runtime or network service.

## Genre scope and future models

The first model is deliberately an electronic/DJ model. It should be trained
and evaluated only for styles with reliable meter and phrase structure (for
example house, techno, trance and drum & bass). The app must expose this scope
in Settings and always retain normal Automix as the fallback for pop, rock,
urban, jazz, ballads and low-confidence analysis.

Do not make one model appear universal by mixing incompatible labels. To add a
genre family later, create a versioned training run with examples from that
family, evaluate it independently, and either:

- add a lightweight genre/suitability gate that chooses the right ranker; or
- train a single mixed-genre ranker only after its per-genre validation beats
  the deterministic planner for every supported family.

The runtime must choose normal Automix whenever genre/suitability is unknown,
the AI score is malformed, or its confidence is below the documented threshold.

## Feature schema

Only consume data already produced by Automix analysis or by the current plan:

- outgoing/incoming BPM and absolute stretch ratio;
- key compatibility / Camelot distance;
- beat and downbeat confidence;
- phrase-boundary alignment error;
- intro/outro and proposed overlap duration;
- energy difference and energy-curve slope;
- vocal activity and predicted simultaneous-vocal fraction;
- analysis completeness and model confidence;
- selected performance mode, strictly for telemetry analysis—not as a musical
  feature at inference time.

Never train on account identity, listening history, handles, cookies, device
identifiers, or raw user audio uploaded from the app.

## Dataset and labels

Use licensed, consented audio or synthetic feature rows. Store feature rows and
candidate plans, not raw listener playback history. Each label should be a
human preference between two safe transitions, with at least two reviewers for
ambiguous pairs. Keep an explicit `no-good-transition` label; forcing a mix is
worse than a normal crossfade.

Split train/validation/test by artist and recording, not by random excerpts,
to avoid a model memorising production traits. Report per-genre, tempo-distance
and vocal-clash slices.

## Acceptance gate before bundling a replacement

1. Export a deterministic ONNX model with a versioned feature schema.
2. Add JVM tests for input ordering, missing-feature defaults and model output
   bounds.
3. Compare against the current planner on a held-out labelled set. The model
   may only rank among candidates the deterministic safety policy accepts.
4. Profile on low/mid/high Android hardware: no playback underruns, no full
   track decode, and no concurrent model analyses.
5. Cache scores by model version plus both track/rendition identifiers.
6. Roll out behind `automix_ai_enabled`; if loading or inference fails, log a
   non-sensitive diagnostic and use the existing planner immediately.

## Runtime contract

`AutomixAiRanker` belongs beside `TransitionPlanner`. It receives immutable
feature values and returns either a bounded score or `null`. `null`, a missing
model, a malformed output, or a disabled setting must all produce the same
normal deterministic transition plan. No retries, downloading, or model
training may happen on the playback path.

This keeps Automix useful offline, predictable under battery/thermal pressure,
and safe while the model evolves.
