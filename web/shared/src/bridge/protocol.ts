/**
 * The page ↔ extension contract (build prompt §4.2).
 *
 * Every call is a typed request/response pair inside a versioned envelope.
 * Credentials never cross this boundary: the extension sends results only,
 * never cookies or tokens.
 */

/** Bumped on any breaking change to an envelope or a method's shape. */
export const BRIDGE_PROTOCOL = 1;

/** Tags every window message so the relay ignores everything else on the page. */
export const BRIDGE_CHANNEL = 'bitchord-bridge';

/** The long-lived port the extension pushes `BridgeEvent`s on. */
export const EVENTS_PORT = 'bitchord-events';

/** What the installed extension can do; `ping` reports it. */
export type Capability = 'yt' | 'yt.auth' | 'yt.stats' | 'addon' | 'lyrics' | 'spotify' | 'discord';

export type BrowserFamily = 'chromium' | 'firefox';

/** A plain HTTP exchange, serialisable across the message channel. */
export interface HttpRequest {
  url: string;
  method?: 'GET' | 'POST' | 'HEAD';
  headers?: Record<string, string>;
  /** Text bodies as-is; binary bodies base64-encoded with `bodyEncoding: 'base64'`. */
  body?: string;
  bodyEncoding?: 'text' | 'base64';
}

export interface HttpResponse {
  status: number;
  headers: Record<string, string>;
  /** UTF-8 text, or base64 when `bodyEncoding` is `'base64'`. */
  body: string;
  bodyEncoding: 'text' | 'base64';
  /** The final URL after redirects. */
  url: string;
}

/** One identity requests can act as: a Google login's own channel, or one of its brand channels. */
export interface YtChannel {
  /** Opaque and stable: what `yt.selectChannel` takes. */
  id: string;
  name: string;
  /** The @handle or byline YouTube shows under the name. */
  handle: string | null;
  avatarUrl: string | null;
  /** A brand channel (sent as `X-Goog-PageId`) rather than the login's own. */
  brand: boolean;
}

/** A Google login in the browser's cookie jar, as `X-Goog-AuthUser` addresses it. */
export interface YtAccount {
  authUser: string;
  name: string;
  email: string | null;
  avatarUrl: string | null;
  channels: YtChannel[];
}

/**
 * Who signed-in YouTube requests act as. Names and pictures only: the
 * cookies and the signature never leave the extension.
 */
export interface YtAuthState {
  /** The browser holds a YouTube session (a SAPISID cookie). */
  browserSignedIn: boolean;
  /** The user lets BitChord use it; "Disconnect" turns this off without signing out of Google. */
  connected: boolean;
  /** True when signed-in requests will be made: signed in, connected, and a channel chosen. */
  signedIn: boolean;
  accounts: YtAccount[];
  /** The channel in use, from `accounts`; null when not signed in. */
  active: (YtChannel & { authUser: string }) | null;
  /** Signed in to the browser but no account could be read: what YouTube answered, for display. */
  problem: string | null;
}

/** The browser's open.spotify.com session, as BitChord may use it (prompt §7.1). Names only, never tokens. */
export interface SpotifyAuthState {
  /** The browser holds a Spotify session (an `sp_dc` cookie). */
  browserSignedIn: boolean;
  /** "Disconnect" turns this off without signing out of Spotify. */
  connected: boolean;
  /** A token was minted for the account: library, playlists and canvas will work. */
  signedIn: boolean;
  user: { name: string; avatarUrl: string | null } | null;
  /** Signed in to the browser but no token came back: what went wrong, for display. */
  problem: string | null;
}

/** A playlist in the Spotify library; `id` is `liked` for Liked Songs. */
export interface SpotifyPlaylist {
  id: string;
  name: string;
  owner: string | null;
  imageUrl: string | null;
}

export interface SpotifyTrack {
  /** The Spotify track id (base-62). */
  id: string;
  title: string;
  /** Credited artists, comma-separated. */
  artist: string;
  album: string | null;
  durationMs: number;
  imageUrl: string | null;
}

