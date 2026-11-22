import { create } from 'zustand';
import type { Track } from '@/data/music/types';
import { queryClient } from '@/lib/queryClient';
import { removeOffline } from './store';

/**
 * One song downloaded or removed from its menu (SongActionsMenu.kt DownloadRow,
 * download/Downloads.kt). The files go in the same store as Keep Liked Songs
 * Downloaded, so they play offline and list in Library › Downloads the same way.
 * Only the running and failed states live here; "saved" is the store itself.
 * The downloader is loaded on first use, as Settings loads it.
 */
export type DownloadState = { kind: 'running' } | { kind: 'failed'; reason: string };

export const useDownloads = create<{ active: Record<string, DownloadState> }>()(() => ({ active: {} }));

const controllers = new Map<string, AbortController>();

function setState(videoId: string, state: DownloadState | null) {
  useDownloads.setState(({ active }) => {
    const { [videoId]: _, ...rest } = active;
    return { active: state ? { ...rest, [videoId]: state } : rest };
  });
}

/** Library › Downloads and Settings' count read the store through these queries. */
const refresh = () => queryClient.invalidateQueries({ queryKey: ['offline'] });

export async function downloadSong(track: Track): Promise<void> {
  const { videoId } = track;
  if (controllers.has(videoId)) return;
  const controller = new AbortController();
  controllers.set(videoId, controller);
  setState(videoId, { kind: 'running' });
  try {
    const { downloadTrack, fits, ROOM_PER_TRACK } = await import('./likedSync');
    if (!(await fits(ROOM_PER_TRACK))) throw new Error('Not enough storage for more downloads');
    await downloadTrack(track, controller.signal);
    setState(videoId, null);
    void refresh();
  } catch (error) {
    // Cancelled is back to "Download", not a failure (Downloads.cancel).
    setState(videoId, controller.signal.aborted ? null : { kind: 'failed', reason: error instanceof Error ? error.message : String(error) });
  } finally {
    controllers.delete(videoId);
  }
}

export function cancelDownload(videoId: string): void {
  controllers.get(videoId)?.abort();
}

/** Downloads.dismissFailure: a failure is said once, not again the next time the menu opens. */
export function dismissFailure(videoId: string): void {
  if (useDownloads.getState().active[videoId]?.kind === 'failed') setState(videoId, null);
}

export async function removeDownload(videoId: string): Promise<void> {
  await removeOffline(videoId);
  await refresh();
}
