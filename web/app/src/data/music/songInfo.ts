import { getInnertube } from '@/playback/youtube/innertube';
import { collect, runsText } from './api';

/** Song info's figures (SongInfoRepository.kt SongInfo); each null when YouTube didn't give it. */
export interface SongStats {
  releaseDate: string | null;
  views: string | null;
  likes: string | null;
  comments: string | null;
}

/**
 * YouTube's short form of a count, as SongInfoRepository.compactCount writes
 * it: 950, 1.2K, 100K, 2.3M. Truncated rather than rounded, so 999999 never
 * shows as 1000K. Anything that isn't a plain number comes back as it was.
 */
export function compactCount(raw: string): string {
  if (!/^\d+$/.test(raw)) return raw;
  const n = Number(raw);
  const [unit, suffix] = n >= 1e9 ? [1e9, 'B'] : n >= 1e6 ? [1e6, 'M'] : n >= 1e3 ? [1e3, 'K'] : [1, ''];
  if (unit === 1) return String(n);
  const whole = Math.floor(n / unit);
  const tenth = Math.floor(((n % unit) * 10) / unit);
  return whole >= 10 || tenth === 0 ? `${whole}${suffix}` : `${whole}.${tenth}${suffix}`;
}

// A count is only a count if it has a digit in it ("Like" alone is the button's label).
const withCount = (text: string) => (/\d/.test(text) ? text : null);

/**
 * The figures in a watch page's `next` response (web client), read the way
 * SongInfoParser reads the watch page: only the video's own primary
 * renderer, since recommendations carry counts too; the release date from
 * the "Released on:" line auto-generated descriptions carry.
 */
export function parseSongStats(next: unknown): SongStats {
  const primary = collect(next, 'videoPrimaryInfoRenderer')[0];
  const views = collect(primary, 'videoViewCountRenderer')[0]?.originalViewCount;
  const like = collect(collect(primary, 'likeButtonViewModel')[0], 'buttonViewModel')[0]?.title;
  const secondary = collect(next, 'videoSecondaryInfoRenderer')[0];
  const description =
    (collect(secondary, 'attributedDescription')[0]?.content as string | undefined) ?? runsText(secondary?.description);
  const released = description.match(/^\s*Released on:\s*(\d{4}-\d{2}-\d{2})\s*$/im)?.[1] ?? null;
  const commentPanel = collect(next, 'engagementPanelSectionListRenderer').find(
    (panel) => panel.targetId === 'engagement-panel-comments-section' || panel.panelIdentifier === 'engagement-panel-comments-section',
  );
  const comments =
    withCount(runsText(collect(next, 'commentsEntryPointHeaderRenderer')[0]?.commentCount)) ??
    withCount(runsText(collect(commentPanel, 'engagementPanelTitleHeaderRenderer')[0]?.contextualInfo));
  return {
    releaseDate: released,
    views: typeof views === 'string' && /^\d+$/.test(views) ? compactCount(views) : null,
    likes: typeof like === 'string' ? withCount(like) : null,
    comments,
  };
}

/** The figures for exactly this upload, never a search match's (SongInfoRepository.load). */
export async function fetchSongStats(videoId: string): Promise<SongStats> {
  const yt = await getInnertube();
  const next = await yt.actions.execute('/next', { videoId });
  return parseSongStats(next.data);
}
