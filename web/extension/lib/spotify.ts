import type { SpotifyAuthState, SpotifyPage, SpotifyPlaylist, SpotifyTrack } from '@bitchord/shared/bridge';
import { installSpotifyFetchRule } from './rules';

/**
 * Spotify (prompt §7.1), with the user's own open.spotify.com session: the
 * `sp_dc` cookie stays in the browser's jar and the tokens minted from it stay
 * here. The page only gets playlists, tracks and canvas URLs.
 *
 * Ports, with what changed:
 * - Tokens: DesktopSpotifyToken (desktop): the web player's `/api/token` with
 *   `sp_dc` sent as a header,
 *   signed with the TOTP it derives from a secret in its own bundle. The
 *   desktop keeps the secret in a table, which had fallen behind (v12; the
 *   live one was v61 on 2026-10-16), so this reads the current secrets the way
 *   SpotifyCanvasQuery.QueryHashes reads query hashes: out of the web player's
 *   scripts, with the table as the fallback. Android mints in a WebView
 *   because a forged token was refused downstream there; from a real browser
 *   the self-minted token reads the library and canvases (measured 2026-10-16).
 * - Queries: SpotifyLibrary.kt (library, playlist, liked songs, cover) and
 *   SpotifyCanvasQuery.kt (canvas). Their hashes are read live as well, with
 *   the Kotlin constants as fallbacks, and looked up again once on
 *   "PersistedQueryNotFound".
 * - Search: api.spotify.com/v1/search (what DesktopSpotifyCanvas used) answers
 *   the web player's token with 429, signed in or not; this asks the web
 *   player's own `searchTracks` query instead.
 */

const WEB_PLAYER = 'https://open.spotify.com';
const CDN = 'https://open.spotifycdn.com/cdn/build/web-player/';
const PATHFINDER = 'https://api-partner.spotify.com/pathfinder/v2/query';
const CLIENT_TOKEN = 'https://clienttoken.spotify.com/v1/clienttoken';

type Operation = 'libraryV3' | 'fetchPlaylist' | 'fetchLibraryTracks' | 'canvas' | 'searchTracks' | 'profileAttributes';

/** The last hashes known to work (SpotifyLibrary.kt, SpotifyCanvasQuery.kt, and the bundle on 2026-10-16). */
const KNOWN_HASHES: Record<Operation, string> = {
  libraryV3: '390c78e5b951029bad359785e69b07b536a509c581cbcd0aded5e5067f187455',
  fetchPlaylist: '21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf',
  fetchLibraryTracks: '087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240',
  canvas: '575138ab27cd5c1b3e54da54d0a7cc8d85485402de26340c2145f0f6bb5e7a9f',
  searchTracks: 'b02683192a98dde7966b5e6655a79eeb62713eab703eda9902c932818dd52751',
  profileAttributes: '08ffb4730af3746e04a8301396f20875dbbce10c75243803091a9274eacc8ac0',
};
const OPERATIONS = Object.keys(KNOWN_HASHES) as Operation[];

/** DesktopSpotifyToken.TOTP_SECRETS: the fallback when the bundle can't be read. */
const KNOWN_SECRET = { version: 12, codes: [107, 81, 49, 57, 67, 93, 87, 81, 69, 67, 40, 93, 48, 50, 46, 91, 94, 113, 41, 47] };

/** Liked Songs has no playlist uri; SpotifyLibrary.LIKED_ID. */
export const LIKED_ID = 'liked';
const LIKED_COVER = 'https://misc.scdn.co/liked-songs/liked-songs-640.png';

const FOUND_TTL_MS = 12 * 60 * 60_000;
const MISSED_TTL_MS = 30 * 60_000;

export class SpotifyAuthError extends Error {
  readonly code = 'unauthenticated';
}

// ---- The web player's own values ---------------------------------------------

interface WebPlayer {
  clientVersion: string;
  /** Character codes of the newest secret, before the XOR the bundle applies. */
  secret: { version: number; codes: number[] };
  hashes: Record<Operation, string>;
  /** Whether everything came from the live bundle; a partial read is retried sooner. */
  complete: boolean;
  at: number;
}

