/**
 * Whether one catalogue's track is the same recording as another's: a port of
 * TrackMatcher.kt (shared/…/data/sources), line for line where it can be.
 *
 * Titles are taken apart into the words that carry identity (must agree), the
 * markers of a different take — remix, live, acoustic (must agree in both
 * directions) — and packaging (film names, "Official Video": a tie-break,
 * never a veto). Then the credit and the runtime are checked against that.
 * The reasoning behind every rule is in the Kotlin file's comments; the
 * comments here only mark where JavaScript needed something different.
 */

/** One row from a catalogue: a YouTube Music search result, a Spotify track, an addon's answer. */
export interface MatchCandidate {
  title: string;
  /** Credited artists, as one string ("A, B & C"). */
  artist: string;
  durationSec: number | null;
  album: string | null;
  explicit?: boolean | null;
}

/** The recording being looked for, as much of it as is known. */
export interface MatchTarget {
  title: string;
  artist?: string;
  durationSec?: number | null;
  album?: string | null;
  isExplicit?: boolean | null;
  /** Music-video timing includes visual intros/outros that catalogue audio omits. */
  isVideo?: boolean;
}

interface TitleParts {
  words: string[];
  core: string;
  versions: Set<string>;
  context: Set<string>;
}

// ── Weights ─────────────────────────────────────────────────────────────

const BASE = 100;
const ARTIST_EXACT = 25;
const ARTIST_SHARED = 10;
const CREDITS_DISAGREE = -30;
const CREDIT_OVERRIDE_SEC = 2;
const DURATION_TIGHT = 40;
const DURATION_LOOSE = 15;
const ALBUM_EXACT = 35;
const EXPLICIT_EXACT = 20;
const CONTEXT_SHARED = 20;
const DURATION_TIGHT_SEC = 3;
export const DURATION_LIMIT_SEC = 30;
const VIDEO_DURATION_LIMIT_SEC = 90;
const BRACKET_PASSES = 3;
const DASH_PASSES = 3;

