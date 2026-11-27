// First: the `browser` global the extension's lib code expects.
import { chromeUserAgent, EXTENSION_ORIGIN, extensionStorage, setTabsHost } from './browserShim';

import { app, BrowserWindow, ipcMain, net, protocol, session, type IpcMainInvokeEvent } from 'electron';
import { existsSync, statSync } from 'node:fs';
import { join, normalize, sep } from 'node:path';
import { pathToFileURL } from 'node:url';
import { errorResponse, isBridgeRequest, type BridgeEvent, type Capability } from '@bitchord/shared/bridge';
import * as discord from '../../extension/lib/discord';
import { createCookieWatcher, createDispatcher } from '../../extension/lib/dispatch';
import { installRules } from '../../extension/lib/rules';
import { installEngine, type RequestContext } from './dnr';
import { browserWindows, closeBrowserWindow, openBrowserWindow, openExternally, opensInApp } from './windows';


/**
 * BitChord's desktop app: the web player (web/app's build) in a window, with
 * the companion extension's work done here instead of in a browser
 * extension. The page finds it as it finds the Firefox extension, through
 * window messages (preload.ts relays them over IPC), so the site's code is
 * the same.
 */

const APP_SCHEME = 'app';
const APP_ORIGIN = `${APP_SCHEME}://bitchord`;
const DEV_ORIGIN = 'http://localhost:5173';
const DEV = process.argv.includes('--dev');
const PAGE_ORIGIN = DEV ? DEV_ORIGIN : APP_ORIGIN;

const RENDERER_DIR = join(__dirname, '..', 'renderer');

protocol.registerSchemesAsPrivileged([
  {
    scheme: APP_SCHEME,
    privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true, stream: true, codeCache: true },
  },
]);

// Google's sign-in refuses browsers that call themselves Electron.
app.userAgentFallback = chromeUserAgent();

if (!app.requestSingleInstanceLock()) app.quit();

let mainWindow: BrowserWindow | null = null;

// ---- The site, from the app's own files ------------------------------------------

/** app://bitchord/<path> → renderer/<path>; anything that isn't a file is the SPA's index.html. */
function serveRenderer(): void {
  protocol.handle(APP_SCHEME, (request) => {
    const { pathname } = new URL(request.url);
    const relative = normalize(decodeURIComponent(pathname)).replace(/^[\\/]+/, '');
    let file = join(RENDERER_DIR, relative);
    if (!file.startsWith(RENDERER_DIR + sep) || !existsSync(file) || statSync(file).isDirectory()) {
      file = join(RENDERER_DIR, 'index.html');
    }
    return net.fetch(pathToFileURL(file).toString(), { bypassCustomProtocolHandlers: true });
  });
}

// ---- Header rules ----------------------------------------------------------------

const appHost = () => new URL(PAGE_ORIGIN).hostname;

/** `scheme://host[:port]`. Node's URL calls a custom scheme's origin "null", so it's built by hand. */
function originOf(url: string | undefined): string | null {
  try {
    const { protocol, host } = new URL(url ?? '');
    return `${protocol}//${host}`;
  } catch {
    return null;
  }
}

/** BitChord's Listen Together and presence API, on whichever server the user picked. */
const PARTY_PATH = /^\/(api\/(time|parties|presence|stats\/live)|ws\/parties)(\/|$)/;

function isPartyRequest(request: RequestContext): boolean {
  if (request.own || request.initiator !== appHost()) return false;
  try {
    return PARTY_PATH.test(new URL(request.url).pathname);
  } catch {
    return false;
  }
}

function deleteHeader(headers: Record<string, unknown>, name: string): void {
  for (const key of Object.keys(headers)) if (key.toLowerCase() === name) delete headers[key];
}

function installHeaderRules(): void {
  installEngine(session.defaultSession, {
    isAppContents: (id) => id !== undefined && mainWindow !== null && !mainWindow.isDestroyed() && mainWindow.webContents.id === id,
    appHost,
    // The party server accepts the browser origins it lists and native
    // clients, which send no Origin. The desktop app is the latter, as the
    // Android app is; its replies then get the CORS headers the page needs.
    extraRequestHeaders(request, headers) {
      if (isPartyRequest(request)) deleteHeader(headers, 'origin');
    },
    extraResponseHeaders(request, headers) {
      if (!isPartyRequest(request) || request.resourceType === 'webSocket') return;
      deleteHeader(headers, 'access-control-allow-origin');
      headers['Access-Control-Allow-Origin'] = ['*'];
      headers['Access-Control-Allow-Methods'] = ['GET, POST, OPTIONS'];
      headers['Access-Control-Allow-Headers'] = ['Content-Type, Authorization'];
    },
  });
  void installRules([APP_ORIGIN, DEV_ORIGIN]);
}

