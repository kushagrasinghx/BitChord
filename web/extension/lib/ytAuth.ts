import type { YtAccount, YtAuthState, YtChannel } from '@bitchord/shared/bridge';
import { installExtensionFetchRule } from './rules';

/**
 * YouTube Music sign-in (prompt §5.3): the user's own browser session, used
 * from here and nowhere else. The cookies stay in the browser's jar (requests
 * go out with `credentials: 'include'`), the SAPISIDHASH is computed here, and
 * the page only ever learns names and pictures.
 *
 * The scheme is the one Innertube.kt and DesktopYouTubeAuth.kt use: the
 * signature over SAPISID, `X-Goog-AuthUser` to pick the login in the jar, and
 * `X-Goog-PageId` for a brand channel. Logins and channels are listed the way
 * the desktop app lists them: `account/account_menu` for each login, then
 * `account/accounts_list` for that login's channels.
 */

export const MUSIC_ORIGIN = 'https://music.youtube.com';

/** The cookies the signature can be taken over, in the order Google's clients prefer them. */
const SIGNING_COOKIES = ['SAPISID', '__Secure-3PAPISID', '__Secure-1PAPISID'];
/** Cookies whose arrival or departure means a sign-in or sign-out (SIDCC and friends rotate constantly). */
const SESSION_COOKIES = new Set([...SIGNING_COOKIES, 'SID', '__Secure-1PSID', '__Secure-3PSID']);

/** Any recent WEB_REMIX version is accepted for the account endpoints (Innertube.kt keeps a fallback the same way). */
const WEB_REMIX_VERSION = '1.20251006.03.00';
/**
 * How many `X-Goog-AuthUser` indices are tried. Google allows ten logins per
 * browser, and the live ones needn't be contiguous: the user's jar had 1, 2
 * and 4 signed in, with 0 and 3 expired (account_menu answers 403 for those).
 */
const MAX_LOGINS = 10;
/** Accounts rarely change; a cookie change clears this at once anyway. */
const ACCOUNTS_TTL_MS = 10 * 60_000;

// ---- Preferences --------------------------------------------------------------

interface Prefs {
  /** "Disconnect" sets this false; the Google session itself is left alone. */
  connected: boolean;
  selectedChannelId: string | null;
}

const PREFS_KEY = 'yt.prefs';

async function loadPrefs(): Promise<Prefs> {
  const stored = (await browser.storage.local.get(PREFS_KEY))[PREFS_KEY] as Partial<Prefs> | undefined;
  return { connected: stored?.connected ?? true, selectedChannelId: stored?.selectedChannelId ?? null };
}

async function savePrefs(change: Partial<Prefs>): Promise<void> {
  await browser.storage.local.set({ [PREFS_KEY]: { ...(await loadPrefs()), ...change } });
}

// ---- Signing --------------------------------------------------------------------

async function signingSecret(): Promise<string | null> {
  for (const name of SIGNING_COOKIES) {
    const cookie = await browser.cookies.get({ url: `${MUSIC_ORIGIN}/`, name }).catch(() => null);
    if (cookie?.value) return cookie.value;
  }
  return null;
}

/** `SAPISIDHASH <seconds>_<sha1(seconds + " " + secret + " " + origin)>`, as DesktopYouTubeAuth.sapisidHash. */
async function sapisidHash(secret: string, origin: string): Promise<string> {
  const timestamp = Math.floor(Date.now() / 1000);
  const digest = await crypto.subtle.digest('SHA-1', new TextEncoder().encode(`${timestamp} ${secret} ${origin}`));
  const hex = [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
  return `SAPISIDHASH ${timestamp}_${hex}`;
}

/** Who a signed request acts as. */
interface Identity {
  authUser: string;
  pageId: string | null;
}

export class SignedOutError extends Error {
  readonly code = 'unauthenticated';
}

let ruleReady: Promise<void> | null = null;

/**
 * A request made as [identity], signed for [signAs] when that isn't the URL's
 * own origin. The browser attaches the cookies; the
 * extension's session rule gives it the YouTube Origin the signature was
 * computed for (rules.ts, installExtensionFetchRule).
 */
export async function signedFetch(
  url: string,
  init: { method?: string; headers?: Record<string, string>; body?: BodyInit | null },
  identity: Identity,
  signAs?: string,
): Promise<Response> {
  const secret = await signingSecret();
  if (!secret) throw new SignedOutError('The browser is not signed in to YouTube.');
  ruleReady ??= installExtensionFetchRule().catch((error: unknown) => {
    ruleReady = null;
    throw error;
  });
  await ruleReady;

  const origin = signAs ?? new URL(url).origin;
  const headers = new Headers(init.headers);
  headers.set('Authorization', await sapisidHash(secret, origin));
  headers.set('X-Goog-AuthUser', identity.authUser);
  headers.set('X-Origin', origin);
  if (identity.pageId) headers.set('X-Goog-PageId', identity.pageId);
  else headers.delete('X-Goog-PageId');
  return fetch(url, { method: init.method ?? 'GET', headers, body: init.body ?? null, credentials: 'include' });
}

async function musicApi(endpoint: string, identity: Identity): Promise<unknown> {
  const response = await signedFetch(
    `${MUSIC_ORIGIN}/youtubei/v1/${endpoint}?prettyPrint=false`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Youtube-Client-Name': '67',
        'X-Youtube-Client-Version': WEB_REMIX_VERSION,
      },
      body: JSON.stringify({
        context: { client: { clientName: 'WEB_REMIX', clientVersion: WEB_REMIX_VERSION, hl: 'en', gl: 'US' }, user: {} },
      }),
    },
    identity,
  );
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}

