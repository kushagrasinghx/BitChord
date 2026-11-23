/**
 * BitChord Pomodoro Focus Timer & Audio Engine Module
 * Integrated custom Pomodoro timer engine with music playback automation,
 * 10-Band Graphic Equalizer, DJ AutoMix / Queue management, and TTS chimes.
 */

import { matchPatternsFor, SITE_ORIGINS } from '../site.config';

export interface PomodoroSettings {
  workDuration: number; // in minutes
  shortBreakDuration: number;
  longBreakDuration: number;
  longBreakInterval: number;
  autoPlayMusicOnBreak: boolean;
  pauseMusicOnFocus: boolean;
  autoOpenBitChord: boolean;
  autoStartBreaks: boolean;
  autoStartWork: boolean;
  soundAlerts: boolean;
  desktopNotifications: boolean;
  bitchordUrl: string;
  enablePomodoro: boolean;
  ttsVoiceEnabled: boolean;
  ttsVoiceStyle: string;
  customBreakMessage: string;
  customWorkMessage: string;
}

export interface PomodoroTimer {
  mode: 'work' | 'shortBreak' | 'longBreak';
  isRunning: boolean;
  remainingSeconds: number;
  totalSeconds: number;
  targetEndTime: number | null;
  cycle: number;
  completedTotal: number;
}

export interface PlayerStatusPayload {
  connected: boolean;
  isPlaying: boolean;
  title: string;
  artist: string;
  artwork: string;
  currentTime?: number;
  duration?: number;
  volume?: number;
  shuffle?: boolean;
  repeat?: string;
  hasLiveLyrics?: boolean;
  queue?: any[];
  crossfadeSec?: number;
  automix?: boolean;
  automixBars?: number;
  eq?: {
    enabled: boolean;
    gains: number[];
  };
  url?: string;
}

export const DEFAULT_POMODORO_SETTINGS: PomodoroSettings = {
  workDuration: 25,
  shortBreakDuration: 5,
  longBreakDuration: 15,
  longBreakInterval: 4,
  autoPlayMusicOnBreak: true,
  pauseMusicOnFocus: true,
  autoOpenBitChord: true,
  autoStartBreaks: true,
  autoStartWork: false,
  soundAlerts: true,
  desktopNotifications: true,
  bitchordUrl: 'https://bitchord-8bw.pages.dev/home',
  enablePomodoro: true,
  ttsVoiceEnabled: true,
  ttsVoiceStyle: 'chill',
  customBreakMessage: '',
  customWorkMessage: '',
};

export const DEFAULT_POMODORO_TIMER: PomodoroTimer = {
  mode: 'work',
  isRunning: false,
  remainingSeconds: 25 * 60,
  totalSeconds: 25 * 60,
  targetEndTime: null,
  cycle: 1,
  completedTotal: 0,
};

let tickInterval: ReturnType<typeof setInterval> | null = null;
let lastBitChordStatus: PlayerStatusPayload | null = null;

// Offscreen audio player manager & Customizable TTS Voice Announcement
export async function playChimeAudio(type: 'break' | 'work', overrideOptions: Partial<PomodoroSettings & { isTest?: boolean }> = {}) {
  const data = await browser.storage.local.get('settings');
  const s = { ...DEFAULT_POMODORO_SETTINGS, ...(data.settings as Partial<PomodoroSettings>), ...overrideOptions };
  if (s.soundAlerts === false && !overrideOptions.isTest) return;

  try {
    // @ts-ignore
    const chromeObj = typeof chrome !== 'undefined' ? chrome : null;
    if (chromeObj?.offscreen?.createDocument && chromeObj?.runtime?.getContexts) {
      const existingContexts = await chromeObj.runtime.getContexts({
        contextTypes: ['OFFSCREEN_DOCUMENT'],
      });

      if (existingContexts.length === 0) {
        await chromeObj.offscreen.createDocument({
          url: browser.runtime.getURL('audio/offscreen.html'),
          reasons: ['AUDIO_PLAYBACK'],
          justification: 'Play Pomodoro session transition chimes and TTS announcements',
        });
      }
    }

    browser.runtime.sendMessage({
      action: 'play_chime',
      soundType: type,
      ttsEnabled: s.ttsVoiceEnabled !== false,
      ttsStyle: s.ttsVoiceStyle || 'chill',
      customBreakMessage: s.customBreakMessage || '',
      customWorkMessage: s.customWorkMessage || '',
      isTest: !!overrideOptions.isTest,
    }).catch(() => {});
  } catch (err) {
    console.warn('[BitChord Pomodoro] Offscreen chime warning:', err);
  }
}

