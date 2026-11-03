import type { MusicItem, SearchResults } from '@/data/music/types';

/** Lower case, accents dropped, so "Bipul" finds "Bípul". */
export function normalize(text: string): string {
  return text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase();
}

/** True when every word of the query appears in the item's title or subtitle. */
export function matches(item: Pick<MusicItem, 'title' | 'subtitle'>, query: string): boolean {
  const words = normalize(query).split(/\s+/).filter(Boolean);
  if (!words.length) return false;
  const haystack = normalize(`${item.title} ${item.subtitle}`);
  return words.every((word) => haystack.includes(word));
}

/** Matching items from the user's own lists: title matches first, at most [limit], one per id. */
export function localMatches<T extends Pick<MusicItem, 'kind' | 'id' | 'title' | 'subtitle'>>(items: T[], query: string, limit = 5): T[] {
  const seen = new Set<string>();
  const found = items.filter((item) => {
    const key = `${item.kind}:${item.id}`;
    if (seen.has(key) || !matches(item, query)) return false;
    seen.add(key);
    return true;
  });
  const q = normalize(query.trim());
  const rank = (item: T) => (normalize(item.title).startsWith(q) ? 0 : normalize(item.title).includes(q) ? 1 : 2);
  return found.sort((a, b) => rank(a) - rank(b)).slice(0, limit);
}

/**
 * YouTube Music's results as one list, Spotify-style: the top results, then
 * playlists, songs, albums, artists and videos, each item once, leaving out
 * what [exclude] already shows.
 */
export function flattenResults(results: SearchResults, exclude: Set<string>, limit = 30): MusicItem[] {
  const out: MusicItem[] = [];
  const seen = new Set(exclude);
  for (const item of [...results.top, ...results.playlists, ...results.songs, ...results.albums, ...results.artists, ...results.videos]) {
    if (item.kind === 'unknown' || seen.has(item.id)) continue;
    seen.add(item.id);
    out.push(item);
    if (out.length >= limit) break;
  }
  return out;
}
