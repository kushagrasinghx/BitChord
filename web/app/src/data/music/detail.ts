import type { MusicItem, Shelf, Track } from './types';

// What the album, playlist and artist pages work out from data they already
// have, as app/.../ui/screens/DetailScreen.kt does.

const fold = (text: string) => text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase();

/** Whether a row stands under the page's filter (Song.matchesSearch): its title, artists or album, ignoring case and accents. */
export function matchesSearch(track: Track, query: string): boolean {
  const needle = fold(query.trim());
  if (!needle) return true;
  return [track.title, track.album?.name ?? '', ...track.artists.map((a) => a.name)].some((field) => fold(field).includes(needle));
}

const RELEASE_YEAR = /\b(19|20)\d{2}\b/;

/**
 * The artist's newest release on their shelves (DetailScreen.kt topRelease):
 * only an MPRE… browse id is a release, so playlists, videos and related
 * artists sharing the shelves are skipped. Newest by the year in the card's
 * subtitle, which is all a shelf says about when; ties, and releases with no
 * year, keep YouTube's own order, which lists albums first.
 */
export function topRelease(shelves: Shelf[]): MusicItem | null {
  let best: MusicItem | null = null;
  let bestYear = -1;
  for (const item of shelves.flatMap((shelf) => shelf.items)) {
    if (!item.id.startsWith('MPRE')) continue;
    const year = Number(RELEASE_YEAR.exec(item.subtitle)?.[0] ?? 0);
    if (year > bestYear) [best, bestYear] = [item, year];
  }
  return best;
}

/** "Recent Single" and "Recent EP" where the card says so, otherwise "Recent Album" (recentLabel). */
export function recentLabel(item: MusicItem): string {
  const kind = item.subtitle.toLowerCase();
  return kind.includes('single') ? 'Recent Single' : /\bep\b/.test(kind) ? 'Recent EP' : 'Recent Album';
}
