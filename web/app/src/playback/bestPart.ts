import { BestPartHeat, BestPartLine, bestPartFromHeat, bestPartHeatmap } from '@bitchord/shared-kotlin';
import { lookupLyrics } from '@/lyrics/repository';
import type { LyricLine } from '@/lyrics/model';

/**
 * Songs' best parts (BestPart.kt, the Android app's own choice): the "Most
 * replayed" curve, fetched from youtube.com and kept, and the start worked out
 * from it and the synced lyrics. A port of BestParts.kt.
 *
 * Only the curves are kept, never the starts: a start depends on the lyrics as
 * well, which this page doesn't store. A curve keeps for a month, since
 * YouTube's replays don't move much in that time; "this video has no curve"
 * for a week, since a song that is new now has the views for one soon. A
 * failed request is kept nowhere: offline is not an answer about the video.
 */

export interface BestPartResult {
  startMs: number;
  /** What said so: HEATMAP, LYRICS or FALLBACK (BestPartStart.Source). */
  source: string;
}

export interface BestPartTrack {
  title: string;
  artist: string;
  album?: string | null;
}

/** Innertube.kt's WEB_CLIENT_VERSION: webNext asks www.youtube.com as its web client. */
const WEB_CLIENT_VERSION = '2.20260708.00.00';
const KEY = 'bitchord:best-part-heat';
const MAX_KEPT = 500;
const HEATMAP_TIMEOUT_MS = 8_000;
const LYRICS_TIMEOUT_MS = 6_000;
const MONTH_MS = 30 * 24 * 3600 * 1000;
const WEEK_MS = 7 * 24 * 3600 * 1000;

/** A curve as fetched. An empty [intensity] means the video has none. */
interface Kept {
  segmentMs: number;
  intensity: number[];
  fetchedAt: number;
}

function loadKept(): Record<string, Kept> {
  try {
    return (JSON.parse(localStorage.getItem(KEY) ?? '{}') as Record<string, Kept>) ?? {};
  } catch {
    return {};
  }
}

function saveKept(videoId: string, kept: Kept) {
  const all = { ...loadKept(), [videoId]: kept };
  const newest = Object.entries(all)
    .sort(([, a], [, b]) => b.fetchedAt - a.fetchedAt)
    .slice(0, MAX_KEPT);
  try {
    localStorage.setItem(KEY, JSON.stringify(Object.fromEntries(newest)));
  } catch {
    // A full or blocked storage only costs a refetch next time.
  }
}

const heatOf = (kept: Kept): BestPartHeat | null =>
  kept.segmentMs > 0 && kept.intensity.length ? new BestPartHeat(kept.segmentMs, Float64Array.from(kept.intensity)) : null;

/** A file or an addon's row has no YouTube video, and so no curve. */
const YOUTUBE_ID = /^[A-Za-z0-9_-]{11}$/;

/**
 * youtubei answers CORS preflights with 400, so this is a "simple" request: a
 * text/plain body, no custom headers, no cookies. The extension's rules
 * (web/extension/lib/rules.ts) dress it as www.youtube.com's own.
 */
async function fetchNext(videoId: string): Promise<string> {
  const response = await fetch('https://www.youtube.com/youtubei/v1/next?prettyPrint=false', {
    method: 'POST',
    headers: { 'Content-Type': 'text/plain;charset=UTF-8' },
    body: JSON.stringify({ videoId, context: { client: { clientName: 'WEB', clientVersion: WEB_CLIENT_VERSION } } }),
    credentials: 'omit',
    signal: AbortSignal.timeout(HEATMAP_TIMEOUT_MS),
  });
  if (!response.ok) throw new Error(`next ${response.status}`);
  return response.text();
}

const asking = new Map<string, Promise<BestPartHeat | null>>();

/** [videoId]'s "Most replayed" curve, kept or fetched; null when it has none or nobody answered. */
export function heatmapFor(videoId: string): Promise<BestPartHeat | null> {
  if (!YOUTUBE_ID.test(videoId)) return Promise.resolve(null);
  let pending = asking.get(videoId);
  if (!pending) {
    pending = askHeatmap(videoId).finally(() => asking.delete(videoId));
    asking.set(videoId, pending);
  }
  return pending;
}

async function askHeatmap(videoId: string): Promise<BestPartHeat | null> {
  const kept = loadKept()[videoId];
  const stale = kept ? heatOf(kept) : null;
  if (kept && Date.now() - kept.fetchedAt < (stale ? MONTH_MS : WEEK_MS)) return stale;
  let answer: string;
  try {
    answer = await fetchNext(videoId);
  } catch {
    return stale; // Stale beats nothing when the network won't answer.
  }
  const heat = bestPartHeatmap(answer) ?? null;
  saveKept(videoId, { segmentMs: heat?.segmentMs ?? 0, intensity: heat ? Array.from(heat.intensity) : [], fetchedAt: Date.now() });
  return heat;
}

/** The lines BestPart.kt reads, from synced lyrics only: plain lyrics have no chorus time to give. */
export function bestPartLines(lines: readonly LyricLine[] | null | undefined, synced: boolean): BestPartLine[] {
  return synced && lines ? lines.map((l) => new BestPartLine(l.timeMs, l.text)) : [];
}

/** BestPart.kt's pick, from a curve (or none) and lines. */
export function pickBestPart(heat: BestPartHeat | null, lines: BestPartLine[], durationMs: number): BestPartResult {
  const pick = bestPartFromHeat(heat, lines, durationMs);
  return { startMs: pick.startMs, source: pick.source };
}

/** Starts already worked out this visit, so asking again doesn't fetch twice. A fallback isn't kept. */
const starts = new Map<string, BestPartResult>();

/**
 * Where [videoId]'s best part starts (BestParts.startFor). The curve first;
 * then, given [track], its synced lyrics for the chorus (the player's own
 * sources and order), each on its own clock. Whatever hasn't answered in time
 * is done without, so this settles: at worst with the FALLBACK point. Null
 * only if the shared code itself failed.
 */
export async function bestPartFor(videoId: string, durationMs: number, track?: BestPartTrack): Promise<BestPartResult | null> {
  const known = starts.get(videoId);
  if (known) return known;
  try {
    const heat = await heatmapFor(videoId);
    // A song with no known length takes the curve's, which covers the whole video.
    const length = durationMs > 0 ? durationMs : heat ? heat.segmentMs * heat.intensity.length : 0;
    let lines: BestPartLine[] = [];
    if (track && length > 0) {
      const found = await Promise.race([
        lookupLyrics({ videoId, title: track.title, artist: track.artist, durationMs: length, album: track.album ?? null }).catch(() => null),
        new Promise<null>((resolve) => setTimeout(() => resolve(null), LYRICS_TIMEOUT_MS)),
      ]);
      lines = bestPartLines(found?.lines, !!found?.synced);
    }
    const result = pickBestPart(heat, lines, length);
    if (result.source !== 'FALLBACK') starts.set(videoId, result);
    return result;
  } catch {
    return null;
  }
}
