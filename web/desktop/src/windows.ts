import { BrowserWindow, session, shell } from 'electron';

/**
 * The app's built-in browser: sign-in windows for Google (YouTube Music),
 * Spotify and Discord. They share the app's session, so the cookies a
 * sign-in leaves behind are the ones the bridge reads (browserShim.ts), and
 * they never reach the page.
 */

/** Hosts a sign-in opens inside the app; every other link goes to the system browser. */
const IN_APP_HOSTS = [
  /(^|\.)google\.com$/,
  /(^|\.)youtube\.com$/,
  /(^|\.)gstatic\.com$/,
  /^accounts\.spotify\.com$/,
  /^open\.spotify\.com$/,
  /^(www\.)?spotify\.com$/,
  /^(www\.)?discord\.com$/,
];

export function opensInApp(url: string): boolean {
  try {
    const { protocol, hostname } = new URL(url);
    return protocol === 'https:' && IN_APP_HOSTS.some((host) => host.test(hostname));
  } catch {
    return false;
  }
}

export function openExternally(url: string): void {
  try {
    const { protocol } = new URL(url);
    if (protocol === 'https:' || protocol === 'http:' || protocol === 'mailto:') void shell.openExternal(url);
  } catch {
    // Not a URL: nothing to open.
  }
}

/**
 * When a sign-in has finished: the window lands back on the service with its
 * session cookie set, and closes itself.
 */
const FINISHED: { start: RegExp; landed: RegExp; cookie: { url: string; name: string } }[] = [
  {
    start: /^https:\/\/accounts\.google\.com\//,
    landed: /^https:\/\/(music|www)\.youtube\.com\//,
    cookie: { url: 'https://music.youtube.com/', name: 'SAPISID' },
  },
  {
    start: /^https:\/\/accounts\.spotify\.com\//,
    landed: /^https:\/\/open\.spotify\.com\//,
    cookie: { url: 'https://open.spotify.com/', name: 'sp_dc' },
  },
];

const windows = new Map<number, BrowserWindow>();

export function openBrowserWindow(url: string, parent?: BrowserWindow | null): BrowserWindow {
  const win = new BrowserWindow({
    width: 520,
    height: 760,
    title: 'BitChord',
    autoHideMenuBar: true,
    ...(parent && !parent.isDestroyed() ? { parent } : {}),
    webPreferences: {
      session: session.defaultSession,
      sandbox: true,
      contextIsolation: true,
      nodeIntegration: false,
    },
  });
  const id = win.webContents.id;
  windows.set(id, win);
  win.on('closed', () => windows.delete(id));

  // Popups inside a sign-in (Google's account chooser, "Continue with…") stay in this window.
  win.webContents.setWindowOpenHandler(({ url: next }) => {
    if (opensInApp(next)) void win.loadURL(next);
    else openExternally(next);
    return { action: 'deny' };
  });

  const finish = FINISHED.find((f) => f.start.test(url));
  if (finish) {
    win.webContents.on('did-navigate', async (_event, landedOn) => {
      if (!finish.landed.test(landedOn)) return;
      const [cookie] = await session.defaultSession.cookies.get(finish.cookie).catch(() => []);
      if (cookie && !win.isDestroyed()) win.close();
    });
  }

  void win.loadURL(url);
  return win;
}

export function browserWindows(): { id: number; url: string }[] {
  return [...windows.entries()]
    .filter(([, win]) => !win.isDestroyed())
    .map(([id, win]) => ({ id, url: win.webContents.getURL() }));
}

export function closeBrowserWindow(id: number): void {
  const win = windows.get(id);
  if (win && !win.isDestroyed()) win.close();
}
