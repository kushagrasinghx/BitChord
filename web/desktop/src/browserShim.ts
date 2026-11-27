import { app, session as electronSession, webContents, type Cookie as ElectronCookie, type Session } from 'electron';
import { join } from 'node:path';
import { OWN_REQUEST_HEADER, updateRules } from './dnr';
import { FileStorage } from './storage';

/**
 * The `browser` global the extension's lib code (web/extension/lib) is
 * written against, backed by Electron. Only what those modules call is here.
 * It is imported before them (main.ts), so the global exists when they load.
 *
 * - cookies: the app's own session, which its sign-in windows share.
 * - declarativeNetRequest: dnr.ts, over webRequest.
 * - storage.local: a JSON file in the user data folder.
 * - tabs and scripting: the app's sign-in windows (windows.ts).
 * - permissions: always granted. A desktop app has no per-host prompt.
 */


/** How the shim reaches the app's windows; main.ts provides it once the app is ready. */
export interface TabsHost {
  open(url: string): Promise<number>;
  list(): { id: number; url: string }[];
  close(id: number): void;
  /** Extension pages (`bitchord-extension://…`) the desktop app serves itself. */
  openExtensionPage(path: string): Promise<void>;
}

let tabsHost: TabsHost | null = null;
export function setTabsHost(host: TabsHost): void {
  tabsHost = host;
}

function host(): TabsHost {
  if (!tabsHost) throw new Error('The app is still starting.');
  return tabsHost;
}

export const EXTENSION_ORIGIN = 'bitchord-extension://app';

function appSession(): Session {
  return electronSession.defaultSession;
}

// ---- fetch: Chromium's network stack, the app's cookies ------------------------

/**
 * The lib code's `fetch`, made through the app's session so its cookies and
 * the header rules apply. OWN_REQUEST_HEADER marks it as the extension's own
 * request (`tabIds: [-1]`); dnr.ts removes it before it leaves.
 */
async function sessionFetch(input: string | URL | Request, init?: RequestInit): Promise<Response> {
  const url = input instanceof Request ? input.url : String(input);
  const headers = new Headers(init?.headers ?? (input instanceof Request ? input.headers : undefined));
  headers.set(OWN_REQUEST_HEADER, '1');
  const response = await appSession().fetch(input instanceof Request ? input : url, { ...init, headers });
  // Electron leaves Response.url unset; the lib code reports it back to the page.
  if (!response.url) Object.defineProperty(response, 'url', { value: url });
  return response;
}

// ---- cookies -----------------------------------------------------------------

function toCookie(cookie: ElectronCookie): Browser.cookies.Cookie {
  return {
    name: cookie.name,
    value: cookie.value,
    domain: cookie.domain ?? '',
    hostOnly: cookie.hostOnly ?? false,
    path: cookie.path ?? '/',
    secure: cookie.secure ?? false,
    httpOnly: cookie.httpOnly ?? false,
    sameSite: (cookie.sameSite === 'no_restriction' ? 'no_restriction' : cookie.sameSite ?? 'unspecified') as Browser.cookies.SameSiteStatus,
    session: cookie.session ?? true,
    expirationDate: cookie.expirationDate,
    storeId: '0',
  } as Browser.cookies.Cookie;
}

type CookieListener = (info: { cookie: Browser.cookies.Cookie; removed: boolean; cause: string }) => void;
const cookieListeners = new Set<CookieListener>();
let watchingCookies = false;

function watchCookies(): void {
  if (watchingCookies) return;
  watchingCookies = true;
  appSession().cookies.on('changed', (_event, cookie, cause, removed) => {
    for (const listener of cookieListeners) listener({ cookie: toCookie(cookie), removed, cause });
  });
}

// ---- storage -----------------------------------------------------------------

let storage: FileStorage | null = null;
export function extensionStorage(): FileStorage {
  storage ??= new FileStorage(join(app.getPath('userData'), 'bitchord-extension.json'));
  return storage;
}

// ---- match patterns (tabs.query) ---------------------------------------------

function patternMatches(pattern: string, url: string): boolean {
  const regex = new RegExp(`^${pattern.replace(/[.+?^${}()|[\]\\/]/g, '\\$&').replace(/\*/g, '.*')}$`);
  return regex.test(url);
}

// ---- the global ----------------------------------------------------------------

