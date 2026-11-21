import type { MusicItem } from './types';

export type LibrarySort = 'recent' | 'alphabetical' | 'creator';

export const LIBRARY_SORTS: readonly { value: LibrarySort; label: string }[] = [
  { value: 'recent', label: 'Recents' },
  { value: 'alphabetical', label: 'Alphabetical' },
  { value: 'creator', label: 'Creator' },
];

function fold(text: string): string {
  return text.normalize('NFKD').replace(/\p{M}/gu, '').toLowerCase();
}

/** Who made it: the credited artists, else YouTube's second line ("Playlist • Momo Snob" → "Momo Snob"). */
export function creatorOf(item: MusicItem): string {
  if (item.artists.length) return item.artists.map((a) => a.name).join(', ');
  const parts = item.subtitle.split(' • ').map((part) => part.trim());
  return parts.length > 1 ? parts.slice(1).join(' • ') : '';
}

/** Items whose title or creator holds every word of [query]. */
export function filterItems(items: readonly MusicItem[], query: string): MusicItem[] {
  const words = fold(query).split(/\s+/).filter(Boolean);
  if (!words.length) return [...items];
  return items.filter((item) => {
    const text = fold(`${item.title} ${creatorOf(item)}`);
    return words.every((word) => text.includes(word));
  });
}

const collator = new Intl.Collator(undefined, { sensitivity: 'base', numeric: true });

/** Recents is YouTube's own order; the others sort by name, ties in that order. */
export function sortItems(items: readonly MusicItem[], sort: LibrarySort): MusicItem[] {
  if (sort === 'recent') return [...items];
  const key = sort === 'alphabetical' ? (item: MusicItem) => item.title : creatorOf;
  return items
    .map((item, i) => ({ item, i }))
    .sort((a, b) => collator.compare(key(a.item), key(b.item)) || a.i - b.i)
    .map(({ item }) => item);
}

/** Pinned items first, in the order they were pinned; the rest as they were. */
export function pinnedFirst<T extends { id: string }>(items: readonly T[], pinned: readonly string[]): T[] {
  const byId = new Map(items.map((item) => [item.id, item]));
  const top = pinned.map((id) => byId.get(id)).filter((item): item is T => item !== undefined);
  const rest = items.filter((item) => !pinned.includes(item.id));
  return [...top, ...rest];
}
