/*
 * BitChord: the second step of a track's analysis, after essentia's
 * (analysis.worker.ts): the phone's models, Beat This! for beats and
 * downbeats and the open-unmix vocal mask (models.ts), merged into essentia's
 * answer. Beat This! replaces essentia's grids wherever it finds one;
 * essentia's stay as the fallback.
 *
 * One track per worker, ended by the analyzer after its answer: a run of
 * Beat This! grows ONNX Runtime's WebAssembly heap to ~360 MB, and that heap
 * never shrinks while the worker lives.
 *
 * Adapted from the analysis worker of AutoMix (https://github.com/heydavidf/automix),
 * Copyright (C) 2026 David Finnegan, AGPL-3.0-only: see ../LICENSE and ../NOTICE.md.
 * The models: see models.ts for their origin and licence (AGPL-3.0, Orchard).
 */
import { fitGrid, normalizeTempo } from './beatgrid';
import type { AnalysisResult, AnalyzeResponse } from './analysis.worker';
import { trackBeats, vocalCurve, VOCAL_FPS, type ModelGrid } from './models';
import type { BeatSide, LocalGrid } from './types';

export interface ModelsRequest {
  type: 'models';
  fileKey: string;
  /** Essentia's answer, which the models' grids and vocal curves are merged into. */
  result: AnalysisResult;
  /** Mono 22,050 Hz windows for Beat This! (the opening and the end). */
  beatWindows: { offsetSec: number; pcm: Float32Array }[];
  /** Stereo 44.1 kHz windows for the vocal mask. */
  vocalWindows: { offsetSec: number; left: Float32Array; right: Float32Array }[];
}

self.onmessage = async (ev: MessageEvent<ModelsRequest>) => {
  const { fileKey, result, beatWindows, vocalWindows } = ev.data;
  try {
    // one at a time: a single wasm session runs one inference at once
    const grids: (ModelGrid | null)[] = [];
    for (const w of beatWindows.slice(0, 2)) grids.push(await trackBeats(w.pcm, w.offsetSec).catch(() => null));
    const [head = null, tail = null] = grids;
    result.headBeats = head ? beatSide(head) : null;
    result.tailBeats = tail ? beatSide(tail) : (beatWindows.length === 1 ? result.headBeats : null);
    if (result.headBeats) {
      const g = localGrid(result.headBeats);
      Object.assign(result, { bpm: g.bpm, beatIntervalSec: g.beatIntervalSec, firstDownbeatSec: g.firstDownbeatSec, gridConfidence: g.gridConfidence });
    }
    if (result.tailBeats) result.tailGrid = localGrid(result.tailBeats);

    for (const w of vocalWindows) {
      const curve = await vocalCurve(w.left, w.right).catch(() => null);
      if (curve) result.vocals.push({ startSec: w.offsetSec, fps: VOCAL_FPS, curve: Array.from(curve) });
    }
    postMessage({ type: 'result', fileKey, result } satisfies AnalyzeResponse);
  } catch (e) {
    postMessage({ type: 'error', fileKey, message: String(e) } satisfies AnalyzeResponse);
  }
};

function beatSide(g: ModelGrid): BeatSide {
  return { beats: g.beats, downbeats: g.downbeats, bpm: g.bpm, confidence: g.confidence };
}

/**
 * BitChord: a model grid as AutoMix's constant grid. The model's confidence
 * (0.35-0.95, the phone's scale, beatmatch from 0.55) maps onto AutoMix's
 * (0.2 gate) so 0.55 lands at 0.33 and 0.95 at 1.
 */
function localGrid(side: BeatSide): LocalGrid {
  // the phase-coherence fit over the model's beats: a median gap of 50 Hz frames drifts over a song
  const fit = fitGrid(side.beats);
  const beatIntervalSec = fit ? normalizeTempo(fit).beatIntervalSec : 60 / side.bpm;
  return {
    bpm: 60 / beatIntervalSec,
    beatIntervalSec,
    firstDownbeatSec: side.downbeats[0] ?? side.beats[0]!,
    gridConfidence: Math.max(0, Math.min(1, (side.confidence - 0.35) / 0.6)),
  };
}
