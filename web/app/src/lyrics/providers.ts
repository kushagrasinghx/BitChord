import { line, lineAlignments, withInstrumentalGaps, wordsFromRuns, type LyricLine, type TimedRun } from './model';
import { decodeEntities, parseEnhancedLrc, parseLrc, parseProvider, parseTtml, plainLines, type Parsed } from './parse';
import { lyricsGet, urlWith } from './http';
import { appleWebToken } from '@/data/apple/webToken';

/**
 * Every provider, ported from shared/…/data/lyrics/<Name>.kt. Each takes the
 * cleaned query and answers with parsed lines (plus the original TTML where
 * the host sent Apple's), or null for a miss. None of them throws.
 */

export interface LyricsQuery {
  videoId: string;
  /** Already cleaned for search (sources.forLyricsSearch). */
  title: string;
  artist: string;
  durationMs: number;
  album: string | null;
  /** The recording, when known (BiniLyrics found it, or a previous lookup did). */
  isrc: string | null;
}

/** Fetches a document once per lookup, however many providers want it (LyricsRepository.SharedDocuments). */
export type SharedGet = (url: string) => Promise<string | null>;

const fromTtml = (ttml: string | null): Parsed | null => {
  if (!ttml) return null;
  const lines = parseTtml(ttml);
  return lines.length ? { lines, ttml } : null;
};
const fromLines = (lines: LyricLine[] | null | undefined): Parsed | null =>
  lines?.some((l) => l.text.trim()) ? { lines, ttml: null } : null;
const seconds = (ms: number) => (ms > 0 ? Math.floor(ms / 1000) : null);

function json<T>(body: string | null): T | null {
  if (!body) return null;
  try {
    return JSON.parse(body) as T;
  } catch {
    return null;
  }
}

// ---- lrc.red (LrcRed.kt) -----------------------------------------------------------------

const LRC_RED = 'https://lrc.red/';
const ISRC = /^[A-Z]{2}[A-Z0-9]{3}\d{7}$/;

export function lrcRedDocument(isrc: string | null | undefined): string | null {
  const code = isrc?.trim().toUpperCase();
  return code && ISRC.test(code) ? `${LRC_RED}s/${code}.ttml` : null;
}

interface LrcRedHit {
  isrc?: string;
  title?: string;
  artist?: string;
  duration?: number;
}

const VERSION_WORDS = new Set([
  'live', 'remix', 'remixed', 'mix', 'acoustic', 'unplugged', 'instrumental', 'karaoke', 'cappella', 'acapella',
  'demo', 'edit', 'version', 'cover', 'sped', 'slowed', 'reverb', 'nightcore', 'lofi', 'orchestral', 'extended',
]);
const BRACKETED = /[([][^)\]]*[)\]]/g;
const ARTIST_SEPARATORS = /\s*(?:,|&|;|\/|\s+and\s+|\s+x\s+|\s+with\s+|\s+feat\.?\s+|\s+ft\.?\s+)\s*/i;

/** Lower case, accents off ("ROSÉ" is "rose"), letters and digits only. */
const normalized = (value: string) =>
  value
    .normalize('NFD')
    .replace(/\p{Mn}+/gu, '')
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]/gu, ' ')
    .replace(/\s+/g, ' ')
    .trim();
const coreOf = (title: string) => normalized(title.replace(BRACKETED, ' ').split(' - ')[0]!) || normalized(title);
const versionOf = (title: string) =>
  new Set(
    normalized(`${(title.match(BRACKETED) ?? []).join(' ')} ${title.includes(' - ') ? title.slice(title.indexOf(' - ') + 3) : ''}`)
      .split(' ')
      .filter((w) => VERSION_WORDS.has(w)),
  );
const artistsOf = (artist: string) => new Set(artist.split(ARTIST_SEPARATORS).map(normalized).filter(Boolean));
const sameSet = (a: Set<string>, b: Set<string>) => a.size === b.size && [...a].every((x) => b.has(x));

/** The hit that is this recording, or null: every test a requirement, the closest length wins. */
export function bestLrcRedHit(hits: LrcRedHit[], title: string, artist: string, durationMs: number): LrcRedHit | null {
  const wantedTitle = coreOf(title);
  const wantedVersion = versionOf(title);
  const wantedArtists = artistsOf(artist);
  const secs = durationMs / 1000;
  const distance = (hit: LrcRedHit) => (durationMs > 0 && hit.duration != null ? Math.abs(hit.duration - secs) : 0);
  const passing = hits.filter(
    (hit) =>
      !!hit.title &&
      !!lrcRedDocument(hit.isrc) &&
      coreOf(hit.title) === wantedTitle &&
      sameSet(versionOf(hit.title), wantedVersion) &&
      (!wantedArtists.size || [...artistsOf(hit.artist ?? '')].some((a) => wantedArtists.has(a))) &&
      distance(hit) <= 3,
  );
  return passing.sort((a, b) => distance(a) - distance(b))[0] ?? null;
}

