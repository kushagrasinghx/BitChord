import {
  line,
  lineAlignments,
  MIN_GAP_MS,
  normalizedSyllables,
  withInstrumentalGaps,
  wordsFromRuns,
  type LyricLine,
  type LyricSyllable,
  type LyricWord,
  type TimedRun,
} from './model';

/**
 * Every wire format the providers use, read into the lyric model: ports of
 * TtmlLyrics.kt, EnhancedLrc.kt, LrcLib.parseLrc, KaraokeLrc and
 * ProviderLyrics.kt.
 */

// ---- EnhancedLrc.decodeEntities -------------------------------------------------------------

/** SimpMusic and friends serve HTML-escaped text; "&#x27;" would otherwise be sung literally. */
/** fromCodePoint throws past U+10FFFF, which would take the whole lookup down: keep such an entity as written. */
const codePoint = (entity: string, value: number) => (value <= 0x10ffff ? String.fromCodePoint(value) : entity);

export function decodeEntities(text: string): string {
  if (!text.includes('&')) return text;
  return text
    .replace(/&#x([0-9a-fA-F]+);/g, (entity, hex: string) => codePoint(entity, Number.parseInt(hex, 16)))
    .replace(/&#(\d+);/g, (entity, dec: string) => codePoint(entity, Number(dec)))
    .replace(/&apos;/g, "'")
    .replace(/&quot;/g, '"')
    .replace(/&nbsp;/g, ' ')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&');
}

// ---- TTML (TtmlLyrics.kt) ---------------------------------------------------------------------

const SKIPPED_ROLES = new Set(['x-translation', 'x-roman']);
const BACKGROUND_ROLE = 'x-bg';

/** TTML clock values: 27.395, 1:05.20, 1:02:03.4, or a number with s/ms. */
export function ttmlTime(value: string | null | undefined): number | null {
  const raw = value?.trim();
  if (!raw) return null;
  if (raw.endsWith('ms')) {
    const ms = Number(raw.slice(0, -2));
    return Number.isFinite(ms) ? Math.trunc(ms) : null;
  }
  const parts = raw.replace(/s$/, '').split(':').map(Number);
  if (parts.some((p) => !Number.isFinite(p)) || parts.length > 3) return null;
  const seconds = parts.reduce((total, part) => total * 60 + part, 0);
  return Math.trunc(seconds * 1000);
}

/** An attribute named with a prefix, whichever way the DOM files it. */
function qualified(element: Element, name: string): string {
  const direct = element.getAttribute(name);
  if (direct) return direct;
  const local = name.split(':')[1] ?? name;
  for (const attribute of Array.from(element.attributes)) {
    if (attribute.name === name || attribute.name === local || attribute.localName === local) return attribute.value;
  }
  return '';
}

type Piece = { kind: 'text'; text: string } | { kind: 'timed'; text: string; start: number; end: number };

function hasTimedChild(element: Element): boolean {
  return Array.from(element.children).some((child) => child.getAttribute('begin') || hasTimedChild(child));
}

function collect(node: Node, out: Piece[], backing: Piece[]): void {
  for (const child of Array.from(node.childNodes)) {
    if (child.nodeType === 1) {
      const element = child as Element;
      const role = qualified(element, 'ttm:role');
      if (SKIPPED_ROLES.has(role)) continue;
      const sink = role === BACKGROUND_ROLE ? backing : out;
      const begin = ttmlTime(element.getAttribute('begin'));
      const end = ttmlTime(element.getAttribute('end'));
      if (begin !== null && end !== null && !hasTimedChild(element)) {
        sink.push({ kind: 'timed', text: element.textContent ?? '', start: begin, end });
      } else {
        collect(element, sink, backing);
      }
    } else if (child.nodeType === 3) {
      const text = child.textContent ?? '';
      if (text) out.push({ kind: 'text', text });
    }
  }
}

/** Glues syllables into words: whitespace, not the span boundary, ends a word. */
function mergeIntoWords(pieces: Piece[]): LyricWord[] {
  const words: LyricWord[] = [];
  let current = '';
  let syllables: LyricSyllable[] = [];
  let start = 0;
  let end = 0;
  let timed = false;
  const flush = () => {
    const text = current.trim();
    current = '';
    if (text && timed) words.push({ startMs: start, endMs: end, text, syllables: normalizedSyllables(syllables, text.length) });
    syllables = [];
    timed = false;
  };
  for (const piece of pieces) {
    if (piece.kind === 'text') {
      if (!piece.text.trim()) flush();
      else if (timed) current += piece.text;
      continue;
    }
    if (!piece.text.trim()) continue;
    if (/^\s/.test(piece.text)) flush();
    if (!current) start = piece.start;
    const from = current.length;
    current += piece.text.trim();
    syllables.push({ startMs: piece.start, endMs: piece.end, startChar: from, endChar: current.length });
    end = piece.end;
    timed = true;
    if (/\s$/.test(piece.text)) flush();
  }
  flush();
  return words;
}

function lineFrom(paragraph: Element): LyricLine | null {
  const pieces: Piece[] = [];
  const backingPieces: Piece[] = [];
  collect(paragraph, pieces, backingPieces);
  const words = mergeIntoWords(pieces);
  const backingWords = mergeIntoWords(backingPieces);
  const backing = backingWords.length
    ? line(backingWords[0]!.startMs, backingWords.map((w) => w.text).join(' '), { words: backingWords })
    : null;
  if (!words.length) {
    const text = paragraph.textContent?.trim() ?? '';
    const begin = ttmlTime(paragraph.getAttribute('begin'));
    if (begin === null || !text) return null;
    const end = ttmlTime(paragraph.getAttribute('end'));
    return line(begin, text, { sungUntilMs: end !== null && end > begin ? end : null });
  }
  const begin = ttmlTime(paragraph.getAttribute('begin')) ?? words[0]!.startMs;
  return line(Math.min(begin, words[0]!.startMs), words.map((w) => w.text).join(' '), { words, background: backing });
}

export function parseTtml(ttml: string): LyricLine[] {
  try {
    const document = new DOMParser().parseFromString(ttml, 'text/xml');
    if (document.getElementsByTagName('parsererror').length) return [];
    const types: Record<string, string> = {};
    const agents = document.getElementsByTagName('ttm:agent').length
      ? document.getElementsByTagName('ttm:agent')
      : document.getElementsByTagName('agent');
    for (const agent of Array.from(agents)) {
      const id = qualified(agent, 'xml:id');
      const type = agent.getAttribute('type');
      if (id && type) types[id] = type;
    }
    const sung: { line: LyricLine; agent: string | null }[] = [];
    for (const paragraph of Array.from(document.getElementsByTagName('p'))) {
      const l = lineFrom(paragraph);
      if (l) sung.push({ line: l, agent: qualified(paragraph, 'ttm:agent') || null });
    }
    sung.sort((a, b) => a.line.timeMs - b.line.timeMs);
    const sides = lineAlignments(
      sung.map((s) => s.agent),
      types,
    );
    return withInstrumentalGaps(sung.map((s, i) => ({ ...s.line, alignment: sides[i]! })));
  } catch {
    return [];
  }
}

// ---- Enhanced (A2) LRC (EnhancedLrc.kt) -----------------------------------------------------

const A2_LINE = /^\[(\d{1,3}):(\d{2})[.:](\d{2,3})\](.*)$/;
const A2_WORD = /<(\d{1,3}):(\d{2})[.:](\d{2,3})>([^<]*)/g;
const TAIL_MS = 800;

function stamp(minutes: string, seconds: string, fraction: string): number {
  const fractionMs = fraction.length === 3 ? Number(fraction) : Number(fraction) * 10;
  return Number(minutes) * 60_000 + Number(seconds) * 1000 + fractionMs;
}

/** Empty when the LRC carries no word stamps, so the caller can fall back to plain LRC. */
export function parseEnhancedLrc(lrc: string): LyricLine[] {
  const rows = lrc
    .split(/\r?\n/)
    .map((l) => A2_LINE.exec(l.trim()))
    .filter((m): m is RegExpExecArray => !!m)
    .map((m) => ({ timeMs: stamp(m[1]!, m[2]!, m[3]!), words: [...m[4]!.matchAll(A2_WORD)], plain: m[4]!.trim() }))
    .sort((a, b) => a.timeMs - b.timeMs);
  if (!rows.some((r) => r.words.length)) return [];
  const lines: LyricLine[] = [];
  rows.forEach((row, index) => {
    if (!row.words.length) {
      const text = decodeEntities(row.plain);
      if (text) lines.push(line(row.timeMs, text));
      return;
    }
    const last = row.words.at(-1)!;
    const lineEnd = rows[index + 1]?.timeMs ?? stamp(last[1]!, last[2]!, last[3]!) + TAIL_MS;
    const runs: TimedRun[] = row.words.map((m, i) => {
      const next = row.words[i + 1];
      return {
        startMs: stamp(m[1]!, m[2]!, m[3]!),
        endMs: next ? stamp(next[1]!, next[2]!, next[3]!) : lineEnd,
        raw: decodeEntities(m[4]!),
      };
    });
    const words = wordsFromRuns(runs);
    if (!words.length) return;
    lines.push(line(Math.min(row.timeMs, words[0]!.startMs), words.map((w) => w.text).join(' '), { words }));
  });
  return withInstrumentalGaps(lines);
}

// ---- Plain LRC (LrcLib.parseLrc) --------------------------------------------------------------

const LRC_STAMP = /\[(\d{1,2}):(\d{2})[.:](\d{2,3})\]/;
const WORD_STAMP = /<(\d{1,3}):(\d{2})[.:](\d{2,3})>/g;
const ALIGNMENT_MARKER = '<R>';

export function parseLrc(lrc: string): LyricLine[] {
  const all: LyricLine[] = [];
  for (const raw of lrc.split(/\r?\n/)) {
    const match = LRC_STAMP.exec(raw);
    if (!match) continue;
    let body = raw.slice((match.index ?? 0) + match[0].length);
    let alignment: LyricLine['alignment'] = 'start';
    if (body.startsWith(ALIGNMENT_MARKER)) {
      body = body.slice(ALIGNMENT_MARKER.length);
      alignment = 'end';
    }
    const marks = [...body.matchAll(WORD_STAMP)];
    const runs: TimedRun[] = marks.map((mark, i) => {
      const until = marks[i + 1]?.index ?? body.length;
      const startMs = stamp(mark[1]!, mark[2]!, mark[3]!);
      const next = marks[i + 1];
      return {
        startMs,
        endMs: next ? stamp(next[1]!, next[2]!, next[3]!) : startMs,
        raw: body.slice((mark.index ?? 0) + mark[0].length, until),
      };
    });
    all.push(
      line(stamp(match[1]!, match[2]!, match[3]!), body.replace(WORD_STAMP, '').trim(), {
        words: marks.length ? wordsFromRuns(runs) : [],
        alignment,
      }),
    );
  }
  all.sort((a, b) => a.timeMs - b.timeMs);
  const kept = all.filter((l, index) => {
    if (l.text) return true;
    const next = all[index + 1];
    return !next || next.timeMs - l.timeMs >= MIN_GAP_MS;
  });
  const first = kept[0];
  return first && first.text && first.timeMs >= MIN_GAP_MS ? [line(0, ''), ...kept] : kept;
}

// ---- QQ/QRC and NetEase YRC (KaraokeLrc) ------------------------------------------------------

const KARAOKE_LINE = /^\[(\d{1,8}),(\d{1,8})\](.*)$/;
const PREFIX_WORD = /\((\d{1,8}),(\d{1,8})(?:,\d{1,8})?\)([^()]*)/g;
const SUFFIX_WORD = /([^()]*)\((\d{1,8}),(\d{1,8})(?:,\d{1,8})?\)/g;
const KARAOKE_TIME = /\(\d{1,8},\d{1,8}(?:,\d{1,8})?\)/g;
const LYRIC_CONTENT = /LyricContent\s*=\s*"([^"]*)"/i;

