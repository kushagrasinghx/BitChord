import {
  BRIDGE_PROTOCOL,
  errorResponse,
  EVENTS_PORT,
  isBridgeRequest,
  okResponse,
  type BridgeErrorCode,
  type BridgeEvent,
  type BridgeRequest,
  type BridgeResponse,
  type Capability,
} from '@bitchord/shared/bridge';
import * as discord from '../lib/discord';
import { grantedOrigins, lyricsFetch, requestOrigins } from '../lib/lyricsFetch';
import { installRules } from '../lib/rules';
import { authState, invalidateAccounts, isSessionCookie, selectChannel, setConnected } from '../lib/ytAuth';
import * as spotify from '../lib/spotify';
import { ytFetch } from '../lib/ytFetch';

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

function failure(id: string, error: unknown): BridgeResponse {
  const known = (error as { code?: string }).code;
  const code: BridgeErrorCode =
    error instanceof spotify.SpotifyAuthError
      ? 'unauthenticated'
      : known === 'forbidden' || known === 'unauthenticated' || known === 'permission'
      ? known
      : error instanceof TypeError
        ? 'network'
        : 'internal';
  return errorResponse(id, code, error instanceof Error ? error.message : String(error));
}

async function handle(request: BridgeRequest): Promise<BridgeResponse> {
  if (request.protocol !== BRIDGE_PROTOCOL) {
    return errorResponse(
      request.id,
      'protocol',
      `The page speaks bridge protocol ${request.protocol}; this extension speaks ${BRIDGE_PROTOCOL}. Update whichever is older.`,
    );
  }
  try {
    switch (request.method) {
      case 'ping':
        return okResponse<'ping'>(request.id, {
          version: browser.runtime.getManifest().version,
          protocol: BRIDGE_PROTOCOL,
          browser: import.meta.env.FIREFOX ? 'firefox' : 'chromium',
          capabilities: CAPABILITIES,
        });
      case 'yt.fetch':
        return okResponse<'yt.fetch'>(request.id, await ytFetch((request as BridgeRequest<'yt.fetch'>).params));
      case 'yt.authState':
        return okResponse<'yt.authState'>(
          request.id,
          await authState((request as BridgeRequest<'yt.authState'>).params.refresh === true),
        );
      case 'yt.selectChannel': {
        const state = await selectChannel((request as BridgeRequest<'yt.selectChannel'>).params.channelId);
        broadcast({ event: 'yt.authState', data: state });
        return okResponse<'yt.selectChannel'>(request.id, state);
      }
      case 'yt.setConnected': {
        const state = await setConnected((request as BridgeRequest<'yt.setConnected'>).params.connected === true);
        broadcast({ event: 'yt.authState', data: state });
        return okResponse<'yt.setConnected'>(request.id, state);
      }
      case 'lyrics.fetch':
        return okResponse<'lyrics.fetch'>(request.id, await lyricsFetch((request as BridgeRequest<'lyrics.fetch'>).params));
      case 'lyrics.permissions':
        return okResponse<'lyrics.permissions'>(request.id, {
          granted: await grantedOrigins((request as BridgeRequest<'lyrics.permissions'>).params.origins ?? []),
        });
      case 'lyrics.requestPermissions':
        await requestOrigins((request as BridgeRequest<'lyrics.requestPermissions'>).params.origins ?? []);
        return okResponse<'lyrics.requestPermissions'>(request.id, {});
      case 'spotify.authState': {
        if ((request as BridgeRequest<'spotify.authState'>).params.refresh) spotify.invalidateSession();
        return okResponse<'spotify.authState'>(request.id, await spotify.authState());
      }
      case 'spotify.setConnected': {
        const state = await spotify.setConnected((request as BridgeRequest<'spotify.setConnected'>).params.connected === true);
        broadcast({ event: 'spotify.authState', data: state });
        return okResponse<'spotify.setConnected'>(request.id, state);
      }
      case 'spotify.signIn':
        await browser.tabs.create({ url: 'https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F' });
        return okResponse<'spotify.signIn'>(request.id, {});
      case 'spotify.playlists': {
        const { offset } = (request as BridgeRequest<'spotify.playlists'>).params;
        return okResponse<'spotify.playlists'>(request.id, await spotify.playlists(offset ?? 0));
      }
      case 'spotify.tracks': {
        const { playlistId, offset } = (request as BridgeRequest<'spotify.tracks'>).params;
        return okResponse<'spotify.tracks'>(request.id, await spotify.tracks(playlistId, offset ?? 0));
      }
      case 'spotify.playlist':
        return okResponse<'spotify.playlist'>(
          request.id,
          await spotify.playlistInfo((request as BridgeRequest<'spotify.playlist'>).params.playlistId),
        );
      case 'spotify.searchTracks':
        return okResponse<'spotify.searchTracks'>(
          request.id,
          await spotify.searchTracks((request as BridgeRequest<'spotify.searchTracks'>).params.query),
        );
      case 'spotify.canvas':
        return okResponse<'spotify.canvas'>(request.id, {
          url: await spotify.canvas((request as BridgeRequest<'spotify.canvas'>).params.trackId),
        });
      case 'discord.state':
        return okResponse<'discord.state'>(request.id, await discord.discordState());
      case 'discord.connect':
        if (!discord.AVAILABLE) return errorResponse(request.id, 'unsupported', 'This build of the extension leaves Discord out.');
        await discord.openConnectPage();
        return okResponse<'discord.connect'>(request.id, {});
      case 'discord.disconnect':
        return okResponse<'discord.disconnect'>(request.id, await discord.disconnect());
      case 'discord.setEnabled':
        return okResponse<'discord.setEnabled'>(
          request.id,
          await discord.setEnabled((request as BridgeRequest<'discord.setEnabled'>).params.enabled === true),
        );
      case 'discord.presence': {
        const { track, options } = (request as BridgeRequest<'discord.presence'>).params;
        await discord.setPresence(track, options);
        return okResponse<'discord.presence'>(request.id, {});
      }
      default:
        return errorResponse(request.id, 'unsupported', `${request.method} isn't available in this version of the extension.`);
    }
  } catch (error) {
    return failure(request.id, error);
  }
}

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
let cookieTimer: ReturnType<typeof setTimeout> | undefined;
let spotifyCookieTimer: ReturnType<typeof setTimeout> | undefined;
function onCookieChanged({ cookie }: Browser.cookies.CookieChangeInfo): void {
  if (spotify.isSessionCookie(cookie)) {
    spotify.invalidateSession();
    clearTimeout(spotifyCookieTimer);
    spotifyCookieTimer = setTimeout(() => {
      spotify
        .authState()
        .then((data) => broadcast({ event: 'spotify.authState', data }))
        .catch((error: unknown) => console.warn('BitChord: reading the Spotify session failed', error));
    }, 1000);
    return;
  }
  if (!isSessionCookie(cookie)) return;
  invalidateAccounts();
  clearTimeout(cookieTimer);
  // Signing in sets several cookies in a burst; answer the last one.
  cookieTimer = setTimeout(() => {
    authState(true)
      .then((data) => broadcast({ event: 'yt.authState', data }))
      .catch((error: unknown) => console.warn('BitChord: reading the YouTube session failed', error));
  }, 1000);
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