export async function lrcRed(q: LyricsQuery, get: SharedGet): Promise<Parsed | null> {
  const known = lrcRedDocument(q.isrc);
  if (known) {
    const found = fromTtml(await get(known));
    if (found) return found;
  }
  const body = json<{ hits?: LrcRedHit[] }>(await lyricsGet(urlWith(`${LRC_RED}search.json`, { q: `${q.title} ${q.artist}`.trim() })));
  const hit = bestLrcRedHit(body?.hits ?? [], q.title, q.artist, q.durationMs);
  const url = lrcRedDocument(hit?.isrc);
  return url && url !== known ? fromTtml(await get(url)) : null;
}

// ---- BiniLyrics (BiniLyrics.kt) ------------------------------------------------------------

export interface BiniHit {
  isrc?: string;
  lyricsUrl?: string;
}

/** Which recording this is, without fetching its words: the ISRC every other source can use. */
export async function biniIdentify(q: LyricsQuery): Promise<BiniHit | null> {
  const url = q.isrc
    ? urlWith('https://lyrics-api.binimum.org/', { isrc: q.isrc })
    : urlWith('https://lyrics-api.binimum.org/', { track: q.title, artist: q.artist, album: q.album, duration: seconds(q.durationMs) });
  return json<{ results?: BiniHit[] }>(await lyricsGet(url))?.results?.[0] ?? null;
}

export async function bini(q: LyricsQuery, get: SharedGet, hit: BiniHit | null): Promise<(Parsed & { isrc: string | null }) | null> {
  const found = hit ?? (await biniIdentify(q));
  if (!found?.lyricsUrl) return null;
  const parsed = fromTtml(await get(found.lyricsUrl));
  return parsed ? { ...parsed, isrc: found.isrc || null } : null;
}

// ---- BetterLyrics (BetterLyrics.kt) -----------------------------------------------------------

async function betterLyricsAt(endpoint: string, q: LyricsQuery): Promise<Parsed | null> {
  const body = await lyricsGet(urlWith(endpoint, { s: q.title, a: q.artist, d: seconds(q.durationMs), al: q.album }));
  return body ? parseProvider(body) : null;
}
export const betterLyrics = (q: LyricsQuery) => betterLyricsAt('https://lyrics-api.boidu.dev/getLyrics', q);
export const betterLyricsPortato = (q: LyricsQuery) => betterLyricsAt('https://lyrics-api.boidu.dev/qq/getLyrics', q);

// ---- Unison (Unison.kt) -----------------------------------------------------------------------

export async function unison(q: LyricsQuery): Promise<Parsed | null> {
  const body = json<{ success?: boolean; data?: { lyrics?: string; format?: string; syncType?: string } }>(
    await lyricsGet(urlWith('https://unison.boidu.dev/lyrics', { song: q.title, artist: q.artist, album: q.album, duration: seconds(q.durationMs) })),
  );
  const data = body?.success ? body.data : null;
  const text = data?.lyrics?.trim();
  if (!text) return null;
  if (data!.format?.toLowerCase() === 'ttml') return fromTtml(text);
  if (data!.syncType?.toLowerCase() === 'plain') return fromLines(plainLines(text));
  const enhanced = parseEnhancedLrc(text);
  return fromLines(enhanced.length ? enhanced : parseLrc(text));
}

// ---- PaxSenix (PaxSenix.kt) ---------------------------------------------------------------------

interface Candidate {
  id: string;
  title: string;
  artist: string;
  durationMs: number;
}

const ID_KEYS = ['id', 'trackId', 'track_id', 'realId'];
const TITLE_KEYS = ['name', 'title', 'trackName', 'track_name'];
const ARTIST_KEYS = ['artistName', 'artist_name'];
const DURATION_KEYS = ['durationInMillis', 'durationMs', 'duration_ms', 'duration'];
type Obj = Record<string, unknown>;
const firstString = (o: Obj, keys: string[]) =>
  keys.map((k) => o[k]).find((v): v is string | number => (typeof v === 'string' && v.trim() !== '') || typeof v === 'number')?.toString().trim();
const firstNumber = (o: Obj, keys: string[]) => keys.map((k) => o[k]).find((v): v is number => typeof v === 'number');
const toDurationMs = (n: number | undefined) => (!n || n <= 0 ? 0 : n < 10_000 ? n * 1000 : n);

function artistNames(o: Obj): string | null {
  const artists = o.artists ?? o.artist;
  if (typeof artists === 'string') return artists;
  if (Array.isArray(artists)) {
    return artists.map((a) => (typeof a === 'string' ? a : a && typeof a === 'object' ? firstString(a as Obj, ['name', 'artistName', 'title']) : null)).filter(Boolean).join(', ') || null;
  }
  if (artists && typeof artists === 'object') return firstString(artists as Obj, ['name', 'artistName', 'title']) ?? null;
  return null;
}