function karaokeContent(raw: string): string {
  const inner = LYRIC_CONTENT.exec(raw)?.[1];
  return inner
    ? inner.replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&')
    : raw;
}

export function looksLikeKaraoke(raw: string): boolean {
  return karaokeContent(raw)
    .split(/\r?\n/)
    .some((l) => {
      const body = KARAOKE_LINE.exec(l.trim())?.[3];
      return !!body && (new RegExp(PREFIX_WORD.source).test(body) || new RegExp(SUFFIX_WORD.source).test(body));
    });
}

export function parseKaraoke(raw: string): LyricLine[] {
  const rows: LyricLine[] = [];
  for (const source of karaokeContent(raw).split(/\r?\n/)) {
    const match = KARAOKE_LINE.exec(source.trim());
    if (!match) continue;
    const lineStart = Number(match[1]);
    const lineDuration = Number(match[2]);
    const body = match[3]!;
    const run = (text: string, start: string, duration: string): TimedRun => ({
      startMs: Number(start),
      endMs: Number(start) + Number(duration),
      raw: decodeEntities(text),
    });
    const prefixed = wordsFromRuns([...body.matchAll(PREFIX_WORD)].map((m) => run(m[3]!, m[1]!, m[2]!)));
    const suffixed = wordsFromRuns([...body.matchAll(SUFFIX_WORD)].map((m) => run(m[1]!, m[2]!, m[3]!)));
    const length = (words: LyricWord[]) => words.reduce((n, w) => n + w.text.length, 0);
    const words = length(prefixed) >= length(suffixed) ? prefixed : suffixed;
    if (!words.length) continue;
    const text = decodeEntities(body.replace(KARAOKE_TIME, '')).trim();
    if (!text) continue;
    rows.push(
      line(Math.min(lineStart, words[0]!.startMs), text, {
        words,
        sungUntilMs: lineDuration > 0 ? lineStart + lineDuration : null,
      }),
    );
  }
  return withInstrumentalGaps(rows.sort((a, b) => a.timeMs - b.timeMs));
}

