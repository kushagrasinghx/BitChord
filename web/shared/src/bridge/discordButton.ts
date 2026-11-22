/** Discord drops a whole activity whose button label isn't 1 to 32 characters (DiscordRPC.BUTTON_LABEL_MAX). */
export const DISCORD_BUTTON_LABEL_MAX = 32;

/**
 * A button's label with {song_name}, {artist_name} and {album_name} filled in,
 * kept to what Discord accepts: cut to fit with an ellipsis, and [fallback]
 * when it comes out empty (an {album_name} for a single), so a long title
 * never takes the whole card down (#1258).
 */
export function discordButtonLabel(
  text: string,
  fallback: string,
  track: { title: string; artist: string; album?: string | null },
): string {
  const label = (text || fallback)
    .replaceAll('{song_name}', track.title)
    .replaceAll('{artist_name}', track.artist)
    .replaceAll('{album_name}', track.album ?? '')
    .trim();
  if (!label) return fallback;
  const chars = [...label]; // by code point, so an emoji is never cut in half
  return chars.length <= DISCORD_BUTTON_LABEL_MAX ? label : `${chars.slice(0, DISCORD_BUTTON_LABEL_MAX - 1).join('').trimEnd()}…`;
}
