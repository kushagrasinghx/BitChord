import type { DiscordNowPlaying, DiscordPresenceOptions, DiscordState } from '@bitchord/shared/bridge';
import { installDiscordFetchRule } from './rules';

declare const __DISCORD_PRESENCE__: boolean;

/**
 * Discord rich presence (prompt §7.2), behind the DISCORD_PRESENCE build flag.
 *
 * This is user-token automation, as on the phone and the desktop: there is no
 * API for a third-party app to set a user's presence, so the gateway is opened
 * with the account's own token, read from a discord.com tab only when the user
 * clicks "Connect Discord". The token is kept in the extension's storage and
 * never sent to the page.
 *
 * Ports, with what changed:
 * - Gateway: com.my.kizzy.gateway.DiscordWebSocket. The same loop (connect,
 *   pump, back off 1 s doubling to 60 s), the heartbeat watchdog that drops a
 *   socket whose heartbeat went unanswered, and the replay of the last
 *   presence onto a new session. It identifies as Discord's web client, not
 *   as Android or the desktop app: the socket comes from a browser and carries
 *   the browser's own User-Agent, which an extension can't change.
 * - Activity: DiscordRPC.updateSong (timestamps with the rate divided out,
 *   the rate tag, buttons with {song_name} variables, external-asset artwork).
 * - An MV3 worker sleeps after 30 s without events. While the socket is up, a
 *   ping every 20 s keeps it awake (an extension API call resets the idle
 *   timer in Chrome and Firefox; Chrome 116+ also counts socket traffic).
 */

/** BitChord's own Discord application (DiscordApplication.kt). */
export const APPLICATION_ID = '1543679166967193620';
const GATEWAY = 'wss://gateway.discord.gg/?v=9&encoding=json';
const API = 'https://discord.com/api/v9';
const PROJECT_URL = 'https://github.com/kushagrasinghx/BitChord';
const DEFAULT_BUTTON_1 = 'Listen on YouTube Music';
const DEFAULT_BUTTON_2 = 'Visit BitChord';
const APP_NAME = 'BitChord';

const INITIAL_RECONNECT_MS = 1_000;
const MAX_RECONNECT_MS = 60_000;
const STALE_GRACE_MS = 15_000;
const SESSION_WAIT_MS = 20_000;
const KEEPALIVE_MS = 20_000;
/** A cleared card with nothing new for this long: the socket goes too. */
const IDLE_CLOSE_MS = 5 * 60_000;

const ACCOUNT_KEY = 'discord.account';
const ENABLED_KEY = 'discord.enabled';

interface Account {
  token: string;
  user: { id: string; name: string; username: string; avatarUrl: string | null };
}

// ---- Identity ------------------------------------------------------------------

function browserName(): string {
  return import.meta.env.FIREFOX ? 'Firefox' : 'Chrome';
}

function osName(): string {
  const ua = navigator.userAgent;
  return /Windows/.test(ua) ? 'Windows' : /Mac OS/.test(ua) ? 'Mac OS X' : /Android/.test(ua) ? 'Android' : 'Linux';
}

/** Built once per worker: a real client never rotates these per request (SuperProperties.kt). */
let superProperties: string | null = null;
function superPropertiesBase64(): string {
  superProperties ??= btoa(
    JSON.stringify({
      os: osName(),
      browser: browserName(),
      device: '',
      system_locale: navigator.language,
      browser_user_agent: navigator.userAgent,
      browser_version: navigator.userAgent.match(/(?:Chrome|Firefox)\/([\d.]+)/)?.[1] ?? '',
      os_version: '',
      referrer: '',
      referring_domain: '',
      release_channel: 'stable',
      client_build_number: 352675,
      client_event_source: null,
    }),
  );
  return superProperties;
}

async function api<T>(token: string, path: string, init: RequestInit = {}): Promise<T> {
  await installDiscordFetchRule();
  const response = await fetch(`${API}${path}`, {
    ...init,
    headers: {
      Authorization: token,
      'X-Super-Properties': superPropertiesBase64(),
      ...(init.body && { 'Content-Type': 'application/json' }),
      ...init.headers,
    },
  });
  if (response.status === 401) throw Object.assign(new Error('Discord refused the token'), { code: 'unauthenticated' });
  if (!response.ok) throw new Error(`Discord ${path} failed (${response.status})`);
  return (await response.json()) as T;
}