function collectCandidates(root: unknown, into: Candidate[] = []): Candidate[] {
  if (Array.isArray(root)) root.forEach((r) => collectCandidates(r, into));
  else if (root && typeof root === 'object') {
    const o = root as Obj;
    const details = (o.attributes && typeof o.attributes === 'object' ? o.attributes : o) as Obj;
    const id = firstString(o, ID_KEYS) ?? firstString(details, ID_KEYS);
    const title = firstString(details, TITLE_KEYS);
    if (id && title) {
      into.push({ id, title, artist: firstString(details, ARTIST_KEYS) ?? artistNames(details) ?? '', durationMs: toDurationMs(firstNumber(details, DURATION_KEYS)) });
    }
    Object.values(o).forEach((v) => collectCandidates(v, into));
  }
  return into;
}

function textScore(candidate: string, wanted: string, exact: number, partial: number): number {
  const c = candidate.toLowerCase();
  const w = wanted.toLowerCase();
  if (!c.trim() || !w.trim()) return 0;
  if (c === w) return exact;
  return c.includes(w) || w.includes(c) ? partial : 0;
}

function bestCandidate(root: unknown, q: LyricsQuery): Candidate | null {
  let best: { c: Candidate; score: number } | null = null;
  for (const c of collectCandidates(root)) {
    let score = textScore(c.title, q.title, 20, 10) + textScore(c.artist, q.artist, 15, 5);
    if (q.durationMs > 0 && c.durationMs > 0) {
      const diff = Math.abs(c.durationMs - q.durationMs);
      score += diff < 3000 ? 10 : diff < 10_000 ? 5 : 0;
    }
    if (!best || score > best.score) best = { c, score };
  }
  return best && best.score >= 10 ? best.c : null;
}

/** PaxSenix's structured Apple payload, keeping its syllable timings (PaxSenix.parseTimedApple). */
export function parsePaxSenixTimed(raw: string): LyricLine[] | null {
  const root = json<unknown>(raw);
  const find = (node: unknown): Obj[] | null => {
    if (Array.isArray(node)) {
      for (const n of node) {
        const found = find(n);
        if (found) return found;
      }
      return null;
    }
    if (node && typeof node === 'object') {
      const content = (node as Obj).content;
      if (Array.isArray(content) && content.some((c) => c && typeof c === 'object' && 'timestamp' in c)) return content as Obj[];
      for (const v of Object.values(node as Obj)) {
        const found = find(v);
        if (found) return found;
      }
    }
    return null;
  };
  const rows = find(root);
  if (!rows) return null;
  const lines: LyricLine[] = [];
  rows.forEach((row, index) => {
    const start = typeof row.timestamp === 'number' ? row.timestamp : null;
    const wordRows = Array.isArray(row.text) ? (row.text as Obj[]) : null;
    if (start === null || !wordRows) return;
    const texts = wordRows.map((w) => (typeof w.text === 'string' ? w.text : null)).filter((t): t is string => t !== null);
    if (!texts.length) return;
    const nextLine = typeof rows[index + 1]?.timestamp === 'number' ? (rows[index + 1]!.timestamp as number) : null;
    const runs: TimedRun[] = [];
    wordRows.forEach((word, i) => {
      const text = typeof word.text === 'string' ? word.text.trim() : '';
      if (!text || typeof word.timestamp !== 'number') return;
      const next = wordRows[i + 1];
      const end = typeof word.endtime === 'number' ? word.endtime : typeof next?.timestamp === 'number' ? next.timestamp : (nextLine ?? word.timestamp + 800);
      runs.push({ startMs: word.timestamp, endMs: end, raw: word.part === true ? text : `${text} ` });
    });
    const timed = wordsFromRuns(runs, true);
    const untimed = runs.length !== texts.filter((t) => t.trim()).length;
    lines.push(
      line(Math.min(start, timed[0]?.startMs ?? start), untimed ? texts.map((t) => t.trim()).join(' ') : timed.map((w) => w.text).join(' '), {
        words: untimed ? [] : timed,
        sungUntilMs: nextLine,
      }),
    );
  });
  const out = withInstrumentalGaps(lines);
  return out.some((l) => l.text.trim()) ? out : null;
}

const paxResponse = (raw: string | null): Parsed | null => (raw ? fromLines(parsePaxSenixTimed(raw)) ?? parseProvider(raw) : null);

export async function paxSenix(q: LyricsQuery): Promise<Parsed | null> {
  const token = await appleWebToken();
  if (!token) return null;
  const search = await lyricsGet(
    urlWith('https://amp-api.music.apple.com/v1/catalog/us/search', { term: `${q.title} ${q.artist}`, types: 'songs', limit: 10, l: 'en-US' }),
    { route: 'extension', headers: { Authorization: `Bearer ${token}` } },
  );
  const id = bestCandidate(json(search), q)?.id;
  if (!id) return null;
  return paxResponse(await lyricsGet(urlWith('https://lyrics.paxsenix.org/apple-music/lyrics', { id, ttml: 'true' })));
}

function paxApi(path: string, params: Record<string, string>, key: string) {
  return lyricsGet(urlWith(`https://api.paxsenix.org/${path}`, params), {
    route: 'extension',
    patient: true,
    headers: { Authorization: `Bearer ${key.replace(/^Bearer\s+/i, '').trim()}` },
  });
}

