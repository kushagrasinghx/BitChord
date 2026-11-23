/**
 * BitChord Pomodoro Focus Timer Module
 * Integrated custom Pomodoro timer engine with music playback automation for breaks.
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

export async function getBitChordTabs(): Promise<Browser.tabs.Tab[]> {
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

export async function sendBitChordCommand(command: 'play' | 'pause' | 'next' | 'previous') {
  const tabs = await getBitChordTabs();
  console.log(`[BitChord Pomodoro] Dispatching player command "${command}" to ${tabs.length} tab(s)`);

  if (tabs.length > 0) {
    for (const tab of tabs) {
      if (tab.id) {
        browser.tabs.sendMessage(tab.id, { action: 'bitchord_command', command }).catch(async () => {
          try {
            // @ts-ignore
            if (typeof chrome !== 'undefined' && chrome.scripting?.executeScript) {
              // @ts-ignore
              await chrome.scripting.executeScript({
                target: { tabId: tab.id },
                world: 'MAIN',
                func: (cmd: string) => {
                  window.postMessage({ channel: 'POMODORO_MAIN_COMMAND', command: cmd }, '*');
                },
                args: [command],
              });
            }
          } catch {}
        });
      }
    }
    return { success: true, tabsCount: tabs.length };
  } else {
    // No BitChord tab active; auto-open if configured and command is 'play'
    const data = await browser.storage.local.get('settings');
    const settings: PomodoroSettings = { ...DEFAULT_POMODORO_SETTINGS, ...(data.settings as Partial<PomodoroSettings>) };

    if (command === 'play' && settings.autoOpenBitChord) {
      const url = settings.bitchordUrl || 'https://bitchord-8bw.pages.dev/home';
      console.log('[BitChord Pomodoro] Auto-opening web player:', url);
      const newTab = await browser.tabs.create({ url, active: false });

      if (newTab.id) {
        const listener = (tabId: number, info: Browser.tabs.TabChangeInfo) => {
          if (tabId === newTab.id && info.status === 'complete') {
            browser.tabs.onUpdated.removeListener(listener);
            setTimeout(() => {
              browser.tabs.sendMessage(newTab.id!, { action: 'bitchord_command', command: 'play' }).catch(() => {});
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
        title: 'Break Time! 🎵',
        message: nextMode === 'longBreak'
          ? `Great focus! Enjoy your ${s.longBreakDuration} min long break.`
          : `Session complete! Relax with a ${s.shortBreakDuration} min break. Autoplaying BitChord...`,
      }).catch(() => {});
    }

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
  });

  browser.alarms?.onAlarm.addListener((alarm) => {
    if (alarm.name === 'pomodoro_alarm') {
      void handleTimerComplete();
    }
  });
}

export async function handlePomodoroMessage(
  request: any,
  sender: Browser.runtime.MessageSender,
  sendResponse: (res: any) => void,
): Promise<boolean> {
  if (!request || typeof request !== 'object') return false;

  const data = await browser.storage.local.get(['timer', 'settings']);
  const currentTimer: PomodoroTimer = { ...DEFAULT_POMODORO_TIMER, ...(data.timer as Partial<PomodoroTimer>) };
  const currentSettings: PomodoroSettings = { ...DEFAULT_POMODORO_SETTINGS, ...(data.settings as Partial<PomodoroSettings>) };

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
      return true;
    }

    case 'pause_timer': {
      currentTimer.isRunning = false;
      currentTimer.targetEndTime = null;
      await browser.storage.local.set({ timer: currentTimer });
      stopTicker();
      updateBadge(currentTimer);
      sendResponse({ success: true, timer: currentTimer });
      return true;
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
      return true;
    }

    case 'skip_timer': {
      await handleTimerComplete();
      const updated = await browser.storage.local.get('timer');
      sendResponse({ success: true, timer: updated.timer });
      return true;
    }

    case 'switch_mode': {
      const newMode = request.mode;
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
      return true;
    }

    case 'update_settings': {
      const newSettings = { ...currentSettings, ...request.settings };
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
      return true;
    }

    case 'get_state': {
      const tabs = await getBitChordTabs();
      sendResponse({
        timer: currentTimer,
        settings: currentSettings,
        hasBitChordTab: tabs.length > 0,
        playerStatus: lastBitChordStatus,
      });
      return true;
    }

    case 'bitchord_action': {
      const result = await sendBitChordCommand(request.command);
      sendResponse(result);
      return true;
    }

    case 'bitchord_status_change': {
      lastBitChordStatus = request.status;
      browser.runtime.sendMessage({
        action: 'bitchord_live_status',
        status: request.status,
      }).catch(() => {});
      sendResponse({ received: true });
      return true;
    }

    default:
      return false;
  }
}