/** KizzyRPC.getUserInfo. */
async function userInfo(token: string): Promise<Account['user']> {
  const me = await api<{ id: string; username: string; global_name?: string | null; avatar?: string | null }>(token, '/users/@me');
  return {
    id: me.id,
    username: me.username,
    name: me.global_name || me.username,
    avatarUrl: me.avatar ? `https://cdn.discordapp.com/avatars/${me.id}/${me.avatar}.png` : null,
  };
}

/** ExternalAssets.kt: Discord's CDN mirror of an http image, as an `mp:` asset; cached (ArtworkCache). */
const artwork = new Map<string, string | null>();
async function externalAsset(token: string, imageUrl: string): Promise<string | null> {
  if (artwork.has(imageUrl)) return artwork.get(imageUrl)!;
  try {
    const list = await api<{ external_asset_path?: string }[]>(token, `/applications/${APPLICATION_ID}/external-assets`, {
      method: 'POST',
      body: JSON.stringify({ urls: [imageUrl] }),
    });
    const path = list[0]?.external_asset_path;
    const asset = path ? `mp:${path}` : null;
    artwork.set(imageUrl, asset);
    if (artwork.size > 50) artwork.delete(artwork.keys().next().value!);
    return asset;
  } catch {
    return null;
  }
}

// ---- The gateway (DiscordWebSocket.kt) ---------------------------------------------

type Presence = { activities: unknown[]; afk: boolean; since: number; status: string };

interface Payload {
  op: number;
  d?: unknown;
  s?: number | null;
  t?: string | null;
}

const OP = { DISPATCH: 0, HEARTBEAT: 1, IDENTIFY: 2, PRESENCE_UPDATE: 3, RESUME: 6, RECONNECT: 7, INVALID_SESSION: 9, HELLO: 10, HEARTBEAT_ACK: 11 };

export class Gateway {
  private socket: WebSocket | null = null;
  private sequence = 0;
  private sessionId: string | null = null;
  private resumeUrl: string | null = null;
  private heartbeatInterval = 0;
  private heartbeatTimer: ReturnType<typeof setTimeout> | undefined;
  private keepAlive: ReturnType<typeof setInterval> | undefined;
  private awaitingAck = false;
  private lastAckAt = 0;
  private immediateRetry = false;
  private backoff = INITIAL_RECONNECT_MS;
  private retryTimer: ReturnType<typeof setTimeout> | undefined;
  private looping = false;
  private closed = false;
  private ready = false;
  private readyWaiters: (() => void)[] = [];
  /** Replayed onto every new session: Discord discards a presence with the session that set it. */
  private lastPresence: Presence | null = null;
  /** What this session already has, so the replay and a push don't both send it. */
  private delivered: string | null = null;

  constructor(
    private readonly token: string,
    private readonly onStatus: (status: DiscordState['gateway'], problem?: string) => void,
    /** Discord's own gateway; a test points this at a local one. */
    private readonly url = GATEWAY,
  ) {}

  connect(): void {
    if (this.closed || this.looping) return;
    this.looping = true;
    this.open();
  }

  /** Cuts a climbing backoff short: there is something to publish now. */
  retryNow(): void {
    if (this.closed || this.socket || !this.looping) return;
    clearTimeout(this.retryTimer);
    this.open();
  }

  private open(): void {
    if (this.closed) return;
    this.onStatus('connecting');
    const socket = new WebSocket(this.resumeUrl ?? this.url);
    this.socket = socket;
    let opened = false;
    socket.onopen = () => {
      opened = true;
      this.backoff = INITIAL_RECONNECT_MS;
      this.keepAlive ??= setInterval(() => void browser.runtime.getPlatformInfo(), KEEPALIVE_MS);
    };
    socket.onmessage = (event) => {
      // Per frame: one payload this build doesn't understand can't take the connection down.
      try {
        this.onMessage(JSON.parse(String(event.data)) as Payload);
      } catch (error) {
        console.warn('BitChord: Discord gateway payload', error);
      }
    };
    socket.onclose = (event) => {
      if (this.socket !== socket) return;
      this.teardown();
      if (this.closed) return;
      // 4004: authentication failed. Retrying with the same token never helps.
      if (event.code === 4004) {
        this.looping = false;
        this.onStatus('error', 'Discord refused the token. Connect the account again.');
        return;
      }
      // An attempt that never opened is as often a stale resume URL as a dead network.
      if (!opened) this.forgetSession();
      if (this.immediateRetry) {
        this.immediateRetry = false;
        this.open();
        return;
      }
      this.onStatus('connecting');
      this.retryTimer = setTimeout(() => this.open(), this.backoff);
      this.backoff = Math.min(this.backoff * 2, MAX_RECONNECT_MS);
    };
  }