async function paxGeneric(q: LyricsQuery, key: string): Promise<Parsed | null> {
  const raw = await paxApi('lyrics/lrcget', { q: `${q.title} ${q.artist}` }, key);
  const root = json<{ lyrics?: unknown[] }>(raw);
  const documents = Array.isArray(root?.lyrics) ? root.lyrics : null;
  if (!documents?.length) return paxResponse(raw);
  // Candidates, not lines: pick one recording before parsing (PaxSenix.parseLrcGet).
  const scored = documents
    .map((document) => {
      const parsed = paxResponse(JSON.stringify(document));
      if (!parsed) return null;
      const c = collectCandidates([document])[0];
      const score = c ? textScore(c.title, q.title, 20, 10) + textScore(c.artist, q.artist, 15, 5) : 0;
      const last = Math.max(0, ...parsed.lines.map((l) => Math.max(l.timeMs, l.sungUntilMs ?? 0, l.words.at(-1)?.endMs ?? 0)));
      return { parsed, score, distance: q.durationMs > 0 ? Math.abs(last - q.durationMs) : 0 };
    })
    .filter((x): x is NonNullable<typeof x> => !!x)
    .sort((a, b) => b.score - a.score || a.distance - b.distance);
  return scored[0]?.parsed ?? null;
}

export async function paxSenixSpotify(q: LyricsQuery, key: string): Promise<Parsed | null> {
  if (!key) return null;
  const id = bestCandidate(json(await paxApi('spotify/search', { q: `${q.title} ${q.artist}` }, key)), q)?.id;
  return (id ? paxResponse(await paxApi('lyrics/spotify', { id }, key)) : null) ?? paxGeneric(q, key);
}

export async function paxSenixMusixmatch(q: LyricsQuery, key: string): Promise<Parsed | null> {
  if (!key) return null;
  const found = paxResponse(await paxApi('lyrics/musixmatch', { t: q.title, a: q.artist, d: String(Math.floor(q.durationMs / 1000)) }, key));
  return found ?? paxGeneric(q, key);
}

// ---- LyricsPlus (LyricsPlus.kt) ------------------------------------------------------------------

// atomix.one (bad certificate), prjktla.workers.dev (worker deleted) and the
// two vercel.app deployments (disabled, 402) were dropped on 2026-10-17.
const MIRRORS = ['https://lyricsplus.prjktla.my.id', 'https://lyricsplus.binimum.org'];
let lastGoodMirror: string | null = null;

interface LyricsPlusResponse {
  lyrics?: { time?: number; duration?: number; text?: string; syllabus?: { time?: number; duration?: number; text?: string }[]; element?: unknown }[];
  metadata?: { agents?: Record<string, { type?: string; alias?: string }> };
}

export function parseLyricsPlus(response: LyricsPlusResponse): LyricLine[] {
  const sung: { line: LyricLine; element: unknown }[] = [];
  for (const l of response.lyrics ?? []) {
    if (typeof l.time !== 'number') continue;
    const words = wordsFromRuns(
      (l.syllabus ?? [])
        .filter((s) => typeof s.text === 'string' && typeof s.time === 'number')
        .map((s) => ({ startMs: s.time!, endMs: s.time! + (s.duration ?? 0), raw: s.text! })),
      true,
    );
    let built: LyricLine | null = null;
    if (words.length) built = line(Math.min(l.time, words[0]!.startMs), words.map((w) => w.text).join(' '), { words });
    else if (l.text?.trim()) built = line(l.time, l.text.trim(), { sungUntilMs: l.duration && l.duration > 0 ? l.time + l.duration : null });
    if (built) sung.push({ line: built, element: l.element });
  }
  sung.sort((a, b) => a.line.timeMs - b.line.timeMs);
  const types: Record<string, string> = {};
  for (const [id, agent] of Object.entries(response.metadata?.agents ?? {})) if (agent.type) types[agent.alias ?? id] = agent.type;
  const singer = (element: unknown) =>
    element && typeof element === 'object' && !Array.isArray(element) && typeof (element as Obj).singer === 'string' ? ((element as Obj).singer as string) : null;
  const sides = lineAlignments(sung.map((s) => singer(s.element)), types);
  return withInstrumentalGaps(
    sung.map((s, i) => {
      const opposite = Array.isArray(s.element) && s.element.some((tag) => tag === 'opposite' || tag === 'right');
      return { ...s.line, alignment: sides[i] === 'end' || opposite ? ('end' as const) : ('start' as const) };
    }),
  );
}

export async function lyricsPlus(q: LyricsQuery): Promise<Parsed | null> {
  const hosts = lastGoodMirror ? [lastGoodMirror, ...MIRRORS.filter((m) => m !== lastGoodMirror)] : MIRRORS;
  // All at once; the first mirror with something usable wins (a 404 doesn't).
  return new Promise((resolve) => {
    let pending = hosts.length;
    let done = false;
    for (const host of hosts) {
      void (async () => {
        const body = json<LyricsPlusResponse>(
          await lyricsGet(urlWith(`${host}/v2/lyrics/get`, { title: q.title, artist: q.artist, duration: seconds(q.durationMs), album: q.album, isrc: q.isrc })),
        );
        const lines = body ? parseLyricsPlus(body) : [];
        if (!done && lines.length) {
          done = true;
          lastGoodMirror = host;
          resolve({ lines, ttml: null });
        }
        if (--pending === 0 && !done) resolve(null);
      })();
    }
  });
}