export async function getBitChordTabs(): Promise<Browser.tabs.Tab[]> {
  try {
    const allTabs = await browser.tabs.query({});
    const matched = allTabs.filter(
      (t) =>
        t.url &&
        (t.url.includes('bitchord-8bw.pages.dev') ||
          t.url.includes('bitchord.in') ||
          t.url.includes('localhost:5173') ||
          t.url.includes('127.0.0.1:5173') ||
          t.url.includes('bitchord.org')),
    );
    if (matched.length > 0) return matched;
  } catch {}

  const patterns = [
    ...matchPatternsFor(SITE_ORIGINS),
    '*://bitchord.in/*',
    '*://*.bitchord.in/*',
    '*://bitchord-8bw.pages.dev/*',
    '*://*.pages.dev/*',
    '*://localhost/*',
    '*://127.0.0.1/*',
  ];
  try {
    return await browser.tabs.query({ url: patterns });
  } catch {
    return [];
  }
}

// Injects content scripts into tabs that were already open before extension reload
export async function injectIntoExistingTabs() {
  try {
    const tabs = await getBitChordTabs();
    console.log(`[BitChord Pomodoro] Found ${tabs.length} existing BitChord tab(s) to verify/re-inject.`);
    // @ts-ignore
    const chromeObj = typeof chrome !== 'undefined' ? chrome : (browser as any);
    for (const tab of tabs) {
      if (!tab.id) continue;
      try {
        if (chromeObj?.scripting?.executeScript) {
          await chromeObj.scripting.executeScript({
            target: { tabId: tab.id },
            world: 'MAIN',
            func: () => {
              window.postMessage({ channel: 'POMODORO_MAIN_COMMAND', command: 'get_status' }, '*');
            },
          }).catch(() => {});
        }
      } catch {}
    }
  } catch (err) {
    console.warn('[BitChord Pomodoro] injectIntoExistingTabs warning:', err);
  }
}