/** One page of a Spotify list: `raw` counts the entries before filtering, which is what advances the offset. */
export interface SpotifyPage<T> {
  items: T[];
  total: number;
  raw: number;
}

/** Discord rich presence (prompt §7.2), when the build includes it. */
export interface DiscordState {
  /** This build can publish a presence at all (the DISCORD_PRESENCE flag). */
  available: boolean;
  /** An account is connected (its token is held by the extension). */
  connected: boolean;
  /** The user turned presence on. */
  enabled: boolean;
  user: { name: string; username: string; avatarUrl: string | null } | null;
  gateway: 'off' | 'connecting' | 'ready' | 'error';
  problem: string | null;
}

/** What the card shows, and how (DiscordRPC.updateSong's options). */
export interface DiscordPresenceOptions {
  activityType: 'listening' | 'playing' | 'watching' | 'competing';
  /** Discord status while listening. */
  status: 'online' | 'idle' | 'dnd' | 'invisible';
  /** Lead with the song title rather than the artist in member lists. */
  useDetails: boolean;
  /** Replaces "BitChord" after "Listening to"; empty keeps it. */
  activityName: string;
  button1Text: string;
  button1Visible: boolean;
  button2Text: string;
  button2Visible: boolean;
}

/** The track being played, as the page reports it. */
export interface DiscordNowPlaying {
  videoId: string;
  title: string;
  artist: string;
  album: string | null;
  artworkUrl: string | null;
  positionMs: number;
  durationMs: number;
  playbackRate: number;
}

/** Pushed by the extension over the event channel (`Bridge.subscribe`). */
export type BridgeEvent =
  | { event: 'yt.authState'; data: YtAuthState }
  | { event: 'spotify.authState'; data: SpotifyAuthState }
  | { event: 'discord.state'; data: DiscordState };

/** A signed-in request is the same exchange with `auth` set; the extension adds the session. */
export interface YtFetchParams extends HttpRequest {
  /** Send it as the chosen channel (cookies, SAPISIDHASH, X-Goog-AuthUser/PageId). */
  auth?: boolean;
}

/**
 * Method name → params and result. `ping` and the `yt.*` methods are served
 * (phase 5); the rest are declared so both sides type-check against one
 * contract as they land.
 */
export interface BridgeMethods {
  ping: {
    params: Record<string, never>;
    result: {
      /** The extension's own version, from its manifest. */
      version: string;
      protocol: number;
      browser: BrowserFamily;
      capabilities: Capability[];
    };
  };
  'yt.fetch': { params: YtFetchParams; result: HttpResponse };
  /** `refresh` re-reads the accounts from YouTube instead of the extension's short cache. */
  'yt.authState': { params: { refresh?: boolean }; result: YtAuthState };
  'yt.selectChannel': { params: { channelId: string }; result: YtAuthState };
  'yt.setConnected': { params: { connected: boolean }; result: YtAuthState };
  'addon.fetch': { params: HttpRequest; result: HttpResponse };
  /** A lyric provider the page can't reach itself; refused with `permission` until its host is granted. */
  'lyrics.fetch': { params: HttpRequest; result: HttpResponse };
  /** Which of these lyric origins the user has granted. */
  'lyrics.permissions': { params: { origins: string[] }; result: { granted: string[] } };
  /** Opens the extension's page that asks for them (a browser prompt needs a click there). */
  'lyrics.requestPermissions': { params: { origins: string[] }; result: Record<string, never> };
  /** `refresh` drops the cached tokens and asks Spotify again. */
  'spotify.authState': { params: { refresh?: boolean }; result: SpotifyAuthState };
  'spotify.setConnected': { params: { connected: boolean }; result: SpotifyAuthState };
  /** Opens Spotify's sign-in in a tab; the state event follows once `sp_dc` arrives. */
  'spotify.signIn': { params: Record<string, never>; result: Record<string, never> };
  /** The library's playlists, 50 at a time; Liked Songs leads the first page. */
  'spotify.playlists': { params: { offset: number }; result: SpotifyPage<SpotifyPlaylist> };
  /** A playlist's (or Liked Songs') tracks, 100 at a time. */
  'spotify.tracks': { params: { playlistId: string; offset: number }; result: SpotifyPage<SpotifyTrack> };
  'spotify.playlist': {
    params: { playlistId: string };
    result: { name: string; owner: string | null; imageUrl: string | null; total: number };
  };
  'spotify.searchTracks': { params: { query: string }; result: SpotifyTrack[] };
  /** The track's looping canvas video, or null when it has none. */
  'spotify.canvas': { params: { trackId: string }; result: { url: string | null } };
  'discord.state': { params: Record<string, never>; result: DiscordState };
  /** Opens the extension's page that connects an account (asks for discord.com, reads the token there). */
  'discord.connect': { params: Record<string, never>; result: Record<string, never> };
  /** Forgets the account and closes the gateway. */
  'discord.disconnect': { params: Record<string, never>; result: DiscordState };
  'discord.setEnabled': { params: { enabled: boolean }; result: DiscordState };
  /** Publishes the playing track; null clears the card (a pause, or the player emptied). */
  'discord.presence': {
    params: { track: DiscordNowPlaying | null; options: DiscordPresenceOptions };
    result: Record<string, never>;
  };
}

