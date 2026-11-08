// From AutoMix (https://github.com/heydavidf/automix), Copyright (C) 2026 David Finnegan.
// AGPL-3.0-only: see ./LICENSE and ./NOTICE.md in playback/automix.

import type { TrackAnalysis } from '../analysis/types';

export const BEATS_PER_BAR = 4;
export const BEATS_PER_PHRASE = 32; // 8 bars

export interface Grid {
  beatSec: number;
  barSec: number;
  phraseSec: number;
  downbeatSec: number;
  bpm: number;
}

export function gridOf(a: TrackAnalysis): Grid | null {
  if (!a.bpm || !a.beatIntervalSec || a.firstDownbeatSec === null) return null;
  const beatSec = a.beatIntervalSec;
  return {
    beatSec,
    barSec: beatSec * BEATS_PER_BAR,
    phraseSec: beatSec * BEATS_PER_PHRASE,
    downbeatSec: a.firstDownbeatSec + (a.manualOverrides?.gridNudgeBeats ?? 0) * beatSec,
    bpm: a.manualOverrides?.bpm ?? a.bpm,
  };
}

/**
 * BitChord: the analysis as the outgoing side of a blend sees it, its grid
 * fields swapped for the tail grid (see TrackAnalysis). The scheduler and the
 * mix loop read the outgoing track through this, so they stay AutoMix's.
 */
export function asOutgoing(a: TrackAnalysis): TrackAnalysis {
  if (a.tailGrid === undefined) return a;
  const t = a.tailGrid;
  return t
    ? { ...a, bpm: t.bpm, beatIntervalSec: t.beatIntervalSec, firstDownbeatSec: t.firstDownbeatSec, gridConfidence: t.gridConfidence }
    : { ...a, bpm: a.bpm, beatIntervalSec: null, firstDownbeatSec: null, gridConfidence: 0 };
}

export function phraseAtOrBefore(g: Grid, t: number): number {
  return g.downbeatSec + Math.floor((t - g.downbeatSec) / g.phraseSec) * g.phraseSec;
}

export function phraseAtOrAfter(g: Grid, t: number): number {
  return g.downbeatSec + Math.ceil((t - g.downbeatSec) / g.phraseSec) * g.phraseSec;
}

export function downbeatAtOrAfter(g: Grid, t: number): number {
  return g.downbeatSec + Math.ceil((t - g.downbeatSec) / g.barSec) * g.barSec;
}
