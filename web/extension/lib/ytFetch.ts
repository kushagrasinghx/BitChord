import type { HttpResponse, YtFetchParams } from '@bitchord/shared/bridge';
import { installExtensionFetchRule } from './rules';
import { activeIdentity, signedFetch, SignedOutError } from './ytAuth';

/** Where an anonymous `yt.fetch` may go: BotGuard, which a page can't call with YouTube's Origin. */
const ALLOWED = [/^https:\/\/www\.youtube\.com\/api\/jnn\//];
/** Where a signed one may go: InnerTube only, never an account or settings page. */
const ALLOWED_SIGNED = [/^https:\/\/(music|www)\.youtube\.com\/youtubei\/v1\//];
/**
 * The play-reporting pings a player response nominates (playbackTracking):
 * playback, watchtime and atr, GET only. They're what puts a play in the
 * account's YouTube Music history (PlaybackTracker.kt on the phone).
 */
const STATS = /^https:\/\/(s|music|www)\.youtube\.com\/api\/stats\/(playback|watchtime|atr)(\?|$)/;
const MUSIC_ORIGIN = 'https://music.youtube.com';
/** Identity is the extension's to set; the page's copies of these are dropped. */
const IDENTITY_HEADERS = ['authorization', 'cookie', 'x-goog-authuser', 'x-goog-pageid', 'x-origin', 'origin', 'referer'];

let ruleReady: Promise<void> | null = null;

function toBytes(base64: string): Uint8Array {
  return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
}

function toBase64(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(binary);
}

function forbidden(url: string): Error {
  const { origin, pathname } = new URL(url);
  return Object.assign(new Error(`yt.fetch doesn't serve ${origin}${pathname}`), { code: 'forbidden' });
}

/**
 * A request to YouTube from the extension, with YouTube's Origin and Referer
 * (rules.ts, installExtensionFetchRule).
 *
 * - Anonymous by default: no cookies.
 * - With `auth`, made as the chosen channel (ytAuth.ts). The page gets the
 *   response, as it would from YouTube's own site, but never the cookies or
 *   the signature that produced it.
 */
export async function ytFetch(request: YtFetchParams): Promise<HttpResponse> {
  if (request.auth) return signedYtFetch(request);
  if (!ALLOWED.some((pattern) => pattern.test(request.url))) throw forbidden(request.url);
  ruleReady ??= installExtensionFetchRule().catch((error: unknown) => {
    ruleReady = null;
    throw error;
  });
  await ruleReady;

  const body =
    request.body === undefined ? undefined : request.bodyEncoding === 'base64' ? toBytes(request.body) : request.body;
  const response = await fetch(request.url, {
    method: request.method ?? 'GET',
    headers: request.headers ?? {},
    body: body as BodyInit | undefined,
    credentials: 'omit',
  });
  return serialise(response);
}

async function signedYtFetch(request: YtFetchParams): Promise<HttpResponse> {
  const stats = STATS.test(request.url) && (request.method ?? 'GET') === 'GET';
  if (!stats && !ALLOWED_SIGNED.some((pattern) => pattern.test(request.url))) throw forbidden(request.url);
  const identity = await activeIdentity();
  if (!identity) throw new SignedOutError('Not signed in to YouTube Music.');
  const headers: Record<string, string> = {};
  for (const [key, value] of Object.entries(request.headers ?? {})) {
    if (!IDENTITY_HEADERS.includes(key.toLowerCase())) headers[key] = value;
  }
  const body =
    request.body === undefined ? undefined : request.bodyEncoding === 'base64' ? toBytes(request.body) : request.body;
  const response = await signedFetch(
    request.url,
    { method: request.method ?? 'GET', headers, body: body as BodyInit | undefined },
    identity,
    // A stats ping is signed for YouTube Music, whichever host it goes to, as the web client's are.
    stats ? MUSIC_ORIGIN : undefined,
  );
  return serialise(response);
}

async function serialise(response: Response): Promise<HttpResponse> {
  const headers: Record<string, string> = {};
  response.headers.forEach((value, key) => (headers[key] = value));
  const type = response.headers.get('content-type') ?? '';
  const textual = /json|text|javascript|xml/.test(type);
  return {
    status: response.status,
    headers,
    url: response.url,
    bodyEncoding: textual ? 'text' : 'base64',
    body: textual ? await response.text() : toBase64(new Uint8Array(await response.arrayBuffer())),
  };
}
