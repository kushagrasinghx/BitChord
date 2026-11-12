import './style.css';
import {
  BRIDGE_CHANNEL,
  BRIDGE_PROTOCOL,
  type BridgeMethod,
  type BridgeResponse,
  type ParamsOf,
  type ResultOf,
  type DiscordState,
  type SpotifyAuthState,
  type YtAuthState,
} from '@bitchord/shared/bridge';

declare const __SITE_ORIGINS__: string[];

/** The popup asks the background, like the page does, so both see one state. */
async function call<M extends BridgeMethod>(method: M, params: ParamsOf<M>): Promise<ResultOf<M>> {
  const response = (await browser.runtime.sendMessage({
    channel: BRIDGE_CHANNEL,
    protocol: BRIDGE_PROTOCOL,
    direction: 'to-extension',
    id: `popup-${Date.now().toString(36)}`,
    method,
    params,
  })) as BridgeResponse<M>;
  if (!response.ok) throw new Error(response.error.message);
  return response.result;
}

function el<K extends keyof HTMLElementTagNameMap>(tag: K, className: string, text?: string): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag);
  node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function simpleRow(name: string, detail: string): HTMLLIElement {
  const row = el('li', 'row');
  row.append(el('span', 'label', name), el('span', 'value', detail));
  return row;
}

/** YouTube Music: who requests act as, and the way to stop (or start) using that session. */
function youtubeRow(state: YtAuthState | null, refresh: (state: YtAuthState) => void): HTMLLIElement {
  if (!state) return simpleRow('YouTube Music', 'Checking…');
  const row = el('li', 'row account');
  // A grey disc until there's a picture; an <img> without a src can draw a broken-image glyph.
  let avatar: HTMLElement = el('span', 'avatar');
  const who = el('span', 'who');
  let action: HTMLElement;

  if (!state.browserSignedIn) {
    who.append(el('span', 'title', 'YouTube Music'), el('span', 'detail', 'Not signed in in this browser'));
    const link = el('a', 'action', 'Sign in');
    (link as HTMLAnchorElement).href = 'https://music.youtube.com/';
    (link as HTMLAnchorElement).target = '_blank';
    action = link;
  } else if (!state.connected || !state.active) {
    const detail = el('span', 'detail', state.connected ? 'Couldn’t read your account' : 'Disconnected');
    if (state.problem) detail.title = state.problem;
    who.append(el('span', 'title', 'YouTube Music'), detail);
    if (state.connected) {
      // Signed in, but the account couldn't be read: say why, and offer another try.
      action = el('button', 'action', 'Retry');
      action.onclick = () => void call('yt.authState', { refresh: true }).then(refresh);
    } else {
      action = el('button', 'action', 'Connect');
      action.onclick = () => void call('yt.setConnected', { connected: true }).then(refresh);
    }
  } else {
    if (state.active.avatarUrl) {
      const image = el('img', 'avatar');
      image.alt = '';
      image.referrerPolicy = 'no-referrer';
      image.src = state.active.avatarUrl;
      avatar = image;
    }
    who.append(el('span', 'title', state.active.name), el('span', 'detail', state.active.handle ?? 'YouTube Music'));
    action = el('button', 'action', 'Disconnect');
    action.title = 'Stop using this account in BitChord. You stay signed in to YouTube.';
    action.onclick = () => void call('yt.setConnected', { connected: false }).then(refresh);
  }
  row.append(avatar, who, action);
  if (state.problem) {
    const why = el('li', 'row problem', state.problem);
    return Object.assign(row, { problemRow: why });
  }
  return row;
}

/** A connected account's picture, or a grey disc. */
function avatarOf(url: string | null | undefined): HTMLElement {
  if (!url) return el('span', 'avatar');
  const image = el('img', 'avatar');
  image.alt = '';
  image.referrerPolicy = 'no-referrer';
  image.src = url;
  return image;
}

function button(text: string, onClick: () => void, title?: string): HTMLElement {
  const action = el('button', 'action', text);
  if (title) action.title = title;
  action.onclick = onClick;
  return action;
}