// ---- YouTube (YouTubeLyrics.kt) -----------------------------------------------------------------

function* objectsNamed(node: unknown, name: string): Generator<Obj> {
  if (Array.isArray(node)) for (const n of node) yield* objectsNamed(n, name);
  else if (node && typeof node === 'object') {
    for (const [key, value] of Object.entries(node as Obj)) {
      if (key === name && value && typeof value === 'object' && !Array.isArray(value)) yield value as Obj;
      yield* objectsNamed(value, name);
    }
  }
}

function youtubeText(node: unknown): string {
  if (typeof node === 'string') return node;
  if (Array.isArray(node)) return node.map(youtubeText).join('');
  if (node && typeof node === 'object') {
    const o = node as Obj;
    if (typeof o.text === 'string') return o.text;
    if (typeof o.simpleText === 'string') return o.simpleText;
    return Object.values(o).map(youtubeText).join('');
  }
  return '';
}

/** Plain lyrics from the Lyrics tab of the exact playing video. */
export async function youtubeMusicLyrics(q: LyricsQuery): Promise<Parsed | null> {
  if (!/^[A-Za-z0-9_-]{11}$/.test(q.videoId)) return null;
  try {
    const { getMusicClient } = await import('@/playback/youtube/innertube');
    const yt = await getMusicClient();
    const shelf = await yt.music.getLyrics(q.videoId);
    const text = shelf?.description?.toString().trim();
    return text ? fromLines(text.split(/\r?\n/).map((t) => t.trim()).filter(Boolean).map((t) => line(0, t))) : null;
  } catch {
    return null;
  }
}

/** Timed captions for the exact playing video (Innertube.transcript). */
export async function youtubeCaptions(q: LyricsQuery): Promise<Parsed | null> {
  if (!/^[A-Za-z0-9_-]{11}$/.test(q.videoId)) return null;
  try {
    const { getMusicClient } = await import('@/playback/youtube/innertube');
    const yt = await getMusicClient();
    // get_transcript takes a tiny protobuf: field 1, length, the video id.
    const id = new TextEncoder().encode(q.videoId);
    const params = btoa(String.fromCharCode(10, id.length, ...id));
    const response = await yt.actions.execute('/get_transcript', { params, client: 'YTMUSIC' });
    const lines = [...objectsNamed(response.data, 'transcriptCueRenderer')]
      .map((cue) => {
        const start = Number(cue.startOffsetMs);
        const text = youtubeText(cue.cue).replace(/^[\s♪]+|[\s♪]+$/g, '');
        return Number.isFinite(start) && text ? line(start, text) : null;
      })
      .filter((l): l is LyricLine => !!l)
      .sort((a, b) => a.timeMs - b.timeMs);
    return fromLines(lines);
  } catch {
    return null;
  }
}

// ---- KuGou (KuGou.kt) ---------------------------------------------------------------------------

