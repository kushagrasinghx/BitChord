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

// ---- Main App Initializer ----------------------------------------------------

document.addEventListener('DOMContentLoaded', async () => {
  // Elements
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

  // BitChord Integration elements
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
  const inpBitchordUrl = document.getElementById('inpBitchordUrl') as HTMLInputElement;

  const radius = ringProgress?.r?.baseVal?.value || 76;
  const CIRCLE_CIRCUMFERENCE = 2 * Math.PI * radius;
  if (ringProgress) {
    ringProgress.style.strokeDasharray = `${CIRCLE_CIRCUMFERENCE} ${CIRCLE_CIRCUMFERENCE}`;
  }

  // Version and site link
  document.getElementById('version')!.textContent = `v${browser.runtime.getManifest().version}`;
  const site = document.getElementById('site') as HTMLAnchorElement;
  if (site) site.href = __SITE_ORIGINS__[0] ?? 'https://bitchord-8bw.pages.dev/home';

  // Dock View Mode Toggle
  const btnToggleDock = document.getElementById('btnToggleDock');
  const iconDockExpand = document.getElementById('iconDockExpand');
  const iconDockCollapse = document.getElementById('iconDockCollapse');
  const appContainer = document.getElementById('appContainer') || document.querySelector('.app-container');

  if (btnToggleDock && appContainer) {
    void browser.storage.local.get('isCompactDock').then((data: any) => {
      if (data?.isCompactDock) {
        appContainer.classList.add('compact-dock');
        iconDockExpand?.classList.add('hidden');
        iconDockCollapse?.classList.remove('hidden');
      }
    });

    btnToggleDock.addEventListener('click', () => {
      const isCompact = appContainer.classList.toggle('compact-dock');
      if (isCompact) {
        iconDockExpand?.classList.add('hidden');
        iconDockCollapse?.classList.remove('hidden');
      } else {
        iconDockExpand?.classList.remove('hidden');
        iconDockCollapse?.classList.add('hidden');
      }
      void browser.storage.local.set({ isCompactDock: isCompact });
    });
  }

  // Top navigation tabs (Pomodoro vs Integrations)
  const navTabPomodoro = document.getElementById('navTabPomodoro');
  const navTabIntegrations = document.getElementById('navTabIntegrations');
  const viewPomodoro = document.getElementById('viewPomodoro');
  const viewIntegrations = document.getElementById('viewIntegrations');

  if (navTabPomodoro && navTabIntegrations && viewPomodoro && viewIntegrations) {
    navTabPomodoro.addEventListener('click', () => {
      navTabPomodoro.classList.add('active');
      navTabIntegrations.classList.remove('active');
      viewPomodoro.classList.remove('hidden');
      viewIntegrations.classList.add('hidden');
    });

    navTabIntegrations.addEventListener('click', () => {
      navTabIntegrations.classList.add('active');
      navTabPomodoro.classList.remove('active');
      viewIntegrations.classList.remove('hidden');
      viewPomodoro.classList.add('hidden');
    });
  }

  let currentTimer: PomodoroTimer | null = null;
  let currentSettings: PomodoroSettings | null = null;
  let isBitChordPlaying = false;
  let hasBitChordTab = false;

  // 1. Initial State Load
  await fetchState();
  setInterval(fetchState, 1000);

  // Initialize Integrations accounts
  renderIntegrations();
  call('yt.authState', {})
    .then(setYoutube)
    .catch(() => list.replaceChildren(simpleRow('YouTube Music', 'Couldn’t read the session')));
  call('spotify.authState', {}).then(setSpotify).catch(() => undefined);
  call('discord.state', {}).then(setDiscord).catch(() => undefined);

  // 2. Fetch Pomodoro state from background
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
      // background might be briefly asleep
    }
  }

  function applyPlayerStatus(st: Partial<PlayerStatusPayload>) {
    if (!st) return;
    hasBitChordTab = true;
    isBitChordPlaying = !!st.isPlaying;

    if (st.title) bcTrackTitle.textContent = st.title;
    if (st.artist) bcTrackArtist.textContent = st.artist;
    if (st.artwork) bcArtwork.src = st.artwork;

    updateBitChordTabStatus();
  }

  // 3. Render Timer and UI
  function renderTimer(timer: PomodoroTimer | null, settings: PomodoroSettings | null) {
    if (!timer) return;

    // Time text
    const mins = Math.floor(timer.remainingSeconds / 60);
    const secs = timer.remainingSeconds % 60;
    timeDisplay.textContent = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;

    // Ring progress calculation
    if (ringProgress) {
      const progressFraction = timer.totalSeconds > 0
        ? (timer.totalSeconds - timer.remainingSeconds) / timer.totalSeconds
        : 0;
      const offset = CIRCLE_CIRCUMFERENCE * (1 - progressFraction);
      ringProgress.style.strokeDashoffset = String(offset);
    }

    // Mode tabs and theme styling
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

    // Play/Pause button icons
    if (timer.isRunning) {
      iconPlay.classList.add('hidden');
      iconPause.classList.remove('hidden');
    } else {
      iconPlay.classList.remove('hidden');
      iconPause.classList.add('hidden');
    }

    // Cycle dots
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

  function updateBitChordTabStatus() {
    if (hasBitChordTab) {
      bcStatusPill.className = 'bc-status-pill online';
      bcStatusText.textContent = isBitChordPlaying ? 'Playing' : 'Connected';
    } else {
      bcStatusPill.className = 'bc-status-pill';
      bcStatusText.textContent = 'Web Ready';
    }

    if (isBitChordPlaying) {
      bcEqBars.classList.remove('hidden');
      bcMiniPlay.classList.add('hidden');
      bcMiniPause.classList.remove('hidden');
    } else {
      bcEqBars.classList.add('hidden');
      bcMiniPlay.classList.remove('hidden');
      bcMiniPause.classList.add('hidden');
    }
  }

  // 4. Primary Transport Controls
  btnTogglePlay.addEventListener('click', async () => {
    if (!currentTimer) return;
    const action = currentTimer.isRunning ? 'pause_timer' : 'start_timer';
    const res: any = await browser.runtime.sendMessage({ action });
    if (res?.timer) {
      currentTimer = res.timer;
      renderTimer(currentTimer, currentSettings);
    }
  });

  btnReset.addEventListener('click', async () => {
    const res: any = await browser.runtime.sendMessage({ action: 'reset_timer' });
    if (res?.timer) {
      currentTimer = res.timer;
      renderTimer(currentTimer, currentSettings);
    }
  });

  btnSkip.addEventListener('click', async () => {
    const res: any = await browser.runtime.sendMessage({ action: 'skip_timer' });
    if (res?.timer) {
      currentTimer = res.timer;
      renderTimer(currentTimer, currentSettings);
    }
  });

  // Mode switcher click
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

  // 5. BitChord Mini Player Controls
  btnBcToggle.addEventListener('click', async () => {
    const command = isBitChordPlaying ? 'pause' : 'play';
    isBitChordPlaying = !isBitChordPlaying;
    updateBitChordTabStatus();
    await browser.runtime.sendMessage({ action: 'bitchord_action', command });
  });

  btnBcNext.addEventListener('click', async () => {
    await browser.runtime.sendMessage({ action: 'bitchord_action', command: 'next' });
  });

  btnBcPrev.addEventListener('click', async () => {
    await browser.runtime.sendMessage({ action: 'bitchord_action', command: 'previous' });
  });

  btnOpenWeb.addEventListener('click', () => {
    const url = currentSettings?.bitchordUrl || 'https://bitchord-8bw.pages.dev/home';
    void browser.tabs.create({ url });
  });

  // 6. Listen to live updates from background & content scripts
  browser.runtime.onMessage.addListener((msg: any) => {
    if (msg.action === 'bitchord_live_status' && msg.status) {
      applyPlayerStatus(msg.status);
    } else if (msg.action === 'timer_state_updated' && msg.timer) {
      currentTimer = msg.timer;
      renderTimer(currentTimer, currentSettings);
    }
  });

  // 7. Settings Modal Handling
  btnSettings.addEventListener('click', () => {
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

    settingsModal.classList.remove('hidden');
  });

  btnCloseSettings.addEventListener('click', () => {
    settingsModal.classList.add('hidden');
  });

  settingsModal.addEventListener('click', (e) => {
    if (e.target === settingsModal) {
      settingsModal.classList.add('hidden');
    }
  });

  // Slider <-> Input synchronization
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

  // Preset pill selection
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

  // Save Settings
  btnSaveSettings.addEventListener('click', async () => {
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
