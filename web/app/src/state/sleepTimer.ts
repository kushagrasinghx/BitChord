import { create } from 'zustand';
import { usePlayer } from './player';

export const SLEEP_TIMER_PRESETS = [15, 30, 45, 60] as const;

interface SleepTimerState {
  minutes: number | null;
  afterTrack: boolean;
  targetMs: number | null;
  start: (minutes: number) => void;
  startAfterTrack: () => void;
  cancel: () => void;
  remainingMinutes: () => number | null;
}

let timerHandle: ReturnType<typeof setTimeout> | null = null;
let unsubscribePlayer: (() => void) | null = null;

function clearExisting() {
  if (timerHandle) {
    clearTimeout(timerHandle);
    timerHandle = null;
  }
  if (unsubscribePlayer) {
    unsubscribePlayer();
    unsubscribePlayer = null;
  }
}

function pausePlayback() {
  const { status, togglePlay } = usePlayer.getState();
  if (status === 'playing' || status === 'buffering') {
    togglePlay();
  }
}

export const useSleepTimer = create<SleepTimerState>()((set, get) => ({
  minutes: null,
  afterTrack: false,
  targetMs: null,

  start: (minutes: number) => {
    clearExisting();
    const targetMs = Date.now() + minutes * 60 * 1000;
    set({ minutes, afterTrack: false, targetMs });

    timerHandle = setTimeout(() => {
      pausePlayback();
      get().cancel();
    }, minutes * 60 * 1000);
  },

  startAfterTrack: () => {
    clearExisting();
    set({ minutes: null, afterTrack: true, targetMs: null });

    const initialIndex = usePlayer.getState().index;
    unsubscribePlayer = usePlayer.subscribe((state) => {
      if (state.index !== initialIndex || state.status === 'idle') {
        pausePlayback();
        get().cancel();
      }
    });
  },

  cancel: () => {
    clearExisting();
    set({ minutes: null, afterTrack: false, targetMs: null });
  },

  remainingMinutes: () => {
    const { targetMs } = get();
    if (!targetMs) return null;
    const diff = targetMs - Date.now();
    return diff > 0 ? Math.ceil(diff / (60 * 1000)) : null;
  },
}));
