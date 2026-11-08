// From AutoMix (https://github.com/heydavidf/automix), Copyright (C) 2026 David Finnegan.
// AGPL-3.0-only: see ./LICENSE and ./NOTICE.md in playback/automix.

import type { TrackAnalysis } from '../analysis/types';
import { camelotScore } from '../analysis/camelot';
import { MIN_GRID_CONFIDENCE } from './transition-scheduler';

export type EnergyDirection = -1 | 0 | 1;

export interface Candidate {
  analysis: TrackAnalysis;
  score: number;
  harmonic: boolean;
  reasons: { camelot: number; bpm: number; energy: number; repeat: number };
}

export interface SelectorOptions {
  energyDirection: EnergyDirection;
  /** Most-recently-played first. */
  recentFileKeys: string[];
  excludeFileKeys?: string[];
}

const BPM_HARD_LIMIT = 0.08; // ±8% stretchable
const BPM_FULL_SCORE = 0.04;

/**
 * Rank the library as next-track candidates: Camelot adjacency + BPM
 * proximity + energy direction - repetition. Harmonic matches sort first;
 * non-harmonic but tempo-compatible tracks are kept as a reserve so the
 * mix never strands.
 */
export function rankCandidates(
  current: TrackAnalysis,
  pool: TrackAnalysis[],
  opts: SelectorOptions,
): Candidate[] {
  const exclude = new Set([current.fileKey, ...(opts.excludeFileKeys ?? [])]);
  const noRepeat = new Set(opts.recentFileKeys.slice(0, Math.min(10, Math.floor(pool.length / 2))));
  const recentArtists = new Set(
    opts.recentFileKeys
      .slice(0, 3)
      .map((k) => pool.find((p) => p.fileKey === k)?.tags.artist)
      .filter(Boolean),
  );

  const candidates: Candidate[] = [];
  for (const a of pool) {
    if (exclude.has(a.fileKey) || noRepeat.has(a.fileKey)) continue;
    if (!a.bpm || !current.bpm) continue;

    const bpmRatio = bpmDistance(current.bpm, a.bpm);
    if (bpmRatio > BPM_HARD_LIMIT) continue;
    const bpmScore = bpmRatio <= BPM_FULL_SCORE ? 1 : 1 - (bpmRatio - BPM_FULL_SCORE) / (BPM_HARD_LIMIT - BPM_FULL_SCORE);

    const camelot = current.camelot && a.camelot ? camelotScore(current.camelot, a.camelot) : 0;

    const energyDelta = a.energy - current.energy;
    const energy =
      opts.energyDirection === 0
        ? Math.max(0, 0.5 - Math.abs(energyDelta) * 2)
        : Math.max(-0.5, Math.min(1, opts.energyDirection * energyDelta * 4));

    const repeat = recentArtists.has(a.tags.artist) ? -0.75 : 0;
    const lowGrid = a.gridConfidence < MIN_GRID_CONFIDENCE ? -0.5 : 0;

    candidates.push({
      analysis: a,
      score: camelot + bpmScore + energy + repeat + lowGrid,
      harmonic: camelot > 0,
      reasons: { camelot, bpm: bpmScore, energy, repeat },
    });
  }

  // rescue tier: nothing is tempo-compatible (e.g. one 130 BPM track in an
  // 85 BPM library) — offer harmonic-first picks anyway; the transition
  // scheduler refuses extreme stretches and plain-crossfades instead.
  if (candidates.length === 0) {
    for (const a of pool) {
      if (exclude.has(a.fileKey) || noRepeat.has(a.fileKey)) continue;
      if (!a.bpm) continue;
      const camelot = current.camelot && a.camelot ? camelotScore(current.camelot, a.camelot) : 0;
      candidates.push({
        analysis: a,
        score: camelot - 1,
        harmonic: false,
        reasons: { camelot, bpm: 0, energy: 0, repeat: 0 },
      });
    }
  }

  candidates.sort((x, y) => {
    if (x.harmonic !== y.harmonic) return x.harmonic ? -1 : 1;
    return y.score - x.score;
  });
  return candidates;
}

/** Relative BPM distance accounting for half/double-time equivalence. */
function bpmDistance(a: number, b: number): number {
  const candidates = [b, b * 2, b / 2];
  return Math.min(...candidates.map((x) => Math.abs(x - a) / a));
}