// Directly queries live DOM and audio state inside the tab
export async function queryBitChordTabDirectly(tabId: number): Promise<PlayerStatusPayload | null> {
  try {
    // @ts-ignore
    const chromeObj = typeof chrome !== 'undefined' ? chrome : (browser as any);
    if (chromeObj?.scripting?.executeScript) {
      const results = await chromeObj.scripting.executeScript({
        target: { tabId },
        world: 'MAIN',
        func: () => {
          let title = 'BitChord Player';
          let artist = 'Ready to stream';
          let artwork = '';
          let isPlaying = false;
          let currentTime = 0;
          let duration = 0;
          let volume = 1;
          let shuffle = false;
          let repeat = 'off';

          // 1. Play/Pause button detection
          const pauseBtn = document.querySelector('button[aria-label="Pause"]:not([disabled])');
          const playBtn = document.querySelector('button[aria-label="Play"]:not([disabled])');
          if (pauseBtn) isPlaying = true;
          else if (playBtn) isPlaying = false;

          // 2. MediaSession detection
          if ('mediaSession' in navigator) {
            if (navigator.mediaSession.playbackState) {
              isPlaying = navigator.mediaSession.playbackState === 'playing';
            }
            const meta = navigator.mediaSession.metadata;
            if (meta) {
              if (meta.title) title = meta.title;
              if (meta.artist) artist = meta.artist;
              if (meta.artwork && meta.artwork.length > 0) {
                artwork = meta.artwork[meta.artwork.length - 1].src;
              }
            }
          }

          // 3. Audio elements
          const domAudios = Array.from(document.querySelectorAll('audio'));
          const playingAudio = domAudios.find((a) => !a.paused && a.currentTime > 0) || domAudios[0];
          if (playingAudio) {
            if (!playingAudio.paused && playingAudio.currentTime > 0) isPlaying = true;
            currentTime = playingAudio.currentTime || 0;
            duration = playingAudio.duration || 0;
            volume = playingAudio.volume !== undefined ? playingAudio.volume : 1;
          }

          // 4. LCD DOM elements
          const titleEl = document.querySelector('.group\\/lcd .type-body-emphasized') || document.querySelector('[role="region"][aria-label="Player"] .type-body-emphasized');
          if (titleEl && titleEl.textContent && title === 'BitChord Player') title = titleEl.textContent.trim();

          const artistEl = document.querySelector('.group\\/lcd .type-callout-medium') || document.querySelector('[role="region"][aria-label="Player"] .type-callout-medium');
          if (artistEl && artistEl.textContent && artist === 'Ready to stream') artist = artistEl.textContent.trim();

          const imgEl = document.querySelector('.group\\/lcd button[aria-label="Open full screen player"] img') || document.querySelector('[role="region"][aria-label="Player"] img');
          if (imgEl && (imgEl as HTMLImageElement).src && !artwork) artwork = (imgEl as HTMLImageElement).src;

          // 5. Shuffle & Repeat
          const shuffleBtn = document.querySelector('button[aria-label="Shuffle"]');
          if (shuffleBtn) shuffle = shuffleBtn.getAttribute('aria-pressed') === 'true';

          const repeatBtn = document.querySelector('button[aria-label^="Repeat"]');
          if (repeatBtn) {
            const l = repeatBtn.getAttribute('aria-label') || '';
            if (l.includes('one')) repeat = 'one';
            else if (l.includes('all') || repeatBtn.getAttribute('aria-pressed') === 'true') repeat = 'all';
            else repeat = 'off';
          }

          // 6. Mixer settings from localStorage or store
          let crossfadeSec = 0;
          let automix = false;
          let automixBars = 16;
          let queue: any[] = [];
          try {
            const s = JSON.parse(localStorage.getItem('bitchord.player.settings') || '{}');
            if (s.crossfadeSec !== undefined) crossfadeSec = s.crossfadeSec;
            if (s.automix !== undefined) automix = !!s.automix;
            if (s.automixBars !== undefined) automixBars = s.automixBars;
          } catch {}

          if ((window as any).__bcPlayer) {
            const st = (window as any).__bcPlayer.getState();
            if (st) {
              if (st.crossfadeSec !== undefined) crossfadeSec = st.crossfadeSec;
              if (st.automix !== undefined) automix = !!st.automix;
              if (st.automixBars !== undefined) automixBars = st.automixBars;
              if (Array.isArray(st.queue)) {
                queue = st.queue.slice(0, 35).map((entry: any, idx: number) => ({
                  index: idx,
                  title: entry.track?.title || entry.title || `Track ${idx + 1}`,
                  artist: entry.track?.artist || entry.artist || '',
                  artwork: entry.track?.artworkUrl || null,
                  durationSec: entry.track?.durationSec || 0,
                  isPlaying: idx === st.index,
                }));
              }
            }
          }

          return {
            connected: true,
            isPlaying,
            title,
            artist,
            artwork,
            currentTime,
            duration,
            volume,
            shuffle,
            repeat,
            crossfadeSec,
            automix,
            automixBars,
            queue,
            url: window.location.href,
          };
        },
      });
      if (results && results[0] && results[0].result) {
        return results[0].result as PlayerStatusPayload;
      }
    }
  } catch (err) {
    console.warn('[BitChord Pomodoro] queryBitChordTabDirectly error:', err);
  }
  return null;
}

