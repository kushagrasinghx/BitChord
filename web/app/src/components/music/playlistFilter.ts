/** Playlists whose title holds every word of [query], in the order YouTube lists them. */
export function filterPlaylists<T extends { title: string }>(options: readonly T[], query: string): T[] {
  const words = query.toLowerCase().split(/\s+/).filter(Boolean);
  return options.filter((option) => {
    const title = option.title.toLowerCase();
    return words.every((word) => title.includes(word));
  });
}
