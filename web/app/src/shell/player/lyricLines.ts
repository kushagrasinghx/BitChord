import type { LyricLine, LyricWord } from '@/lyrics/model';

/**
 * How much of a word has been sung at `ms`, 0–1 (LyricLine.kt charsSungAt):
 * each syllable fills its own characters over its own time, and a pause
 * between two rests on the boundary rather than creeping on.
 */
export function wordSung(word: LyricWord, ms: number): number {
  if (ms <= word.startMs) return 0;
  if (ms >= word.endMs || !word.text.length) return 1;
  if (!word.syllables.length) return (ms - word.startMs) / Math.max(1, word.endMs - word.startMs);
  for (const s of word.syllables) {
    if (ms < s.startMs) return s.startChar / word.text.length;
    if (ms < s.endMs) return (s.startChar + ((ms - s.startMs) / Math.max(1, s.endMs - s.startMs)) * (s.endChar - s.startChar)) / word.text.length;
  }
  return 1;
}

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

/** strings.xml lyrics_loading_lines: what the strip says while the lookup runs, one picked per track. */
export const LOADING_LINES = [
  'Getting the lyrics',
  'Finding the words',
  'Fetching the verses',
  'Lyrics are loading',
  'Checking the lyric sheet',
  'Searching the songbook',
  'Lining up the lyrics',
  'Words are on the way',
  'Checking the archives',
  'Finding the right words',
  'Lyrics are coming together',
  'Almost found the words',
];
