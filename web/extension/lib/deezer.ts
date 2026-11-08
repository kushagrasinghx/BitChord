import type { DeezerAuthState, DeezerPage, DeezerPlaylist, DeezerTrack } from '@bitchord/shared/bridge';
import {
  coverUrl,
  hasErrors,
  LOVED_ID,
  num,
  obj,
  parsePlaylistPage,
  parsePublicPlaylistsPage,
  parsePublicTracksPage,
  parseTrackPage,
  parseUserData,
  path,
  str,
  type Json,
} from './deezerParse';
import { installDeezerFetchRule } from './rules';

export { LOVED_ID, parsePlaylistPage, parsePublicPlaylistsPage, parsePublicTracksPage, parseTrackPage, parseUserData };

/**
 * Deezer, with the user's own www.deezer.com session: the `arl` cookie stays
 * in the browser's jar and the `gw-light.php` session tokens minted from it
 * stay here. The page only gets playlists and tracks.
 *
 * Ports DeezerLibrary.kt:
 * - Session: `deezer.getUserData` on `www.deezer.com/ajax/gw-light.php` with
 *   the `arl` cookie returns `checkForm` (the `api_token` for subsequent
 *   calls), `USER.USER_ID`, `USER.BLOG_NAME`, `USER.USER_PICTURE` and
 *   `USER.LOVEDTRACKS_ID`.
 * - Library: `deezer.pageProfile` (`tab: "playlists"`) with fallback to
 *   `api.deezer.com/user/{id}/playlists`. Favorite Tracks (`LOVED_ID`) leads
 *   the first page.
 * - Tracks: `playlist.getSongs` on `gw-light.php` (private and public
 *   playlists, including Favorite Tracks), with fallback to
 *   `api.deezer.com/playlist/{id}/tracks`.
 */

const WEB_ORIGIN = 'https://www.deezer.com';
const GW_LIGHT = 'https://www.deezer.com/ajax/gw-light.php';
const PUBLIC_API = 'https://api.deezer.com';

const SESSION_TTL_MS = 20 * 60_000;
const RETRY_MS = 30_000;

export class DeezerAuthError extends Error {
  readonly code = 'unauthenticated';
}

// ---- Session (gw-light.php) ------------------------------------------------------

interface Session {
  apiToken: string;
  sid: string | null;
  userId: string;
  lovedTracksId: string | null;
  user: { name: string; avatarUrl: string | null };
  cookie: string;
  expiresAt: number;
}

let deezerRules: Promise<void> | null = null;
let session: Session | null = null;
let opening: Promise<Session> | null = null;
let retryAfter = 0;

async function readCookie(name: string): Promise<string | null> {
  const primary = await browser.cookies.get({ url: `${WEB_ORIGIN}/`, name }).catch(() => null);
  if (primary?.value) return primary.value;
  const root = await browser.cookies.get({ url: 'https://deezer.com/', name }).catch(() => null);
  return root?.value ?? null;
}

const SESSION_COOKIE_RULE_ID = 502;
let activeCookieRuleCount = 0;
let installedCookieValue: string | null = null;

/**
 * Like Spotify's `sp_dc`, `credentials: 'include'` from a service worker cannot
 * be relied upon to attach `arl` across browsers, so a temporary session rule
 * sets the `Cookie` header on this extension's own `gw-light.php` call.
 * Refcounted so concurrent `gwCall` requests never remove the rule mid-flight.
 */
async function withSessionCookie<T>(arl: string, sid: string | null, run: () => Promise<T>): Promise<T> {
  const value = sid ? `arl=${arl}; sid=${sid}` : `arl=${arl}`;
  activeCookieRuleCount++;
  try {
    if (installedCookieValue !== value) {
      await browser.declarativeNetRequest.updateSessionRules({
        removeRuleIds: [SESSION_COOKIE_RULE_ID],
        addRules: [
          {
            id: SESSION_COOKIE_RULE_ID,
            priority: 2,
            condition: { urlFilter: '||www.deezer.com/ajax/gw-light.php', tabIds: [-1], resourceTypes: ['xmlhttprequest'] },
            action: { type: 'modifyHeaders', requestHeaders: [{ header: 'Cookie', operation: 'set', value }] },
          },
        ],
      });
      installedCookieValue = value;
    }
    return await run();
  } finally {
    activeCookieRuleCount--;
    if (activeCookieRuleCount === 0) {
      installedCookieValue = null;
      await browser.declarativeNetRequest.updateSessionRules({ removeRuleIds: [SESSION_COOKIE_RULE_ID] }).catch(() => undefined);
    }
  }
}

async function getSession(): Promise<Session> {
  const arl = await readCookie('arl');
  if (session && session.cookie !== arl) invalidateSession();
  if (session && Date.now() < session.expiresAt) return session;
  opening ??= openSession().finally(() => (opening = null));
  return opening;
}

