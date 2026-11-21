import type { SpotifyPlaylist } from '@bitchord/shared/bridge';

/** Lower case with accents dropped, so "Beyonce" finds "Beyoncé". */
function fold(text: string): string {
  return text.normalize('NFKD').replace(/\p{M}+/gu, '').toLowerCase().trim();
}

/**
 * Of [found], the playlists Spotify itself made about [artist], for the artist
 * page's On Spotify row (SpotifyLibrary.playlistsAbout on the phone): owned by
 * Spotify, named with the artist's whole name, ignoring case and accents, so
 * "Radio" doesn't find "This Is Radiohead". "This Is <artist>" leads, then
 * "<artist> Radio"; the rest keep the search's order. Each playlist once.
 */
export function playlistsAbout(artist: string, found: SpotifyPlaylist[]): SpotifyPlaylist[] {
  const name = fold(artist);
  if (!name) return [];
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const whole = new RegExp(`(^|[^\\p{L}\\p{N}])${escaped}($|[^\\p{L}\\p{N}])`, 'u');
  const seen = new Set<string>();
  const about = found.filter((p) => {
    if (seen.has(p.id)) return false;
    seen.add(p.id);
    return p.owner?.toLowerCase() === 'spotify' && whole.test(fold(p.name));
  });
  const rank = (p: SpotifyPlaylist) => {
    const title = fold(p.name);
    return title === `this is ${name}` ? 0 : title === `${name} radio` ? 1 : 2;
  };
  // Array.prototype.sort is stable, so equal ranks keep the search's order.
  return about.sort((a, b) => rank(a) - rank(b));
}
