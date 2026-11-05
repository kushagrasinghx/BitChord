/** Anonymous requests accepted by the extension's YouTube bridge. */
export function allowsAnonymousYtRequest(url: string): boolean {
  try {
    const parsed = new URL(url);
    if (parsed.protocol !== 'https:' || parsed.port || parsed.username || parsed.password) return false;
    if (!['www.youtube.com', 'music.youtube.com'].includes(parsed.hostname)) return false;
    return /^\/(iframe_api|sw\.js_data)$/.test(parsed.pathname) ||
      /^\/(s\/player\/|youtubei\/v1\/|api\/jnn\/)/.test(parsed.pathname);
  } catch {
    return false;
  }
}