const BRACKETED = /[([]([^()[\]]*)[)\]]/g;
const BRACKETED_TEST = /[([]([^()[\]]*)[)\]]/;
const DASH = /\s+[-–—|]+\s+/;
const FEATURING = /\b(feat|ft|featuring|with)\b.*/g;
const WORD_SPLIT = /[\s.·~〜]+/u;
const NON_ALNUM = /[^\p{L}\p{N}]/gu;
const LATIN = /[a-z0-9]/;
const LATIN_QUERY_CHARS = 'abcdefghijklmnopqrstuvwxyz0123456789 ';
const CJK_OPEN = /[「『【〈《〔［｛]/g;
const CJK_CLOSE = /[」』】〉》〕］｝]/g;
/** Java's `\p{Punct}` is ASCII punctuation only; spelled out here. */
const CJK_CORE_STRIP = /[\s!-/:-@[-`{-~・～〜ー-]+/gu;
/** Character.UnicodeBlock: HIRAGANA, KATAKANA, CJK_UNIFIED_IDEOGRAPHS, HANGUL_SYLLABLES. */
const CJK = /[぀-ゟ゠-ヿ一-鿿가-힯]/;
const ARTIST_SEPARATORS =
  /\s*(?:[,&/;·|、，×]|\band\b|\bx\b|\bvs\.?\b|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|\bwith\b)\s*/u;

const VERSION_WORDS = new Set([
  'remix', 'remixes', 'rmx', 'refix', 'flip', 'bootleg', 'mashup', 'medley',
  'live', 'concert', 'unplugged', 'acoustic', 'instrumental', 'karaoke',
  'vocals', 'vocal', 'acapella', 'acappella', 'backing', 'stems', 'stem',
  'cover', 'demo', 'reprise', 'remake', 'rework', 'extended', 'edit',
  'version', 'mix', 'dub', 'vip', 'session', 'sessions',
  'sped', 'slowed', 'reverb', 'nightcore', 'lofi', 'orchestral', 'symphonic',
  'part', 'pt', 'chapter',
  'カバー', 'カラオケ', 'リミックス', 'ライブ', 'アコースティック',
  'インスト', 'バージョン', '歌ってみた', '踊ってみた', '弾いてみた',
  '翻唱', '现场', '混音',
  '커버', '라이브', '리믹스',
]);
const TRAILING_TAKES = new Set(['instrumental', 'karaoke', 'acapella', 'acappella', 'remix', 'rmx', 'nightcore']);
const NOVOCAL_TAKES = new Set(['instrumental', 'karaoke', 'acapella', 'acappella', 'backing', 'stems', 'stem']);
const NEUTRAL_SEGMENTS = new Set([
  'albumversion', 'originalversion', 'originalmix', 'singleversion',
  'radioversion', 'radioedit', 'stereoversion', 'monoversion',
  'studioversion', 'fullversion', 'standardversion', 'explicitversion',
  'deluxeversion', 'originaltrack',
]);
const NOISE_WORDS = new Set([
  'official', 'video', 'audio', 'lyrics', 'lyric', 'lyrical', 'visualizer',
  'song', 'songs', 'full', 'music', 'the', 'and', 'from', 'feat', 'ft',
  'featuring', 'with', 'new', 'latest', 'free', 'download', 'remaster',
  'remastered', 'explicit', 'clean', 'bonus', 'track', 'deluxe', 'original',
  'album', 'single', 'hd', 'hq', '4k', 'mp3',
]);
const ALBUM_NOISE_WORDS = new Set([
  'album', 'deluxe', 'edition', 'expanded', 'remaster', 'remastered',
  'version', 'explicit', 'clean', 'bonus', 'anniversary',
]);
const TRAILING_NOISE = new Set([
  'song', 'songs', 'video', 'audio', 'lyrics', 'lyric', 'lyrical',
  'official', 'full', 'hd', 'hq', '4k', 'mp3', 'ost', 'soundtrack',
]);
const JOINING_WORDS = new Set(['and']);

// ── Small helpers ──────────────────────────────────────────────────────────

const strip = (word: string) => word.replace(NON_ALNUM, '');
const wordsOf = (text: string) => text.split(WORD_SPLIT).map(strip).filter((w) => w.length > 0);
const sameSet = <T>(a: Set<T>, b: Set<T>) => a.size === b.size && [...a].every((x) => b.has(x));
const union = <T>(...sets: Iterable<T>[]) => new Set(sets.flatMap((s) => [...s]));
const intersect = <T>(a: Set<T>, b: Set<T>) => new Set([...a].filter((x) => b.has(x)));

export function normalize(text: string): string {
  return text
    .normalize('NFKC')
    .toLowerCase()
    .replaceAll('&', ' and ')
    .replace(CJK_OPEN, '(')
    .replace(CJK_CLOSE, ')');
}

// ── Asking ─────────────────────────────────────────────────────────────────

/** What to put to a search box, best query first (TrackMatcher.queries). */
export function queries(target: MatchTarget): string[] {
  const title = searchableTitle(target.title, target.artist ?? '');
  if (!title.trim()) return [];
  const artist = primaryArtist(target.artist ?? '');
  const out = artist ? [`${title} ${artist}`, title] : [title];
  const latin = romajiOf(title).trim();
  const otherScript =
    latin && latin !== title && [...latin].every((c) => LATIN_QUERY_CHARS.includes(c))
      ? latin
      : scriptMates(target.title).find((s) => s.trim() && s !== title);
  if (otherScript) out.push(otherScript);
  return [...new Set(out)];
}

export function searchableTitle(title: string, artist = ''): string {
  const parts = parseTitle(title, artist);
  return [...parts.words, ...parts.versions].join(' ');
}

export function primaryArtist(artist: string): string {
  return normalize(artist).split(ARTIST_SEPARATORS)[0]?.trim() ?? '';
}

export function sharesArtist(wanted: string, got: string): boolean {
  const want = artistNames(wanted);
  const have = artistNames(got);
  return want.length > 0 && have.length > 0 && want.some((w) => have.some((h) => sameArtist(w, h)));
}

// ── Judging ────────────────────────────────────────────────────────────────

export function best<T extends MatchCandidate>(candidates: T[], target: MatchTarget): T | null {
  return ranked(candidates, target)[0] ?? null;
}

export function ranked<T extends MatchCandidate>(candidates: T[], target: MatchTarget): T[] {
  const scored = candidates.flatMap((candidate) => {
    const value = score(candidate, target);
    return value === null ? [] : [{ candidate, value }];
  });
  const credited = scored.filter(({ candidate }) => artistScore(target.artist ?? '', candidate.artist) !== null);
  // Stable: equal scores keep the search's order, as Kotlin's sortedByDescending does.
  return (credited.length ? credited : scored).sort((a, b) => b.value - a.value).map((s) => s.candidate);
}

/** How confident this is the same recording, or null when it is not one. */
export function score(candidate: MatchCandidate, target: MatchTarget): number | null {
  const wanted = parseTitle(target.title, target.artist ?? '');
  const got = parseTitle(candidate.title, candidate.artist);
  if (!wanted.core.trim() || !got.core.trim()) return null;
  const isTitleMatch =
    wanted.core === got.core ||
    romajiOf(wanted.core) === romajiOf(got.core) ||
    isTranslationMatch(wanted, got) ||
    (!([...wanted.core].length <= 3 || [...got.core].length <= 3) &&
      (wanted.core.includes(got.core) || got.core.includes(wanted.core)));
  if (!isTitleMatch) return null;

  const albumMarks = albumVersionMarkers(candidate.album);
  const effectiveVersions = union(got.versions, intersect(albumMarks, NOVOCAL_TAKES), intersect(albumMarks, wanted.versions));
  if (!sameSet(wanted.versions, effectiveVersions)) return null;

  const creditedArtist = artistScore(target.artist ?? '', candidate.artist);
  const duration = durationScore(target.durationSec ?? null, positive(candidate.durationSec), creditedArtist !== null);
  if (duration === null) return null;
  const artist =
    creditedArtist ??
    (!target.isVideo && withinSeconds(candidate, target, CREDIT_OVERRIDE_SEC) ? CREDITS_DISAGREE : null);
  if (artist === null) return null;
  const explicit = explicitScore(target.isExplicit ?? null, candidate.explicit ?? null);
  if (explicit === null) return null;
  return BASE + artist + duration + albumScore(target.album ?? null, candidate.album) + explicit + contextScore(wanted, got);
}

export function matches(candidate: MatchCandidate, title: string, artist: string, durationSec: number | null = null): boolean {
  return score(candidate, { title, artist, durationSec }) !== null;
}

/** TrackMatcher.hasConflictingAlbums. */
export function hasConflictingAlbums(candidates: MatchCandidate[], target: MatchTarget): boolean {
  if (target.album?.trim()) return false;
  const comparable =
    target.durationSec != null ? candidates.filter((c) => withinSeconds(c, target, DURATION_LIMIT_SEC)) : candidates;
  if (target.durationSec != null && comparable.length === 0) return false;
  return new Set(comparable.map((c) => albumKey(c.album)).filter((k): k is string => !!k)).size > 1;
}

/** TrackMatcher.uniquelyMostCreditedCloseMatch. */
export function uniquelyMostCreditedCloseMatch<T extends MatchCandidate>(candidates: T[], target: MatchTarget): T | null {
  const close = candidates.filter((c) => withinSeconds(c, target, DURATION_LIMIT_SEC));
  if (close.length < 2) return null;
  const counted = close.map((c) => ({ c, n: artistNames(c.artist).length }));
  const top = Math.max(...counted.map((x) => x.n));
  if (top < 2) return null;
  const winners = counted.filter((x) => x.n === top);
  return winners.length === 1 ? winners[0]!.c : null;
}

export function withinSeconds(candidate: MatchCandidate, target: MatchTarget, seconds: number): boolean {
  const wanted = target.durationSec;
  const got = positive(candidate.durationSec);
  if (wanted == null || got === null) return false;
  return Math.abs(wanted - got) <= seconds;
}

export function isSevereMismatch(expectedSec: number | null, actualSec: number | null): boolean {
  if (expectedSec == null || actualSec == null) return false;
  return Math.abs(actualSec - expectedSec) > DURATION_LIMIT_SEC;
}

/** secondsOf's `takeIf { it > 0 }`: an unstated runtime is null, not zero. */
function positive(seconds: number | null | undefined): number | null {
  return seconds != null && seconds > 0 ? Math.round(seconds) : null;
}

// ── Title ──────────────────────────────────────────────────────────────────

export function parseTitle(raw: string, artist = ''): TitleParts {
  const versions = new Set<string>();
  const context = new Set<string>();
  let text = normalize(raw);

  for (let pass = 0; pass < BRACKET_PASSES; pass++) {
    if (!BRACKETED_TEST.test(text)) continue;
    text = text.replace(BRACKETED, (_, inner: string) => {
      classify(inner, versions, context);
      return ' ';
    });
  }
  const open = text.search(/[([]/);
  if (open >= 0) {
    classify(text.slice(open), versions, context);
    text = text.slice(0, open);
  }

  for (let pass = 0; pass < DASH_PASSES; pass++) {
    const dash = DASH.exec(text);
    if (!dash) continue;
    const head = text.slice(0, dash.index);
    const tail = text.slice(dash.index + dash[0].length);
    if (isArtistName(head, artist)) {
      classify(head, versions, context);
      text = tail;
    } else if (!hasFilingLatin(head) && hasFilingLatin(tail) && !isArtistName(tail, artist)) {
      classify(head, versions, context);
      text = tail;
    } else {
      classify(tail, versions, context);
      text = head;
    }
  }

  text = text.replace(FEATURING, ' ');
  const containsCJK = CJK.test(text);

  let words = text.split(WORD_SPLIT).map(strip).filter((w) => w.length > 0 && !JOINING_WORDS.has(w));
  while (words.length > 1) {
    const last = words.at(-1)!;
    if (TRAILING_NOISE.has(last)) words = words.slice(0, -1);
    else if (TRAILING_TAKES.has(last)) {
      versions.add(last);
      words = words.slice(0, -1);
    } else break;
  }

  const core = containsCJK ? text.replace(CJK_CORE_STRIP, '') : words.join('');
  return { words, core, versions, context };
}

function classify(segment: string, versions: Set<string>, context: Set<string>): void {
  const words = wordsOf(segment);
  if (!words.length) return;
  if (NEUTRAL_SEGMENTS.has(words.join(''))) return;
  const marks = words.filter((w) => VERSION_WORDS.has(w));
  if (marks.length) {
    marks.forEach((m) => versions.add(m));
    return;
  }
  // A single CJK syllable is a whole title: the length gate exists
  // for Latin glue words and must not drop it.
  words.filter((w) => ([...w].length > 2 || CJK.test(w)) && !NOISE_WORDS.has(w)).forEach((w) => context.add(w));
}

function containsCJKChar(text: string): boolean {
  return CJK.test(text);
}

/**
 * Whether a bracketed aside is the same title in another script rather
 * than packaging around it. True when one core is CJK-only, the other is
 * Latin, and the other's aside is that core in full (one word of a longer
 * aside is not enough). Same-script asides never qualify.
 */
export function isTranslationMatch(wanted: TitleParts, got: TitleParts): boolean {
  const wantedCJK = containsCJKChar(wanted.core) && !hasFilingLatin(wanted.core);
  const gotCJK = containsCJKChar(got.core) && !hasFilingLatin(got.core);
  const wantedLatin = hasFilingLatin(wanted.core);
  const gotLatin = hasFilingLatin(got.core);
  if ((wantedCJK && gotLatin) || (gotCJK && wantedLatin)) {
    if ([...wanted.context].join('') === got.core) return true;
    if ([...got.context].join('') === wanted.core) return true;
  }
  return false;
}

function isArtistName(text: string, artist: string): boolean {
  if (!artist.trim()) return false;
  const words = wordsOf(text);
  if (!words.length) return false;
  const credited = new Set(wordsOf(normalize(artist)));
  return words.every((w) => credited.has(w));
}

export function hasFilingLatin(text: string): boolean {
  return wordsOf(text).some(
    (w) => LATIN.test(w) && !VERSION_WORDS.has(w) && !NOISE_WORDS.has(w) && !TRAILING_NOISE.has(w) && !JOINING_WORDS.has(w),
  );
}

export function scriptMates(raw: string): string[] {
  const text = normalize(raw);
  const dash = DASH.exec(text);
  if (!dash) return [];
  const head = searchableTitle(text.slice(0, dash.index));
  const tail = searchableTitle(text.slice(dash.index + dash[0].length));
  if (!head.trim() || !tail.trim() || head === tail) return [];
  return romajiOf(head) === tail || romajiOf(tail) === head ? [head, tail] : [];
}

// ── Romaji ─────────────────────────────────────────────────────────────────

const KANA_ROMAJI: Map<string, string> = (() => {
  const map = new Map<string, string>();
  const row = (consonant: string, kana: string, vowels = 'aiueo') =>
    [...kana].forEach((k, i) => map.set(k, consonant + vowels[i]));
  row('', 'あいうえお');
  row('k', 'かきくけこ');
  row('g', 'がぎぐげご');
  row('s', 'さしすせそ');
  map.set('し', 'shi');
  row('z', 'ざじずぜぞ');
  map.set('じ', 'ji');
  row('t', 'たちつてと');
  map.set('ち', 'chi');
  map.set('つ', 'tsu');
  row('d', 'だぢづでど');
  map.set('ぢ', 'ji');
  map.set('づ', 'zu');
  row('n', 'なにぬねの');
  row('h', 'はひふへほ');
  map.set('ふ', 'fu');
  row('b', 'ばびぶべぼ');
  row('p', 'ぱぴぷぺぽ');
  row('m', 'まみむめも');
  map.set('や', 'ya');
  map.set('ゆ', 'yu');
  map.set('よ', 'yo');
  row('r', 'らりるれろ');
  map.set('わ', 'wa');
  map.set('を', 'wo');
  map.set('ん', 'n');
  map.set('ゔ', 'vu');
  for (const [k, v] of Object.entries({ ぁ: 'a', ぃ: 'i', ぅ: 'u', ぇ: 'e', ぉ: 'o', ゃ: 'ya', ゅ: 'yu', ょ: 'yo', ゎ: 'wa', ゕ: 'ka', ゖ: 'ke' })) {
    map.set(k, v);
  }
  return map;
})();
const SMALL_Y: Record<string, string> = { ゃ: 'ya', ゅ: 'yu', ょ: 'yo' };
const SMALL_V: Record<string, string> = { ぁ: 'a', ぃ: 'i', ぅ: 'u', ぇ: 'e', ぉ: 'o' };
const CONS_OVERRIDES: [string, string][] = [
  ['shi', 'sh'], ['chi', 'ch'], ['tsu', 'ts'], ['fu', 'f'], ['ji', 'j'], ['zu', 'z'],
];

/** Katakana to hiragana, as the Kotlin `c.code - 0x60` over 'ァ'..'ヶ'. */
const toHiragana = (c: string | undefined): string | undefined => {
  if (c === undefined) return undefined;
  const code = c.charCodeAt(0);
  return code >= 0x30a1 && code <= 0x30f6 ? String.fromCharCode(code - 0x60) : c;
};

export function romajiOf(text: string): string {
  let out = '';
  let geminate = false;
  let lastVowel: string | null = null;
  const emit = (roma: string) => {
    let r = roma;
    if (geminate) {
      geminate = false;
      r = r.startsWith('ch') ? `t${r}` : r.startsWith('sh') ? `s${r}` : r.startsWith('ts') ? `t${r}` : `${r[0]}${r}`;
    }
    out += r;
    lastVowel = [...r].reverse().find((c) => 'aeiou'.includes(c)) ?? null;
  };
  let i = 0;
  while (i < text.length) {
    const c = toHiragana(text[i])!;
    if (c === 'っ') {
      geminate = true;
      i++;
    } else if (c === 'ー') {
      if (lastVowel) out += lastVowel;
      i++;
    } else if (c === 'ん') {
      const peek = KANA_ROMAJI.get(toHiragana(text[i + 1]) ?? '')?.[0];
      emit(peek === 'b' || peek === 'm' || peek === 'p' ? 'm' : 'n');
      i++;
    } else {
      const base = KANA_ROMAJI.get(c);
      if (base === undefined) {
        out += text[i];
        lastVowel = null;
        geminate = false;
        i++;
      } else {
        const next = toHiragana(text[i + 1]);
        const small = next !== undefined ? (SMALL_Y[next] ?? SMALL_V[next]) : undefined;
        if (small !== undefined) {
          const cons = CONS_OVERRIDES.find(([key]) => base.startsWith(key))?.[1] ?? base.slice(0, -1);
          const glide = cons === 'sh' || cons === 'ch' || cons === 'j' ? small.replace(/^y/, '') : small;
          emit(cons + glide);
          i += 2;
        } else {
          emit(base);
          i++;
        }
      }
    }
  }
  return out;
}

// ── Artist ─────────────────────────────────────────────────────────────────

function artistScore(wanted: string, got: string): number | null {
  const want = artistNames(wanted);
  const have = artistNames(got);
  if (!want.length || !have.length) return 0;
  if (!sharesArtist(wanted, got)) return null;
  const key = (names: string[][]) => names.map((n) => n.join(' ')).sort().join('|');
  return key(want) === key(have) ? ARTIST_EXACT : ARTIST_SHARED;
}

/** The credited artists, each as its own list of words; a Set in Kotlin, de-duplicated here. */
export function artistNames(value: string): string[][] {
  const seen = new Map<string, string[]>();
  for (const name of normalize(value).split(ARTIST_SEPARATORS)) {
    const words = name.split(WORD_SPLIT).map(strip).filter((w) => [...w].length > 1);
    if (words.length) seen.set(words.join(' '), words);
  }
  return [...seen.values()];
}

const sameArtist = (a: string[], b: string[]) => runOf(a, b) || runOf(b, a);

function runOf(outer: string[], inner: string[]): boolean {
  if (!inner.length || inner.length > outer.length) return false;
  for (let at = 0; at <= outer.length - inner.length; at++) {
    if (inner.every((w, i) => outer[at + i] === w)) return true;
  }
  return false;
}

// ── Duration, album, explicit, context ─────────────────────────────────────

function durationScore(wanted: number | null, got: number | null, allowVideoDrift: boolean): number | null {
  if (wanted === null || got === null) return 0;
  const drift = Math.abs(wanted - got);
  if (drift > DURATION_LIMIT_SEC && allowVideoDrift && drift <= VIDEO_DURATION_LIMIT_SEC) return 0;
  if (drift > DURATION_LIMIT_SEC) return null;
  return drift <= DURATION_TIGHT_SEC ? DURATION_TIGHT : DURATION_LOOSE;
}

function albumScore(wanted: string | null, got: string | null): number {
  const want = albumKey(wanted);
  const have = albumKey(got);
  return want && have && want === have ? ALBUM_EXACT : 0;
}

function explicitScore(wanted: boolean | null, got: boolean | null): number | null {
  if (wanted === null || got === null) return 0;
  return wanted !== got ? null : EXPLICIT_EXACT;
}

export function albumVersionMarkers(album: string | null | undefined): Set<string> {
  return album?.trim() ? parseTitle(album).versions : new Set();
}

function albumKey(value: string | null | undefined): string | null {
  let text = normalize(value ?? '').trim();
  if (!text) return null;
  for (let pass = 0; pass < BRACKET_PASSES; pass++) text = text.replace(BRACKETED, ' ');
  const key = text.split(WORD_SPLIT).map(strip).filter((w) => w && !ALBUM_NOISE_WORDS.has(w)).join('');
  return key || null;
}

function contextScore(wanted: TitleParts, got: TitleParts): number {
  return [...wanted.context].some((w) => got.context.has(w)) ? CONTEXT_SHARED : 0;
}
