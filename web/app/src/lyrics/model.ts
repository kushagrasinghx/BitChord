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
/** Whether anything said when the singing stops (LyricLine.hasKnownEnd). */
export const hasKnownEnd = (l: LyricLine) => l.words.length > 0 || l.sungUntilMs !== null;

/** When the last word finishes, or the stated end, or the start; the answering vocal counts. */
export function endMs(l: LyricLine): number {
  const lead = l.words.at(-1)?.endMs ?? l.sungUntilMs ?? l.timeMs;
  return Math.max(lead, l.background ? endMs(l.background) : lead);
}

/** LyricSyllable.normalized: in order, end to end, covering the word; empty when there's nothing to split. */
export function normalizedSyllables(parts: LyricSyllable[], length: number): LyricSyllable[] {
  const usable = parts.filter((p) => p.endChar > p.startChar && p.startChar < length).sort((a, b) => a.startChar - b.startChar);
  if (usable.length < 2) return [];
  return usable.map((part, index) => ({
    startMs: part.startMs,
    endMs: Math.max(part.startMs, part.endMs),
    startChar: index === 0 ? 0 : part.startChar,
    endChar: usable[index + 1]?.startChar ?? length,
  }));
}

// ---- SyllableRuns.kt --------------------------------------------------------------------

/** One stamped run as a provider wrote it, spacing untouched: a word, or one syllable of one. */
export interface TimedRun {
  startMs: number;
  endMs: number;
  raw: string;
}

const UNSPACED = /[㐀-鿿぀-ヿ　-〿＀-￯฀-๿]/;
const isWhitespace = (c: string | undefined) => c !== undefined && /\s/.test(c);

/**
 * Glues runs into words. A run that neither ends in whitespace nor is followed
 * by one starting with it is the front of a word that carries on. Han, kana
 * and Thai never glue, and a line with no whitespace anywhere isn't stating
 * word boundaries (unless [spacingIsExplicit]).
 */
export function wordsFromRuns(runs: TimedRun[], spacingIsExplicit = false): LyricWord[] {
  const glue = spacingIsExplicit || runs.some((run) => /\s/.test(run.raw));
  const words: LyricWord[] = [];
  let current = '';
  let parts: LyricSyllable[] = [];
  let start = 0;
  let end = 0;
  const flush = () => {
    if (current) words.push({ startMs: start, endMs: Math.max(start, end), text: current, syllables: normalizedSyllables(parts, current.length) });
    current = '';
    parts = [];
  };
  for (const run of runs) {
    const content = run.raw.trim();
    if (!content) {
      flush();
      continue;
    }
    const joins =
      glue && current.length > 0 && !isWhitespace(run.raw[0]) && !UNSPACED.test(current.at(-1)!) && !UNSPACED.test(content[0]!);
    if (!joins) flush();
    if (!current) {
      start = run.startMs;
      end = run.startMs;
    }
    const from = current.length;
    current += content;
    const runEnd = Math.max(run.startMs, run.endMs);
    parts.push({ startMs: run.startMs, endMs: runEnd, startChar: from, endChar: current.length });
    end = Math.max(end, runEnd);
    if (!glue || isWhitespace(run.raw.at(-1))) flush();
  }
  flush();
  return words;
}

// ---- LyricGaps.kt ---------------------------------------------------------------------

/** Shorter instrumental breaks aren't worth interrupting the line for. */
export const MIN_GAP_MS = 4000;

/** Blank lines for the instrumental stretches, only where a line says when its singing stopped. */
export function withInstrumentalGaps(lines: LyricLine[]): LyricLine[] {
  if (!lines.length) return lines;
  const out: LyricLine[] = [];
  if (lines[0]!.timeMs >= MIN_GAP_MS) out.push(line(0, ''));
  lines.forEach((l, index) => {
    out.push(l);
    const next = lines[index + 1];
    if (!next || !hasKnownEnd(l)) return;
    const end = endMs(l);
    if (next.timeMs - end >= MIN_GAP_MS && end > l.timeMs) out.push(line(end, ''));
  });
  return out;
}

// ---- BackgroundVocals.kt ----------------------------------------------------------------

function bracketStart(text: string): number | null {
  if (!text.endsWith(')')) return null;
  let depth = 0;
  for (let index = text.length - 1; index >= 0; index--) {
    if (text[index] === ')') depth++;
    else if (text[index] === '(') {
      depth--;
      if (depth === 0) return index > 0 ? index : null;
    }
  }
  return null;
}

function wordStartingAt(words: LyricWord[], offset: number): number | null {
  let at = 0;
  for (let index = 0; index < words.length; index++) {
    if (at === offset) return index;
    if (at > offset) return null;
    at += words[index]!.text.length + 1;
  }
  return null;
}

/** A trailing "(answer)" bracket becomes the line's background vocal, with its own timing. */
export function withBackgroundVocals(lines: LyricLine[]): LyricLine[] {
  return lines.map((l) => {
    if (l.background || isGap(l)) return l;
    const open = bracketStart(l.text);
    if (open === null) return l;
    const lead = l.text.slice(0, open).trimEnd();
    const backing = l.text.slice(open).trim();
    if (!lead || !/[\p{L}\p{N}]/u.test(backing)) return l;
    if (!l.words.length) return { ...l, text: lead, background: line(l.timeMs, backing, { sungUntilMs: l.sungUntilMs }) };
    const split = wordStartingAt(l.words, open);
    if (!split || split <= 0) return l;
    const backingWords = l.words.slice(split);
    return {
      ...l,
      text: lead,
      words: l.words.slice(0, split),
      background: line(backingWords[0]!.startMs, backing, { words: backingWords }),
    };
  });
}

// ---- LyricAlignments.kt ---------------------------------------------------------------------

const MOSTLY_RIGHT = 0.85;

/** Which side each line is sung from, alternating as the voice changes; group lines stay left. */
export function lineAlignments(singers: (string | null | undefined)[], types: Record<string, string>): LyricAlignment[] {
  let left = true;
  let lastVoice: string | null = null;
  let rightward = 0;
  let placed = 0;
  const sides = singers.map((singer): LyricAlignment => {
    if (!singer) return 'start';
    const type = types[singer] ?? (singer === 'v1000' ? 'group' : singer === 'v2000' ? 'other' : 'person');
    placed += 1;
    if (type === 'group') return 'start';
    if (lastVoice === null) left = type !== 'other';
    else if (singer !== lastVoice) left = !left;
    lastVoice = singer;
    if (!left) rightward += 1;
    return left ? 'start' : 'end';
  });
  if (placed === 0 || rightward / placed < MOSTLY_RIGHT) return sides;
  return sides.map((s) => (s === 'start' ? 'end' : 'start'));
}

/** Genius's "[Chorus]" markers, rather than words to sing. */
export function isSectionHeader(text: string): boolean {
  const t = text.trim();
  return t.startsWith('[') && t.endsWith(']') && t.length >= 3 && t.length <= 60;
}
