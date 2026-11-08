// From AutoMix (https://github.com/heydavidf/automix), Copyright (C) 2026 David Finnegan.
// AGPL-3.0-only: see ./LICENSE and ./NOTICE.md in playback/automix.

/**
 * Analysis worker: essentia.js (WASM) for BPM/ticks/key, plus our own
 * beat-grid regression, downbeat phase, energy curve, and intro/outro.
 * Input audio must be mono 44100 Hz (RhythmExtractor2013 assumes 44.1k).
 */
import { EssentiaWASM } from 'essentia.js/dist/essentia-wasm.es.js';
import Essentia from 'essentia.js/dist/essentia.js-core.es.js';
import { fitGrid, normalizeTempo, findDownbeat } from './beatgrid';
import { energyCurve, detectIntroOutro } from './energy';
import { toCamelot } from './camelot';
import { trackBeats, vocalCurve, VOCAL_FPS, type ModelGrid } from './models';
import type { BeatSide, LocalGrid, VocalWindow } from './types';

export interface AnalyzeRequest {
  type: 'analyze';
  fileKey: string;
  /** Mono, 44.1 kHz: essentia's key, energy and fallback rhythm. */
  samples: Float32Array;
  sampleRate: number;
  /** BitChord: mono 22,050 Hz windows for Beat This! (the opening and the end). */
  beatWindows?: { offsetSec: number; pcm: Float32Array }[];
  /** BitChord: stereo 44.1 kHz windows for the vocal mask. */
  vocalWindows?: { offsetSec: number; left: Float32Array; right: Float32Array }[];
}

export interface AnalyzeResponse {
  type: 'result' | 'error';
  fileKey: string;
  message?: string;
  result?: {
    bpm: number | null;
    beatIntervalSec: number | null;
    firstDownbeatSec: number | null;
    gridConfidence: number;
    camelot: { num: number; letter: 'A' | 'B' } | null;
    keyName: string | null;
    keyConfidence: number;
    energy: number;
    energyCurve: number[];
    introEndSec: number | null;
    outroStartSec: number | null;
    tailGrid: LocalGrid | null;
    headBeats: BeatSide | null;
    tailBeats: BeatSide | null;
    vocals: VocalWindow[];
  };
}

const ready = new Promise<void>((resolve) => {
  if (EssentiaWASM.calledRun) resolve();
  else EssentiaWASM.onRuntimeInitialized = () => resolve();
});

let essentia: Essentia | null = null;

self.onmessage = async (ev: MessageEvent<AnalyzeRequest>) => {
  const { fileKey, samples, sampleRate, beatWindows = [], vocalWindows = [] } = ev.data;
  try {
    await ready;
    essentia ??= new Essentia(EssentiaWASM);
    const result = analyze(essentia, samples, sampleRate)!;

    // BitChord: the phone's models. Beat This! replaces essentia's grids
    // wherever it finds one; essentia's stay as the fallback.
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

function analyze(ess: Essentia, samples: Float32Array, sampleRate: number): AnalyzeResponse['result'] {
  const durationSec = samples.length / sampleRate;
  const vec = ess.arrayToVector(samples);
  try {
    // --- rhythm ---
    // BitChord: AutoMix ran this once over the whole track. It runs here over
    // the opening (where the track is mixed in) and the last two minutes
    // (where it is mixed out), each with its own grid and confidence.
    const head = rhythmGrid(ess, samples, sampleRate, 0, Math.min(durationSec, HEAD_SEC));
    const tailGrid = durationSec > HEAD_SEC ? rhythmGrid(ess, samples, sampleRate, Math.max(0, durationSec - TAIL_SEC), durationSec) : head;
    const bpm = head?.bpm ?? tailGrid?.bpm ?? null;
    const beatIntervalSec = head?.beatIntervalSec ?? null;
    const firstDownbeatSec = head?.firstDownbeatSec ?? null;
    const gridConfidence = head?.gridConfidence ?? 0;

    // --- key ---
    const key = ess.KeyExtractor(vec);
    const camelot = toCamelot(key.key, key.scale);

    // --- energy / structure ---
    const windowSec = (beatIntervalSec ?? tailGrid?.beatIntervalSec) ? (beatIntervalSec ?? tailGrid!.beatIntervalSec) * 16 : 4;
    const curve = energyCurve(samples, sampleRate, windowSec);
    const { introEndSec, outroStartSec } = detectIntroOutro(curve, windowSec, durationSec);
    const energy = curve.reduce((a, b) => a + b, 0) / Math.max(1, curve.length);

    return {
      bpm,
      beatIntervalSec,
      firstDownbeatSec,
      gridConfidence,
      camelot,
      keyName: `${key.key} ${key.scale}`,
      keyConfidence: key.strength,
      energy,
      energyCurve: curve,
      introEndSec,
      outroStartSec,
      tailGrid,
      headBeats: null,
      tailBeats: null,
      vocals: [],
    };
  } finally {
    vec.delete();
  }
}

/** BitChord: essentia's "excellent" beat-tracking confidence. */
const ESS_EXCELLENT = 3.5;
/** BitChord: the residual a steady beat still shows, from essentia's 11.6 ms hop and its tick wobble. */
const TICK_JITTER_MS = 20;

/** BitChord: how much of the opening the mix-in grid is fitted to; the mix-in point is at most a third in. */
const HEAD_SEC = 100;
/** BitChord: how much of the end the mix-out grid is fitted to (a 32-bar blend at 90 BPM is 85 s). */
const TAIL_SEC = 120;

/** AutoMix's rhythm step (RhythmExtractor2013 -> fitGrid -> downbeat -> confidence) over [startSec, endSec). */
function rhythmGrid(ess: Essentia, samples: Float32Array, sampleRate: number, startSec: number, endSec: number): LocalGrid | null {
  const slice = samples.subarray(Math.round(startSec * sampleRate), Math.round(endSec * sampleRate));
  const vec = ess.arrayToVector(slice);
  try {
    const rhythm = ess.RhythmExtractor2013(vec, 208, 'multifeature', 60);
    const ticks = ess.vectorToArray(rhythm.ticks);
    rhythm.ticks.delete();
    rhythm.estimates.delete();
    rhythm.bpmIntervals.delete();
    // essentia multifeature confidence ranges 0..5.32; BitChord reads it on
    // essentia's documented scale, where 1.5-3.5 is "good" and above is
    // "excellent" (AutoMix divided by 5.32, so a well-tracked song topped out
    // near 0.3 and almost nothing cleared MIN_GRID_CONFIDENCE).
    const essConfidence = Math.max(0, Math.min(1, rhythm.confidence / ESS_EXCELLENT));
    const rawFit = fitGrid(ticks);
    if (!rawFit) return null;
    const fit = normalizeTempo(rawFit);
    const db = findDownbeat(slice, sampleRate, fit.beatIntervalSec, fit.offsetSec);
    return {
      bpm: fit.bpm,
      beatIntervalSec: fit.beatIntervalSec,
      firstDownbeatSec: startSec + db.firstDownbeatSec,
      // jittery ticks (vs the constant grid) and a weak downbeat both reduce trust
      // (BitChord: less the ~20 ms essentia's own tick jitter always leaves)
      gridConfidence: essConfidence * Math.exp(-Math.max(0, fit.residualMs - TICK_JITTER_MS) / 50) * (0.5 + 0.5 * db.confidence),
    };
  } finally {
    vec.delete();
  }
}

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
