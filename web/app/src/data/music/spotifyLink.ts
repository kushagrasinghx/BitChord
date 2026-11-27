/**
 * A Spotify playlist's id from what was pasted: its share link
 * (open.spotify.com/playlist/…), its URI (spotify:playlist:…) or the bare
 * 22-character id. Null for anything else, an album's link included.
 */
export function spotifyPlaylistId(text: string): string | null {
  const match = /^([A-Za-z0-9]{22})$|playlist[/:]([A-Za-z0-9]{22})(?![A-Za-z0-9])/.exec(text.trim());
  return match?.[1] ?? match?.[2] ?? null;
}
