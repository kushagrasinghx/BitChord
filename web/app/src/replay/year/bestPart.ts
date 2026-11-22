import { bestPart } from '@bitchord/shared-kotlin';

/**
 * Where a song's best part starts, in ms: what the year story starts each
 * clip from. A function type so the story doesn't care who answers it; the
 * player's own Best Part lookup (playback/bestPart.ts `bestPartFor`, once it
 * lands) can replace [heatmapBestPart] without the story changing.
 */
export type BestPartLookup = (videoId: string) => Promise<number>;

/** As the phone's BestParts.kt: a curve that hasn't come in 8 s is done without. */
const HEATMAP_TIMEOUT_MS = 8_000;
const YOUTUBE_ID = /^[A-Za-z0-9_-]{11}$/;

/** Starts already worked out this visit, so reopening the story doesn't ask twice. */
const known = new Map<string, number>();

/**
 * The Android app's own choice (BestPart.kt, through the shared Kotlin): the
 * bump in YouTube's "Most replayed" curve from www.youtube.com's `next`
 * answer, else a fixed point past the intro.
 *
 * shortcut: no synced-lyrics tier. The listening files keep no song length,
 * which a lyrics match needs and only the curve could supply; the player's
 * bestPartFor has both and is the intended replacement.
 */
export const heatmapBestPart: BestPartLookup = async (videoId) => {
  const cached = known.get(videoId);
  if (cached !== undefined) return cached;
  const next = YOUTUBE_ID.test(videoId) ? await fetchNext(videoId) : null;
  const pick = bestPart(next, [], 0);
  // A fallback is not remembered: the network may be back next time.
  if (pick.source !== 'FALLBACK') known.set(videoId, pick.startMs);
  return pick.startMs;
};

async function fetchNext(videoId: string): Promise<string | null> {
  const ask = (async () => {
    const { getInnertube } = await import('@/playback/youtube/innertube');
    const yt = await getInnertube();
    // The WEB client: www.youtube.com is where the heat markers are (Innertube.webNext on the phone).
    const response = await yt.actions.execute('/next', { videoId, client: 'WEB', parse: false });
    return JSON.stringify(response.data);
  })();
  const timeout = new Promise<null>((resolve) => setTimeout(() => resolve(null), HEATMAP_TIMEOUT_MS));
  return Promise.race([ask, timeout]).catch(() => null);
}