async function openSession(): Promise<Session> {
  if (!(await prefs()).connected) throw new DeezerAuthError('Deezer is disconnected');
  const arl = await readCookie('arl');
  if (!arl) throw new DeezerAuthError('Not signed in to Deezer in this browser');
  if (Date.now() < retryAfter) throw new DeezerAuthError('Deezer would not open a session; trying again shortly');
  deezerRules ??= installDeezerFetchRule();
  await deezerRules;

  const existingSid = await readCookie('sid');
  const query = new URLSearchParams({
    method: 'deezer.getUserData',
    input: '3',
    api_version: '1.0',
    api_token: 'null',
  });

  const { status, body } = await withSessionCookie(arl, existingSid, async () => {
    const response = await fetch(`${GW_LIGHT}?${query}`, {
      method: 'POST',
      credentials: 'include',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json;charset=UTF-8' },
      body: '{}',
    });
    return {
      status: response.status,
      body: (await response.json().catch(() => null)) as Json | null,
    };
  });

  if (status === 401 || status === 403) {
    throw new DeezerAuthError('Your Deezer session has expired. Sign in again at www.deezer.com.');
  }
  if (!body) {
    retryAfter = Date.now() + RETRY_MS;
    throw new DeezerAuthError('Deezer did not answer');
  }

  const parsed = parseUserData(body);
  if (!parsed) {
    throw new DeezerAuthError('Your Deezer session has expired. Sign in again at www.deezer.com.');
  }

  const jarSid = await readCookie('sid');
  session = {
    apiToken: parsed.apiToken,
    sid: jarSid ?? existingSid,
    userId: parsed.userId,
    lovedTracksId: parsed.lovedTracksId,
    user: { name: parsed.name, avatarUrl: parsed.avatarUrl },
    cookie: arl,
    expiresAt: Date.now() + SESSION_TTL_MS,
  };
  return session;
}

async function gwCall(method: string, params: Json, retried = false): Promise<Json> {
  const s = await getSession();
  const query = new URLSearchParams({
    method,
    input: '3',
    api_version: '1.0',
    api_token: s.apiToken,
  });
  const response = await withSessionCookie(s.cookie, s.sid, () =>
    fetch(`${GW_LIGHT}?${query}`, {
      method: 'POST',
      credentials: 'include',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json;charset=UTF-8' },
      body: JSON.stringify(params),
    }),
  );
  if ((response.status === 401 || response.status === 403) && !retried) {
    session = null;
    return gwCall(method, params, true);
  }
  const body = (await response.json().catch(() => null)) as Json | null;
  if (!body) throw new Error(`Deezer ${method} answered with something other than JSON`);
  if (hasErrors(body.error)) {
    if (!retried) {
      session = null;
      return gwCall(method, params, true);
    }
    throw new Error(`Deezer ${method} returned an error`);
  }
  if (!response.ok) throw new Error(`Deezer ${method} failed (${response.status})`);
  return body;
}

// ---- What the bridge serves -------------------------------------------------------

export async function playlists(offset: number): Promise<DeezerPage<DeezerPlaylist>> {
  const s = await getSession();
  let page: DeezerPage<DeezerPlaylist>;
  if (offset === 0) {
    try {
      const root = await gwCall('deezer.pageProfile', { user_id: s.userId, tab: 'playlists', nb: 2000 });
      page = parsePlaylistPage(root);
      // deezer.pageProfile returns all items in one response.
      page = { ...page, total: page.raw };
    } catch {
      const response = await fetch(`${PUBLIC_API}/user/${encodeURIComponent(s.userId)}/playlists?index=${offset}&limit=100`, {
        credentials: 'omit',
      });
      if (!response.ok) throw new Error(`Deezer playlists failed (${response.status})`);
      const pub = parsePublicPlaylistsPage((await response.json()) as Json);
      if (!s.lovedTracksId && pub.lovedTracksId) s.lovedTracksId = pub.lovedTracksId;
      page = pub;
    }
  } else {
    const response = await fetch(`${PUBLIC_API}/user/${encodeURIComponent(s.userId)}/playlists?index=${offset}&limit=100`, {
      credentials: 'omit',
    });
    if (!response.ok) throw new Error(`Deezer playlists failed (${response.status})`);
    const pub = parsePublicPlaylistsPage((await response.json()) as Json);
    if (!s.lovedTracksId && pub.lovedTracksId) s.lovedTracksId = pub.lovedTracksId;
    page = pub;
  }

  const lovedId = s.lovedTracksId;
  const hasLovedEntry = !!lovedId || page.items.some((p) => p.id === LOVED_ID);
  const filtered = page.items.filter((p) => p.id !== lovedId && p.id !== LOVED_ID);
  if (offset === 0 && hasLovedEntry) {
    filtered.unshift({ id: LOVED_ID, name: 'Favorite Tracks', owner: null, imageUrl: null });
  }
  return { items: filtered, total: page.total, raw: page.raw };
}

