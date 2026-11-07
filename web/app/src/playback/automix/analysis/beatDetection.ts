// Chunking adapted from CPJKU/beat_this (MIT), Copyright (c) 2024 Institute
// of Computational Perception, JKU Linz. See native/analyzer/BEAT_THIS_LICENSE.
/** Pure Beat This! chunking/postprocessing; kept in sync with shared/.../BeatDetection.kt. */
export const BEAT_FPS = 50;
export const CHUNK_FRAMES = 1500;
export const BORDER_FRAMES = 6;
const MIN_BEATS = 8;

export interface BeatChunk {
  startFrame: number;
  frameCount: number;
}

/** CPJKU split_piece: padded outer borders, full final chunk, keep-first aggregation. */
export function beatChunks(frames: number): BeatChunk[] {
  if (!Number.isSafeInteger(frames) || frames <= 0) return [];
  const starts: number[] = [];
  for (let start = -BORDER_FRAMES; start < frames - BORDER_FRAMES; start += CHUNK_FRAMES - 2 * BORDER_FRAMES) starts.push(start);
  if (frames > CHUNK_FRAMES - 2 * BORDER_FRAMES) starts[starts.length - 1] = frames - (CHUNK_FRAMES - BORDER_FRAMES);
  return starts.map((startFrame) => ({
    startFrame,
    frameCount: Math.min(startFrame + CHUNK_FRAMES, frames) - Math.max(0, startFrame)
      + Math.max(0, -startFrame) + Math.min(BORDER_FRAMES, Math.max(0, startFrame + CHUNK_FRAMES - frames)),
  }));
}

/** Finite positive local maxima, flat runs collapsed, with sub-frame refinement. */
export function pickPeaks(logits: Float32Array): number[] {
  const peaks: number[] = [];
  for (let i = 0; i < logits.length; i++) {
    const value = logits[i]!;
    if (!Number.isFinite(value) || value <= 0) continue;
    let maximum = true;
    for (let j = Math.max(0, i - 3); j <= Math.min(logits.length - 1, i + 3); j++) {
      if (Number.isFinite(logits[j]) && logits[j]! > value) { maximum = false; break; }
    }
    if (maximum) peaks.push(i);
  }
  const result: number[] = [];
  for (let i = 0; i < peaks.length; i++) {
    const first = peaks[i]!;
    let last = first;
    while (i + 1 < peaks.length && peaks[i + 1] === last + 1) last = peaks[++i]!;
    if (last > first) { result.push((first + last) / 2); continue; }
    let position = first;
    if (first > 0 && first + 1 < logits.length) {
      const left = logits[first - 1]!;
      const centre = logits[first]!;
      const right = logits[first + 1]!;
      const curvature = left - 2 * centre + right;
      if (Number.isFinite(left) && Number.isFinite(right) && curvature < -1e-9) {
        position += Math.max(-0.5, Math.min(0.5, (0.5 * (left - right)) / curvature));
      }
    }
    result.push(position);
  }
  return result;
}

function median(values: number[]): number {
  if (!values.length) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const middle = sorted.length >> 1;
  return sorted.length % 2 ? sorted[middle]! : (sorted[middle - 1]! + sorted[middle]!) / 2;
}

function beatGaps(beats: number[]): number[] | null {
  if (beats.length < MIN_BEATS || beats.some((b) => !Number.isFinite(b))) return null;
  const gaps = beats.slice(1).map((b, i) => b - beats[i]!);
  return gaps.every((gap) => Number.isFinite(gap) && gap > 0) ? gaps : null;
}

export function tempoFromBeats(beats: number[]): number {
  const gaps = beatGaps(beats);
  if (!gaps) return 0;
  const rough = median(gaps);
  const kept = gaps.filter((gap) => Math.abs(gap - rough) <= rough * 0.2);
  const bpm = 60 / median(kept.length >= 4 ? kept : gaps);
  return bpm >= 40 && bpm <= 220 ? bpm : 0;
}

/** Conservative mixing score: strength above the model's decision boundary, regularity and support. */
export function gridConfidence(beats: number[], peakLogits: number[]): number {
  const gaps = beatGaps(beats);
  if (!gaps || peakLogits.length !== beats.length || peakLogits.some((l) => !Number.isFinite(l)) || tempoFromBeats(beats) <= 0) return 0;
  const interval = median(gaps);
  const regular = gaps.filter((gap) => Math.abs(gap - interval) <= interval * 0.1).length / gaps.length;
  const strength = Math.max(0, Math.min(1, 2 / (1 + Math.exp(-median(peakLogits))) - 1));
  return Math.max(0, Math.min(0.95, 0.95 * regular * strength * Math.min(1, beats.length / 16)));
}

/** Independently predicted downbeats must already lie close to a beat. */
export function snapDownbeats(beats: number[], downbeats: number[]): number[] {
  const bpm = tempoFromBeats(beats);
  if (bpm <= 0) return [];
  const tolerance = Math.min(2 / BEAT_FPS, (0.1 * 60) / bpm);
  return [...new Set(downbeats.filter(Number.isFinite).map((time) => {
    const nearest = beats.reduce((best, b) => Math.abs(b - time) < Math.abs(best - time) ? b : best, beats[0]!);
    return Math.abs(nearest - time) <= tolerance + 1e-9 ? nearest : null;
  }).filter((time): time is number => time !== null))].sort((a, b) => a - b);
}
