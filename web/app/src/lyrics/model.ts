import * as shared from '@bitchord/shared-kotlin';

/**
 * The lyric model, as shared/…/data/lyrics/LyricLine.kt has it: the data
 * only. Drawing is am-lyrics' job (prompt §10.2); these types exist so every
 * provider's answer can be compared, cleaned up and written out as TTML.
 */

/** One separately timed piece of a word: characters [startChar, endChar) sung from startMs to endMs. */
export interface LyricSyllable {
  startMs: number;
  endMs: number;
  startChar: number;
  endChar: number;
}

/** One word of a line; Apple splits long ones into syllables, which keep their own timing. */
export interface LyricWord {
  startMs: number;
  endMs: number;
  text: string;
  /** Empty for a word nobody split, which is most of them. */
  syllables: LyricSyllable[];
}

export type LyricAlignment = 'start' | 'end';

/** One line. A blank text is an instrumental stretch; words only where the provider timed them. */
export interface LyricLine {
  timeMs: number;
  text: string;
  words: LyricWord[];
  /** The line's own end, where a line-synced provider states one. */
  sungUntilMs: number | null;
  /** The answering vocal, drawn under the lead; never nested. */
  background: LyricLine | null;
  alignment: LyricAlignment;
}

export function line(timeMs: number, text: string, extra: Partial<LyricLine> = {}): LyricLine {
  return { timeMs, text, words: [], sungUntilMs: null, background: null, alignment: 'start', ...extra };
}

export const isGap = (l: LyricLine) => l.text.length === 0;
export const isWordSynced = (l: LyricLine) => l.words.length > 0;

// Letters of the right-to-left scripts in the Basic Multilingual Plane (Java's directionality R and AL).
const RTL_LETTER = /[\p{Script=Hebrew}\p{Script=Arabic}\p{Script=Syriac}\p{Script=Thaana}\p{Script=Nko}\p{Script=Samaritan}\p{Script=Mandaic}]/u;
const LETTER = /\p{L}/u;

/**
 * PlayerLyrics.kt `isRtl`: whether [text] reads right to left, by its own
 * letters rather than the app's language. More right-to-left letters than
 * left-to-right ones, or any Hebrew presentation form; a Hebrew line with
 * one English word in it is still Hebrew.
 */
export function isRtl(text: string): boolean {
  let rtl = 0;
  let ltr = 0;
  for (const ch of text) {
    const code = ch.codePointAt(0)!;
    if (code >= 0xfb1d && code <= 0xfb4f) return true;
    if (!LETTER.test(ch)) continue;
    if (RTL_LETTER.test(ch)) rtl++;
    else ltr++;
  }
  return rtl > ltr;
}

/** KuGou's and QQ's "纯音乐，请欣赏" ("pure music, please enjoy"), and its longer variants. */
export const INSTRUMENTAL_NOTICE = /纯音乐.*欣赏/;

/** Lyrics made of nothing but that notice: an instrumental, which has no lyrics to show. */
export function isInstrumentalNotice(lines: LyricLine[]): boolean {
  const sung = lines.map((l) => l.text.trim()).filter(Boolean);
  return sung.length > 0 && sung.every((t) => INSTRUMENTAL_NOTICE.test(t));
}
/** Whether anything said when the singing stops (LyricLine.hasKnownEnd). */
export const hasKnownEnd = (l: LyricLine) => l.words.length > 0 || l.sungUntilMs !== null;

/** When the last word finishes, or the stated end, or the start; the answering vocal counts. */
export function endMs(l: LyricLine): number {
  const lead = l.words.at(-1)?.endMs ?? l.sungUntilMs ?? l.timeMs;
  return Math.max(lead, l.background ? endMs(l.background) : lead);
}

/** LyricSyllable.normalized: in order, end to end, covering the word; empty when there's nothing to split. */
export const normalizedSyllables: (parts: LyricSyllable[], length: number) => LyricSyllable[] = shared.normalizedSyllables;

// ---- SyllableRuns.kt --------------------------------------------------------------------

/** One stamped run as a provider wrote it, spacing untouched: a word, or one syllable of one. */
export interface TimedRun {
  startMs: number;
  endMs: number;
  raw: string;
}

/** SyllableRuns.kt: glues runs into words; Han, kana and Thai never glue. */
export const wordsFromRuns = (runs: TimedRun[], spacingIsExplicit = false): LyricWord[] => shared.wordsFromRuns(runs, spacingIsExplicit);

// ---- LyricGaps.kt ---------------------------------------------------------------------

/** Shorter instrumental breaks aren't worth interrupting the line for. */
export const MIN_GAP_MS = 4000;

/** LyricGaps.kt: blank lines for the instrumental stretches, only where a line says when its singing stopped. */
export const withInstrumentalGaps: (lines: LyricLine[]) => LyricLine[] = shared.withInstrumentalGaps;

// ---- BackgroundVocals.kt ----------------------------------------------------------------

/** BackgroundVocals.kt: a trailing "(answer)" bracket becomes the line's background vocal, with its own timing. */
export const withBackgroundVocals: (lines: LyricLine[]) => LyricLine[] = shared.withBackgroundVocals;

// ---- LyricAlignments.kt ---------------------------------------------------------------------

/** LyricAlignments.kt: which side each line is sung from, alternating as the voice changes; group lines stay left. */
export const lineAlignments = (singers: (string | null | undefined)[], types: Record<string, string>): LyricAlignment[] =>
  shared.lineAlignments(
    singers.map((s) => s ?? null),
    types,
  ) as LyricAlignment[];

/** LyricSections.kt: Genius's "[Chorus]" markers, rather than words to sing. */
export const isSectionHeader: (text: string) => boolean = shared.isSectionHeader;
