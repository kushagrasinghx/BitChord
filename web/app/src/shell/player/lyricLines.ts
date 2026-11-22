import type { LyricLine } from '@/lyrics/model';

/** The line being sung at `ms`: the last one started (PlayerLyrics.kt indexOfLast { timeMs <= clock }), −1 before the first. */
export function currentLineIndex(lines: readonly LyricLine[], ms: number): number {
  for (let i = lines.length - 1; i >= 0; i--) if (lines[i]!.timeMs <= ms) return i;
  return -1;
}

/** strings.xml lyrics_intro_lines: what the strip says before the first words, one picked per track. */
export const INTRO_LINES = [
  'The beat is landing',
  'The song is starting',
  'Warming up',
  'Setting the mood',
  'Bass first, words later',
  'Wait for it',
  'Feel that build',
  'Just the groove for now',
  'The hook is on the way',
  'Let it breathe',
  'Cue the vocals',
  'First notes in',
];
