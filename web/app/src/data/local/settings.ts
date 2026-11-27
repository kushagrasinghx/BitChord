import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/**
 * Local Music's settings, kept across visits: the "Filter non-music audio"
 * switch (AppSettings.filterNonMusicAudio, on by default as there), and the
 * chosen folder's name, which tells startup whether there is a library to
 * load at all without opening IndexedDB or the shared Kotlin.
 */
export const useLocalSettings = create<{
  filterNonMusic: boolean;
  folder: string | null;
  setFilterNonMusic: (on: boolean) => void;
  setFolder: (name: string | null) => void;
}>()(
  persist(
    (set) => ({
      filterNonMusic: true,
      folder: null,
      setFilterNonMusic: (filterNonMusic) => set({ filterNonMusic }),
      setFolder: (folder) => set({ folder }),
    }),
    { name: 'bitchord:local-music', version: 1 },
  ),
);

/**
 * Whether this browser can open a folder: the File System Access API's
 * directory picker, which Chromium has on the desktop (and WebView2, for the
 * Windows app) and Firefox, Safari and phones don't.
 */
export const localMusicSupported = (): boolean => typeof window !== 'undefined' && 'showDirectoryPicker' in window;
