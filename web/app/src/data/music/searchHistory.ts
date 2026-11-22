import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { MusicItem } from './types';

/** Deep enough to be useful, shallow enough that the list stays scannable (SearchHistory.kt MAX_ENTRIES). */
export const MAX_RECENT_SEARCHES = 20;

/**
 * What was found by searching lately, kept in this browser only, as the phone
 * keeps it on the device (app/.../data/settings/SearchHistory.kt): the song,
 * album, artist or playlist picked from the results, with its artwork, rather
 * than the words typed, so the list shows what was actually wanted. Newest
 * first; picking something already here moves it to the top.
 */
export const useSearchHistory = create<{
  items: MusicItem[];
  record: (item: MusicItem) => void;
  remove: (id: string) => void;
  clear: () => void;
}>()(
  persist(
    (set) => ({
      items: [],
      record: (item) => set((s) => ({ items: [item, ...s.items.filter((i) => i.id !== item.id)].slice(0, MAX_RECENT_SEARCHES) })),
      remove: (id) => set((s) => ({ items: s.items.filter((i) => i.id !== id) })),
      clear: () => set({ items: [] }),
    }),
    { name: 'bitchord:search-history', version: 1 },
  ),
);
