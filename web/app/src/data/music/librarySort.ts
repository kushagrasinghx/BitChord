import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { MusicItem } from './types';

/** The orders a library Show all grid can take (data/settings/LibrarySort.kt), as its picker labels them. */
export const LIBRARY_SORTS = [
  { value: 'DEFAULT', label: 'Default' },
  { value: 'TITLE_ASC', label: 'A–Z' },
  { value: 'TITLE_DESC', label: 'Z–A' },
] as const;

export type LibrarySort = (typeof LIBRARY_SORTS)[number]['value'];

/** One order for every grid, kept until changed, as the phone keeps AppSettings.librarySort. */
export const useLibrarySort = create<{ sort: LibrarySort; setSort: (sort: LibrarySort) => void }>()(
  persist((set) => ({ sort: 'DEFAULT', setSort: (sort) => set({ sort }) }), { name: 'bitchord:library-sort', version: 1 }),
);

/**
 * [items] in [sort]'s order (LibraryScreen.kt sortedForLibrary): a card's
 * title is all it carries, so anything but the default is alphabetical,
 * ignoring case.
 */
export function sortedForLibrary(items: MusicItem[], sort: LibrarySort): MusicItem[] {
  if (sort === 'DEFAULT') return items;
  const sign = sort === 'TITLE_ASC' ? 1 : -1;
  return [...items].sort((a, b) => sign * a.title.localeCompare(b.title, undefined, { sensitivity: 'base' }));
}