// ---- Whatever a provider sent (ProviderLyrics.kt) ------------------------------------------------

const CONTENT_KEYS = [
  'ttml', 'ttmlContent', 'lyrics', 'lrc', 'content', 'text', 'plainLyrics', 'syncedLyrics',
  'line', 'lines', 'lyric', 'data', 'result', 'response',
];

function extract(value: unknown): string | null {
  if (value === null || value === undefined) return null;
  if (typeof value === 'string') {
    const text = value.trim();
    try {
      const nested: unknown = JSON.parse(text);
      if (nested && typeof nested === 'object') return extract(nested);
    } catch {
      // Not JSON: the text itself.
    }
    return text;
  }
  if (Array.isArray(value)) return value.map(extract).filter(Boolean).join('\n') || null;
  if (typeof value === 'object') {
    const object = value as Record<string, unknown>;
    if (object.isError === true || object.ok === false || (object.error && object.error !== false && object.error !== '')) return null;
    for (const key of CONTENT_KEYS) {
      const found = key in object ? extract(object[key]) : null;
      if (found) return found;
    }
    return extract(object.metadata) ?? extract(object.words);
  }
  return null;
}

/** Providers sometimes wrap the lyric string in one or two JSON envelopes. */
export function unwrap(raw: string): string | null {
  let value = raw.replace(/﻿/g, '').trim();
  if (value.startsWith('```')) {
    const lines = value.split('\n').slice(1);
    if (lines.at(-1)?.trim() === '```') lines.pop();
    value = lines.join('\n').trim();
  }
  if (!value) return null;
  try {
    return extract(JSON.parse(value))?.trim() || null;
  } catch {
    return value;
  }
}