export type BridgeMethod = keyof BridgeMethods;
export type ParamsOf<M extends BridgeMethod> = BridgeMethods[M]['params'];
export type ResultOf<M extends BridgeMethod> = BridgeMethods[M]['result'];

export interface BridgeRequest<M extends BridgeMethod = BridgeMethod> {
  channel: typeof BRIDGE_CHANNEL;
  protocol: number;
  direction: 'to-extension';
  id: string;
  method: M;
  params: ParamsOf<M>;
}

export type BridgeErrorCode =
  | 'unsupported' // the method exists in the contract but this extension can't serve it
  | 'forbidden' // the caller's origin isn't allowed, or a host permission is missing
  | 'protocol' // envelope version mismatch
  | 'network'
  | 'unauthenticated' // a signed-in call while not signed in (or disconnected)
  | 'permission' // a lyric host the user hasn't granted yet
  | 'internal';

export interface BridgeError {
  code: BridgeErrorCode;
  message: string;
}

export type BridgeResponse<M extends BridgeMethod = BridgeMethod> = {
  channel: typeof BRIDGE_CHANNEL;
  protocol: number;
  direction: 'to-page';
  id: string;
} & ({ ok: true; result: ResultOf<M> } | { ok: false; error: BridgeError });

export function isBridgeRequest(value: unknown): value is BridgeRequest {
  const v = value as Partial<BridgeRequest> | null;
  return (
    typeof v === 'object' &&
    v !== null &&
    v.channel === BRIDGE_CHANNEL &&
    v.direction === 'to-extension' &&
    typeof v.id === 'string' &&
    typeof v.method === 'string'
  );
}

/** An event as the Firefox relay forwards it to the page. */
export interface BridgeEventMessage {
  channel: typeof BRIDGE_CHANNEL;
  direction: 'event';
  payload: BridgeEvent;
}

export function isBridgeEventMessage(value: unknown): value is BridgeEventMessage {
  const v = value as Partial<BridgeEventMessage> | null;
  return typeof v === 'object' && v !== null && v.channel === BRIDGE_CHANNEL && v.direction === 'event' && !!v.payload;
}

export function isBridgeResponse(value: unknown): value is BridgeResponse {
  const v = value as Partial<BridgeResponse> | null;
  return (
    typeof v === 'object' &&
    v !== null &&
    v.channel === BRIDGE_CHANNEL &&
    v.direction === 'to-page' &&
    typeof v.id === 'string' &&
    typeof v.ok === 'boolean'
  );
}

export function okResponse<M extends BridgeMethod>(id: string, result: ResultOf<M>): BridgeResponse<M> {
  return { channel: BRIDGE_CHANNEL, protocol: BRIDGE_PROTOCOL, direction: 'to-page', id, ok: true, result };
}

export function errorResponse(id: string, code: BridgeErrorCode, message: string): BridgeResponse {
  return { channel: BRIDGE_CHANNEL, protocol: BRIDGE_PROTOCOL, direction: 'to-page', id, ok: false, error: { code, message } };
}