const stripParenthetical = (s: string) => s.replace(/[(（].*?[)）]/g, '').trim() || s;
const KUGOU_STAMPED = /^\[\d{2}:\d{2}\.\d{2,3}\].*/;
const KUGOU_CREDIT = /.+\][^[]+[:：].+/;

/** KuGou files open and close with credit lines ("作词: …") stamped like lyrics; cut them. */
export function stripKugouCredits(lrc: string): string {
  const lines = lrc.split(/\r?\n/).filter((l) => KUGOU_STAMPED.test(l));
  if (!lines.length) return '';
  let head = 0;
  for (let i = Math.min(30, lines.length - 1); i >= 0; i--) {
    if (KUGOU_CREDIT.test(lines[i]!)) {
      head = i + 1;
      break;
    }
  }
  const body = lines.slice(head);
  let tail = 0;
  for (let i = 0; i <= Math.min(30, body.length - 1); i++) {
    if (KUGOU_CREDIT.test(body[body.length - 1 - i]!)) {
      tail = i + 1;
      break;
    }
  }
  return body.slice(0, body.length - tail).join('\n');
}

const KUGOU_STAMP = /\[\d{2}:\d{2}\.\d{2,3}\]/g;
const KUGOU_INSTRUMENTAL = /纯音乐.*欣赏/;

/** A file holding nothing but KuGou's "纯音乐，请欣赏" ("pure music, please enjoy") notice. */
export function isKugouInstrumentalNotice(lrc: string): boolean {
  const lines = lrc.split(/\r?\n/).map((l) => l.replace(KUGOU_STAMP, '').trim()).filter(Boolean);
  return lines.length > 0 && lines.every((l) => KUGOU_INSTRUMENTAL.test(l));
}

export async function kugou(q: LyricsQuery): Promise<Parsed | null> {
  const keyword = `${stripParenthetical(q.title)} - ${stripParenthetical(q.artist)}${q.album ? ` ${q.album}` : ''}`;
  const secs = Math.floor(q.durationMs / 1000);
  const ext = { route: 'extension' as const };
  const songs = json<{ data?: { info?: { hash: string; duration?: number }[] } }>(
    await lyricsGet(urlWith('https://mobileservice.kugou.com/api/v3/search/song', { version: 9108, plat: 0, pagesize: 8, showtype: 0, keyword }), ext),
  );
  const hashes = (songs?.data?.info ?? [])
    .filter((s) => secs <= 0 || Math.abs((s.duration ?? -1) - secs) <= 8)
    .sort((a, b) => Math.abs((a.duration ?? 0) - secs) - Math.abs((b.duration ?? 0) - secs))
    .map((s) => s.hash);
  type Candidates = { candidates?: { id: string; accesskey: string }[] };
  let candidate: { id: string; accesskey: string } | undefined;
  for (const hash of hashes) {
    candidate = json<Candidates>(await lyricsGet(urlWith('https://lyrics.kugou.com/search', { ver: 1, man: 'yes', client: 'pc', hash }), ext))?.candidates?.[0];
    if (candidate) break;
  }
  candidate ??= json<Candidates>(
    await lyricsGet(urlWith('https://lyrics.kugou.com/search', { ver: 1, man: 'yes', client: 'pc', keyword, duration: secs > 0 ? secs * 1000 : null }), ext),
  )?.candidates?.[0];
  if (!candidate) return null;
  const download = json<{ content?: string }>(
    await lyricsGet(urlWith('https://lyrics.kugou.com/download', { fmt: 'lrc', charset: 'utf8', client: 'pc', ver: 1, id: candidate.id, accesskey: candidate.accesskey }), ext),
  );
  if (!download?.content) return null;
  try {
    const lrc = new TextDecoder().decode(Uint8Array.from(atob(download.content), (c) => c.charCodeAt(0)));
    const stripped = stripKugouCredits(lrc);
    if (isKugouInstrumentalNotice(stripped)) return null;
    return fromLines(parseLrc(stripped));
  } catch {
    return null;
  }
}

// ---- LRCLIB (LrcLib.kt) ---------------------------------------------------------------------------

const LRCLIB_NOISE = /\((?:from|feat\.?|official|lyrical|video|audio|remix)[^)]*\)|\[[^\]]*\]|\b(?:official (?:video|audio|music video)|lyrical|full song|4k video)\b/gi;
const lrclibClean = (s: string) => s.replace(LRCLIB_NOISE, ' ').split(' | ')[0]!.replace(/\s+/g, ' ').trim() || s;

export async function lrclib(q: LyricsQuery): Promise<Parsed | null> {
  const title = lrclibClean(q.title);
  const artist = lrclibClean(q.artist);
  const secs = Math.floor(q.durationMs / 1000);
  const exact = json<{ syncedLyrics?: string }>(
    await lyricsGet(urlWith('https://lrclib.net/api/get', { track_name: title, artist_name: artist, duration: secs })),
  )?.syncedLyrics;
  let synced = exact;
  if (!synced) {
    const hits = json<{ syncedLyrics?: string; duration?: number }[]>(await lyricsGet(urlWith('https://lrclib.net/api/search', { track_name: title, artist_name: artist })));
    synced = (hits ?? [])
      .filter((h) => h.syncedLyrics?.trim())
      .sort((a, b) => Math.abs((a.duration ?? 0) - secs) - Math.abs((b.duration ?? 0) - secs))[0]?.syncedLyrics;
  }
  return synced ? fromLines(parseLrc(synced)) : null;
}

// ---- Musixmatch (Musixmatch.kt) -------------------------------------------------------------------

const MXM = 'https://apic.musixmatch.com/ws/1.1';
const MXM_APP = 'mobile-app-v1.0';
const MXM_FALLBACK_SECRET = 'f09016176ba43a1cfd1031fbd6b3d26c';
const mxmGuid = crypto.randomUUID();
let mxmSecret: string | null = null;
let mxmToken: string | null = null;