function unescapeTtml(value: string): string {
  return /&lt;tt/i.test(value)
    ? value.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;|&apos;/g, "'").replace(/&amp;/g, '&')
    : value;
}

function plain(content: string): LyricLine[] {
  if (/\b(?:lyrics? (?:not found|unavailable)|error)\b/i.test(content)) return [];
  return content
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter((l) => l && !/^\[[A-Za-z]+:.*\]$/.test(l))
    .map((l) => line(0, l));
}

export interface Parsed {
  lines: LyricLine[];
  /** The original document, when it was TTML: am-lyrics renders it as is. */
  ttml: string | null;
}

/** Turns any provider's body into lines (and keeps a TTML original). */
export function parseProvider(raw: string): Parsed | null {
  const unwrapped = unwrap(raw);
  if (!unwrapped) return null;
  const content = unescapeTtml(unwrapped);
  let lines: LyricLine[];
  let ttml: string | null = null;
  if (/<tt(?:\s|>)/i.test(content) || /http:\/\/www\.w3\.org\/ns\/ttml/i.test(content)) {
    lines = parseTtml(content);
    ttml = content;
  } else if (looksLikeKaraoke(content)) lines = parseKaraoke(content);
  else if (content.trimStart().startsWith('<')) lines = [];
  else {
    lines = parseEnhancedLrc(content);
    if (!lines.length) lines = parseLrc(content);
    if (!lines.length) lines = plain(content);
  }
  return lines.some((l) => l.text.trim()) ? { lines, ttml } : null;
}

export { plain as plainLines };