// ---- Reading the account renderers (DesktopAccountParser) ----------------------

type Json = Record<string, unknown>;

/** Every object stored under [key], anywhere inside. */
function renderers(root: unknown, key: string, out: Json[] = []): Json[] {
  if (Array.isArray(root)) for (const item of root) renderers(item, key, out);
  else if (root && typeof root === 'object') {
    for (const [k, v] of Object.entries(root)) {
      if (k === key && v && typeof v === 'object' && !Array.isArray(v)) out.push(v as Json);
      else renderers(v, key, out);
    }
  }
  return out;
}

/** The first string under [key], anywhere inside. */
function findString(root: unknown, key: string): string | null {
  if (Array.isArray(root)) {
    for (const item of root) {
      const found = findString(item, key);
      if (found) return found;
    }
  } else if (root && typeof root === 'object') {
    for (const [k, v] of Object.entries(root)) {
      if (k === key && typeof v === 'string' && v) return v;
      const found = findString(v, key);
      if (found) return found;
    }
  }
  return null;
}

/** A `runs` or `simpleText` field as plain text. */
function text(node: unknown): string {
  const n = node as { runs?: { text?: string }[]; simpleText?: string } | undefined;
  if (n?.runs) return n.runs.map((r) => r.text ?? '').join('');
  return n?.simpleText ?? '';
}

function photo(item: Json): string | null {
  const thumbnails = (item.accountPhoto as { thumbnails?: { url?: string }[] } | undefined)?.thumbnails;
  return thumbnails?.at(-1)?.url ?? null;
}

interface StoredChannel extends YtChannel {
  authUser: string;
  pageId: string | null;
  /** YouTube's own choice for this login, which is the default here too. */
  selectedOnWeb: boolean;
}

interface StoredAccount extends Omit<YtAccount, 'channels'> {
  channels: StoredChannel[];
}

function channelId(authUser: string, pageId: string | null): string {
  return `${authUser}:${pageId ?? 'own'}`;
}

function parseChannels(root: unknown, authUser: string): StoredChannel[] {
  const channels: StoredChannel[] = [];
  for (const item of renderers(root, 'accountItem')) {
    const name = text(item.accountName);
    if (!name) continue;
    const pageId = findString(item, 'pageId');
    const handle = text(item.channelHandle) || text(item.accountByline) || null;
    const id = channelId(authUser, pageId);
    if (channels.some((c) => c.id === id)) continue;
    channels.push({
      id,
      name,
      handle,
      avatarUrl: photo(item),
      brand: pageId !== null,
      authUser,
      pageId,
      selectedOnWeb: item.isSelected === true,
    });
  }
  return channels;
}

/** What went wrong reading the accounts, in words the popup can show; null when it worked. */
let problem: string | null = null;

/** Why a response has no account in it, read from what Google says about the session. */
function describeEmpty(endpoint: string, body: unknown): string {
  // responseContext.serviceTrackingParams carries GFEEDBACK's `logged_in`
  // flag ("0" on an anonymous answer), whatever else the response holds.
  const services =
    (body as { responseContext?: { serviceTrackingParams?: { params?: Json[] }[] } })?.responseContext
      ?.serviceTrackingParams ?? [];
  const loggedIn = services.flatMap((service) => service.params ?? []).find((param) => param.key === 'logged_in')?.value;
  if (loggedIn === '0') return `${endpoint}: YouTube answered as if signed out (the session wasn't accepted)`;
  const keys = body && typeof body === 'object' ? Object.keys(body).join(', ') : typeof body;
  return `${endpoint}: no account in the answer (${keys})`;
}

/**
 * One login, or null with the reason when that index isn't signed in.
 * Only `account_menu` decides: `accounts_list` answers even for an expired
 * index (with another login's channels), which once made a dead login look
 * like the active one.
 */
async function readLogin(authUser: string): Promise<{ account: StoredAccount | null; why: string | null }> {
  const identity = { authUser, pageId: null };
  let header: Json | undefined;
  try {
    const menu = await musicApi('account/account_menu', identity);
    header = renderers(menu, 'activeAccountHeaderRenderer')[0];
    if (!header) return { account: null, why: describeEmpty('account_menu', menu) };
  } catch (error) {
    return { account: null, why: `account_menu: ${error instanceof Error ? error.message : String(error)}` };
  }

  const name = text(header.accountName) || 'YouTube Music';
  const email = text(header.email) || null;
  const handle = text(header.channelHandle) || null;
  const avatarUrl = photo(header);
  let channels: StoredChannel[] = [];
  try {
    channels = parseChannels(await musicApi('account/accounts_list', identity), authUser);
  } catch {
    // The switcher isn't answered for every account; the login's own channel always exists.
  }
  if (!channels.length) {
    channels = [
      { id: channelId(authUser, null), name, handle: handle ?? email, avatarUrl, brand: false, authUser, pageId: null, selectedOnWeb: true },
    ];
  }
  return { account: { authUser, name, email, avatarUrl, channels }, why: null };
}

