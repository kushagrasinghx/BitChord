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
import type { PomodoroTimer, PomodoroSettings, PlayerStatusPayload } from '../../lib/pomodoro';

declare const __SITE_ORIGINS__: string[];

// ---- Bridge Calling (Accounts tab) -------------------------------------------

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

function youtubeRow(state: YtAuthState | null, refresh: (state: YtAuthState) => void): HTMLLIElement {
  if (!state) return simpleRow('YouTube Music', 'Checking…');
  const row = el('li', 'row account');
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

function renderIntegrations(): void {
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
  renderIntegrations();
}
function setSpotify(state: SpotifyAuthState): void {
  spotifyState = state;
  renderIntegrations();
}
function setDiscord(state: DiscordState): void {
  discordStateNow = state;
  renderIntegrations();
}

function escapeHtml(str: string | null | undefined): string {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function formatTime(sec: number): string {
  if (!sec || isNaN(sec) || sec < 0) return '0:00';
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return `${m}:${String(s).padStart(2, '0')}`;
}

// ---- Main App Initializer ----------------------------------------------------

document.addEventListener('DOMContentLoaded', async () => {
  // --- DOM Elements ---

  // Timer Section Elements
  const ringProgress = document.getElementById('ringProgress') as SVGCircleElement | null;
  const timeDisplay = document.getElementById('timeDisplay')!;
  const sessionBadge = document.getElementById('sessionBadge')!;
  const cycleText = document.getElementById('cycleText')!;
  const cycleDots = document.getElementById('cycleDots')!;

  const btnTogglePlay = document.getElementById('btnTogglePlay')!;
  const iconPlay = document.getElementById('iconPlay')!;
  const iconPause = document.getElementById('iconPause')!;
  const btnReset = document.getElementById('btnReset')!;
  const btnSkip = document.getElementById('btnSkip')!;

  const modeTabs = document.querySelectorAll<HTMLButtonElement>('.mode-tab');
  const modeSelector = document.querySelector('.mode-selector') as HTMLElement | null;

  // Focus View Mini Player Card
  const bcArtwork = document.getElementById('bcArtwork') as HTMLImageElement;
  const bcEqBars = document.getElementById('bcEqBars')!;
  const bcTrackTitle = document.getElementById('bcTrackTitle')!;
  const bcTrackArtist = document.getElementById('bcTrackArtist')!;
  const bcStatusPill = document.getElementById('bcStatusPill')!;
  const bcStatusText = document.getElementById('bcStatusText')!;

  const btnBcPrev = document.getElementById('btnBcPrev')!;
  const btnBcToggle = document.getElementById('btnBcToggle')!;
  const bcMiniPlay = document.getElementById('bcMiniPlay')!;
  const bcMiniPause = document.getElementById('bcMiniPause')!;
  const btnBcNext = document.getElementById('btnBcNext')!;
  const btnOpenWeb = document.getElementById('btnOpenWeb')!;

  // Dedicated Player View Elements
  const navTabPomodoro = document.getElementById('navTabPomodoro');
  const navTabPlayer = document.getElementById('navTabPlayer');
  const navTabIntegrations = document.getElementById('navTabIntegrations');
  const viewPomodoro = document.getElementById('viewPomodoro');
  const viewPlayerOnly = document.getElementById('viewPlayerOnly');
  const viewIntegrations = document.getElementById('viewIntegrations');

  const playerOnlyContainer = document.getElementById('playerOnlyContainer');
  const poFullBackdrop = document.getElementById('poFullBackdrop');
  const poArtwork = document.getElementById('poArtwork') as HTMLImageElement | null;
  const poEqBadge = document.getElementById('poEqBadge');
  const poTrackTitle = document.getElementById('poTrackTitle');
  const poTrackArtist = document.getElementById('poTrackArtist');
  const poStatusBadge = document.getElementById('poStatusBadge');
  const poStatusText = document.getElementById('poStatusText');

  const btnToggleFullCover = document.getElementById('btnToggleFullCover');
  const iconExpandArt = document.getElementById('iconExpandArt');
  const iconCollapseArt = document.getElementById('iconCollapseArt');

  const btnPoShuffle = document.getElementById('btnPoShuffle');
  const btnPoRepeat = document.getElementById('btnPoRepeat');
  const repeatBadge = document.getElementById('repeatBadge');

  const poScrubberHitbox = document.getElementById('poScrubberHitbox');
  const poScrubberTrack = document.getElementById('poScrubberTrack');
  const poScrubberFill = document.getElementById('poScrubberFill');
  const poScrubberThumb = document.getElementById('poScrubberThumb');
  const poTimeElapsed = document.getElementById('poTimeElapsed');
  const poTimeTotal = document.getElementById('poTimeTotal');

  const btnPoPrev = document.getElementById('btnPoPrev');
  const btnPoToggle = document.getElementById('btnPoToggle');
  const poPlayIcon = document.getElementById('poPlayIcon');
  const poPauseIcon = document.getElementById('poPauseIcon');
  const btnPoNext = document.getElementById('btnPoNext');
  const btnPoOpenBitChord = document.getElementById('btnPoOpenBitChord');

  const btnPoVolume = document.getElementById('btnPoVolume');
  const iconVolHigh = document.getElementById('iconVolHigh');
  const iconVolMuted = document.getElementById('iconVolMuted');
  const poVolumeRange = document.getElementById('poVolumeRange') as HTMLInputElement | null;

  // Lyrics Drawer Elements
  const btnToggleLyrics = document.getElementById('btnToggleLyrics');
  const poLyricsDrawer = document.getElementById('poLyricsDrawer');
  const btnCloseLyrics = document.getElementById('btnCloseLyrics');
  const poLyricsBadge = document.getElementById('poLyricsBadge');
  const poLyricsScroll = document.getElementById('poLyricsScroll');
  const poLyricsHint = document.getElementById('poLyricsHint');

  // Queue & AutoMix Drawer Elements
  const btnToggleQueue = document.getElementById('btnToggleQueue');
  const poQueueDrawer = document.getElementById('poQueueDrawer');
  const btnCloseQueue = document.getElementById('btnCloseQueue');
  const poQueueCountBadge = document.getElementById('poQueueCountBadge');
  const chkAutoMix = document.getElementById('chkAutoMix') as HTMLInputElement | null;
  const selAutoMixBars = document.getElementById('selAutoMixBars') as HTMLSelectElement | null;
  const rangeCrossfade = document.getElementById('rangeCrossfade') as HTMLInputElement | null;
  const lblCrossfadeVal = document.getElementById('lblCrossfadeVal');
  const btnClearQueue = document.getElementById('btnClearQueue');
  const poQueueList = document.getElementById('poQueueList');

  let isQueueVisible = false;
  let currentQueue: any[] = [];

  // Equalizer Drawer Elements
  const btnToggleEq = document.getElementById('btnToggleEq');
  const poEqDrawer = document.getElementById('poEqDrawer');
  const btnCloseEq = document.getElementById('btnCloseEq');
  const chkEqToggle = document.getElementById('chkEqToggle') as HTMLInputElement | null;
  const selEqPreset = document.getElementById('selEqPreset') as HTMLSelectElement | null;
  const btnResetEq = document.getElementById('btnResetEq');
  const svgEqLine = document.getElementById('svgEqLine');
  const svgEqArea = document.getElementById('svgEqArea');
  const eqSlidersGrid = document.getElementById('eqSlidersGrid');

  let isEqVisible = false;
  let eqEnabled = true;
  const EQ_FREQS = [32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000];
  const EQ_LABELS = ['32', '64', '125', '250', '500', '1K', '2K', '4K', '8K', '16K'];
  let currentEqGains = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0];

  const EQ_PRESETS: Record<string, number[]> = {
    flat: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
    bassBoost: [5.5, 4.5, 3.5, 2.0, 0.5, 0, 0, 0, 0, 0],
    bassReducer: [-5.0, -4.0, -3.0, -1.5, 0, 0, 0, 0, 0, 0],
    electronic: [4.0, 3.5, 1.5, 0, -1.5, 1.5, 1.0, 2.0, 3.5, 4.0],
    hiphop: [5.0, 4.0, 1.5, 2.0, -1.0, -1.0, 1.0, -1.0, 2.0, 3.0],
    rock: [4.5, 3.0, -1.5, -2.5, -1.0, 1.0, 3.0, 4.0, 4.5, 4.5],
    pop: [-1.5, -1.0, 1.5, 3.0, 4.0, 3.5, 1.5, -1.0, -1.5, -2.0],
    acoustic: [3.5, 3.0, 2.0, 1.0, 1.5, 1.5, 2.5, 3.0, 3.5, 2.5],
    vocalBooster: [-2.0, -2.5, -1.5, 1.5, 4.0, 4.5, 3.5, 2.0, 0.5, -1.0],
    classical: [4.0, 3.0, 2.5, 2.0, -1.5, -1.5, 0, 2.0, 3.0, 3.5],
    rnb: [3.0, 6.0, 5.0, 1.5, -1.5, -1.0, 2.0, 2.5, 3.0, 3.5],
    trebleBooster: [0, 0, 0, 0, 0, 1.5, 3.0, 4.5, 5.5, 6.5],
    lateNight: [3.5, 3.0, 1.5, 0, -1.0, -1.5, 0, 1.5, 2.5, -1.5],
  };

  // Floating Window & Mode elements
  const btnFloatWindow = document.getElementById('btnFloatWindow');
  const btnFloatFromSettings = document.getElementById('btnFloatFromSettings');
  const iconFloat = document.getElementById('iconFloat');
  const iconSidebar = document.getElementById('iconSidebar');
  const chkEnablePomodoro = document.getElementById('chkEnablePomodoro') as HTMLInputElement | null;

  // Settings modal elements
  const btnSettings = document.getElementById('btnSettings')!;
  const settingsModal = document.getElementById('settingsModal')!;
  const btnCloseSettings = document.getElementById('btnCloseSettings')!;
  const btnSaveSettings = document.getElementById('btnSaveSettings')!;

  const presetPills = document.querySelectorAll<HTMLButtonElement>('.preset-pill');
  const inpWorkMin = document.getElementById('inpWorkMin') as HTMLInputElement;
  const rangeWork = document.getElementById('rangeWork') as HTMLInputElement;
  const inpShortMin = document.getElementById('inpShortMin') as HTMLInputElement;
  const rangeShort = document.getElementById('rangeShort') as HTMLInputElement;
  const inpLongMin = document.getElementById('inpLongMin') as HTMLInputElement;
  const rangeLong = document.getElementById('rangeLong') as HTMLInputElement;
  const inpCycleInterval = document.getElementById('inpCycleInterval') as HTMLInputElement;

  const chkAutoPlayBreak = document.getElementById('chkAutoPlayBreak') as HTMLInputElement;
  const chkPauseFocus = document.getElementById('chkPauseFocus') as HTMLInputElement;
  const chkAutoOpenTab = document.getElementById('chkAutoOpenTab') as HTMLInputElement;
  const chkSoundAlerts = document.getElementById('chkSoundAlerts') as HTMLInputElement;
  const chkTtsVoice = document.getElementById('chkTtsVoice') as HTMLInputElement | null;
  const selTtsStyle = document.getElementById('selTtsStyle') as HTMLSelectElement | null;
  const boxCustomTts = document.getElementById('boxCustomTts');
  const inpTtsBreak = document.getElementById('inpTtsBreak') as HTMLInputElement | null;
  const inpTtsWork = document.getElementById('inpTtsWork') as HTMLInputElement | null;
  const btnTestVoice = document.getElementById('btnTestVoice');
  const inpBitchordUrl = document.getElementById('inpBitchordUrl') as HTMLInputElement;

  const appContainer = document.getElementById('appContainer') || document.querySelector('.app-container');

  // Progress ring setup
  const radius = ringProgress?.r?.baseVal?.value || 76;
  const CIRCLE_CIRCUMFERENCE = 2 * Math.PI * radius;
  if (ringProgress) {
    ringProgress.style.strokeDasharray = `${CIRCLE_CIRCUMFERENCE} ${CIRCLE_CIRCUMFERENCE}`;
  }

  // Version and site link
  document.getElementById('version')!.textContent = `v${browser.runtime.getManifest().version}`;
  const site = document.getElementById('site') as HTMLAnchorElement;
  if (site) site.href = __SITE_ORIGINS__[0] ?? 'https://bitchord-8bw.pages.dev/home';

  // Remove any legacy compact dock state
  void browser.storage.local.remove('isCompactDock');

  // --- Floating Window Handling ---
  const openFloatWindow = () => {
    browser.windows?.create?.({
      url: browser.runtime.getURL('popup/index.html?floating=1'),
      type: 'popup',
      width: 340,
      height: 560,
      focused: true,
    });
  };

  if (window.location.search.includes('floating')) {
    iconFloat?.classList.add('hidden');
    iconSidebar?.classList.remove('hidden');
    if (btnFloatWindow) btnFloatWindow.title = 'Dock back to Chrome Sidebar';
    btnFloatWindow?.addEventListener('click', () => {
      // @ts-ignore
      browser.sidePanel?.open?.({ windowId: browser.windows?.WINDOW_ID_CURRENT }).catch(() => {});
      window.close();
    });
  } else {
    btnFloatWindow?.addEventListener('click', openFloatWindow);
  }
  btnFloatFromSettings?.addEventListener('click', openFloatWindow);

  // --- Top Navigation Switcher ---
  function switchTopTab(tabName: string) {
    navTabPomodoro?.classList.toggle('active', tabName === 'pomodoro');
    navTabPlayer?.classList.toggle('active', tabName === 'player');
    navTabIntegrations?.classList.toggle('active', tabName === 'integrations');

    viewPomodoro?.classList.toggle('hidden', tabName !== 'pomodoro');
    viewPlayerOnly?.classList.toggle('hidden', tabName !== 'player');
    viewIntegrations?.classList.toggle('hidden', tabName !== 'integrations');

    void browser.storage.local.set({ activeTopTab: tabName });
  }

  navTabPomodoro?.addEventListener('click', () => switchTopTab('pomodoro'));
  navTabPlayer?.addEventListener('click', () => switchTopTab('player'));
  navTabIntegrations?.addEventListener('click', () => switchTopTab('integrations'));

  // Load Pomodoro Enabled & Active Tab State
  void browser.storage.local.get(['pomodoroEnabled', 'activeTopTab', 'isFullCoverMode']).then((res: any) => {
    const isPomodoroEnabled = res.pomodoroEnabled !== false;
    if (chkEnablePomodoro) chkEnablePomodoro.checked = isPomodoroEnabled;
    appContainer?.classList.toggle('pomodoro-disabled', !isPomodoroEnabled);

    let targetTab = res.activeTopTab || (isPomodoroEnabled ? 'pomodoro' : 'player');
    if (!isPomodoroEnabled && targetTab === 'pomodoro') {
      targetTab = 'player';
    }
    switchTopTab(targetTab);

    if (res.isFullCoverMode) {
      setFullCoverMode(true);
    }
  });

  chkEnablePomodoro?.addEventListener('change', () => {
    const enabled = chkEnablePomodoro.checked;
    appContainer?.classList.toggle('pomodoro-disabled', !enabled);
    void browser.storage.local.set({ pomodoroEnabled: enabled });
    if (!enabled && navTabPomodoro?.classList.contains('active')) {
      switchTopTab('player');
    }
  });

  // --- Full Cover Art Mode Toggle ---
  function setFullCoverMode(isFull: boolean) {
    playerOnlyContainer?.classList.toggle('full-cover-mode', isFull);
    iconExpandArt?.classList.toggle('hidden', isFull);
    iconCollapseArt?.classList.toggle('hidden', !isFull);
    btnToggleFullCover?.classList.toggle('active', isFull);
    void browser.storage.local.set({ isFullCoverMode: isFull });
  }

  btnToggleFullCover?.addEventListener('click', () => {
    const isNowFull = !playerOnlyContainer?.classList.contains('full-cover-mode');
    setFullCoverMode(isNowFull);
  });

  // --- Track State & Lyrics Engine ---
  let currentTimer: PomodoroTimer | null = null;
  let currentSettings: PomodoroSettings | null = null;
  let isBitChordPlaying = false;
  let hasBitChordTab = false;
  let lastCurrentTime = 0;
  let lastDuration = 0;
  let lastTrackSignature = '';
  let parsedLyrics: Array<{ time: number; text: string }> = [];
  let isLyricsVisible = false;

  function parseLrc(lrcText: string) {
    const lines = lrcText.split('\n');
    const result: Array<{ time: number; text: string }> = [];
    const timeRegex = /\[(\d{2}):(\d{2}(?:\.\d{1,3})?)\]/g;

    for (const rawLine of lines) {
      const matches = [...rawLine.matchAll(timeRegex)];
      if (!matches.length) {
        const clean = rawLine.replace(/\[.*?\]/g, '').trim();
        if (clean) result.push({ time: -1, text: clean });
        continue;
      }
      const text = rawLine.replace(timeRegex, '').trim();
      for (const match of matches) {
        const min = parseFloat(match[1]);
        const sec = parseFloat(match[2]);
        result.push({ time: min * 60 + sec, text: text || '♪' });
      }
    }
    return result.sort((a, b) => a.time - b.time);
  }

  async function loadLyricsForTrack(title: string, artist: string, duration: number) {
    if (!title || title === 'BitChord Player' || title === 'Ready to stream') return;
    const sig = `${title}::${artist}`;
    if (sig === lastTrackSignature) return;
    lastTrackSignature = sig;

    if (poLyricsHint) poLyricsHint.textContent = `Searching lyrics for "${title}"...`;
    if (poLyricsBadge) poLyricsBadge.textContent = 'Searching';
    if (poLyricsScroll) {
      poLyricsScroll.innerHTML = '';
      if (poLyricsHint) poLyricsScroll.appendChild(poLyricsHint);
    }

    try {
      const res: any = await browser.runtime.sendMessage({
        action: 'get_lyrics',
        title,
        artist,
        duration,
      });

      if (res?.success && res.lyrics) {
        if (res.lyrics.syncedLyrics) {
          parsedLyrics = parseLrc(res.lyrics.syncedLyrics);
          if (poLyricsBadge) poLyricsBadge.textContent = 'Synced';
          renderLyricsList();
        } else if (res.lyrics.plainLyrics) {
          parsedLyrics = res.lyrics.plainLyrics.split('\n').filter(Boolean).map((t: string) => ({ time: -1, text: t.trim() }));
          if (poLyricsBadge) poLyricsBadge.textContent = 'Plain';
          renderLyricsList();
        } else if (res.lyrics.instrumental) {
          if (poLyricsHint) poLyricsHint.textContent = '🎼 Instrumental track — no vocal lyrics.';
          if (poLyricsBadge) poLyricsBadge.textContent = 'Instrumental';
        }
      } else {
        if (poLyricsHint) poLyricsHint.textContent = 'No synced lyrics found for this song.';
        if (poLyricsBadge) poLyricsBadge.textContent = 'None';
      }
    } catch {
      if (poLyricsHint) poLyricsHint.textContent = 'Could not load lyrics at this time.';
      if (poLyricsBadge) poLyricsBadge.textContent = 'Offline';
    }
  }

  function renderLyricsList() {
    if (!poLyricsScroll) return;
    poLyricsScroll.innerHTML = '';
    if (!parsedLyrics.length) {
      if (poLyricsHint) poLyricsScroll.appendChild(poLyricsHint);
      return;
    }

    parsedLyrics.forEach((line, idx) => {
      const div = document.createElement('div');
      div.className = 'lyric-line';
      div.textContent = line.text || '♪';
      div.dataset.index = String(idx);
      if (line.time >= 0) {
        div.dataset.time = String(line.time);
        div.title = `Jump to ${formatTime(line.time)}`;
        div.addEventListener('click', () => {
          void browser.runtime.sendMessage({
            action: 'bitchord_action',
            command: 'seek',
            args: { seconds: line.time },
          });
        });
      }
      poLyricsScroll.appendChild(div);
    });
  }

  function syncLyricsHighlight(curr: number) {
    if (!poLyricsScroll || !parsedLyrics.length || parsedLyrics[0].time < 0) return;

    let activeIdx = -1;
    for (let i = 0; i < parsedLyrics.length; i++) {
      if (parsedLyrics[i].time <= curr + 0.3) {
        activeIdx = i;
      } else {
        break;
      }
    }

    const lines = poLyricsScroll.querySelectorAll<HTMLElement>('.lyric-line');
    lines.forEach((lineEl, idx) => {
      const isActive = idx === activeIdx;
      lineEl.classList.toggle('active', isActive);
      if (isActive && isLyricsVisible) {
        lineEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    });
  }

  btnToggleLyrics?.addEventListener('click', () => {
    isLyricsVisible = !isLyricsVisible;
    if (isLyricsVisible) {
      isQueueVisible = false;
      poQueueDrawer?.classList.add('hidden');
      btnToggleQueue?.classList.remove('active');
      isEqVisible = false;
      poEqDrawer?.classList.add('hidden');
      btnToggleEq?.classList.remove('active');
    }
    poLyricsDrawer?.classList.toggle('hidden', !isLyricsVisible);
    btnToggleLyrics?.classList.toggle('active', isLyricsVisible);
    if (isLyricsVisible && lastCurrentTime > 0) {
      syncLyricsHighlight(lastCurrentTime);
    }
  });

  btnCloseLyrics?.addEventListener('click', () => {
    isLyricsVisible = false;
    poLyricsDrawer?.classList.add('hidden');
    btnToggleLyrics?.classList.remove('active');
  });

  // --- Apple Music Style 10-Band Graphic Equalizer Engine ---
  function drawEqCurve(gains: number[]) {
    if (!svgEqLine || !svgEqArea) return;
    const width = 280;
    const height = 60;
    const centerY = 30; // 0 dB
    const maxDb = 12;

    const points = gains.map((g, i) => {
      const x = 14 + (i * (width - 28)) / (gains.length - 1);
      const clampedG = Math.max(-12, Math.min(12, g));
      const y = centerY - (clampedG / maxDb) * 25;
      return { x, y };
    });

    let pathD = `M 0,${centerY} L ${points[0].x.toFixed(1)},${points[0].y.toFixed(1)}`;
    for (let i = 0; i < points.length - 1; i++) {
      const p0 = points[i === 0 ? 0 : i - 1];
      const p1 = points[i];
      const p2 = points[i + 1];
      const p3 = points[i + 2 < points.length ? i + 2 : i + 1];

      const cp1x = p1.x + (p2.x - p0.x) / 6;
      const cp1y = p1.y + (p2.y - p0.y) / 6;
      const cp2x = p2.x - (p3.x - p1.x) / 6;
      const cp2y = p2.y - (p3.y - p1.y) / 6;

      pathD += ` C ${cp1x.toFixed(1)},${cp1y.toFixed(1)} ${cp2x.toFixed(1)},${cp2y.toFixed(1)} ${p2.x.toFixed(1)},${p2.y.toFixed(1)}`;
    }
    pathD += ` L ${width},${centerY}`;

    svgEqLine.setAttribute('d', pathD);
    svgEqArea.setAttribute('d', `${pathD} L ${width},${height} L 0,${height} Z`);
  }

  function buildEqSliders() {
    if (!eqSlidersGrid) return;
    eqSlidersGrid.innerHTML = '';
    EQ_FREQS.forEach((freq, idx) => {
      const col = document.createElement('div');
      col.className = 'eq-slider-col';
      col.id = `eqCol${idx}`;
      col.innerHTML = `
        <span class="eq-db-val" id="eqDbVal${idx}">0dB</span>
        <div class="eq-slider-wrap">
          <input type="range" class="eq-slider-vert" id="eqSlider${idx}" min="-12" max="12" step="0.5" value="0" />
        </div>
        <span class="eq-freq-label">${EQ_LABELS[idx]}</span>
      `;
      eqSlidersGrid.appendChild(col);

      const slider = col.querySelector<HTMLInputElement>(`#eqSlider${idx}`);
      const valLabel = col.querySelector<HTMLElement>(`#eqDbVal${idx}`);

      slider?.addEventListener('input', () => {
        const val = parseFloat(slider.value);
        currentEqGains[idx] = val;
        if (valLabel) valLabel.textContent = val > 0 ? `+${val}dB` : `${val}dB`;
        col.classList.toggle('active-band', Math.abs(val) > 0.1);
        if (selEqPreset) selEqPreset.value = 'custom';
        drawEqCurve(currentEqGains);
        sendEqUpdate();
      });
    });
  }

  function updateEqSlidersUI(gains: number[]) {
    if (!Array.isArray(gains)) return;
    gains.forEach((g, idx) => {
      const slider = document.getElementById(`eqSlider${idx}`) as HTMLInputElement | null;
      const valLabel = document.getElementById(`eqDbVal${idx}`);
      const col = document.getElementById(`eqCol${idx}`);
      if (slider) slider.value = String(g);
      if (valLabel) valLabel.textContent = g > 0 ? `+${g}dB` : `${g}dB`;
      if (col) col.classList.toggle('active-band', Math.abs(g) > 0.1);
    });
    drawEqCurve(gains);
  }

  function sendEqUpdate() {
    void browser.runtime.sendMessage({
      action: 'bitchord_action',
      command: 'set_eq',
      args: { gains: currentEqGains, enabled: eqEnabled },
    });
    void browser.storage.local.set({
      eqGains: currentEqGains,
      eqEnabled,
      eqPreset: selEqPreset?.value || 'flat',
    });
  }

  btnToggleEq?.addEventListener('click', () => {
    isEqVisible = !isEqVisible;
    if (isEqVisible) {
      isLyricsVisible = false;
      poLyricsDrawer?.classList.add('hidden');
      btnToggleLyrics?.classList.remove('active');
      isQueueVisible = false;
      poQueueDrawer?.classList.add('hidden');
      btnToggleQueue?.classList.remove('active');
    }
    poEqDrawer?.classList.toggle('hidden', !isEqVisible);
    btnToggleEq?.classList.toggle('active', isEqVisible);
    if (isEqVisible) {
      drawEqCurve(currentEqGains);
    }
  });

  btnCloseEq?.addEventListener('click', () => {
    isEqVisible = false;
    poEqDrawer?.classList.add('hidden');
    btnToggleEq?.classList.remove('active');
  });

  chkEqToggle?.addEventListener('change', () => {
    eqEnabled = chkEqToggle.checked;
    sendEqUpdate();
  });

  selEqPreset?.addEventListener('change', () => {
    const preset = selEqPreset.value;
    if (EQ_PRESETS[preset]) {
      currentEqGains = [...EQ_PRESETS[preset]];
      updateEqSlidersUI(currentEqGains);
      sendEqUpdate();
    }
  });

  btnResetEq?.addEventListener('click', () => {
    currentEqGains = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
    if (selEqPreset) selEqPreset.value = 'flat';
    updateEqSlidersUI(currentEqGains);
    sendEqUpdate();
  });

  // --- Apple Music Style DJ AutoMix & Up Next Queue Engine ---
  function renderQueueList(queue: any[]) {
    if (!poQueueList) return;
    if (!queue || !queue.length) {
      poQueueList.innerHTML = '<div class="queue-empty-hint" id="poQueueEmpty">Queue is empty or waiting for BitChord tab...</div>';
      return;
    }
    poQueueList.innerHTML = '';
    queue.forEach((item, idx) => {
      const row = document.createElement('div');
      row.className = `queue-track-item ${item.isPlaying ? 'now-playing' : ''}`;
      row.innerHTML = `
        <img class="queue-item-art" src="${item.artwork || '/icon/32.png'}" alt="Art" />
        <div class="queue-item-info">
          <span class="queue-item-title">${escapeHtml(item.title || `Track ${idx + 1}`)}</span>
          <span class="queue-item-artist">${escapeHtml(item.artist || 'Unknown Artist')}</span>
        </div>
        <div class="queue-item-actions">
          <button class="queue-btn-action play-now" title="Play Now" data-action="jump" data-idx="${item.index}">
            <svg width="12" height="12" viewBox="2 0 28 28" fill="currentColor">
              <path d="M10.345 23.287c.415 0 .763-.15 1.22-.407l12.742-7.404c.838-.481 1.178-.855 1.178-1.46 0-.599-.34-.972-1.178-1.462L11.565 5.158c-.457-.265-.805-.407-1.22-.407-.789 0-1.345.606-1.345 1.57V21.71c0 .971.556 1.577 1.345 1.577z"/>
            </svg>
          </button>
          ${idx > 0 ? `
          <button class="queue-btn-action move-up" title="Move Up" data-action="up" data-idx="${item.index}">
            <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="18 15 12 9 6 15"></polyline>
            </svg>
          </button>` : ''}
          ${idx < queue.length - 1 ? `
          <button class="queue-btn-action move-down" title="Move Down" data-action="down" data-idx="${item.index}">
            <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="6 9 12 15 18 9"></polyline>
            </svg>
          </button>` : ''}
          <button class="queue-btn-action remove" title="Remove from Queue" data-action="remove" data-idx="${item.index}">
            <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>
      `;
      poQueueList.appendChild(row);
    });
  }

  btnToggleQueue?.addEventListener('click', () => {
    isQueueVisible = !isQueueVisible;
    if (isQueueVisible) {
      isLyricsVisible = false;
      poLyricsDrawer?.classList.add('hidden');
      btnToggleLyrics?.classList.remove('active');
      isEqVisible = false;
      poEqDrawer?.classList.add('hidden');
      btnToggleEq?.classList.remove('active');
      renderQueueList(currentQueue);
    }
    poQueueDrawer?.classList.toggle('hidden', !isQueueVisible);
    btnToggleQueue?.classList.toggle('active', isQueueVisible);
  });

  btnCloseQueue?.addEventListener('click', () => {
    isQueueVisible = false;
    poQueueDrawer?.classList.add('hidden');
    btnToggleQueue?.classList.remove('active');
  });

  chkAutoMix?.addEventListener('change', () => {
    const enabled = chkAutoMix.checked;
    const bars = parseInt(selAutoMixBars?.value || '16', 10);
    void browser.runtime.sendMessage({
      action: 'bitchord_action',
      command: 'set_automix',
      args: { enabled, bars },
    });
  });

  selAutoMixBars?.addEventListener('change', () => {
    const bars = parseInt(selAutoMixBars.value, 10);
    void browser.runtime.sendMessage({
      action: 'bitchord_action',
      command: 'set_automix',
      args: { enabled: chkAutoMix?.checked, bars },
    });
  });

  rangeCrossfade?.addEventListener('input', () => {
    const sec = parseInt(rangeCrossfade.value, 10);
    if (lblCrossfadeVal) lblCrossfadeVal.textContent = `${sec}s`;
    void browser.runtime.sendMessage({
      action: 'bitchord_action',
      command: 'set_crossfade',
      args: { seconds: sec },
    });
  });

  btnClearQueue?.addEventListener('click', () => {
    void browser.runtime.sendMessage({
      action: 'bitchord_action',
      command: 'queue_clear',
    });
    currentQueue = [];
    renderQueueList(currentQueue);
  });

  poQueueList?.addEventListener('click', (e) => {
    const target = e.target as HTMLElement | null;
    const btn = target?.closest<HTMLButtonElement>('.queue-btn-action');
    if (!btn) return;
    const action = btn.dataset.action;
    const idx = parseInt(btn.dataset.idx || '0', 10);
    if (action === 'jump') {
      void browser.runtime.sendMessage({ action: 'bitchord_action', command: 'queue_jump', args: { index: idx } });
    } else if (action === 'up') {
      void browser.runtime.sendMessage({ action: 'bitchord_action', command: 'queue_move', args: { from: idx, to: idx - 1 } });
    } else if (action === 'down') {
      void browser.runtime.sendMessage({ action: 'bitchord_action', command: 'queue_move', args: { from: idx, to: idx + 1 } });
    } else if (action === 'remove') {
      void browser.runtime.sendMessage({ action: 'bitchord_action', command: 'queue_remove', args: { index: idx } });
    }
  });

  // --- Live Scrubber Seek Handling ---
  let isDraggingScrubber = false;

  function handleSeekEvent(e: MouseEvent) {
    if (!lastDuration || lastDuration <= 0 || !poScrubberTrack) return;
    const rect = poScrubberTrack.getBoundingClientRect();
    const clickX = Math.max(0, Math.min(rect.width, e.clientX - rect.left));
    const fraction = clickX / rect.width;
    const targetSeconds = fraction * lastDuration;

    if (poScrubberFill) poScrubberFill.style.width = `${fraction * 100}%`;
    if (poScrubberThumb) poScrubberThumb.style.left = `${fraction * 100}%`;
    if (poTimeElapsed) poTimeElapsed.textContent = formatTime(targetSeconds);

    void browser.runtime.sendMessage({
      action: 'bitchord_action',
      command: 'seek',
      args: { seconds: targetSeconds },
    });
  }

  poScrubberHitbox?.addEventListener('click', (e) => handleSeekEvent(e as MouseEvent));

  // --- Player State Application ---
  function applyPlayerStatus(st: Partial<PlayerStatusPayload>) {
    if (!st) return;
    hasBitChordTab = true;
    isBitChordPlaying = !!st.isPlaying;

    // 1. Text & Artwork
    const title = st.title || 'BitChord Player';
    const artist = st.artist || 'Ready to stream';
    const artwork = st.artwork || '/icon/128.png';

    if (bcTrackTitle) bcTrackTitle.textContent = title;
    if (bcTrackArtist) bcTrackArtist.textContent = artist;
    if (bcArtwork) bcArtwork.src = artwork;

    if (poTrackTitle) poTrackTitle.textContent = title;
    if (poTrackArtist) poTrackArtist.textContent = artist;
    if (poArtwork) poArtwork.src = artwork;
    if (poFullBackdrop) poFullBackdrop.style.backgroundImage = `url("${artwork}")`;

    // 2. Times & Progress
    lastCurrentTime = st.currentTime || 0;
    lastDuration = st.duration || 0;

    if (poTimeElapsed) poTimeElapsed.textContent = formatTime(lastCurrentTime);
    if (poTimeTotal) poTimeTotal.textContent = formatTime(lastDuration);

    if (lastDuration > 0 && !isDraggingScrubber) {
      const pct = Math.min(100, Math.max(0, (lastCurrentTime / lastDuration) * 100));
      if (poScrubberFill) poScrubberFill.style.width = `${pct}%`;
      if (poScrubberThumb) poScrubberThumb.style.left = `${pct}%`;
    }

    // 3. Shuffle & Repeat States
    btnPoShuffle?.classList.toggle('active', !!st.shuffle);

    if (st.repeat === 'one') {
      btnPoRepeat?.classList.add('active');
      repeatBadge?.classList.remove('hidden');
    } else if (st.repeat === 'all') {
      btnPoRepeat?.classList.add('active');
      repeatBadge?.classList.add('hidden');
    } else {
      btnPoRepeat?.classList.remove('active');
      repeatBadge?.classList.add('hidden');
    }

    // 4. Volume State
    if (st.volume !== undefined && poVolumeRange && document.activeElement !== poVolumeRange) {
      poVolumeRange.value = String(st.volume);
      iconVolHigh?.classList.toggle('hidden', st.volume === 0);
      iconVolMuted?.classList.toggle('hidden', st.volume > 0);
    }

    // 5. Lyrics Sync
    if (title !== 'BitChord Player') {
      void loadLyricsForTrack(title, artist, lastDuration);
      syncLyricsHighlight(lastCurrentTime);
    }

    // 6. Queue & AutoMix Sync
    if (Array.isArray(st.queue)) {
      currentQueue = st.queue;
      if (poQueueCountBadge) poQueueCountBadge.textContent = `${currentQueue.length} tracks`;
      if (isQueueVisible) renderQueueList(currentQueue);
    }
    if (st.automix !== undefined && chkAutoMix && document.activeElement !== chkAutoMix) {
      chkAutoMix.checked = !!st.automix;
    }
    if (st.automixBars && selAutoMixBars && document.activeElement !== selAutoMixBars) {
      selAutoMixBars.value = String(st.automixBars);
    }
    if (st.crossfadeSec !== undefined && rangeCrossfade && document.activeElement !== rangeCrossfade) {
      rangeCrossfade.value = String(st.crossfadeSec);
      if (lblCrossfadeVal) lblCrossfadeVal.textContent = `${st.crossfadeSec}s`;
    }

    // 7. Equalizer Sync
    if (st.eq) {
      if (Array.isArray(st.eq.gains) && JSON.stringify(st.eq.gains) !== JSON.stringify(currentEqGains)) {
        currentEqGains = st.eq.gains;
        updateEqSlidersUI(currentEqGains);
      }
      if (typeof st.eq.enabled === 'boolean') {
        eqEnabled = st.eq.enabled;
        if (chkEqToggle) chkEqToggle.checked = eqEnabled;
      }
    }

    updateBitChordTabStatus();
  }

  function updateBitChordTabStatus() {
    if (hasBitChordTab) {
      if (bcStatusPill) bcStatusPill.className = 'bc-status-pill online';
      if (bcStatusText) bcStatusText.textContent = isBitChordPlaying ? 'Playing' : 'Connected';
      if (poStatusBadge) poStatusBadge.className = 'po-status-badge online';
      if (poStatusText) poStatusText.textContent = isBitChordPlaying ? 'Playing' : 'Connected';
    } else {
      if (bcStatusPill) bcStatusPill.className = 'bc-status-pill';
      if (bcStatusText) bcStatusText.textContent = 'Web Ready';
      if (poStatusBadge) poStatusBadge.className = 'po-status-badge';
      if (poStatusText) poStatusText.textContent = 'Web Ready';
    }

    if (isBitChordPlaying) {
      bcEqBars?.classList.remove('hidden');
      bcMiniPlay?.classList.add('hidden');
      bcMiniPause?.classList.remove('hidden');

      poEqBadge?.classList.remove('hidden');
      poPlayIcon?.classList.add('hidden');
      poPauseIcon?.classList.remove('hidden');
    } else {
      bcEqBars?.classList.add('hidden');
      bcMiniPlay?.classList.remove('hidden');
      bcMiniPause?.classList.add('hidden');

      poEqBadge?.classList.add('hidden');
      poPlayIcon?.classList.remove('hidden');
      poPauseIcon?.classList.add('hidden');
    }
  }

  // --- Volume Control Slider ---
  let prevVolume = 1;
  poVolumeRange?.addEventListener('input', () => {
    const vol = parseFloat(poVolumeRange.value);
    iconVolHigh?.classList.toggle('hidden', vol === 0);
    iconVolMuted?.classList.toggle('hidden', vol > 0);
    void browser.runtime.sendMessage({
      action: 'bitchord_action',
      command: 'set_volume',
      args: { volume: vol },
    });
  });

  btnPoVolume?.addEventListener('click', () => {
    if (!poVolumeRange) return;
    const current = parseFloat(poVolumeRange.value);
    if (current > 0) {
      prevVolume = current;
      poVolumeRange.value = '0';
      iconVolHigh?.classList.add('hidden');
      iconVolMuted?.classList.remove('hidden');
      void browser.runtime.sendMessage({
        action: 'bitchord_action',
        command: 'set_volume',
        args: { volume: 0 },
      });
    } else {
      const restore = prevVolume || 0.8;
      poVolumeRange.value = String(restore);
      iconVolHigh?.classList.remove('hidden');
      iconVolMuted?.classList.add('hidden');
      void browser.runtime.sendMessage({
        action: 'bitchord_action',
        command: 'set_volume',
        args: { volume: restore },
      });
    }
  });

  // --- Shuffle & Repeat Controls ---
  btnPoShuffle?.addEventListener('click', () => {
    void browser.runtime.sendMessage({ action: 'bitchord_action', command: 'toggle_shuffle' });
  });

  btnPoRepeat?.addEventListener('click', () => {
    void browser.runtime.sendMessage({ action: 'bitchord_action', command: 'cycle_repeat' });
  });

  // --- Transport Buttons with Debounce Protection ---
  let isTogglingBc = false;
  const handleTogglePlay = async () => {
    if (isTogglingBc) return;
    isTogglingBc = true;
    isBitChordPlaying = !isBitChordPlaying;
    updateBitChordTabStatus();
    try {
      await browser.runtime.sendMessage({ action: 'bitchord_action', command: 'toggle' });
    } finally {
      setTimeout(() => {
        isTogglingBc = false;
      }, 350);
    }
  };

  btnBcToggle?.addEventListener('click', handleTogglePlay);
  btnPoToggle?.addEventListener('click', handleTogglePlay);

  // Switch to Player tab when clicking mini player info/artwork on Focus tab
  const bcArtworkContainer = document.querySelector('.bc-artwork-container') as HTMLElement | null;
  const bcInfo = document.querySelector('.bc-info') as HTMLElement | null;
  [bcArtworkContainer, bcInfo].forEach((cEl) => {
    cEl?.addEventListener('click', () => switchTopTab('player'));
    if (cEl) cEl.style.cursor = 'pointer';
  });

  btnBcNext?.addEventListener('click', () => {
    void browser.runtime.sendMessage({ action: 'bitchord_action', command: 'next' });
  });
  btnPoNext?.addEventListener('click', () => {
    void browser.runtime.sendMessage({ action: 'bitchord_action', command: 'next' });
  });

  btnBcPrev?.addEventListener('click', () => {
    void browser.runtime.sendMessage({ action: 'bitchord_action', command: 'previous' });
  });
  btnPoPrev?.addEventListener('click', () => {
    void browser.runtime.sendMessage({ action: 'bitchord_action', command: 'previous' });
  });

  const openBitChordTab = () => {
    const url = currentSettings?.bitchordUrl || 'https://bitchord-8bw.pages.dev/home';
    void browser.tabs.create({ url });
  };
  btnOpenWeb?.addEventListener('click', openBitChordTab);
  btnPoOpenBitChord?.addEventListener('click', openBitChordTab);

  // --- Timer Dial & Controls ---
  function renderTimer(timer: PomodoroTimer | null, settings: PomodoroSettings | null) {
    if (!timer) return;

    const mins = Math.floor(timer.remainingSeconds / 60);
    const secs = timer.remainingSeconds % 60;
    timeDisplay.textContent = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;

    const progressFraction = timer.totalSeconds > 0
      ? (timer.totalSeconds - timer.remainingSeconds) / timer.totalSeconds
      : 0;
    const offset = CIRCLE_CIRCUMFERENCE * (1 - progressFraction);
    if (ringProgress) ringProgress.style.strokeDashoffset = String(offset);

    const modes = ['work', 'shortBreak', 'longBreak'];
    const activeIndex = modes.indexOf(timer.mode);
    modeSelector?.setAttribute('data-active-index', String(activeIndex));

    modeTabs.forEach((tab) => {
      tab.classList.toggle('active', tab.getAttribute('data-mode') === timer.mode);
    });

    if (timer.mode === 'work') {
      sessionBadge.textContent = 'FOCUS SESSION';
      document.documentElement.style.setProperty('--activeAccent', 'var(--keyColor)');
      document.documentElement.style.setProperty('--activeAccentGlow', 'var(--keyColorGlow)');
    } else if (timer.mode === 'shortBreak') {
      sessionBadge.textContent = 'SHORT BREAK';
      document.documentElement.style.setProperty('--activeAccent', 'var(--breakGreen)');
      document.documentElement.style.setProperty('--activeAccentGlow', 'var(--breakGreenGlow)');
    } else {
      sessionBadge.textContent = 'LONG BREAK';
      document.documentElement.style.setProperty('--activeAccent', 'var(--breakGreen)');
      document.documentElement.style.setProperty('--activeAccentGlow', 'var(--breakGreenGlow)');
    }

    if (timer.isRunning) {
      iconPlay?.classList.add('hidden');
      iconPause?.classList.remove('hidden');
    } else {
      iconPlay?.classList.remove('hidden');
      iconPause?.classList.add('hidden');
    }

    const totalInterval = settings?.longBreakInterval || 4;
    cycleText.textContent = `Session ${timer.cycle} of ${totalInterval}`;
    cycleDots.innerHTML = '';
    for (let i = 1; i <= totalInterval; i++) {
      const dot = document.createElement('span');
      dot.className = 'dot';
      if (i < timer.cycle) dot.classList.add('completed');
      else if (i === timer.cycle) dot.classList.add('active');
      cycleDots.appendChild(dot);
    }

    updateBitChordTabStatus();
  }

  btnTogglePlay?.addEventListener('click', async () => {
    if (!currentTimer) return;
    const action = currentTimer.isRunning ? 'pause_timer' : 'start_timer';
    const res: any = await browser.runtime.sendMessage({ action });
    if (res?.timer) {
      currentTimer = res.timer;
      renderTimer(currentTimer, currentSettings);
    }
  });

  btnReset?.addEventListener('click', async () => {
    const res: any = await browser.runtime.sendMessage({ action: 'reset_timer' });
    if (res?.timer) {
      currentTimer = res.timer;
      renderTimer(currentTimer, currentSettings);
    }
  });

  btnSkip?.addEventListener('click', async () => {
    const res: any = await browser.runtime.sendMessage({ action: 'skip_timer' });
    if (res?.timer) {
      currentTimer = res.timer;
      renderTimer(currentTimer, currentSettings);
    }
  });

  modeTabs.forEach((tab) => {
    tab.addEventListener('click', async () => {
      const mode = tab.getAttribute('data-mode');
      const res: any = await browser.runtime.sendMessage({ action: 'switch_mode', mode });
      if (res?.timer) {
        currentTimer = res.timer;
        renderTimer(currentTimer, currentSettings);
      }
    });
  });

  // --- Initial State Fetch & Heartbeat ---
  async function fetchState() {
    try {
      const response: any = await browser.runtime.sendMessage({ action: 'get_state' });
      if (response && response.timer) {
        currentTimer = response.timer;
        currentSettings = response.settings;
        if (currentSettings && (!currentSettings.bitchordUrl || currentSettings.bitchordUrl.includes('bitchord.in'))) {
          currentSettings.bitchordUrl = 'https://bitchord-8bw.pages.dev/home';
        }
        hasBitChordTab = response.hasBitChordTab;
        if (response.playerStatus) {
          applyPlayerStatus(response.playerStatus);
        }
        renderTimer(currentTimer, currentSettings);
      }
    } catch {
      // background service worker might be briefly sleeping
    }
  }

  // Restore EQ from storage
  void browser.storage.local.get(['eqGains', 'eqEnabled', 'eqPreset']).then((stored: any) => {
    if (Array.isArray(stored?.eqGains)) {
      currentEqGains = stored.eqGains;
    }
    if (typeof stored?.eqEnabled === 'boolean') {
      eqEnabled = stored.eqEnabled;
      if (chkEqToggle) chkEqToggle.checked = eqEnabled;
    }
    if (stored?.eqPreset && selEqPreset) {
      selEqPreset.value = stored.eqPreset;
    }
    buildEqSliders();
    updateEqSlidersUI(currentEqGains);
  });

  await fetchState();
  setInterval(fetchState, 1000);

  // Initialize Integrations accounts
  renderIntegrations();
  call('yt.authState', {})
    .then(setYoutube)
    .catch(() => list.replaceChildren(simpleRow('YouTube Music', 'Couldn’t read the session')));
  call('spotify.authState', {}).then(setSpotify).catch(() => undefined);
  call('discord.state', {}).then(setDiscord).catch(() => undefined);

  // Runtime Live Status Updates
  browser.runtime.onMessage.addListener((msg: any) => {
    if (msg.action === 'bitchord_live_status' && msg.status) {
      applyPlayerStatus(msg.status);
    } else if (msg.action === 'timer_state_updated' && msg.timer) {
      currentTimer = msg.timer;
      renderTimer(currentTimer, currentSettings);
    }
  });

  // --- Settings Modal Handling ---
  btnSettings?.addEventListener('click', () => {
    if (!currentSettings) return;
    inpWorkMin.value = String(currentSettings.workDuration);
    rangeWork.value = String(currentSettings.workDuration);
    inpShortMin.value = String(currentSettings.shortBreakDuration);
    rangeShort.value = String(currentSettings.shortBreakDuration);
    inpLongMin.value = String(currentSettings.longBreakDuration);
    rangeLong.value = String(currentSettings.longBreakDuration);
    inpCycleInterval.value = String(currentSettings.longBreakInterval);

    chkAutoPlayBreak.checked = currentSettings.autoPlayMusicOnBreak;
    chkPauseFocus.checked = currentSettings.pauseMusicOnFocus;
    chkAutoOpenTab.checked = currentSettings.autoOpenBitChord;
    chkSoundAlerts.checked = currentSettings.soundAlerts;
    inpBitchordUrl.value = currentSettings.bitchordUrl || 'https://bitchord-8bw.pages.dev/home';

    if (chkTtsVoice) chkTtsVoice.checked = currentSettings.ttsVoiceEnabled !== false;
    if (selTtsStyle) selTtsStyle.value = currentSettings.ttsVoiceStyle || 'chill';
    boxCustomTts?.classList.toggle('hidden', selTtsStyle?.value !== 'custom');
    if (inpTtsBreak) inpTtsBreak.value = currentSettings.customBreakMessage || 'Focus session complete. Stretch, take a breath, and let the music flow.';
    if (inpTtsWork) inpTtsWork.value = currentSettings.customWorkMessage || 'Break is over. Let us enter deep focus flow.';

    settingsModal.classList.remove('hidden');
  });

  selTtsStyle?.addEventListener('change', () => {
    boxCustomTts?.classList.toggle('hidden', selTtsStyle.value !== 'custom');
  });

  btnTestVoice?.addEventListener('click', async () => {
    btnTestVoice.textContent = '🔊 Playing Announcement...';
    try {
      await browser.runtime.sendMessage({
        action: 'test_tts',
        soundType: 'break',
        ttsStyle: selTtsStyle?.value,
        customBreakMessage: inpTtsBreak?.value,
        customWorkMessage: inpTtsWork?.value,
      });
    } finally {
      setTimeout(() => {
        btnTestVoice.textContent = '🔊 Test Spoken Voice Announcement';
      }, 2000);
    }
  });

  btnCloseSettings?.addEventListener('click', () => {
    settingsModal.classList.add('hidden');
  });

  settingsModal?.addEventListener('click', (e) => {
    if (e.target === settingsModal) {
      settingsModal.classList.add('hidden');
    }
  });

  const bindPair = (input: HTMLInputElement, range: HTMLInputElement) => {
    input.addEventListener('input', () => {
      range.value = input.value;
      clearPresetActive();
    });
    range.addEventListener('input', () => {
      input.value = range.value;
      clearPresetActive();
    });
  };

  bindPair(inpWorkMin, rangeWork);
  bindPair(inpShortMin, rangeShort);
  bindPair(inpLongMin, rangeLong);

  function clearPresetActive() {
    presetPills.forEach((p) => p.classList.remove('active'));
  }

  presetPills.forEach((pill) => {
    pill.addEventListener('click', () => {
      clearPresetActive();
      pill.classList.add('active');
      const w = pill.dataset.work || '25';
      const s = pill.dataset.short || '5';
      const l = pill.dataset.long || '15';

      inpWorkMin.value = w;
      rangeWork.value = w;
      inpShortMin.value = s;
      rangeShort.value = s;
      inpLongMin.value = l;
      rangeLong.value = l;
    });
  });

  btnSaveSettings?.addEventListener('click', async () => {
    const updated: Partial<PomodoroSettings> = {
      workDuration: parseInt(inpWorkMin.value, 10) || 25,
      shortBreakDuration: parseInt(inpShortMin.value, 10) || 5,
      longBreakDuration: parseInt(inpLongMin.value, 10) || 15,
      longBreakInterval: parseInt(inpCycleInterval.value, 10) || 4,
      autoPlayMusicOnBreak: chkAutoPlayBreak.checked,
      pauseMusicOnFocus: chkPauseFocus.checked,
      autoOpenBitChord: chkAutoOpenTab.checked,
      soundAlerts: chkSoundAlerts.checked,
      bitchordUrl: inpBitchordUrl.value.trim() || 'https://bitchord-8bw.pages.dev/home',
      ttsVoiceEnabled: chkTtsVoice?.checked ?? true,
      ttsVoiceStyle: selTtsStyle?.value || 'chill',
      customBreakMessage: inpTtsBreak?.value.trim() || '',
      customWorkMessage: inpTtsWork?.value.trim() || '',
    };

    const res: any = await browser.runtime.sendMessage({
      action: 'update_settings',
      settings: updated,
    });

    if (res?.settings) {
      currentSettings = res.settings;
      if (res.timer) currentTimer = res.timer;
      renderTimer(currentTimer, currentSettings);
    }

    settingsModal.classList.add('hidden');
  });
});