const WEB_PLAYER_KEY = 'spotify.webPlayer';
let webPlayerCache: WebPlayer | null = null;

/**
 * A JavaScript string literal's body, unescaped. The bundle writes its secrets
 * as literals; MV3 forbids `eval`, so this reads the escapes itself.
 */
export function unescapeJs(body: string): string {
  return body.replace(/\\(u\{[0-9a-fA-F]+\}|u[0-9a-fA-F]{4}|x[0-9a-fA-F]{2}|[\s\S])/g, (_, esc: string) => {
    if (esc[0] === 'u') return String.fromCodePoint(parseInt(esc.replace(/[u{}]/g, ''), 16));
    if (esc[0] === 'x') return String.fromCharCode(parseInt(esc.slice(1), 16));
    return ({ n: '\n', r: '\r', t: '\t', b: '\b', f: '\f', v: '\v', 0: '\0' } as Record<string, string>)[esc] ?? esc;
  });
}

/** `{secret:'…',version:61}` as the bundle declares them, newest first. */
export function secretsIn(script: string): { version: number; codes: number[] }[] {
  return [...script.matchAll(/\{secret:(['"])((?:\\.|(?!\1).)*)\1,version:(\d+)\}/g)]
    .map((m) => ({ version: Number(m[3]), codes: [...unescapeJs(m[2]!)].map((c) => c.charCodeAt(0)) }))
    .sort((a, b) => b.version - a.version);
}

/** `"canvas","query","<64 hex>"`: SpotifyCanvasQuery.findQueryHash. */
export function queryHashIn(script: string, operation: string): string | null {
  return script.match(new RegExp(`["']${operation}["']\\s*,\\s*["']query["']\\s*,\\s*["']([0-9a-f]{64})["']`))?.[1] ?? null;
}

async function text(url: string): Promise<string | null> {
  try {
    const response = await fetch(url, { credentials: 'omit' });
    return response.ok ? await response.text() : null;
  } catch {
    return null;
  }
}

async function readWebPlayer(): Promise<WebPlayer> {
  const html = (await text(`${WEB_PLAYER}/`)) ?? '';
  let clientVersion = '';
  const config = html.match(/<script id="appServerConfig" type="text\/plain">([^<]+)<\/script>/)?.[1];
  if (config) {
    try {
      clientVersion = (JSON.parse(atob(config)) as { clientVersion?: string }).clientVersion ?? '';
    } catch {
      /* stays empty */
    }
  }
  const bundleUrl = html.match(/https:\/\/open\.spotifycdn\.com\/cdn\/build\/web-player\/web-player\.[0-9a-f]+\.js/)?.[0];
  const bundle = bundleUrl ? ((await text(bundleUrl)) ?? '') : '';
  // searchTracks is declared in the search route's chunk, which the player
  // loads on demand; its file name comes from the bundle's chunk table.
  let searchChunk = '';
  const searchId = bundle.match(/(\d+):"xpui-routes-search"/)?.[1];
  if (searchId) {
    for (const [, part] of bundle.matchAll(new RegExp(`\\b${searchId}:"([0-9a-f]{8})"`, 'g'))) {
      searchChunk = (await text(`${CDN}xpui-routes-search.${part}.js`)) ?? '';
      if (searchChunk) break;
    }
  }
  const secret = secretsIn(bundle)[0];
  const found = Object.fromEntries(OPERATIONS.map((op) => [op, queryHashIn(bundle, op) ?? queryHashIn(searchChunk, op)]));
  const complete = !!secret && !!clientVersion && OPERATIONS.every((op) => found[op]);
  if (!complete) console.warn('BitChord: Spotify web player read partially', { clientVersion: !!clientVersion, secret: secret?.version, found });
  return {
    clientVersion: clientVersion || '1.2.77.0',
    secret: secret ?? KNOWN_SECRET,
    hashes: Object.fromEntries(OPERATIONS.map((op) => [op, found[op] ?? KNOWN_HASHES[op]])) as Record<Operation, string>,
    complete,
    at: Date.now(),
  };
}

/** Kept in storage, not just memory: the bundle is 4 MB, and the worker restarts often. */
async function webPlayer(force = false): Promise<WebPlayer> {
  if (!force) {
    webPlayerCache ??= ((await browser.storage.local.get(WEB_PLAYER_KEY))[WEB_PLAYER_KEY] as WebPlayer | undefined) ?? null;
    const cached = webPlayerCache;
    if (cached && Date.now() - cached.at < (cached.complete ? FOUND_TTL_MS : MISSED_TTL_MS)) return cached;
  }
  webPlayerCache = await readWebPlayer();
  await browser.storage.local.set({ [WEB_PLAYER_KEY]: webPlayerCache });
  return webPlayerCache;
}

// ---- TOTP (DesktopSpotifyToken.totp) -----------------------------------------------

/** Six digits over SHA-1, thirty-second steps, from the secret's codes XORed with their index. */
export async function totp(codes: number[], epochMs: number): Promise<string> {
  const key = new TextEncoder().encode(codes.map((code, i) => code ^ ((i % 33) + 9)).join(''));
  const counter = new ArrayBuffer(8);
  new DataView(counter).setBigUint64(0, BigInt(Math.floor(epochMs / 1000 / 30)));
  const hmac = await crypto.subtle.importKey('raw', key, { name: 'HMAC', hash: 'SHA-1' }, false, ['sign']);
  const hash = new Uint8Array(await crypto.subtle.sign('HMAC', hmac, counter));
  const offset = hash[hash.length - 1]! & 0x0f;
  const binary =
    ((hash[offset]! & 0x7f) << 24) | (hash[offset + 1]! << 16) | (hash[offset + 2]! << 8) | hash[offset + 3]!;
  return String(binary % 1_000_000).padStart(6, '0');
}

// ---- Tokens ------------------------------------------------------------------------

interface AccessToken {
  token: string;
  expiresAt: number;
  clientId: string | null;
  /** The `sp_dc` it was minted from: another one means another sign-in. */
  cookie: string;
}

let spotifyRules: Promise<void> | null = null;
let access: AccessToken | null = null;
let client: { token: string; expiresAt: number } | null = null;
/** After a failed mint, how long before asking again (DesktopSpotifyToken.RETRY_MS, shortened: the user is waiting). */
let retryAfter = 0;
const RETRY_MS = 60_000;

function invalidateTokens() {
  access = null;
  client = null;
}

/**
 * The web player's bearer for the signed-in user, or a `SpotifyAuthError`.
 * Anonymous tokens are refused: they can't read a library or a canvas.
 * Concurrent callers share one mint.
 */
let minting: Promise<AccessToken> | null = null;
async function accessToken(): Promise<AccessToken> {
  // Checked on every use rather than left to cookies.onChanged, which didn't
  // fire in testing: a token from another sign-in must never be used.
  const cookie = await browser.cookies.get({ url: `${WEB_PLAYER}/`, name: 'sp_dc' }).catch(() => null);
  if (access && access.cookie !== cookie?.value) invalidateSession();
  if (access && Date.now() < access.expiresAt - 30_000) return access;
  minting ??= mint().finally(() => (minting = null));
  return minting;
}

/**
 * `credentials: 'include'` from the worker did not carry `sp_dc` in testing
 * (Chromium sent the request without it under every SameSite setting), so the
 * cookie goes as an explicit header, as DesktopSpotifyToken sends it: read
 * through the cookies API and set by a session rule that matches only this
 * extension's own token request, for as long as that request takes.
 */
async function withSessionCookie<T>(value: string, run: () => Promise<T>): Promise<T> {
  await browser.declarativeNetRequest.updateSessionRules({
    removeRuleIds: [TOKEN_COOKIE_RULE_ID],
    addRules: [
      {
        id: TOKEN_COOKIE_RULE_ID,
        priority: 2,
        condition: { urlFilter: '||open.spotify.com/api/token', tabIds: [-1], resourceTypes: ['xmlhttprequest'] },
        action: { type: 'modifyHeaders', requestHeaders: [{ header: 'Cookie', operation: 'set', value: `sp_dc=${value}` }] },
      },
    ],
  });
  try {
    return await run();
  } finally {
    await browser.declarativeNetRequest.updateSessionRules({ removeRuleIds: [TOKEN_COOKIE_RULE_ID] }).catch(() => undefined);
  }
}
const TOKEN_COOKIE_RULE_ID = 303;

async function mint(): Promise<AccessToken> {
  if (!(await prefs()).connected) throw new SpotifyAuthError('Spotify is disconnected');
  const cookie = await browser.cookies.get({ url: `${WEB_PLAYER}/`, name: 'sp_dc' }).catch(() => null);
  if (!cookie?.value) throw new SpotifyAuthError('Not signed in to Spotify in this browser');
  if (Date.now() < retryAfter) throw new SpotifyAuthError('Spotify would not mint a token; trying again shortly');
  spotifyRules ??= installSpotifyFetchRule();
  await spotifyRules;
  for (const force of [false, true]) {
    const player = await webPlayer(force);
    const code = await totp(player.secret.codes, Date.now());
    const query = new URLSearchParams({
      reason: 'init',
      productType: 'web-player',
      totp: code,
      totpServer: code,
      totpVer: String(player.secret.version),
    });
    const { status, body } = await withSessionCookie(cookie.value, async () => {
      const response = await fetch(`${WEB_PLAYER}/api/token?${query}`, { credentials: 'omit' });
      return {
        status: response.status,
        body: (await response.json().catch(() => ({}))) as {
          accessToken?: string;
          accessTokenExpirationTimestampMs?: number;
          clientId?: string;
          isAnonymous?: boolean;
          totpVerExpired?: string;
        },
      };
    });
    // The web player reads this flag to learn its secret has been retired.
    if (body.totpVerExpired && !force) continue;
    if (status === 401) throw new SpotifyAuthError('Your Spotify session has expired. Sign in again at open.spotify.com.');
    if (body.isAnonymous) throw new SpotifyAuthError('Not signed in to Spotify in this browser');
    if (body.accessToken) {
      access = {
        token: body.accessToken,
        expiresAt: body.accessTokenExpirationTimestampMs ?? Date.now() + 3_600_000,
        clientId: body.clientId ?? null,
        cookie: cookie.value,
      };
      return access;
    }
    if (force) break;
  }
  retryAfter = Date.now() + RETRY_MS;
  throw new SpotifyAuthError('Spotify would not mint a token');
}

let deviceId: string | null = null;
async function getDeviceId(): Promise<string> {
  if (deviceId) return deviceId;
  const stored = (await browser.storage.local.get('spotify.deviceId'))['spotify.deviceId'] as string | undefined;
  deviceId = stored ?? crypto.randomUUID().replaceAll('-', '');
  if (!stored) await browser.storage.local.set({ 'spotify.deviceId': deviceId });
  return deviceId;
}

function platformOs(): string {
  const ua = navigator.userAgent;
  return /Windows/.test(ua) ? 'windows' : /Mac OS/.test(ua) ? 'macos' : 'linux';
}

/** SpotifyToken.clientToken: best-effort, a request without it is still sent. */
async function clientToken(): Promise<string | null> {
  if (client && Date.now() < client.expiresAt - 30_000) return client.token;
  const bearer = await accessToken();
  if (!bearer.clientId) return null;
  const player = await webPlayer();
  try {
    const response = await fetch(CLIENT_TOKEN, {
      method: 'POST',
      // The bare type, deliberately: clienttoken 400s on a charset suffix.
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({
        client_data: {
          client_version: player.clientVersion,
          client_id: bearer.clientId,
          js_sdk_data: {
            device_brand: 'unknown',
            device_model: 'unknown',
            os: platformOs(),
            os_version: 'unknown',
            device_id: await getDeviceId(),
            device_type: 'computer',
          },
        },
      }),
    });
    const body = (await response.json()) as {
      response_type?: string;
      granted_token?: { token?: string; expires_after_seconds?: number };
    };
    if (body.response_type !== 'RESPONSE_GRANTED_TOKEN_RESPONSE' || !body.granted_token?.token) return null;
    client = { token: body.granted_token.token, expiresAt: Date.now() + (body.granted_token.expires_after_seconds ?? 3600) * 1000 };
    return client.token;
  } catch {
    return null;
  }
}

// ---- Pathfinder -----------------------------------------------------------------

type Json = Record<string, unknown>;

async function gql(operation: Operation, variables: Json, retried = false): Promise<Json> {
  const bearer = await accessToken();
  const player = await webPlayer();
  const clientTok = await clientToken();
  const response = await fetch(PATHFINDER, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${bearer.token}`,
      Accept: 'application/json',
      'Content-Type': 'application/json;charset=UTF-8',
      'App-Platform': 'WebPlayer',
      ...(clientTok && { 'Client-Token': clientTok }),
    },
    body: JSON.stringify({
      variables,
      operationName: operation,
      extensions: { persistedQuery: { version: 1, sha256Hash: player.hashes[operation] } },
    }),
  });
  if (response.status === 401 && !retried) {
    invalidateTokens();
    return gql(operation, variables, true);
  }
  const body = (await response.json().catch(() => null)) as Json | null;
  const errors = (body?.errors as { message?: string }[] | undefined)?.map((e) => e.message ?? '') ?? [];
  // A rebuilt web player retires the old hash: look it up again once.
  if (errors.some((m) => /PersistedQueryNotFound/i.test(m)) && !retried) {
    await webPlayer(true);
    return gql(operation, variables, true);
  }
  if (!response.ok) throw new Error(`Spotify ${operation} failed (${response.status})`);
  if (!body) throw new Error(`Spotify ${operation} answered with something other than JSON`);
  if (errors.length && !body.data) throw new Error(errors.join('; '));
  return body;
}

// ---- Parsing (SpotifyLibrary.kt) -------------------------------------------------

const obj = (v: unknown): Json | null => (v && typeof v === 'object' && !Array.isArray(v) ? (v as Json) : null);
const arr = (v: unknown): unknown[] => (Array.isArray(v) ? v : []);
const str = (v: unknown): string | null => (typeof v === 'string' ? v : null);
const num = (v: unknown): number | null => (typeof v === 'number' ? v : null);
const path = (v: unknown, ...keys: string[]): unknown => keys.reduce<unknown>((at, key) => obj(at)?.[key], v);

/** The widest source: Spotify lists sizes in no fixed order (SpotifyLibrary.largestSource). */
function largest(sources: unknown): string | null {
  let best: { url: string; width: number } | null = null;
  for (const source of arr(sources)) {
    const url = str(path(source, 'url'));
    const width = num(path(source, 'width')) ?? 0;
    if (url && (!best || width > best.width)) best = { url, width };
  }
  return best?.url ?? null;
}

/** The smallest source at least `min` wide, else the widest: list rows don't need 640px. */
function sized(sources: unknown, min = 300): string | null {
  const list = arr(sources)
    .map((s) => ({ url: str(path(s, 'url')), width: num(path(s, 'width')) ?? 0 }))
    .filter((s): s is { url: string; width: number } => !!s.url)
    .sort((a, b) => a.width - b.width);
  return (list.find((s) => s.width >= min) ?? list.at(-1))?.url ?? null;
}

function artistsOf(data: unknown): string {
  return arr(path(data, 'artists', 'items'))
    .map((a) => str(path(a, 'profile', 'name')))
    .filter((n): n is string => !!n?.trim())
    .join(', ');
}

function trackOf(data: unknown, uri: string | null): SpotifyTrack | null {
  const title = str(path(data, 'name'))?.trim();
  if (!title || !uri) return null;
  const album = obj(path(data, 'albumOfTrack'));
  return {
    id: uri.split(':').pop()!,
    title,
    artist: artistsOf(data),
    album: str(album?.name) ?? null,
    durationMs: num(path(data, 'trackDuration', 'totalMilliseconds')) ?? num(path(data, 'duration', 'totalMilliseconds')) ?? 0,
    imageUrl: sized(path(album, 'coverArt', 'sources')),
  };
}

export function parsePlaylistPage(root: Json): SpotifyPage<SpotifyPlaylist> {
  const library = obj(path(root, 'data', 'me', 'libraryV3'));
  if (!library) throw new Error('Spotify library response was empty');
  const raw = arr(library.items);
  const items = raw.flatMap((entry): SpotifyPlaylist[] => {
    const wrapper = obj(path(entry, 'item'));
    if (!wrapper || !str(wrapper.__typename)?.toLowerCase().includes('playlist')) return [];
    const data = obj(wrapper.data);
    if (str(data?.__typename) !== 'Playlist') return [];
    const uri = str(wrapper._uri);
    const name = str(data?.name);
    if (!uri || !name?.trim()) return [];
    return [
      {
        id: uri.split(':').pop()!,
        name,
        owner: str(path(data, 'ownerV2', 'data', 'name')),
        imageUrl: largest(path(arr(path(data, 'images', 'items'))[0], 'sources')),
      },
    ];
  });
  return { items, total: num(library.totalCount) ?? raw.length, raw: raw.length };
}

export function parseTrackPage(root: Json): SpotifyPage<SpotifyTrack> {
  const content = obj(path(root, 'data', 'playlistV2', 'content'));
  if (!content) throw new Error('Spotify playlist response was empty');
  const raw = arr(content.items);
  const items = raw.flatMap((entry) => {
    const data = path(entry, 'itemV2', 'data');
    const track = trackOf(data, str(path(data, 'uri')) ?? str(path(data, '_uri')));
    return track ? [track] : [];
  });
  return { items, total: num(content.totalCount) ?? raw.length, raw: raw.length };
}

export function parseLikedPage(root: Json): SpotifyPage<SpotifyTrack> {
  const tracks = obj(path(root, 'data', 'me', 'library', 'tracks'));
  if (!tracks) throw new Error('Spotify liked songs response was empty');
  const raw = arr(tracks.items);
  const items = raw.flatMap((entry) => {
    const wrapper = path(entry, 'track');
    const data = path(wrapper, 'data');
    const track = trackOf(data, str(path(wrapper, '_uri')) ?? str(path(data, 'uri')));
    return track ? [track] : [];
  });
  return { items, total: num(tracks.totalCount) ?? raw.length, raw: raw.length };
}

export function parseSearchTracks(root: Json): SpotifyTrack[] {
  return arr(path(root, 'data', 'searchV2', 'tracksV2', 'items')).flatMap((entry) => {
    const data = path(entry, 'item', 'data');
    const track = trackOf(data, str(path(data, 'uri')));
    return track ? [track] : [];
  });
}

/** SpotifyCanvasQuery.parse: a playable `.mp4`, or null. */
export function parseCanvas(root: Json): string | null {
  const url = str(path(root, 'data', 'trackUnion', 'canvas', 'url'));
  return url?.startsWith('https://') && /\.mp4(?:[?#]|$)/i.test(url) ? url : null;
}

// ---- What the bridge serves -------------------------------------------------------

export async function playlists(offset: number): Promise<SpotifyPage<SpotifyPlaylist>> {
  const page = parsePlaylistPage(
    await gql('libraryV3', {
      filters: ['Playlists'],
      order: null,
      textFilter: '',
      features: ['LIKED_SONGS', 'YOUR_EPISODES_V2', 'PRERELEASES', 'EVENTS'],
      limit: 50,
      offset,
      flatten: true,
      expandedFolders: [],
      folderUri: null,
      includeFoldersWhenFlattening: false,
    }),
  );
  // Liked Songs is not a playlist to the library query; it always comes first.
  if (offset === 0) page.items.unshift({ id: LIKED_ID, name: 'Liked Songs', owner: null, imageUrl: LIKED_COVER });
  return page;
}

export async function tracks(playlistId: string, offset: number): Promise<SpotifyPage<SpotifyTrack>> {
  if (playlistId === LIKED_ID) return parseLikedPage(await gql('fetchLibraryTracks', { offset, limit: 100 }));
  return parseTrackPage(
    await gql('fetchPlaylist', { uri: `spotify:playlist:${playlistId}`, offset, limit: 100, enableWatchFeedEntrypoint: false }),
  );
}

/** A playlist's name and cover at full size (the library list only carries a thumbnail). */
export async function playlistInfo(playlistId: string): Promise<{ name: string; owner: string | null; imageUrl: string | null; total: number }> {
  if (playlistId === LIKED_ID) {
    const page = parseLikedPage(await gql('fetchLibraryTracks', { offset: 0, limit: 1 }));
    return { name: 'Liked Songs', owner: null, imageUrl: LIKED_COVER, total: page.total };
  }
  const root = await gql('fetchPlaylist', { uri: `spotify:playlist:${playlistId}`, offset: 0, limit: 1, enableWatchFeedEntrypoint: false });
  const playlist = obj(path(root, 'data', 'playlistV2'));
  return {
    name: str(playlist?.name) ?? 'Playlist',
    owner: str(path(playlist, 'ownerV2', 'data', 'name')),
    imageUrl: largest(path(arr(path(playlist, 'images', 'items'))[0], 'sources')),
    total: num(path(playlist, 'content', 'totalCount')) ?? 0,
  };
}

export async function searchTracks(query: string): Promise<SpotifyTrack[]> {
  return parseSearchTracks(
    await gql('searchTracks', {
      searchTerm: query,
      offset: 0,
      limit: 10,
      numberOfTopResults: 5,
      includeAudiobooks: false,
      includePreReleases: false,
      includeAuthors: false,
    }),
  );
}

/** A track's canvas by Spotify id. Signed in only: anonymous tokens are shown none. */
export async function canvas(trackId: string): Promise<string | null> {
  return parseCanvas(await gql('canvas', { trackUri: `spotify:track:${trackId}` }));
}

// ---- Sign-in state ---------------------------------------------------------------

interface Prefs {
  connected: boolean;
}
const PREFS_KEY = 'spotify.prefs';

async function prefs(): Promise<Prefs> {
  const stored = (await browser.storage.local.get(PREFS_KEY))[PREFS_KEY] as Partial<Prefs> | undefined;
  return { connected: stored?.connected ?? true };
}

export async function setConnected(connected: boolean): Promise<SpotifyAuthState> {
  await browser.storage.local.set({ [PREFS_KEY]: { connected } });
  invalidateTokens();
  retryAfter = 0;
  profileCache = null;
  return authState();
}

/** `sp_dc` arriving or leaving is a Spotify sign-in or sign-out. */
export function isSessionCookie(cookie: Browser.cookies.Cookie): boolean {
  return cookie.name === 'sp_dc' && cookie.domain.endsWith('spotify.com');
}

export function invalidateSession(): void {
  invalidateTokens();
  retryAfter = 0;
  profileCache = null;
}

let profileCache: { name: string; avatarUrl: string | null } | null = null;

export async function authState(): Promise<SpotifyAuthState> {
  const cookie = await browser.cookies.get({ url: `${WEB_PLAYER}/`, name: 'sp_dc' }).catch(() => null);
  const browserSignedIn = !!cookie?.value;
  const { connected } = await prefs();
  const base: SpotifyAuthState = { browserSignedIn, connected, signedIn: false, user: null, problem: null };
  if (!browserSignedIn || !connected) return base;
  try {
    if (!profileCache) {
      const profile = obj(path(await gql('profileAttributes', {}), 'data', 'me', 'profile'));
      profileCache = {
        name: str(profile?.name) ?? str(profile?.username) ?? 'Spotify',
        avatarUrl: sized(path(profile, 'avatar', 'sources'), 64),
      };
    }
    return { ...base, signedIn: true, user: profileCache };
  } catch (error) {
    return { ...base, problem: error instanceof Error ? error.message : String(error) };
  }
}