export async function sendBitChordCommand(command: string, args?: any) {
  const tabs = await getBitChordTabs();
  console.log(`[BitChord Pomodoro] Dispatching player command "${command}" to ${tabs.length} tab(s) with args:`, args);

  if (tabs.length > 0) {
    for (const tab of tabs) {
      if (!tab.id) continue;
      // @ts-ignore
      const chromeObj = typeof chrome !== 'undefined' ? chrome : (browser as any);
      try {
        if (chromeObj?.scripting?.executeScript) {
          await chromeObj.scripting.executeScript({
            target: { tabId: tab.id },
            world: 'MAIN',
            func: (cmd: string, a: any) => {
              if (cmd === 'toggle') {
                const pauseBtn = document.querySelector<HTMLButtonElement>('button[aria-label="Pause"]:not([disabled])');
                if (pauseBtn) { pauseBtn.click(); return; }
                const playBtn = document.querySelector<HTMLButtonElement>('button[aria-label="Play"]:not([disabled])');
                if (playBtn) { playBtn.click(); return; }
                const audios = Array.from(document.querySelectorAll('audio'));
                const aud = audios.find((el) => !el.paused) || audios[0];
                if (aud) {
                  if (aud.paused) aud.play().catch(() => {});
                  else aud.pause();
                  return;
                }
                const shelfPlay =
                  document.querySelector<HTMLButtonElement>('button[aria-label^="Play "]:not([disabled])') ||
                  document.querySelector<HTMLButtonElement>('.group\\/track button:not([disabled])') ||
                  document.querySelector<HTMLButtonElement>('button[aria-label*="Play"]:not([disabled])');
                if (shelfPlay) { shelfPlay.click(); return; }
                window.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', code: 'Space', keyCode: 32, which: 32, bubbles: true }));
              } else if (cmd === 'play') {
                const pauseBtn = document.querySelector('button[aria-label="Pause"]:not([disabled])');
                if (pauseBtn) return;
                const playBtn = document.querySelector<HTMLButtonElement>('button[aria-label="Play"]:not([disabled])');
                if (playBtn) { playBtn.click(); return; }
                const shelfPlay =
                  document.querySelector<HTMLButtonElement>('button[aria-label^="Play "]:not([disabled])') ||
                  document.querySelector<HTMLButtonElement>('.group\\/track button:not([disabled])');
                if (shelfPlay) { shelfPlay.click(); return; }
                window.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', code: 'Space', keyCode: 32, which: 32, bubbles: true }));
              } else if (cmd === 'pause') {
                const pauseBtn = document.querySelector<HTMLButtonElement>('button[aria-label="Pause"]:not([disabled])');
                if (pauseBtn) { pauseBtn.click(); return; }
                document.querySelectorAll('audio').forEach((el) => { try { el.pause(); } catch {} });
                window.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', code: 'Space', keyCode: 32, which: 32, bubbles: true }));
              } else if (cmd === 'next') {
                const nextBtn = document.querySelector<HTMLButtonElement>('button[aria-label="Next"]:not([disabled])');
                if (nextBtn) { nextBtn.click(); return; }
                window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', code: 'ArrowRight', ctrlKey: true, bubbles: true }));
              } else if (cmd === 'previous') {
                const prevBtn = document.querySelector<HTMLButtonElement>('button[aria-label="Previous"]:not([disabled])');
                if (prevBtn) { prevBtn.click(); return; }
                window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', code: 'ArrowLeft', ctrlKey: true, bubbles: true }));
              } else if (cmd === 'seek' && a && typeof a.seconds === 'number') {
                document.querySelectorAll('audio').forEach((el) => { el.currentTime = a.seconds; });
              } else if (cmd === 'toggle_shuffle') {
                const btn = document.querySelector<HTMLButtonElement>('button[aria-label="Shuffle"]:not([disabled])');
                if (btn) btn.click();
              } else if (cmd === 'cycle_repeat') {
                const btn = document.querySelector<HTMLButtonElement>('button[aria-label^="Repeat"]:not([disabled])');
                if (btn) btn.click();
              } else if (cmd === 'set_volume' && a && typeof a.volume === 'number') {
                document.querySelectorAll('audio').forEach((el) => { el.volume = a.volume; });
              } else if (cmd === 'set_crossfade' && a) {
                if ((window as any).__bcPlayer) (window as any).__bcPlayer.getState().setCrossfade(a.seconds);
                try {
                  const s = JSON.parse(localStorage.getItem('bitchord.player.settings') || '{}');
                  s.crossfadeSec = a.seconds;
                  s.automix = false;
                  localStorage.setItem('bitchord.player.settings', JSON.stringify(s));
                } catch {}
              } else if (cmd === 'set_automix' && a) {
                if ((window as any).__bcPlayer) {
                  (window as any).__bcPlayer.getState().setAutomix(a.enabled);
                  if (a.bars) (window as any).__bcPlayer.getState().setAutomixBars(a.bars);
                }
                try {
                  const s = JSON.parse(localStorage.getItem('bitchord.player.settings') || '{}');
                  s.automix = !!a.enabled;
                  if (a.bars) s.automixBars = a.bars;
                  localStorage.setItem('bitchord.player.settings', JSON.stringify(s));
                } catch {}
              } else if (cmd === 'queue_jump' && a) {
                if ((window as any).__bcPlayer && typeof (window as any).__bcPlayer.getState().jumpTo === 'function') {
                  (window as any).__bcPlayer.getState().jumpTo(a.index);
                }
              } else if (cmd === 'queue_move' && a) {
                if ((window as any).__bcPlayer && typeof (window as any).__bcPlayer.getState().move === 'function') {
                  (window as any).__bcPlayer.getState().move(a.from, a.to);
                }
              } else if (cmd === 'queue_remove' && a) {
                if ((window as any).__bcPlayer && typeof (window as any).__bcPlayer.getState().removeAt === 'function') {
                  (window as any).__bcPlayer.getState().removeAt(a.index);
                }
              } else if (cmd === 'queue_clear') {
                if ((window as any).__bcPlayer && typeof (window as any).__bcPlayer.getState().clearUpNext === 'function') {
                  (window as any).__bcPlayer.getState().clearUpNext();
                }
              } else if (cmd === 'set_eq' && a) {
                if (typeof (window as any).__setBitChordEq === 'function') {
                  (window as any).__setBitChordEq(a.gains, a.enabled);
                }
              }
            },
            args: [command, args],
          });
        }
      } catch {
        browser.tabs.sendMessage(tab.id, { action: 'bitchord_command', command, args }).catch(() => {});
      }
    }
    return { success: true, tabsCount: tabs.length };
  } else {
    // No BitChord tab active; auto-open if configured and command is 'play' or 'toggle'
    const data = await browser.storage.local.get('settings');
    const settings: PomodoroSettings = { ...DEFAULT_POMODORO_SETTINGS, ...(data.settings as Partial<PomodoroSettings>) };

    if ((command === 'play' || command === 'toggle') && settings.autoOpenBitChord) {
      const url = settings.bitchordUrl || 'https://bitchord-8bw.pages.dev/home';
      console.log('[BitChord Pomodoro] Auto-opening web player:', url);
      const newTab = await browser.tabs.create({ url, active: false });

      if (newTab.id) {
        const listener = (tabId: number, info: Browser.tabs.TabChangeInfo) => {
          if (tabId === newTab.id && info.status === 'complete') {
            browser.tabs.onUpdated.removeListener(listener);
            setTimeout(() => {
              void sendBitChordCommand('play');
            }, 2500);
          }
        };
        browser.tabs.onUpdated.addListener(listener);
      }
      return { success: true, openedTab: true };
    }
    return { success: false, tabsCount: 0 };
  }
}