// ---- The bridge ------------------------------------------------------------------

const CAPABILITIES: Capability[] = ['yt', 'yt.auth', 'yt.stats', 'lyrics', 'spotify', ...(discord.AVAILABLE ? (['discord'] as const) : [])];

function broadcast(event: BridgeEvent): void {
  if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send('bitchord-bridge:event', event);
}

const dispatch = createDispatcher({
  version: __BRIDGE_VERSION__,
  browser: 'chromium',
  capabilities: CAPABILITIES,
  broadcast,
});

/** Only the app's own page, at its own origin, talks to the bridge. */
function fromPage(event: IpcMainInvokeEvent): boolean {
  if (!mainWindow || event.sender !== mainWindow.webContents) return false;
  return originOf(event.senderFrame?.url) === PAGE_ORIGIN;
}

function serveBridge(): void {
  ipcMain.handle('bitchord-bridge:call', async (event, message: unknown) => {
    if (!isBridgeRequest(message)) return null;
    if (!fromPage(event)) return errorResponse(message.id, 'forbidden', 'Only the BitChord page may use the bridge.');
    return dispatch(message).catch((error: unknown) => errorResponse(message.id, 'internal', String(error)));
  });

  const onCookie = createCookieWatcher(broadcast);
  browser.cookies.onChanged.addListener(({ cookie }) => onCookie(cookie));

  discord.onDiscordState((data) => broadcast({ event: 'discord.state', data }));
  extensionStorage().onChanged((changes) => {
    if (!Object.keys(changes).some((key) => key.startsWith('discord.'))) return;
    void discord.discordState().then((data) => broadcast({ event: 'discord.state', data }));
  });

  setTabsHost({
    async open(url) {
      return openBrowserWindow(url, mainWindow).webContents.id;
    },
    list: browserWindows,
    close: closeBrowserWindow,
    async openExtensionPage(path) {
      // The extension's connect page asks for discord.com and then reads the
      // session; a desktop app has nothing to ask, so it goes straight on.
      if (path.startsWith('/discord.html')) {
        discord.connectFromTab(() => {}).catch((error: unknown) => console.warn('BitChord: connecting Discord failed', error));
        return;
      }
      console.warn(`BitChord: no desktop page for ${EXTENSION_ORIGIN}${path}`);
    },
  });
}

// ---- The window ------------------------------------------------------------------

function createMainWindow(): void {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 760,
    minHeight: 560,
    title: 'BitChord',
    show: false,
    backgroundColor: '#000000',
    autoHideMenuBar: true,
    icon: join(__dirname, '..', 'build', 'icon.png'),
    webPreferences: {
      preload: join(__dirname, 'preload.cjs'),
      sandbox: true,
      contextIsolation: true,
      nodeIntegration: false,
      // Playback keeps going, and its timers keep their pace, while minimised.
      backgroundThrottling: false,
      autoplayPolicy: 'no-user-gesture-required',
    },
  });

  mainWindow.once('ready-to-show', () => mainWindow?.show());

  // Sign-ins open in the app's own browser; every other link in the system's.
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (opensInApp(url)) openBrowserWindow(url, mainWindow);
    else openExternally(url);
    return { action: 'deny' };
  });
  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (originOf(url) === PAGE_ORIGIN) return;
    event.preventDefault();
    if (opensInApp(url)) openBrowserWindow(url, mainWindow);
    else openExternally(url);
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
    discord.allPagesClosed();
    // Sign-in windows don't outlive the player (macOS keeps the app itself running).
    for (const { id } of browserWindows()) closeBrowserWindow(id);
  });

  void mainWindow.loadURL(`${PAGE_ORIGIN}/`);
  if (DEV) mainWindow.webContents.openDevTools({ mode: 'detach' });
}

app.on('second-instance', () => {
  if (!mainWindow) return createMainWindow();
  if (mainWindow.isMinimized()) mainWindow.restore();
  mainWindow.focus();
});

app.whenReady().then(() => {
  serveRenderer();
  installHeaderRules();
  serveBridge();
  createMainWindow();
  app.on('activate', () => {
    if (!mainWindow) createMainWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

app.on('before-quit', () => extensionStorage().flush());