  private teardown(): void {
    this.ready = false;
    this.awaitingAck = false;
    this.delivered = null;
    clearTimeout(this.heartbeatTimer);
    clearInterval(this.keepAlive);
    this.keepAlive = undefined;
    const socket = this.socket;
    this.socket = null;
    if (socket && socket.readyState <= WebSocket.OPEN) socket.close();
  }

  private forgetSession(): void {
    this.resumeUrl = null;
    this.sessionId = null;
    this.sequence = 0;
  }

  private drop(why: string): void {
    console.info(`BitChord: Discord gateway dropped (${why})`);
    this.ready = false;
    this.socket?.close();
  }

  private send(op: number, d: unknown): boolean {
    if (this.socket?.readyState !== WebSocket.OPEN) return false;
    this.socket.send(JSON.stringify({ op, d }));
    return true;
  }

  private onMessage(payload: Payload): void {
    if (typeof payload.s === 'number') this.sequence = payload.s;
    switch (payload.op) {
      case OP.DISPATCH:
        if (payload.t === 'READY') {
          const ready = payload.d as { session_id: string; resume_gateway_url: string };
          this.sessionId = ready.session_id;
          this.resumeUrl = `${ready.resume_gateway_url}/?v=9&encoding=json`;
          this.sessionReady();
        } else if (payload.t === 'RESUMED') {
          this.sessionReady();
        }
        break;
      case OP.HEARTBEAT:
        this.heartbeat();
        break;
      case OP.HEARTBEAT_ACK:
        this.awaitingAck = false;
        this.lastAckAt = Date.now();
        break;
      case OP.RECONNECT:
        this.immediateRetry = true;
        this.drop('gateway asked us to reconnect');
        break;
      case OP.INVALID_SESSION:
        this.forgetSession();
        setTimeout(() => this.identify(), 150);
        break;
      case OP.HELLO: {
        this.heartbeatInterval = (payload.d as { heartbeat_interval: number }).heartbeat_interval;
        if (this.sequence > 0 && this.sessionId) {
          this.send(OP.RESUME, { token: this.token, session_id: this.sessionId, seq: this.sequence });
        } else {
          this.identify();
        }
        this.startHeartbeats();
        break;
      }
    }
  }

  private identify(): void {
    // Identify.toIdentifyPayload, with the web client's properties.
    this.send(OP.IDENTIFY, {
      token: this.token,
      capabilities: 16381,
      properties: { os: osName(), browser: browserName(), device: '' },
      presence: { status: 'online', since: 0, activities: [], afk: false },
      compress: false,
      client_state: {
        guild_versions: {},
        highest_last_message_id: '0',
        read_state_version: 0,
        user_guild_settings_version: -1,
        user_settings_version: -1,
        private_channels_version: '0',
        api_code_version: 0,
      },
    });
  }

  private heartbeat(): void {
    this.send(OP.HEARTBEAT, this.sequence || null);
  }

  /** The watchdog: a heartbeat unanswered for a whole interval means the socket is gone. */
  private startHeartbeats(): void {
    clearTimeout(this.heartbeatTimer);
    this.awaitingAck = false;
    this.lastAckAt = Date.now();
    const beat = () => {
      if (this.awaitingAck) return this.drop('heartbeat went unacknowledged');
      this.awaitingAck = true;
      this.heartbeat();
      this.heartbeatTimer = setTimeout(beat, this.heartbeatInterval);
    };
    // Discord asks for the first one after a random fraction of the interval.
    this.heartbeatTimer = setTimeout(beat, this.heartbeatInterval * Math.random());
  }

  private stale(): boolean {
    return this.heartbeatInterval > 0 && this.lastAckAt > 0 && Date.now() - this.lastAckAt > this.heartbeatInterval + STALE_GRACE_MS;
  }

  private sessionReady(): void {
    this.lastAckAt = Date.now();
    this.ready = true;
    this.onStatus('ready');
    this.readyWaiters.splice(0).forEach((resolve) => resolve());
    if (this.lastPresence) this.deliver(this.lastPresence);
  }

  private deliver(presence: Presence): void {
    const json = JSON.stringify(presence);
    if (this.delivered === json) return;
    if (this.send(OP.PRESENCE_UPDATE, presence)) this.delivered = json;
  }

