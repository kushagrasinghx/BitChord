/** Lower case with accents and marks dropped, so "Beyoncé" is found by "beyonce". */
const folded = (text: string) => text.normalize('NFKD').replace(/\p{M}+/gu, '').toLowerCase();

/**
 * Find a playlist (PlaylistFilter.kt, ported: it leans on java.text.Normalizer,
 * so it can't run from commonMain; playlistFilter.test.ts holds its test's
 * cases): the playlists whose name holds every word of [query], in any order,
 * ignoring case and accents. All of them for a blank query, in the order given.
 */
export function filterPlaylists<T>(playlists: T[], query: string, name: (playlist: T) => string): T[] {
  const words = folded(query).split(/\s+/).filter(Boolean);
  if (!words.length) return playlists;
  return playlists.filter((playlist) => {
    const title = folded(name(playlist));
    return words.every((word) => title.includes(word));
  });
}