export function updateBadge(timer: PomodoroTimer | null) {
  if (!timer) return;
  if (!timer.isRunning) {
    if (timer.remainingSeconds === timer.totalSeconds) {
      browser.action?.setBadgeText({ text: '' }).catch(() => {});
    } else {
      browser.action?.setBadgeText({ text: 'II' }).catch(() => {});
      browser.action?.setBadgeBackgroundColor({ color: '#8E8E93' }).catch(() => {});
    }
    return;
  }

  const mins = Math.ceil(timer.remainingSeconds / 60);
  const text = mins >= 1 ? `${mins}m` : '<1m';
  browser.action?.setBadgeText({ text }).catch(() => {});

  if (timer.mode === 'work') {
    browser.action?.setBadgeBackgroundColor({ color: '#FA586A' }).catch(() => {}); // BitChord Crimson
  } else {
    browser.action?.setBadgeBackgroundColor({ color: '#32D74B' }).catch(() => {}); // Break Green
  }
}

export function startTicker() {
  if (tickInterval) clearInterval(tickInterval);
  tickInterval = setInterval(async () => {
    await syncTimerFromTarget();
  }, 1000);
}

export function stopTicker() {
  if (tickInterval) {
    clearInterval(tickInterval);
    tickInterval = null;
  }
  browser.alarms?.clear('pomodoro_alarm').catch(() => {});
}