  /** Publishes, waiting a while for a session; a push that times out is replayed when one arrives. */
  async sendActivity(presence: Presence): Promise<void> {
    this.lastPresence = presence;
    if (this.closed) return;
    if (this.ready && this.stale()) this.drop('stale before a presence push');
    this.connect();
    this.retryNow();
    if (!this.ready) {
      await new Promise<void>((resolve) => {
        const timer = setTimeout(resolve, SESSION_WAIT_MS);
        this.readyWaiters.push(() => (clearTimeout(timer), resolve()));
      });
    }
    if (this.ready) this.deliver(presence);
  }

  /** An activity-less presence clears the card; it becomes what a reconnect replays. */
  clearActivity(): void {
    const presence: Presence = { activities: [], afk: true, since: 0, status: 'online' };
    this.lastPresence = presence;
    if (this.ready && !this.stale()) this.deliver(presence);
  }

  close(): void {
    this.closed = true;
    this.looping = false;
    clearTimeout(this.retryTimer);
    this.lastPresence = null;
    this.forgetSession();
    this.teardown();
    this.onStatus('off');
  }
}

// ---- The activity (DiscordRPC.updateSong) -------------------------------------------

function resolveVariables(text: string, track: DiscordNowPlaying): string {
  return text
    .replaceAll('{song_name}', track.title)
    .replaceAll('{artist_name}', track.artist)
    .replaceAll('{album_name}', track.album ?? '');
}

const ACTIVITY_TYPES = { playing: 0, listening: 2, watching: 3, competing: 5 } as const;

export async function buildPresence(token: string, track: DiscordNowPlaying, options: DiscordPresenceOptions): Promise<Presence> {
  const now = Date.now();
  const rate = track.playbackRate > 0 ? track.playbackRate : 1;
  const watchUrl = `https://music.youtube.com/watch?v=${track.videoId}`;
  const buttons: [string, string][] = [];
  if (options.button1Visible) buttons.push([resolveVariables(options.button1Text || DEFAULT_BUTTON_1, track), watchUrl]);
  if (options.button2Visible) buttons.push([resolveVariables(options.button2Text || DEFAULT_BUTTON_2, track), PROJECT_URL]);
  const art = track.artworkUrl?.startsWith('http') ? track.artworkUrl : null;
  // An unreachable mirror keeps the raw URL, as RpcImage.ExternalImage does.
  const largeImage = art ? ((await externalAsset(token, art)) ?? art) : null;
  return {
    activities: [
      {
        name: options.activityName.trim() || APP_NAME,
        details: rate !== 1 ? `${track.title} [${rate.toFixed(2)}x]` : track.title,
        details_url: watchUrl,
        state: track.artist,
        state_url: null,
        type: ACTIVITY_TYPES[options.activityType] ?? 2,
        status_display_type: options.useDetails ? 2 : 1,
        timestamps: {
          start: Math.round(now - track.positionMs / rate),
          end: Math.round(now + Math.max(0, track.durationMs - track.positionMs) / rate),
        },
        assets: { large_image: largeImage, small_image: null, large_text: null, small_text: null },
        ...(buttons.length && {
          buttons: buttons.map(([label]) => label),
          metadata: { button_urls: buttons.map(([, url]) => url) },
          application_id: APPLICATION_ID,
        }),
        url: null,
      },
    ],
    afk: true,
    since: now,
    status: options.status,
  };
}

// ---- The service ----------------------------------------------------------------

let gateway: Gateway | null = null;
let gatewayStatus: DiscordState['gateway'] = 'off';
let gatewayProblem: string | null = null;
let idleTimer: ReturnType<typeof setTimeout> | undefined;
let listener: ((state: DiscordState) => void) | null = null;

export const AVAILABLE = typeof __DISCORD_PRESENCE__ !== 'undefined' && __DISCORD_PRESENCE__;

/** The background broadcasts every change to the open pages. */
export function onDiscordState(callback: (state: DiscordState) => void): void {
  listener = callback;
}

async function account(): Promise<Account | null> {
  return ((await browser.storage.local.get(ACCOUNT_KEY))[ACCOUNT_KEY] as Account | undefined) ?? null;
}

async function enabled(): Promise<boolean> {
  return ((await browser.storage.local.get(ENABLED_KEY))[ENABLED_KEY] as boolean | undefined) ?? false;
}

