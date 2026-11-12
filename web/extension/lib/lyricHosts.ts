/**
 * Lyric providers the page can't reach itself (prompt §10.1): hosts that send
 * no CORS headers (KuGou, Musixmatch, Genius, Apple's catalogue search), and
 * a fallback for the ones that only allow some origins (BetterLyrics allows
 * localhost but not an arbitrary production domain).
 *
 * Each host is an optional permission, granted once by the user on the
 * extension's own page (grant.html): Chrome only shows a permission prompt
 * in answer to a click inside the extension. A request to a host not on this
 * list, or not granted, is refused.
 */
export const LYRIC_ORIGINS = [
  'https://lrc.red',
  'https://lyrics-api.binimum.org',
  'https://lyrics-api.boidu.dev',
  'https://unison.boidu.dev',
  'https://lyrics.paxsenix.org',
  'https://api.paxsenix.org',
  'https://lyricsplus.prjktla.my.id',
  'https://lyricsplus.atomix.one',
  'https://lyricsplus.binimum.org',
  'https://lyricsplus.prjktla.workers.dev',
  'https://lyricsplus-seven.vercel.app',
  'https://lyrics-plus-backend.vercel.app',
  'https://lrclib.net',
  'https://mobileservice.kugou.com',
  'https://lyrics.kugou.com',
  'https://apic.musixmatch.com',
  'https://www.musixmatch.com',
  'https://genius.com',
  'https://music.apple.com',
  'https://amp-api.music.apple.com',
] as const;

export const patternOf = (origin: string) => `${origin}/*`;

