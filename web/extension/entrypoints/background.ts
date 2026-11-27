import {
  errorResponse,
  isBridgeRequest,
  EVENTS_PORT,
  type BridgeEvent,
  type BridgeResponse,
  type Capability,
} from '@bitchord/shared/bridge';
import * as discord from '../lib/discord';
import { createCookieWatcher, createDispatcher } from '../lib/dispatch';
import { installRules } from '../lib/rules';

declare const __SITE_ORIGINS__: string[];

/** Exact origins (port included) allowed to talk to the extension: the site's, and the popup's. */
const ALLOWED_ORIGINS = new Set(__SITE_ORIGINS__);
const OWN_ORIGIN = new URL(browser.runtime.getURL('/')).origin;

/** Grows as each phase lands its methods; Discord only in builds that include it. */
const CAPABILITIES: Capability[] = ['yt', 'yt.auth', 'yt.stats', 'lyrics', 'spotify', ...(discord.AVAILABLE ? (['discord'] as const) : [])];

function originOf(sender: Browser.runtime.MessageSender): string | null {
  if (sender.origin) return sender.origin;
  try {
    return sender.url ? new URL(sender.url).origin : null;
  } catch {
    return null;
  }
}

function allowed(sender: Browser.runtime.MessageSender): boolean {
  const origin = originOf(sender);
  return !!origin && (ALLOWED_ORIGINS.has(origin) || (origin === OWN_ORIGIN && sender.id === browser.runtime.id));
}

const handle = createDispatcher({
  version: browser.runtime.getManifest().version,
  browser: import.meta.env.FIREFOX ? 'firefox' : 'chromium',
  capabilities: CAPABILITIES,
  broadcast: (event) => broadcast(event),
});

/**
 * One entry point for both transports: validates the envelope and the
 * sender's origin, then answers through `sendResponse`.
 */
function listener(
  message: unknown,
  sender: Browser.runtime.MessageSender,
  sendResponse: (response: BridgeResponse) => void,
): boolean {
  if (!isBridgeRequest(message)) return false;
  if (!allowed(sender)) {
    sendResponse(errorResponse(message.id, 'forbidden', `Origin ${originOf(sender) ?? 'unknown'} isn't allowed.`));
    return false;
  }
  handle(message)
    .catch((error: unknown) => errorResponse(message.id, 'internal', String(error)))
    .then(sendResponse);
  return true; // answering asynchronously
}

// ---- Event ports (Bridge.subscribe) -------------------------------------------

const ports = new Set<Browser.runtime.Port>();

function broadcast(event: BridgeEvent): void {
  for (const port of ports) {
    try {
      port.postMessage(event);
    } catch {
      ports.delete(port);
    }
  }
}

function acceptPort(port: Browser.runtime.Port): void {
  if (port.name !== EVENTS_PORT || !port.sender || !allowed(port.sender)) {
    port.disconnect();
    return;
  }
  ports.add(port);
  port.onDisconnect.addListener(() => {
    ports.delete(port);
    // A hidden tab's port can drop and come back; only a lasting silence means the player is gone.
    clearTimeout(lastPortTimer);
    if (!ports.size) lastPortTimer = setTimeout(() => !ports.size && discord.allPagesClosed(), 30_000);
  });
}

let lastPortTimer: ReturnType<typeof setTimeout> | undefined;

/** A sign-in or sign-out elsewhere in the browser: re-read the accounts and tell the open pages. */
const onCookie = createCookieWatcher(broadcast);
function onCookieChanged({ cookie }: Browser.cookies.CookieChangeInfo): void {
  onCookie(cookie);
}

export default defineBackground(() => {
  // Dynamic rules persist, but are rewritten on every install and startup so
  // an update that changes them never leaves stale ones behind.
  const refreshRules = () =>
    installRules(__SITE_ORIGINS__).catch((error: unknown) => console.error('BitChord: installing rules failed', error));
  browser.runtime.onInstalled.addListener(refreshRules);
  browser.runtime.onStartup.addListener(refreshRules);

  // Chromium: the page itself, through externally_connectable.
  browser.runtime.onMessageExternal?.addListener(listener);
  browser.runtime.onConnectExternal?.addListener(acceptPort);

  // Firefox: the relay content script on the site's tab, and the popup in
  // both. Only the extension's own scripts can reach onMessage; the sender's
  // URL is the page's origin (or the popup's).
  browser.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (sender.id !== browser.runtime.id) return false;
    return listener(message, sender, sendResponse);
  });
  browser.runtime.onConnect.addListener((port) => {
    if (port.sender?.id !== browser.runtime.id) return port.disconnect();
    acceptPort(port);
  });

  browser.cookies.onChanged.addListener(onCookieChanged);

  // Discord's account is written by the connect page as well as here.
  discord.onDiscordState((data) => broadcast({ event: 'discord.state', data }));
  browser.storage.onChanged.addListener((changes, area) => {
    if (area !== 'local' || !Object.keys(changes).some((key) => key.startsWith('discord.'))) return;
    void discord.discordState().then((data) => broadcast({ event: 'discord.state', data }));
  });
});