/** Spotify: the browser's open.spotify.com session, as for YouTube. */
function spotifyRow(state: SpotifyAuthState | null): HTMLLIElement {
  if (!state) return simpleRow('Spotify', 'Checking…');
  const row = el('li', 'row account');
  const who = el('span', 'who');
  let action: HTMLElement;
  if (!state.browserSignedIn) {
    who.append(el('span', 'title', 'Spotify'), el('span', 'detail', 'Not signed in in this browser'));
    action = button('Sign in', () => void call('spotify.signIn', {}));
  } else if (!state.connected) {
    who.append(el('span', 'title', 'Spotify'), el('span', 'detail', 'Disconnected'));
    action = button('Connect', () => void call('spotify.setConnected', { connected: true }).then(setSpotify));
  } else if (!state.signedIn) {
    const detail = el('span', 'detail', 'Couldn’t read your account');
    if (state.problem) detail.title = state.problem;
    who.append(el('span', 'title', 'Spotify'), detail);
    action = button('Retry', () => void call('spotify.authState', { refresh: true }).then(setSpotify));
  } else {
    who.append(el('span', 'title', state.user?.name ?? 'Spotify'), el('span', 'detail', 'Spotify'));
    action = button(
      'Disconnect',
      () => void call('spotify.setConnected', { connected: false }).then(setSpotify),
      'Stop using this account in BitChord. You stay signed in to Spotify.',
    );
  }
  row.append(avatarOf(state.user?.avatarUrl), who, action);
  return row;
}

/** Discord: connect on the extension's own page; presence itself is switched on in the web player. */
function discordRow(state: DiscordState | null): HTMLLIElement | null {
  if (state && !state.available) return null;
  if (!state) return simpleRow('Discord', 'Checking…');
  const row = el('li', 'row account');
  const who = el('span', 'who');
  let action: HTMLElement;
  if (!state.connected || !state.user) {
    who.append(el('span', 'title', 'Discord'), el('span', 'detail', 'Not connected'));
    action = button('Connect', () => void call('discord.connect', {}).then(() => window.close()));
  } else {
    const detail =
      state.gateway === 'ready'
        ? 'Showing what you play'
        : state.gateway === 'connecting'
          ? 'Connecting…'
          : state.gateway === 'error'
            ? (state.problem ?? 'Couldn’t connect')
            : state.enabled
              ? 'On, waiting for a song'
              : 'Presence off';
    who.append(el('span', 'title', state.user.name), el('span', 'detail', detail));
    action = button('Disconnect', () => void call('discord.disconnect', {}).then(setDiscord), 'Forget this Discord account.');
  }
  row.append(avatarOf(state.user?.avatarUrl), who, action);
  return row;
}

const list = document.getElementById('integrations')!;
let youtubeState: YtAuthState | null = null;
let spotifyState: SpotifyAuthState | null = null;
let discordStateNow: DiscordState | null = null;

function render(): void {
  const youtube = youtubeRow(youtubeState, setYoutube) as HTMLLIElement & { problemRow?: HTMLLIElement };
  const discord = discordRow(discordStateNow);
  list.replaceChildren(
    youtube,
    ...(youtube.problemRow ? [youtube.problemRow] : []),
    spotifyRow(spotifyState),
    ...(discord ? [discord] : []),
  );
}

function setYoutube(state: YtAuthState): void {
  youtubeState = state;
  render();
}
function setSpotify(state: SpotifyAuthState): void {
  spotifyState = state;
  render();
}
function setDiscord(state: DiscordState): void {
  discordStateNow = state;
  render();
}

document.getElementById('version')!.textContent = `v${browser.runtime.getManifest().version}`;
const site = document.getElementById('site') as HTMLAnchorElement;
site.href = __SITE_ORIGINS__[0] ?? '#';

render();
call('yt.authState', {})
  .then(setYoutube)
  .catch(() => list.replaceChildren(simpleRow('YouTube Music', 'Couldn’t read the session')));
call('spotify.authState', {}).then(setSpotify).catch(() => undefined);
call('discord.state', {}).then(setDiscord).catch(() => undefined);