export async function tracks(playlistId: string, offset: number): Promise<DeezerPage<DeezerTrack>> {
  const s = await getSession();
  const resolvedId = playlistId === LOVED_ID ? (s.lovedTracksId ?? '') : playlistId;
  if (!resolvedId) return { items: [], total: 0, raw: 0 };

  try {
    const root = await gwCall('playlist.getSongs', { playlist_id: resolvedId, nb: 200, start: offset });
    if (obj(root.results)) {
      return parseTrackPage(root);
    }
  } catch {
    // Fall back to the public REST API for public playlists.
  }

  const response = await fetch(`${PUBLIC_API}/playlist/${encodeURIComponent(resolvedId)}/tracks?index=${offset}&limit=100`, {
    credentials: 'omit',
  });
  if (!response.ok) throw new Error(`Deezer playlist tracks failed (${response.status})`);
  return parsePublicTracksPage((await response.json()) as Json);
}

export async function playlistInfo(
  playlistId: string,
): Promise<{ name: string; owner: string | null; imageUrl: string | null; total: number }> {
  const s = await getSession();
  const resolvedId = playlistId === LOVED_ID ? (s.lovedTracksId ?? '') : playlistId;
  if (!resolvedId) return { name: 'Favorite Tracks', owner: null, imageUrl: null, total: 0 };

  if (playlistId === LOVED_ID) {
    const page = await tracks(LOVED_ID, 0);
    return { name: 'Favorite Tracks', owner: null, imageUrl: null, total: page.total };
  }

  try {
    const root = await gwCall('deezer.pagePlaylist', {
      playlist_id: resolvedId,
      lang: 'en',
      nb: 1,
      start: 0,
      tab: 0,
      tags: true,
      header: true,
    });
    const data = obj(path(root, 'results', 'DATA'));
    if (data) {
      const picType = str(data.PICTURE_TYPE)?.trim() || 'playlist';
      return {
        name: str(data.TITLE)?.trim() || 'Playlist',
        owner: str(data.PARENT_USERNAME)?.trim() || null,
        imageUrl: coverUrl(picType, str(data.PLAYLIST_PICTURE)),
        total: num(path(root, 'results', 'SONGS', 'total')) ?? num(data.NB_SONG) ?? 0,
      };
    }
  } catch {
    // Fall back to public API below.
  }

  const response = await fetch(`${PUBLIC_API}/playlist/${encodeURIComponent(resolvedId)}`, { credentials: 'omit' });
  if (!response.ok) throw new Error(`Deezer playlist failed (${response.status})`);
  const body = (await response.json()) as Json;
  if (hasErrors(body.error)) throw new Error('Deezer playlist not found');
  return {
    name: str(body.title)?.trim() || 'Playlist',
    owner: str(path(body, 'creator', 'name'))?.trim() || null,
    imageUrl:
      str(body.picture_big) ??
      str(body.picture_xl) ??
      str(body.picture_medium) ??
      coverUrl(str(body.picture_type)?.trim() || 'playlist', str(body.md5_image)),
    total: num(body.nb_tracks) ?? 0,
  };
}

// ---- Sign-in state ---------------------------------------------------------------

interface Prefs {
  connected: boolean;
}
const PREFS_KEY = 'deezer.prefs';

async function prefs(): Promise<Prefs> {
  const stored = (await browser.storage.local.get(PREFS_KEY))[PREFS_KEY] as Partial<Prefs> | undefined;
  return { connected: stored?.connected ?? true };
}

export async function setConnected(connected: boolean): Promise<DeezerAuthState> {
  await browser.storage.local.set({ [PREFS_KEY]: { connected } });
  invalidateSession();
  return authState();
}

/** `arl` arriving or leaving is a Deezer sign-in or sign-out. */
export function isSessionCookie(cookie: Browser.cookies.Cookie): boolean {
  return cookie.name === 'arl' && cookie.domain.endsWith('deezer.com');
}

export function invalidateSession(): void {
  session = null;
  retryAfter = 0;
}

export async function authState(): Promise<DeezerAuthState> {
  const arl = await readCookie('arl');
  const browserSignedIn = !!arl;
  const { connected } = await prefs();
  const base: DeezerAuthState = { browserSignedIn, connected, signedIn: false, user: null, problem: null };
  if (!browserSignedIn || !connected) return base;
  try {
    const s = await getSession();
    return { ...base, signedIn: true, user: s.user };
  } catch (error) {
    return { ...base, problem: error instanceof Error ? error.message : String(error) };
  }
}