export async function discordState(): Promise<DiscordState> {
  const held = AVAILABLE ? await account() : null;
  return {
    available: AVAILABLE,
    connected: !!held,
    enabled: AVAILABLE && (await enabled()),
    user: held ? { name: held.user.name, username: held.user.username, avatarUrl: held.user.avatarUrl } : null,
    gateway: gatewayStatus,
    problem: gatewayProblem,
  };
}

async function announce(): Promise<void> {
  listener?.(await discordState());
}

function closeGateway(): void {
  clearTimeout(idleTimer);
  gateway?.close();
  gateway = null;
}

function ensureGateway(token: string): Gateway {
  gateway ??= new Gateway(token, (status, problem) => {
    gatewayStatus = status;
    gatewayProblem = problem ?? (status === 'error' ? gatewayProblem : null);
    void announce();
  });
  return gateway;
}

/** The page reports what's playing; null takes the card down but leaves the socket up for a while. */
export async function setPresence(track: DiscordNowPlaying | null, options: DiscordPresenceOptions): Promise<void> {
  if (!AVAILABLE) return;
  const held = await account();
  if (!held || !(await enabled())) return;
  clearTimeout(idleTimer);
  if (!track) {
    gateway?.clearActivity();
    idleTimer = setTimeout(closeGateway, IDLE_CLOSE_MS);
    return;
  }
  const presence = await buildPresence(held.token, track, options);
  await ensureGateway(held.token).sendActivity(presence);
}

/** Every BitChord page has gone: nobody is playing, so the card comes down with the socket. */
export function allPagesClosed(): void {
  gateway?.clearActivity();
  idleTimer = setTimeout(closeGateway, 2_000);
}

export async function setEnabled(on: boolean): Promise<DiscordState> {
  await browser.storage.local.set({ [ENABLED_KEY]: on });
  if (!on) closeGateway();
  await announce();
  return discordState();
}

export async function disconnect(): Promise<DiscordState> {
  closeGateway();
  gatewayStatus = 'off';
  gatewayProblem = null;
  artwork.clear();
  await browser.storage.local.remove([ACCOUNT_KEY]);
  await announce();
  return discordState();
}

// ---- Connecting an account ------------------------------------------------------

export const DISCORD_ORIGINS = ['https://discord.com/*'];

/** Opens the extension's connect page: the permission prompt only follows a click there. */
export async function openConnectPage(): Promise<void> {
  await browser.tabs.create({ url: browser.runtime.getURL('/discord.html') });
}

/**
 * DiscordLoginScreen.TOKEN_SNIPPET: Discord takes `localStorage` away from its
 * window once the app boots, but a fresh same-origin iframe still sees it.
 * Runs in the page's own world, only on the user's "Connect Discord".
 */
function readToken(): string {
  try {
    const frame = document.createElement('iframe');
    frame.style.display = 'none';
    document.body.appendChild(frame);
    const raw = frame.contentWindow?.localStorage.getItem('token') ?? null;
    frame.remove();
    return raw ? raw.slice(1, -1) : '';
  } catch {
    return '';
  }
}

/**
 * Reads the token from a signed-in discord.com tab, opening one if there is
 * none, and keeps it once Discord confirms who it belongs to. Polls for two
 * minutes, which covers signing in from scratch in that tab.
 */
export async function connectFromTab(onProgress: (message: string) => void): Promise<DiscordState> {
  let [tab] = await browser.tabs.query({ url: 'https://discord.com/*' });
  let opened = false;
  if (!tab?.id) {
    tab = await browser.tabs.create({ url: 'https://discord.com/app', active: true });
    opened = true;
    onProgress('Sign in to Discord in the tab that opened. This page will notice.');
  } else {
    onProgress('Reading your Discord session…');
  }
  const deadline = Date.now() + 120_000;
  while (Date.now() < deadline) {
    try {
      const [result] = await browser.scripting.executeScript({ target: { tabId: tab.id! }, func: readToken, world: 'MAIN' });
      const token = typeof result?.result === 'string' ? result.result : '';
      if (token) {
        const user = await userInfo(token);
        await browser.storage.local.set({ [ACCOUNT_KEY]: { token, user } satisfies Account, [ENABLED_KEY]: true });
        gatewayProblem = null;
        if (opened) await browser.tabs.remove(tab.id!).catch(() => undefined);
        await announce();
        return discordState();
      }
    } catch {
      // The tab is still loading, or navigating between sign-in pages.
    }
    await new Promise((r) => setTimeout(r, 1000));
  }
  throw new Error('No Discord session turned up. Sign in at discord.com and try again.');
}