/**
 * The login YouTube Music itself is using in this browser: its page's
 * `SESSION_INDEX` (ytcfg). That's the right default rather than index 0,
 * which may be a login that has expired.
 */
async function webSessionIndex(): Promise<string | null> {
  try {
    const response = await signedFetch(`${MUSIC_ORIGIN}/`, { headers: { Accept: 'text/html' } }, { authUser: '0', pageId: null });
    const html = await response.text();
    return /"SESSION_INDEX":"?(\d+)/.exec(html)?.[1] ?? null;
  } catch {
    return null;
  }
}

async function readAccounts(): Promise<{ list: StoredAccount[]; webIndex: string | null }> {
  problem = null;
  const [results, webIndex] = await Promise.all([
    Promise.all(Array.from({ length: MAX_LOGINS }, (_, i) => readLogin(String(i)))),
    webSessionIndex(),
  ]);
  const list: StoredAccount[] = [];
  const seen = new Set<string>();
  for (const { account } of results) {
    if (!account) continue;
    const key = account.email ?? `${account.name}|${account.channels.map((c) => c.id.split(':')[1]).join(',')}`;
    if (seen.has(key)) continue;
    seen.add(key);
    list.push(account);
  }
  if (!list.length) {
    problem = results[Number(webIndex ?? 0)]?.why ?? results[0]?.why ?? 'no signed-in Google account found';
    console.warn(`BitChord: no YouTube account found: ${problem}`);
  }
  return { list, webIndex };
}

// ---- State --------------------------------------------------------------------

interface Accounts {
  list: StoredAccount[];
  /** The login YouTube Music's own page uses, the default choice. */
  webIndex: string | null;
}

let cache: (Accounts & { at: number }) | null = null;
let reading: Promise<Accounts> | null = null;

async function accounts(refresh: boolean): Promise<Accounts> {
  if (!refresh && cache && Date.now() - cache.at < ACCOUNTS_TTL_MS) return cache;
  reading ??= readAccounts()
    .then((read) => {
      cache = { ...read, at: Date.now() };
      return read;
    })
    .finally(() => (reading = null));
  return reading;
}

/** Forgets the account list, after a sign-in or sign-out in the browser. */
export function invalidateAccounts(): void {
  cache = null;
}

export function isSessionCookie(cookie: { name: string; domain: string }): boolean {
  return SESSION_COOKIES.has(cookie.name) && /(^|\.)youtube\.com$/.test(cookie.domain.replace(/^\./, ''));
}

function chooseChannel({ list, webIndex }: Accounts, selectedId: string | null): StoredChannel | null {
  const all = list.flatMap((a) => a.channels);
  const web = list.find((a) => a.authUser === webIndex) ?? list[0];
  return (
    all.find((c) => c.id === selectedId) ??
    web?.channels.find((c) => c.selectedOnWeb) ??
    web?.channels[0] ??
    null
  );
}

function publicChannel({ id, name, handle, avatarUrl, brand }: StoredChannel): YtChannel {
  return { id, name, handle, avatarUrl, brand };
}

export async function authState(refresh = false): Promise<YtAuthState> {
  const prefs = await loadPrefs();
  const browserSignedIn = (await signingSecret()) !== null;
  if (!browserSignedIn || !prefs.connected) {
    return { browserSignedIn, connected: prefs.connected, signedIn: false, accounts: [], active: null, problem: null };
  }
  const read = await accounts(refresh);
  const { list } = read;
  const active = chooseChannel(read, prefs.selectedChannelId);
  return {
    browserSignedIn,
    connected: true,
    signedIn: active !== null,
    problem: active ? null : problem,
    accounts: list.map((a) => ({ ...a, channels: a.channels.map(publicChannel) })),
    active: active && { ...publicChannel(active), authUser: active.authUser },
  };
}

/** The identity signed requests use right now, or null when they can't be made. */
export async function activeIdentity(): Promise<Identity | null> {
  const prefs = await loadPrefs();
  if (!prefs.connected || (await signingSecret()) === null) return null;
  const active = chooseChannel(await accounts(false), prefs.selectedChannelId);
  return active && { authUser: active.authUser, pageId: active.pageId };
}

export async function selectChannel(id: string): Promise<YtAuthState> {
  const { list } = await accounts(false);
  if (!list.some((a) => a.channels.some((c) => c.id === id))) throw new Error(`No channel ${id} in this browser`);
  await savePrefs({ selectedChannelId: id });
  return authState();
}

export async function setConnected(connected: boolean): Promise<YtAuthState> {
  await savePrefs({ connected });
  return authState(connected);
}
