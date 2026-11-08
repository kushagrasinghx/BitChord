// From AutoMix (https://github.com/heydavidf/automix), Copyright (C) 2026 David Finnegan.
// AGPL-3.0-only: see ./LICENSE and ./NOTICE.md in playback/automix.

import type { CamelotKey } from './types';

const MAJOR: Record<string, number> = {
  C: 8, G: 9, D: 10, A: 11, E: 12, B: 1,
  'F#': 2, Gb: 2, 'C#': 3, Db: 3, 'G#': 4, Ab: 4,
  'D#': 5, Eb: 5, 'A#': 6, Bb: 6, F: 7,
};

const MINOR: Record<string, number> = {
  A: 8, E: 9, B: 10, 'F#': 11, Gb: 11, 'C#': 12, Db: 12,
  'G#': 1, Ab: 1, 'D#': 2, Eb: 2, 'A#': 3, Bb: 3,
  F: 4, C: 5, G: 6, D: 7,
};

export interface ScaleSpec {
  /** Root pitch class, 0-11 (C=0). */
  rootPc: number;
  scale: 'major' | 'minor';
  /** Semitone offsets of the diatonic degrees from the root. */
  intervals: number[];
}

const PITCH_CLASS: Record<string, number> = {
  C: 0, 'C#': 1, Db: 1, D: 2, 'D#': 3, Eb: 3, E: 4, F: 5,
  'F#': 6, Gb: 6, G: 7, 'G#': 8, Ab: 8, A: 9, 'A#': 10, Bb: 10, B: 11,
};

// Invert the note→Camelot tables to Camelot-number→root-pitch-class.
// Enharmonics share a pitch class, so taking the first listed note is exact.
function invertToRoot(table: Record<string, number>): Record<number, number> {
  const out: Record<number, number> = {};
  for (const [note, num] of Object.entries(table)) {
    if (out[num] === undefined) out[num] = PITCH_CLASS[note]!;
  }
  return out;
}
const MAJOR_ROOT = invertToRoot(MAJOR);
const MINOR_ROOT = invertToRoot(MINOR);

const MAJOR_STEPS = [0, 2, 4, 5, 7, 9, 11];
const MINOR_STEPS = [0, 2, 3, 5, 7, 8, 10]; // natural minor

/** Camelot key → root pitch class + diatonic scale (the wheel is deterministic). */
export function camelotToScale(c: CamelotKey): ScaleSpec {
  const scale: 'major' | 'minor' = c.letter === 'B' ? 'major' : 'minor';
  const rootPc = (scale === 'major' ? MAJOR_ROOT : MINOR_ROOT)[c.num] ?? 0;
  return { rootPc, scale, intervals: scale === 'major' ? MAJOR_STEPS : MINOR_STEPS };
}

export function toCamelot(key: string, scale: 'major' | 'minor'): CamelotKey | null {
  const table = scale === 'major' ? MAJOR : MINOR;
  const num = table[key];
  if (num === undefined) return null;
  return { num, letter: scale === 'major' ? 'B' : 'A' };
}

export function formatCamelot(c: CamelotKey | null): string {
  return c ? `${c.num}${c.letter}` : '—';
}

/**
 * Harmonic compatibility score between two Camelot keys, for next-track
 * selection. Same key 3; ±1 same letter / relative major-minor 2.5;
 * +2 same letter ("energy boost") 1; everything else 0.
 */
export function camelotScore(from: CamelotKey, to: CamelotKey): number {
  const dist = (a: number, b: number) => Math.min((a - b + 12) % 12, (b - a + 12) % 12);
  if (from.letter === to.letter) {
    const d = dist(from.num, to.num);
    if (d === 0) return 3;
    if (d === 1) return 2.5;
    if (d === 2) return 1;
    return 0;
  }
  return dist(from.num, to.num) === 0 ? 2.5 : 0;
}
