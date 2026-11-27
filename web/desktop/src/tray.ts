import { app, Menu, nativeImage, powerMonitor, Tray, type BrowserWindow } from 'electron';
import { join } from 'node:path';
import { FileStorage } from './storage';

/**
 * Closing the window can keep BitChord running in the system tray (the menu
 * bar on macOS), still playing; the tray icon brings it back or quits. On by
 * default; the site's Settings page turns it off (web/app/src/desktop.ts).
 */

export interface DesktopSettings {
  closeToTray: boolean;
}

const DEFAULTS: DesktopSettings = { closeToTray: true };

let store: FileStorage | null = null;
function settingsStore(): FileStorage {
  store ??= new FileStorage(join(app.getPath('userData'), 'bitchord-desktop.json'));
  return store;
}

export async function desktopSettings(): Promise<DesktopSettings> {
  const stored = (await settingsStore().get('settings')).settings as Partial<DesktopSettings> | undefined;
  return { ...DEFAULTS, ...stored };
}

let current: DesktopSettings = { ...DEFAULTS };
/** The settings as last read, for the window's close handler (which can't wait). */
export const settingsNow = () => current;

export function flushDesktopSettings(): void {
  store?.flush();
}

// ---- The tray icon -----------------------------------------------------------

let tray: Tray | null = null;
let quitting = false;

/** Set once the app is really quitting, so the close handler lets the window go. */
export const isQuitting = () => quitting;
const letGo = () => {
  quitting = true;
};
app.on('before-quit', letGo);
// A hidden window that refused to close would hold up shutting down or logging off.
app.whenReady().then(() => powerMonitor.on('shutdown', letGo));
export const onSessionEnd = letGo;

/** tray.png, with tray@2x.png and tray@3x.png picked up for high-density screens. */
const trayImage = () => nativeImage.createFromPath(join(__dirname, '..', 'build', 'tray.png'));

function showWindow(window: BrowserWindow | null, create: () => void): void {
  if (!window || window.isDestroyed()) return create();
  if (window.isMinimized()) window.restore();
  window.show();
  window.focus();
}

function buildTray(getWindow: () => BrowserWindow | null, create: () => void): Tray {
  const icon = new Tray(trayImage());
  icon.setToolTip('BitChord');
  icon.setContextMenu(
    Menu.buildFromTemplate([
      { label: 'Show BitChord', click: () => showWindow(getWindow(), create) },
      { type: 'separator' },
      { label: 'Quit BitChord', click: () => app.quit() },
    ]),
  );
  // Windows and most Linux trays: a click brings the window back. (macOS opens the menu.)
  icon.on('click', () => showWindow(getWindow(), create));
  return icon;
}

function syncTray(getWindow: () => BrowserWindow | null, create: () => void): void {
  if (current.closeToTray && !tray) tray = buildTray(getWindow, create);
  if (!current.closeToTray && tray) {
    tray.destroy();
    tray = null;
  }
}

/**
 * Reads the settings, puts up the tray icon if they ask for it, and returns
 * the setter the page's Settings toggle calls.
 */
export async function setUpTray(
  getWindow: () => BrowserWindow | null,
  create: () => void,
): Promise<(change: Partial<DesktopSettings>) => Promise<DesktopSettings>> {
  current = await desktopSettings();
  syncTray(getWindow, create);
  return async (change) => {
    current = { ...current, ...(typeof change.closeToTray === 'boolean' && { closeToTray: change.closeToTray }) };
    await settingsStore().set({ settings: current });
    syncTray(getWindow, create);
    return current;
  };
}

/**
 * The window's close: hidden instead when the tray keeps the app running.
 * The first time, Windows says where it went.
 */
export function closeToTray(window: BrowserWindow, event: Electron.Event): void {
  if (quitting || !current.closeToTray || !tray) return;
  event.preventDefault();
  window.hide();
  void (async () => {
    const seen = (await settingsStore().get('trayHintShown')).trayHintShown === true;
    if (seen || process.platform !== 'win32' || !tray) return;
    tray.displayBalloon({
      iconType: 'info',
      title: 'BitChord is still running',
      content: 'Music keeps playing. Click the tray icon to bring BitChord back, or right-click it to quit.',
    });
    await settingsStore().set({ trayHintShown: true });
  })();
}
