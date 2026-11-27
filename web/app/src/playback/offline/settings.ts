import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/**
 * Settings › Keep Liked Songs Downloaded (AppSettings.downloadLikedSongs), and
 * when Liked Music was last gone through in full. Its own small module: the
 * app reads it on start, and only loads the downloader when it is on.
 */
export const useOfflineSettings = create<{
  keepLiked: boolean;
  lastCheck: number;
  setKeepLiked: (on: boolean) => void;
  setLastCheck: (at: number) => void;
}>()(
  persist(
    (set) => ({
      keepLiked: false,
      lastCheck: 0,
      setKeepLiked: (keepLiked) => set({ keepLiked }),
      setLastCheck: (lastCheck) => set({ lastCheck }),
    }),
    { name: 'bitchord:offline', version: 1 },
  ),
);

/** How the downloader (likedSync.ts) is getting on, for Settings. */
export const useOfflineStatus = create<{
  running: boolean;
  done: number;
  total: number;
  /** In the last look: tracks only served over SABR, and tracks that failed. */
  skippedSabr: number;
  failed: number;
  error: string | null;
}>()(() => ({ running: false, done: 0, total: 0, skippedSabr: 0, failed: 0, error: null }));
