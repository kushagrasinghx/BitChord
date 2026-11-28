import { INSTRUMENTAL_NOTICE, line, lineAlignments, withInstrumentalGaps, wordsFromRuns, type LyricLine, type TimedRun } from './model';
import { decodeEntities, parseEnhancedLrc, parseLrc, parseProvider, parseTtml, plainLines, type Parsed } from './parse';
import { findLyricsBrowseId, timedYoutubeLyrics } from './youtube';
import { lyricsGet, urlWith } from './http';

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

type Obj = Record<string, unknown>;

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

/** Timed lyrics from the Lyrics tab of the exact playing video. */
export async function youtubeMusicLyrics(q: LyricsQuery): Promise<Parsed | null> {
  if (!/^[A-Za-z0-9_-]{11}$/.test(q.videoId)) return null;
  try {
    const { getMusicClient, browseAsAndroidMusic } = await import('@/playback/youtube/innertube');
    const yt = await getMusicClient();
    // The web client is answered with the plain description shelf even when
    // the track has timed lyrics; only the Android Music client gets the
    // timedLyricsModel. Ask as the phone first, then fall back to the shelf.
    try {
      const watch = await yt.actions.execute('/next', { videoId: q.videoId, client: 'YTMUSIC', parse: false });
      const browseId = findLyricsBrowseId(watch.data);
      if (browseId) {
        const timed = timedYoutubeLyrics(await browseAsAndroidMusic(yt, browseId));
        if (timed) return fromLines(timed);
      }
    } catch {
      // The shelf below is still worth asking for.
    }
    const shelf = await yt.music.getLyrics(q.videoId);
    const text = shelf?.description?.toString().trim();
    return text ? fromLines(plainLines(text)) : null;
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

/** A file holding nothing but KuGou's "纯音乐，请欣赏" ("pure music, please enjoy") notice. */
export function isKugouInstrumentalNotice(lrc: string): boolean {
  const lines = lrc.split(/\r?\n/).map((l) => l.replace(KUGOU_STAMP, '').trim()).filter(Boolean);
  return lines.length > 0 && lines.every((l) => INSTRUMENTAL_NOTICE.test(l));
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