async function syncTimerFromTarget() {
  const data = await browser.storage.local.get(['timer', 'settings']);
  const timer = data.timer as PomodoroTimer | undefined;
  if (!timer || !timer.isRunning) return;

  if (!timer.targetEndTime) {
    timer.targetEndTime = Date.now() + timer.remainingSeconds * 1000;
  }

  const now = Date.now();
  const diffSec = Math.round((timer.targetEndTime - now) / 1000);

  if (diffSec <= 0) {
    timer.remainingSeconds = 0;
    await browser.storage.local.set({ timer });
    updateBadge(timer);
    await handleTimerComplete();
  } else {
    timer.remainingSeconds = diffSec;
    await browser.storage.local.set({ timer });
    updateBadge(timer);
  }
}

export async function handleTimerComplete() {
  stopTicker();
  const data = await browser.storage.local.get(['timer', 'settings']);
  const s: PomodoroSettings = { ...DEFAULT_POMODORO_SETTINGS, ...(data.settings as Partial<PomodoroSettings>) };
  const currentTimer: PomodoroTimer = { ...DEFAULT_POMODORO_TIMER, ...(data.timer as Partial<PomodoroTimer>) };

  const prevMode = currentTimer.mode;
  let nextMode: 'work' | 'shortBreak' | 'longBreak' = 'work';
  let nextDuration = s.workDuration * 60;
  let autoStart = false;

  if (prevMode === 'work') {
    currentTimer.completedTotal += 1;
    if (currentTimer.cycle % s.longBreakInterval === 0) {
      nextMode = 'longBreak';
      nextDuration = s.longBreakDuration * 60;
    } else {
      nextMode = 'shortBreak';
      nextDuration = s.shortBreakDuration * 60;
    }

    autoStart = s.autoStartBreaks;

    if (s.desktopNotifications && browser.notifications) {
      browser.notifications.create({
        type: 'basic',
        iconUrl: browser.runtime.getURL('/icon/128.png'),
        title: 'Break Time! Take a rest 🎵',
        message: nextMode === 'longBreak'
          ? `Great focus! Enjoy your ${s.longBreakDuration} min long break.`
          : `Session complete! Relax with a ${s.shortBreakDuration} min break. Autoplaying BitChord...`,
      }).catch(() => {});
    }

    // Play chime sound and TTS
    void playChimeAudio('break');

    // Autoplay music on break
    if (s.autoPlayMusicOnBreak) {
      void sendBitChordCommand('play');
    }
  } else {
    nextMode = 'work';
    nextDuration = s.workDuration * 60;
    if (prevMode === 'longBreak') {
      currentTimer.cycle = 1;
    } else {
      currentTimer.cycle += 1;
    }

    autoStart = s.autoStartWork;

    if (s.desktopNotifications && browser.notifications) {
      browser.notifications.create({
        type: 'basic',
        iconUrl: browser.runtime.getURL('/icon/128.png'),
        title: 'Focus Time! ⚡',
        message: `Break is over. Ready to focus for ${s.workDuration} minutes?`,
      }).catch(() => {});
    }

    // Play chime sound and TTS
    void playChimeAudio('work');

    // Pause music on focus
    if (s.pauseMusicOnFocus) {
      void sendBitChordCommand('pause');
    }
  }

  currentTimer.mode = nextMode;
  currentTimer.remainingSeconds = nextDuration;
  currentTimer.totalSeconds = nextDuration;
  currentTimer.isRunning = autoStart;
  currentTimer.targetEndTime = autoStart ? Date.now() + nextDuration * 1000 : null;

  await browser.storage.local.set({ timer: currentTimer });
  updateBadge(currentTimer);

  if (autoStart) {
    browser.alarms?.create('pomodoro_alarm', { when: currentTimer.targetEndTime }).catch(() => {});
    startTicker();
  }

  browser.runtime.sendMessage({ action: 'timer_state_updated', timer: currentTimer }).catch(() => {});
}