async function musixmatchSecret(): Promise<string> {
  if (mxmSecret) return mxmSecret;
  const ext = { route: 'extension' as const };
  try {
    const page = await lyricsGet('https://www.musixmatch.com/search', ext);
    const script = page?.match(/src=["']([^"']*\/_next\/static\/chunks\/pages\/_app-[^"']+\.js)["']/i)?.[1];
    const source = script ? await lyricsGet(new URL(script, 'https://www.musixmatch.com/search').toString(), ext) : null;
    const encoded = source?.match(/from\(\s*["']([^"']+)["']\s*\.split/)?.[1];
    const decoded = encoded ? atob([...encoded].reverse().join('')) : '';
    mxmSecret = decoded.trim() || MXM_FALLBACK_SECRET;
  } catch {
    mxmSecret = MXM_FALLBACK_SECRET;
  }
  return mxmSecret;
}

/** Signs `<url><UTC yyyyMMdd>` with HMAC-SHA256, as Musixmatch's web client does. */
async function mxmSign(url: string, secret: string): Promise<string> {
  const normalizedUrl = url.replace(/%20/g, '+').replace(/ /g, '+');
  const now = new Date();
  const date = `${now.getUTCFullYear()}${String(now.getUTCMonth() + 1).padStart(2, '0')}${String(now.getUTCDate()).padStart(2, '0')}`;
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const raw = new Uint8Array(await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(`${normalizedUrl}${date}`)));
  const signature = btoa(String.fromCharCode(...raw));
  return `${normalizedUrl}&signature=${encodeURIComponent(signature)}&signature_protocol=sha256`;
}

async function mxmGet(build: (token: string) => string): Promise<Obj | null> {
  for (let attempt = 0; attempt < 2; attempt++) {
    const secret = await musixmatchSecret();
    if (!mxmToken) {
      const body = json<{ message?: { body?: { user_token?: string } } }>(
        await lyricsGet(await mxmSign(urlWith(`${MXM}/token.get`, { app_id: MXM_APP, guid: mxmGuid, format: 'json' }), secret), { route: 'extension' }),
      );
      mxmToken = body?.message?.body?.user_token ?? null;
      if (!mxmToken) return null;
    }
    const body = json<{ message?: { header?: { status_code?: number }; body?: Obj } }>(
      await lyricsGet(await mxmSign(build(mxmToken), secret), { route: 'extension' }),
    );
    const status = body?.message?.header?.status_code;
    if (body && status !== 401 && status !== 402) return body.message?.body ?? null;
    // An expired token or rotated key: refresh both once.
    mxmToken = null;
    mxmSecret = null;
  }
  return null;
}

/** Rich sync, without flattening it through LRC (Musixmatch.parseRichSyncBody). */
export function parseRichSync(body: string): LyricLine[] {
  const entries = json<{ ts: number; te: number; l?: { c: string; o: number }[]; x?: string }[]>(body) ?? [];
  const lines = entries
    .map((entry) => {
      const lineStart = Math.trunc(entry.ts * 1000);
      const lineEnd = Math.max(lineStart, Math.trunc(entry.te * 1000));
      let previous = lineStart;
      const fragments = entry.l ?? [];
      const runs: TimedRun[] = [];
      fragments.forEach((fragment, index) => {
        if (!fragment.c) return;
        const start = Math.max(lineStart, previous, Math.trunc((entry.ts + fragment.o) * 1000));
        const next = fragments[index + 1];
        const until = next ? Math.trunc((entry.ts + next.o) * 1000) : lineEnd;
        previous = start;
        runs.push({ startMs: start, endMs: Math.max(start, Math.min(lineEnd, until)), raw: fragment.c });
      });
      const words = wordsFromRuns(runs, true);
      const text = (entry.x ?? '').trim() || words.map((w) => w.text).join(' ');
      return text ? line(Math.min(lineStart, words[0]?.startMs ?? lineStart), text, { words, sungUntilMs: lineEnd > lineStart ? lineEnd : null }) : null;
    })
    .filter((l): l is LyricLine => !!l)
    .sort((a, b) => a.timeMs - b.timeMs);
  return withInstrumentalGaps(lines);
}

export async function musixmatch(q: LyricsQuery): Promise<Parsed | null> {
  const secs = Math.floor(q.durationMs / 1000);
  const search = await mxmGet((token) =>
    urlWith(`${MXM}/track.search`, {
      app_id: MXM_APP, format: 'json', q_track: q.title, q_artist: q.artist, f_has_lyrics: 1,
      s_track_rating: 'desc', quorum_factor: 1, page_size: 10, page: 1, usertoken: token,
    }),
  );
  type Track = { track_id: number; track_name: string; artist_name?: string; track_length?: number; has_subtitles?: number; has_richsync?: number };
  const tracks = ((search?.track_list as { track: Track }[] | undefined) ?? []).map((t) => t.track);
  const score = (t: Track) => {
    const name = t.track_name.trim().toLowerCase();
    const want = q.title.trim().toLowerCase();
    let s = name === want ? 80 : name.includes(want) || want.includes(name) ? 40 : 0;
    if ((t.artist_name ?? '').toLowerCase().includes(q.artist.toLowerCase())) s += 40;
    if (t.track_length != null) {
      const diff = Math.abs(t.track_length - secs);
      s += diff <= 2 ? 30 : diff <= 5 ? 15 : diff <= 10 ? 5 : -20;
    }
    return s;
  };
  const track = tracks.sort((a, b) => score(b) - score(a))[0];
  if (!track) return null;
  if (track.has_richsync) {
    const rich = await mxmGet((token) => urlWith(`${MXM}/track.richsync.get`, { app_id: MXM_APP, format: 'json', track_id: track.track_id, usertoken: token }));
    const body = (rich?.richsync as { richsync_body?: string } | undefined)?.richsync_body;
    const lines = body ? parseRichSync(body) : [];
    if (lines.some((l) => l.words.length)) return { lines, ttml: null };
  }
  if (!track.has_subtitles) return null;
  const subtitle = await mxmGet((token) =>
    urlWith(`${MXM}/track.subtitle.get`, { app_id: MXM_APP, format: 'json', track_id: track.track_id, subtitle_format: 'mxm', usertoken: token }),
  );
  const body = (subtitle?.subtitle as { subtitle_body?: string } | undefined)?.subtitle_body;
  const entries = json<{ text: string; time: { total: number } }[]>(body ?? null) ?? [];
  return fromLines(entries.filter((e) => e.text.trim()).map((e) => line(Math.trunc(e.time.total * 1000), e.text.trim())));
}

// ---- Genius (Genius.kt) ---------------------------------------------------------------------------

const GENIUS_NOISE =
  /\s*[([]\s*(?:from|feat\.?|ft\.?|featuring|with|prod\.?|produced by|official|lyrical|video|audio|remix|music video|visualizer|mv|hd|4k|hq|full song)[^)\]]*[)\]]|\s*\b(?:official\s+(?:music\s+)?(?:video|audio)|lyrical(?:\s+video)?|full\s+song|4k\s+video|hd\s+video|music\s+video)\b/gi;
