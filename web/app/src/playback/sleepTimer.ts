import { create } from 'zustand';
import { getAudio, setSleepFade, usePlayer } from '@/state/player';
import { toast } from '@/state/toasts';
import { FADE_MS, fadeLevel, remainingMs, type SleepTimer } from './sleepTimerPlan';

interface SleepTimerState {
  timer: SleepTimer | null;
  /** Ms until it stops playback; refreshed while it runs, for the player bar. */
  remaining: number;
  start: (choice: number | 'track') => void;
  cancel: () => void;
}

const TICK_MS = 250;
let ticker: number | null = null;
/** Time left at the last tick, to tell a song that ran out from one skipped. */
let lastLeft = Infinity;

function stop() {
  if (ticker !== null) window.clearInterval(ticker);
  ticker = null;
  lastLeft = Infinity;
  setSleepFade(1);
}

function tick() {
  const { timer } = useSleepTimer.getState();
  if (!timer) return stop();
  const audio = getAudio();
  const player = usePlayer.getState();
  const changed = timer.kind === 'track' && player.nowPlaying?.videoId !== timer.videoId;
  if (changed && lastLeft > 1500) {
    // Skipped to another song: the timer waits for the end of that one instead.
    useSleepTimer.setState({ timer: { kind: 'track', videoId: player.nowPlaying?.videoId ?? null } });
    lastLeft = Infinity;
    return;
  }
  // The song ran out and the next began before a tick caught its end: stop that one.
  const left = changed
    ? 0
    : remainingMs(timer, Date.now(), {
        durationSec: audio?.duration ?? NaN,
        positionSec: audio?.currentTime ?? 0,
        crossfadeSec: player.automix ? 0 : player.crossfadeSec,
      });
  lastLeft = left;
  useSleepTimer.setState({ remaining: left });
  if (left > 0) return setSleepFade(fadeLevel(left, FADE_MS[timer.kind]));
  // Time's up: pause, then put the volume back for whenever play resumes.
  if (player.status === 'loading') return setSleepFade(0); // Paused once it has started, on a later tick.
  if (player.status === 'playing' || player.status === 'buffering') player.togglePlay();
  useSleepTimer.setState({ timer: null, remaining: 0 });
  stop();
  toast('Sleep timer ended');
}

/** The sleep timer: stops playback after some minutes or at the end of the song, fading out first. */
export const useSleepTimer = create<SleepTimerState>()((set) => ({
  timer: null,
  remaining: 0,
  start: (choice) => {
    stop();
    set({ timer: choice === 'track' ? { kind: 'track', videoId: usePlayer.getState().nowPlaying?.videoId ?? null } : { kind: 'minutes', minutes: choice, endsAt: Date.now() + choice * 60_000 } });
    ticker = window.setInterval(tick, TICK_MS);
    tick();
    toast(choice === 'track' ? 'Sleep timer: end of this song' : `Sleep timer: ${choice} minutes`);
  },
  cancel: () => {
    set({ timer: null, remaining: 0 });
    stop();
    toast('Sleep timer off');
  },
}));

// For browser-driven tests, next to __bcPlayer.
if (import.meta.env.DEV) Object.assign(window, { __bcSleep: useSleepTimer });
