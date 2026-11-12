import type { HttpRequest, HttpResponse } from '@bitchord/shared/bridge';

import { LYRIC_ORIGINS, patternOf } from './lyricHosts';

export { LYRIC_ORIGINS, patternOf };

export class LyricsPermissionError extends Error {
  readonly code = 'permission';
}

function toBase64(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(binary);
}

/** Headers some hosts need that a fetch may not set, applied to the extension's own requests (tabId -1). */
const RULES = [
  {
    id: 200,
    urlFilter: '||amp-api.music.apple.com/',
    headers: [
      { header: 'Origin', operation: 'set', value: 'https://music.apple.com' },
      { header: 'Referer', operation: 'set', value: 'https://music.apple.com/' },
    ],
  },
  {
    // Musixmatch's web client and the signing key it carries (Musixmatch.kt's browserGet).
    id: 201,
    urlFilter: '||musixmatch.com/',
    headers: [{ header: 'Cookie', operation: 'set', value: 'mxm_bab=AB' }],
  },
] as const;

let rulesReady: Promise<void> | null = null;

function installRules(): Promise<void> {
  rulesReady ??= browser.declarativeNetRequest
    .updateSessionRules({
      removeRuleIds: RULES.map((r) => r.id),
      addRules: RULES.map((r) => ({
        id: r.id,
        priority: 1,
        condition: { urlFilter: r.urlFilter, tabIds: [-1], resourceTypes: ['xmlhttprequest'] },
        action: { type: 'modifyHeaders', requestHeaders: r.headers.map((h) => ({ ...h })) },
      })) as Browser.declarativeNetRequest.Rule[],
    })
    .catch((error: unknown) => {
      rulesReady = null;
      throw error;
    });
  return rulesReady;
}

/** Which of [origins] the user has granted. */
export async function grantedOrigins(origins: string[]): Promise<string[]> {
  const known = origins.filter((o) => (LYRIC_ORIGINS as readonly string[]).includes(o));
  const answers = await Promise.all(known.map((o) => browser.permissions.contains({ origins: [patternOf(o)] })));
  return known.filter((_, i) => answers[i]);
}

/** Opens the extension's page that asks for [origins]; the browser only prompts after a click there. */
export async function requestOrigins(origins: string[]): Promise<void> {
  const known = origins.filter((o) => (LYRIC_ORIGINS as readonly string[]).includes(o));
  if (!known.length) return;
  const url = browser.runtime.getURL(`/grant.html?origins=${encodeURIComponent(known.join(','))}`);
  await browser.tabs.create({ url });
}

export async function lyricsFetch(request: HttpRequest): Promise<HttpResponse> {
  const url = new URL(request.url);
  if (!(LYRIC_ORIGINS as readonly string[]).includes(url.origin)) {
    throw Object.assign(new Error(`lyrics.fetch doesn't serve ${url.origin}`), { code: 'forbidden' });
  }
  if (!(await browser.permissions.contains({ origins: [patternOf(url.origin)] }))) {
    throw new LyricsPermissionError(`BitChord needs permission to reach ${url.host} for lyrics.`);
  }
  await installRules();
  const response = await fetch(url, {
    method: request.method ?? 'GET',
    headers: request.headers ?? {},
    ...(request.body !== undefined && { body: request.body }),
    credentials: 'omit',
    signal: AbortSignal.timeout(15_000),
  });
  const headers: Record<string, string> = {};
  response.headers.forEach((value, key) => (headers[key] = value));
  const type = response.headers.get('content-type') ?? '';
  const textual = /json|text|javascript|xml|html/.test(type) || !type;
  return {
    status: response.status,
    headers,
    url: response.url,
    bodyEncoding: textual ? 'text' : 'base64',
    body: textual ? await response.text() : toBase64(new Uint8Array(await response.arrayBuffer())),
  };
}