const geniusClean = (s: string) =>
  s
    .replace(/[♪♫★☆【】《》「」~_]/g, ' ')
    .replace(GENIUS_NOISE, ' ')
    .replace(/\b(?:prod(?:uced)?\.?(?:\s+by)?)\s+.*$/i, ' ')
    .split(' | ')[0]!
    .replace(/\s+/g, ' ')
    .trim() || s.trim();

/** Genius's page HTML to lines, keeping "[Chorus]" headers and stanza breaks. */
export function parseGeniusHtml(html: string): LyricLine[] {
  const doc = new DOMParser().parseFromString(html, 'text/html');
  let containers = [...doc.querySelectorAll('div[data-lyrics-container="true"]')];
  if (!containers.length) containers = [...doc.querySelectorAll('div.lyrics')];
  let text = '';
  for (const container of containers) {
    container
      .querySelectorAll('[data-exclude-from-selection="true"], [class*="LyricsHeader__Container"], [class*="SongBioPreview"], [class*="InreadAd"], button, script, style')
      .forEach((n) => n.remove());
    container.querySelectorAll('br').forEach((br) => br.replaceWith('\n'));
    text += `${container.textContent ?? ''}\n`;
  }
  const cleaned = text
    .replace(/[  ﻿]/g, ' ')
    .replace(/\d*You might also like/gi, '')
    .trim()
    .replace(/\d*Embed\s*$/i, '')
    .trim();
  const out: LyricLine[] = [];
  for (const raw of cleaned.split('\n')) {
    const t = raw.trim().replace(/\d*Embed\s*$/i, '').trim();
    if (!t) {
      if (out.length && out.at(-1)!.text) out.push(line(0, ''));
    } else out.push(line(0, decodeEntities(t)));
  }
  while (out.length && !out[0]!.text) out.shift();
  while (out.length && !out.at(-1)!.text) out.pop();
  return out;
}

export async function genius(q: LyricsQuery): Promise<Parsed | null> {
  const title = geniusClean(q.title);
  const artist = geniusClean(q.artist);
  const bare = title.replace(/\s*[([].*?[)\]]/g, ' ').replace(/[^\p{L}\p{N}\s]/gu, ' ').replace(/\s+/g, ' ').trim();
  const attempts = [...new Set([`${artist} ${title}`.trim(), `${artist} ${bare}`.trim(), bare || title])];
  for (const query of attempts) {
    const body = json<{ response?: { sections?: { type?: string; hits?: { result?: Obj }[] }[] } }>(
      await lyricsGet(urlWith('https://genius.com/api/search/multi', { q: query }), { route: 'extension' }),
    );
    const hits = body?.response?.sections?.find((s) => s.type === 'song')?.hits?.map((h) => h.result).filter((r): r is Obj => !!r) ?? [];
    const t = (bare || title).toLowerCase();
    const a = artist.toLowerCase();
    const scored = hits
      .map((hit) => {
        const ht = String(hit.title ?? '').toLowerCase();
        const ha = String(hit.artist_names ?? '').toLowerCase();
        const titleMatch = !!t && (ht === t || ht.includes(t) || t.includes(ht));
        const artistMatch = !!a && (ha === a || ha.includes(a) || a.includes(ha));
        if (!titleMatch && !artistMatch) return null;
        let score = (ht === t ? 50 : titleMatch ? 25 : 0) + (artistMatch ? (ha === a ? 40 : 20) : 0);
        const path = String(hit.path ?? '');
        if (/translation/i.test(path) && !t.includes('translation')) score -= 30;
        if (/tracklist|album-art/i.test(path)) score -= 50;
        return score > 0 ? { hit, score } : null;
      })
      .filter((x): x is { hit: Obj; score: number } => !!x)
      .sort((x, y) => y.score - x.score);
    const url = scored[0]?.hit.url;
    if (typeof url !== 'string') continue;
    const html = await lyricsGet(url, { route: 'extension' });
    const lines = html ? parseGeniusHtml(html) : [];
    if (lines.length) return { lines, ttml: null };
  }
  return null;
}
