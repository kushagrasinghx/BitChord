import { lyricsGet } from '@/lyrics/http';

let appleToken: Promise<string | null> | null = null;

/**
 * Apple's web player token, scraped from its own script (through the
 * extension: no CORS there). Shared by the Apple lyrics lookup and the artist
 * page's Apple art.
 */
export function appleWebToken(): Promise<string | null> {
  appleToken ??= (async () => {
    const page = await lyricsGet('https://music.apple.com/us/new', { route: 'extension' });
    const script = page?.match(/\/assets\/index~[^"]+\.js/)?.[0];
    if (!script) return null;
    const source = await lyricsGet(`https://music.apple.com${script}`, { route: 'extension' });
    return source?.match(/eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/)?.[0] ?? null;
  })().then((token) => {
    if (!token) appleToken = null;
    return token;
  });
  return appleToken;
}
