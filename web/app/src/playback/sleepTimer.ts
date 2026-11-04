import { create } from 'zustand';

/** Presets offered in the sleep timer picker: 15, 30, 45, 60 minutes (matching SleepTimer.kt). */
export const SLEEP_TIMER_PRESETS = [15, 30, 45, 60] as const;
export type SleepTimerMinutes = (typeof SLEEP_TIMER_PRESETS)[number];

export interface SleepTimerState {
  /** Timestamp in ms (Date.now() + durationMs) when playback pauses, or null when off. */
  deadline: number | null;
  /** The preset that was chosen (e.g. 15, 30, 45, 60), or null when off or custom. */
  minutes: number | null;
  /** Pause when the current track finishes playing instead of after a fixed duration. */
  afterTrack: boolean;
  /** Whether any kind of timer is currently armed. */
  isRunning: boolean;
  /** Start a timer for the given number of minutes. */
  start: (minutes: number) => void;
  /** Pause once the track playing right now finishes. */
  startAfterTrack: () => void;
  /** Cancel any active sleep timer. */
  cancel: () => void;
  /** How many milliseconds are left, or null when no timer is armed. */
  remainingMs: () => number | null;
}

let timeoutId: ReturnType<typeof setTimeout> | null = null;
let pauseHandler: (() => void) | null = null;

export function setSleepTimerPauseHandler(handler: () => void): void {
  pauseHandler = handler;
}

function cancelTimeout(): void {
  if (timeoutId !== null) {
    clearTimeout(timeoutId);
    timeoutId = null;
  }
}

function firePause(): void {
  cancelTimeout();
  useSleepTimer.setState({ deadline: null, minutes: null, afterTrack: false, isRunning: false });
  pauseHandler?.();
}

export const useSleepTimer = create<SleepTimerState>()((set, get) => ({
  deadline: null,
  minutes: null,
  afterTrack: false,
  isRunning: false,

  start: (minutes: number) => {
    cancelTimeout();
    const durationMs = minutes * 60 * 1000;
    const deadline = Date.now() + durationMs;
    set({ deadline, minutes, afterTrack: false, isRunning: true });

    timeoutId = setTimeout(() => {
      firePause();
    }, durationMs);
  },

  startAfterTrack: () => {
    cancelTimeout();
    set({ deadline: null, minutes: null, afterTrack: true, isRunning: true });
  },

  cancel: () => {
    cancelTimeout();
    set({ deadline: null, minutes: null, afterTrack: false, isRunning: false });
  },

  remainingMs: () => {
    const { deadline } = get();
    return deadline ? Math.max(0, deadline - Date.now()) : null;
  },
}));

/**
 * Called by playback engine when a track naturally reaches its end.
 * If afterTrack is armed, cancels the timer, pauses playback, and returns true.
 */
export function checkSleepAfterTrack(): boolean {
  const { afterTrack, cancel } = useSleepTimer.getState();
  if (afterTrack) {
    cancel();
    pauseHandler?.();
    return true;
  }
  return false;
}

/** Formats remaining milliseconds into human-readable e.g. "14m" or "45s". */
export function formatSleepRemaining(ms: number | null): string | null {
  if (ms === null) return null;
  const totalSec = Math.ceil(ms / 1000);
  if (totalSec <= 0) return '0m';
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  return min > 0 ? `${min}m` : `${sec}s`;
}