export function initPomodoro() {
  // Ensure sidePanel behavior opens panel on extension click
  // @ts-ignore
  if (browser.sidePanel?.setPanelBehavior) {
    // @ts-ignore
    browser.sidePanel.setPanelBehavior({ openPanelOnActionClick: true }).catch(() => {});
  }

  browser.runtime.onInstalled.addListener(async () => {
    // @ts-ignore
    if (browser.sidePanel?.setPanelBehavior) {
      // @ts-ignore
      browser.sidePanel.setPanelBehavior({ openPanelOnActionClick: true }).catch(() => {});
    }
    const data = await browser.storage.local.get(['settings', 'timer']);
    if (!data.settings) {
      await browser.storage.local.set({ settings: DEFAULT_POMODORO_SETTINGS });
    }
    if (!data.timer) {
      await browser.storage.local.set({ timer: DEFAULT_POMODORO_TIMER });
    }
    updateBadge(DEFAULT_POMODORO_TIMER);
    void injectIntoExistingTabs();
  });

  browser.runtime.onStartup.addListener(async () => {
    // @ts-ignore
    if (browser.sidePanel?.setPanelBehavior) {
      // @ts-ignore
      browser.sidePanel.setPanelBehavior({ openPanelOnActionClick: true }).catch(() => {});
    }
    const data = await browser.storage.local.get('timer');
    const timer = data.timer as PomodoroTimer | undefined;
    if (timer && timer.isRunning && timer.targetEndTime) {
      await syncTimerFromTarget();
      startTicker();
    } else if (timer) {
      updateBadge(timer);
    }
    void injectIntoExistingTabs();
  });

  browser.alarms?.onAlarm.addListener((alarm) => {
    if (alarm.name === 'pomodoro_alarm') {
      void handleTimerComplete();
    }
  });

  void injectIntoExistingTabs();
}

