import { fetchPlaylist } from '@/data/music/api';
import { LIKED_MUSIC_PLAYLIST } from '@/data/music/library';
import type { Track } from '@/data/music/types';
import { ensureYtAuth } from '@/extension/useYtAuth';
import { queryClient } from '@/lib/queryClient';
import { fetchTrackAudio } from '@/playback/automix/fetchAudio';
import { UnplayableError } from '@/playback/youtube/resolver';
import { trackLog } from '../trackLog';
import { useOfflineSettings, useOfflineStatus } from './settings';
import { offlineTracks, saveOffline } from './store';

/**
 * Keeps the account's Liked Music downloaded, as the phone does with
 * "Download liked songs" (PlaylistSync.kt): looked through every 12 hours,
 * a new like fetched 20 seconds after it lands, and only ever adding: un-liking
 * a song keeps its file, and only Remove Downloads takes them away.
 *
 * A browser can't run this in the background, so it runs while the app is open:
 * at start when the last full look is old enough, then on the same schedule.
 * The audio is the file the player streams, fetched whole by range the way
 * Automix fetches it (fetchTrackAudio). A track YouTube only serves over SABR
 * has no file to fetch, so it is skipped and counted.
 */

/** PlaylistSync.PERIOD_HOURS. */
const PERIOD_MS = 12 * 60 * 60_000;
/** PlaylistSync.LIKED_SETTLE_SECONDS: long enough for the like to reach the top of Liked Music. */
const LIKED_SETTLE_MS = 20_000;
/** Room kept free under the browser's quota, so a download never fills it. */
const HEADROOM_BYTES = 200 * 1024 * 1024;
const MAX_DURATION_SEC = 30 * 60;

async function likedTracks(newestOnly: boolean): Promise<Track[]> {
  const { page, more } = await fetchPlaylist(LIKED_MUSIC_PLAYLIST);
  const tracks = [...page.tracks];
  for (let next = newestOnly ? null : more; next; ) {
    const chunk = await next();
    tracks.push(...chunk.data);
    next = chunk.next;
  }
  return tracks.filter((t) => !t.unavailable);
}

/** A four-minute Opus file is ~5 MB; room is asked for a long one. */
export const ROOM_PER_TRACK = 15 * 1024 * 1024;

/** Whether another file of about [bytes] fits under the browser's quota with room to spare. */
export async function fits(bytes: number): Promise<boolean> {
  const { usage = 0, quota = Infinity } = (await navigator.storage?.estimate?.()) ?? {};
  return usage + bytes + HEADROOM_BYTES < quota;
}

/**
 * Fetches [track]'s whole file and keeps it. Shared by this sync and the song
 * menu's Download (downloads.ts), as the phone's Downloads serves both
 * PlaylistSync and SongActionsMenu's DownloadRow.
 */
export async function downloadTrack(track: Track, signal?: AbortSignal): Promise<void> {
  // shortcut: fetchTrackAudio stops at 40 MB (about 33 minutes of Opus), so longer tracks would be cut short; they are counted as failed until it can fetch a whole file of any length.
  if ((track.durationSec ?? 0) > MAX_DURATION_SEC) throw new Error('too long to download whole');
  const audio = await fetchTrackAudio(track.videoId, signal);
  await saveOffline(
    {
      videoId: track.videoId,
      title: track.title,
      author: track.artists.map((a) => a.name).join(', '),
      durationSec: track.durationSec ?? 0,
      thumbnail: track.thumbnail,
    },
    new Uint8Array(audio.bytes),
  );
}

let running: Promise<void> | null = null;

/**
 * Downloads every liked song not here yet. [newestOnly] is the quick look
 * after a like: the first page of Liked Music, where a new like lands.
 */
export function checkLiked(newestOnly = false): Promise<void> {
  running ??= run(newestOnly).finally(() => {
    running = null;
  });
  return running;
}

async function run(newestOnly: boolean): Promise<void> {
  if (!useOfflineSettings.getState().keepLiked) return;
  const auth = await ensureYtAuth().catch(() => null);
  if (!auth?.signedIn) return;
  const status = useOfflineStatus.setState;
  status({ running: true, done: 0, total: 0, skippedSabr: 0, failed: 0, error: null });
  let whole = true;
  try {
    const have = new Set((await offlineTracks()).map((t) => t.videoId));
    const missing = (await likedTracks(newestOnly)).filter((t) => !have.has(t.videoId));
    status({ total: missing.length });
    for (const track of missing) {
      // Stopped from Settings, or Remove Downloads pressed, part-way through.
      if (!useOfflineSettings.getState().keepLiked) return;
      if (!(await fits(ROOM_PER_TRACK))) {
        status({ error: 'Not enough storage for more downloads' });
        whole = false;
        break;
      }
      try {
        await downloadTrack(track);
      } catch (error) {
        whole = false;
        if (/SABR only/.test(String(error))) status((s) => ({ skippedSabr: s.skippedSabr + 1 }));
        else {
          status((s) => ({ failed: s.failed + 1 }));
          if (!(error instanceof UnplayableError)) trackLog.w(`download failed: ${String(error)}`, track.videoId);
        }
      }
      status((s) => ({ done: s.done + 1 }));
    }
    if (whole && !newestOnly) useOfflineSettings.getState().setLastCheck(Date.now());
  } catch (error) {
    status({ error: error instanceof Error ? error.message : String(error) });
  } finally {
    status({ running: false });
  }
}

let started = false;

/**
 * Starts the schedule: a full look soon if the last is over 12 hours old, then
 * hourly checks of the same, and a quick look 20 seconds after each new like.
 */
export function startLikedSync(): void {
  if (started) return;
  started = true;
  const due = () => Date.now() - useOfflineSettings.getState().lastCheck >= PERIOD_MS;
  // Not in the first seconds, which belong to the page and the first track.
  setTimeout(() => due() && void checkLiked(), 30_000);
  setInterval(() => {
    if (due()) void checkLiked();
  }, 60 * 60_000);

  // A like from anywhere (the star, the track menu) writes 'LIKE' into the
  // like query by hand (useSetLiked); a status merely read from YouTube
  // arrives as a fetch, which isn't a new like.
  let soon: ReturnType<typeof setTimeout> | undefined;
  queryClient.getQueryCache().subscribe((event) => {
    if (event.type !== 'updated' || event.action.type !== 'success' || !event.action.manual) return;
    const [scope, kind] = event.query.queryKey as unknown[];
    if (scope !== 'music' || kind !== 'like' || event.action.data !== 'LIKE') return;
    if (!useOfflineSettings.getState().keepLiked) return;
    clearTimeout(soon);
    soon = setTimeout(() => void checkLiked(true), LIKED_SETTLE_MS);
  });
}
