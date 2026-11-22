/**
 * Where a song's best part starts, in ms: what the year story starts each
 * clip from. A function type so the story doesn't care who answers it.
 */
export type BestPartLookup = (videoId: string) => Promise<number>;

const YOUTUBE_ID = /^[A-Za-z0-9_-]{11}$/;

/** Past the intro, as BestPart.fallbackMs: where a clip starts when nothing better is known. */
const FALLBACK_MS = 45_000;

/**
 * The player's own Best Part lookup (playback/bestPart.ts): the same fetch, the
 * same 30-day curve cache and the same Kotlin pick (BestPart.kt) as the Best Part
 * button. The listening files keep no song length, so it takes the curve's.
 *
 * shortcut: no synced-lyrics tier; the year's clips carry only a video id. Pass the
 * clip's title and artist through to bestPartFor once the plan exposes them.
 */
export const heatmapBestPart: BestPartLookup = async (videoId) => {
  if (!YOUTUBE_ID.test(videoId)) return FALLBACK_MS;
  const { bestPartFor } = await import('@/playback/bestPart');
  return (await bestPartFor(videoId, 0))?.startMs ?? FALLBACK_MS;
};