export async function handlePomodoroMessage(
  request: any,
  sender: Browser.runtime.MessageSender,
  sendResponse: (res: any) => void,
) {
  const data = await browser.storage.local.get(['timer', 'settings']);
  const currentSettings: PomodoroSettings = { ...DEFAULT_POMODORO_SETTINGS, ...(data.settings as Partial<PomodoroSettings>) };
  const currentTimer: PomodoroTimer = { ...DEFAULT_POMODORO_TIMER, ...(data.timer as Partial<PomodoroTimer>) };

  switch (request.action) {
    case 'start_timer': {
      currentTimer.isRunning = true;
      currentTimer.targetEndTime = Date.now() + currentTimer.remainingSeconds * 1000;
      await browser.storage.local.set({ timer: currentTimer });
      browser.alarms?.create('pomodoro_alarm', { when: currentTimer.targetEndTime }).catch(() => {});
      startTicker();
      updateBadge(currentTimer);

      if (currentTimer.mode === 'work' && currentSettings.pauseMusicOnFocus) {
        void sendBitChordCommand('pause');
      } else if (currentTimer.mode !== 'work' && currentSettings.autoPlayMusicOnBreak) {
        void sendBitChordCommand('play');
      }

      sendResponse({ success: true, timer: currentTimer });
      break;
    }

    case 'pause_timer': {
      currentTimer.isRunning = false;
      currentTimer.targetEndTime = null;
      await browser.storage.local.set({ timer: currentTimer });
      stopTicker();
      updateBadge(currentTimer);
      sendResponse({ success: true, timer: currentTimer });
      break;
    }

    case 'reset_timer': {
      currentTimer.isRunning = false;
      currentTimer.targetEndTime = null;
      let duration = currentSettings.workDuration * 60;
      if (currentTimer.mode === 'shortBreak') duration = currentSettings.shortBreakDuration * 60;
      if (currentTimer.mode === 'longBreak') duration = currentSettings.longBreakDuration * 60;
      currentTimer.remainingSeconds = duration;
      currentTimer.totalSeconds = duration;
      await browser.storage.local.set({ timer: currentTimer });
      stopTicker();
      updateBadge(currentTimer);
      sendResponse({ success: true, timer: currentTimer });
      break;
    }

    case 'skip_timer': {
      await handleTimerComplete();
      const updated = await browser.storage.local.get('timer');
      sendResponse({ success: true, timer: updated.timer });
      break;
    }

    case 'switch_mode': {
      const newMode = request.mode as 'work' | 'shortBreak' | 'longBreak';
      currentTimer.mode = newMode;
      currentTimer.isRunning = false;
      currentTimer.targetEndTime = null;

      let duration = currentSettings.workDuration * 60;
      if (newMode === 'shortBreak') duration = currentSettings.shortBreakDuration * 60;
      if (newMode === 'longBreak') duration = currentSettings.longBreakDuration * 60;

      currentTimer.remainingSeconds = duration;
      currentTimer.totalSeconds = duration;
      await browser.storage.local.set({ timer: currentTimer });
      stopTicker();
      updateBadge(currentTimer);
      sendResponse({ success: true, timer: currentTimer });
      break;
    }

    case 'update_settings': {
      const newSettings: PomodoroSettings = { ...currentSettings, ...request.settings };
      await browser.storage.local.set({ settings: newSettings });

      if (!currentTimer.isRunning) {
        let duration = newSettings.workDuration * 60;
        if (currentTimer.mode === 'shortBreak') duration = newSettings.shortBreakDuration * 60;
        if (currentTimer.mode === 'longBreak') duration = newSettings.longBreakDuration * 60;
        currentTimer.remainingSeconds = duration;
        currentTimer.totalSeconds = duration;
        await browser.storage.local.set({ timer: currentTimer });
        updateBadge(currentTimer);
      }

      sendResponse({ success: true, settings: newSettings, timer: currentTimer });
      break;
    }

    case 'get_state': {
      if (currentSettings.bitchordUrl && currentSettings.bitchordUrl.includes('bitchord.in')) {
        currentSettings.bitchordUrl = 'https://bitchord-8bw.pages.dev/home';
        await browser.storage.local.set({ settings: currentSettings });
      }
      const tabs = await getBitChordTabs();
      if (tabs.length > 0 && tabs[0].id) {
        const directStatus = await queryBitChordTabDirectly(tabs[0].id);
        if (directStatus) {
          lastBitChordStatus = directStatus;
        }
      }
      sendResponse({
        timer: currentTimer,
        settings: currentSettings,
        hasBitChordTab: tabs.length > 0,
        playerStatus: lastBitChordStatus,
      });
      break;
    }

    case 'bitchord_action': {
      const result = await sendBitChordCommand(request.command, request.args);
      sendResponse(result);
      break;
    }

    case 'get_lyrics': {
      try {
        const url = new URL('https://lrclib.net/api/get');
        url.searchParams.set('track_name', request.title || '');
        url.searchParams.set('artist_name', request.artist || '');
        if (request.duration) url.searchParams.set('duration', String(Math.round(request.duration)));
        const res = await fetch(url.toString());
        if (res.ok) {
          const lData = await res.json();
          sendResponse({ success: true, lyrics: lData });
        } else {
          sendResponse({ success: false, error: 'Lyrics not found' });
        }
      } catch (err: any) {
        sendResponse({ success: false, error: err.message });
      }
      break;
    }

    case 'test_tts': {
      try {
        await playChimeAudio(request.soundType || 'break', {
          ttsVoiceEnabled: true,
          ttsVoiceStyle: request.ttsStyle || 'chill',
          customBreakMessage: request.customBreakMessage,
          customWorkMessage: request.customWorkMessage,
          isTest: true,
        });
        sendResponse({ success: true });
      } catch (err: any) {
        sendResponse({ success: false, error: err.message });
      }
      break;
    }

    case 'bitchord_status_change': {
      lastBitChordStatus = request.status;
      browser.runtime
        .sendMessage({
          action: 'bitchord_live_status',
          status: request.status,
        })
        .catch(() => {});
      sendResponse({ received: true });
      break;
    }

    default:
      sendResponse({ error: 'Unknown action' });
  }
}
