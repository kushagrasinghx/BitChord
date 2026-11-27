import {
  BRIDGE_PROTOCOL,
  errorResponse,
  okResponse,
  type BridgeErrorCode,
  type BridgeEvent,
  type BridgeRequest,
  type BridgeResponse,
  type BrowserFamily,
  type Capability,
} from '@bitchord/shared/bridge';
import * as discord from './discord';
import { grantedOrigins, lyricsFetch, requestOrigins } from './lyricsFetch';
import * as spotify from './spotify';
import { authState, invalidateAccounts, isSessionCookie, selectChannel, setConnected } from './ytAuth';
import { ytFetch } from './ytFetch';

/**
 * The bridge's method table, apart from how messages arrive. The extension's
 * background serves it over runtime messaging; the desktop app (web/desktop)
 * serves the same table over Electron IPC, with its own `browser` underneath.
 */
export interface DispatchHost {
  /** What `ping` reports as the version. */
  version: string;
  browser: BrowserFamily;
  capabilities: Capability[];
  /** Pushes an event to every open page. */
  broadcast(event: BridgeEvent): void;
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

export function createDispatcher(host: DispatchHost): (request: BridgeRequest) => Promise<BridgeResponse> {
  const { broadcast } = host;

  return async function handle(request: BridgeRequest): Promise<BridgeResponse> {
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
            version: host.version,
            protocol: BRIDGE_PROTOCOL,
            browser: host.browser,
            capabilities: host.capabilities,
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
          if (!discord.AVAILABLE) return errorResponse(request.id, 'unsupported', 'This build leaves Discord out.');
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
  };
}

/**
 * A sign-in or sign-out elsewhere in the browser: re-reads the accounts and
 * tells the open pages. Signing in sets several cookies in a burst; this
 * answers the last one.
 */
export function createCookieWatcher(broadcast: (event: BridgeEvent) => void): (cookie: Browser.cookies.Cookie) => void {
  let cookieTimer: ReturnType<typeof setTimeout> | undefined;
  let spotifyCookieTimer: ReturnType<typeof setTimeout> | undefined;
  return (cookie) => {
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
    cookieTimer = setTimeout(() => {
      authState(true)
        .then((data) => broadcast({ event: 'yt.authState', data }))
        .catch((error: unknown) => console.warn('BitChord: reading the YouTube session failed', error));
    }, 1000);
  };
}