function osName(): 'win' | 'mac' | 'linux' {
  return process.platform === 'win32' ? 'win' : process.platform === 'darwin' ? 'mac' : 'linux';
}

const shim = {
  runtime: {
    id: 'bitchord-desktop',
    getURL: (path: string) => `${EXTENSION_ORIGIN}${path.startsWith('/') ? '' : '/'}${path}`,
    getManifest: () => ({ version: __BRIDGE_VERSION__, manifest_version: 3, name: 'BitChord' }),
    getPlatformInfo: async () => ({ os: osName(), arch: process.arch === 'arm64' ? 'arm64' : 'x86-64' }),
  },
  cookies: {
    async get({ url, name }: { url: string; name: string }) {
      const [cookie] = await appSession().cookies.get({ url, name });
      return cookie ? toCookie(cookie) : null;
    },
    async getAll(filter: { url?: string; name?: string; domain?: string }) {
      return (await appSession().cookies.get(filter)).map(toCookie);
    },
    onChanged: {
      addListener(listener: CookieListener) {
        watchCookies();
        cookieListeners.add(listener);
      },
      removeListener(listener: CookieListener) {
        cookieListeners.delete(listener);
      },
    },
  },
  declarativeNetRequest: {
    async updateDynamicRules(options: { removeRuleIds?: number[]; addRules?: Browser.declarativeNetRequest.Rule[] }) {
      updateRules('dynamic', options);
    },
    async updateSessionRules(options: { removeRuleIds?: number[]; addRules?: Browser.declarativeNetRequest.Rule[] }) {
      updateRules('session', options);
    },
  },
  storage: {
    local: {
      get: (keys?: string | string[] | Record<string, unknown> | null) => extensionStorage().get(keys),
      set: (items: Record<string, unknown>) => extensionStorage().set(items),
      remove: (keys: string | string[]) => extensionStorage().remove(keys),
      clear: () => extensionStorage().clear(),
    },
    onChanged: {
      addListener(listener: (changes: Record<string, unknown>, area: string) => void) {
        extensionStorage().onChanged(listener);
      },
    },
  },
  permissions: {
    contains: async () => true,
    request: async () => true,
  },
  tabs: {
    async create({ url }: { url: string; active?: boolean }) {
      if (url.startsWith(EXTENSION_ORIGIN)) {
        await host().openExtensionPage(url.slice(EXTENSION_ORIGIN.length));
        return { id: undefined, url };
      }
      return { id: await host().open(url), url };
    },
    async query({ url }: { url?: string | string[] }) {
      const patterns = url === undefined ? null : Array.isArray(url) ? url : [url];
      return host()
        .list()
        .filter((tab) => !patterns || patterns.some((p) => patternMatches(p, tab.url)));
    },
    async remove(ids: number | number[]) {
      for (const id of Array.isArray(ids) ? ids : [ids]) host().close(id);
    },
  },
  scripting: {
    async executeScript({
      target,
      func,
      args = [],
    }: {
      target: { tabId: number };
      func: (...args: unknown[]) => unknown;
      args?: unknown[];
      world?: string;
    }) {
      const contents = webContents.fromId(target.tabId);
      if (!contents || contents.isDestroyed()) throw new Error(`No window ${target.tabId}`);
      const source = `(${func.toString()})(...${JSON.stringify(args)})`;
      return [{ frameId: 0, result: await contents.executeJavaScript(source, true) }];
    },
  },
};

/** The lib code reads the browser's user agent (Spotify's and Discord's client descriptions). */
export function chromeUserAgent(): string {
  const platform =
    process.platform === 'win32'
      ? 'Windows NT 10.0; Win64; x64'
      : process.platform === 'darwin'
        ? 'Macintosh; Intel Mac OS X 10_15_7'
        : 'X11; Linux x86_64';
  const major = process.versions.chrome.split('.')[0];
  return `Mozilla/5.0 (${platform}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${major}.0.0.0 Safari/537.36`;
}

const globals = globalThis as Record<string, unknown>;
globals.browser = shim;
globals.chrome = shim;
globals.fetch = sessionFetch;
Object.defineProperty(globalThis, 'navigator', {
  configurable: true,
  value: { userAgent: chromeUserAgent(), language: 'en-US', languages: ['en-US', 'en'], platform: process.platform },
});
