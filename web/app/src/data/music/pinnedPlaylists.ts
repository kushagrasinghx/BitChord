import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { MusicItem } from './types';

/** How many playlists can be pinned at once (AppSettings.MAX_PINNED_PLAYLISTS). */
export const MAX_PINNED_PLAYLISTS = 5;

// A playlist arrives as VLPL… from a card and PL… from some links, so pins are kept and matched without the VL.
const keyOf = (id: string) => id.replace(/^VL/, '');

interface PinnedPlaylists {
  /** Playlist ids, VL-less, in the order they were pinned. */
  ids: string[];
  /**
   * Pins or unpins [id], returning whether it is pinned afterwards
   * (AppSettings.togglePinnedPlaylist). Pinning past the cap is refused
   * rather than evicting the oldest pin, which would drop a playlist someone
   * pinned on purpose without their touching it.
   */
  toggle: (id: string) => boolean;
}

/** Playlists pinned to the front of the Library's Playlists shelf, kept as the phone keeps AppSettings.pinnedPlaylists. */
export const usePinnedPlaylists = create<PinnedPlaylists>()(
  persist(
    (set, get) => ({
      ids: [],
      toggle: (id) => {
        const key = keyOf(id);
        const current = get().ids;
        if (current.includes(key)) {
          set({ ids: current.filter((k) => k !== key) });
          return false;
        }
        if (current.length >= MAX_PINNED_PLAYLISTS) return false;
        set({ ids: [...current, key] });
        return true;
      },
    }),
    { name: 'bitchord:pinned-playlists', version: 1, partialize: ({ ids }) => ({ ids }) },
  ),
);

export const isPinned = (ids: readonly string[], id: string) => ids.includes(keyOf(id));

/**
 * [items] with the pinned playlists moved to the front in pin order, the rest
 * in their existing order behind them (LibraryScreen.kt pinnedFirst). Only
 * playlists match, so any other shelf passes through untouched.
 */
export function pinnedFirst(items: MusicItem[], ids: readonly string[]): MusicItem[] {
  if (!ids.length) return items;
  const playlists = items.filter((item) => item.kind === 'playlist');
  const pinned = ids.flatMap((key) => playlists.find((item) => keyOf(item.id) === key) ?? []);
  if (!pinned.length) return items;
  return [...pinned, ...items.filter((item) => !pinned.includes(item))];
}
