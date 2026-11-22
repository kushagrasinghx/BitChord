import { sleepFadeVolume, sleepTimerOptions } from '@bitchord/shared-kotlin';
import { create } from 'zustand';
import { getAudio, onPlaybackTick, onTrackEnded, setPauseAfterTrack, setSleepGain, usePlayer } from '@/state/player';

/** The presets, the custom picker's 5-minute steps up to 12 hours, and the 30-second fade (SleepTimerSteps.kt). */
export const SLEEP_TIMER = sleepTimerOptions();

/**
 * The sleep timer (SleepTimer.kt): a deadline, or "after this song". A
 * deadline rather than a countdown, so nothing has to tick for it to stay
 * right, on performance.now(), so changing the system clock can't cut it
 * short. Like the phone's, a running timer doesn't outlive the page; only the
 * fade setting is kept.
 */
interface SleepTimerState {
  /** On performance.now(); null when no timer is set. */
  deadline: number | null;
  /** The duration chosen, so the menu can tick it; null when off. */
  minutes: number | null;
  /** Pause when the playing song ends, instead of after a wait. */
  afterTrack: boolean;
  /** Turn the volume down over the last 30 seconds (AppSettings.sleepTimerFadeOut, on by default). */
  fadeOut: boolean;
  start: (minutes: number) => void;
  startAfterTrack: () => void;
  cancel: () => void;
  setFadeOut: (on: boolean) => void;
}

const KEY = 'bitchord:sleep-timer';

function loadFadeOut(): boolean {
  try {
    return (JSON.parse(localStorage.getItem(KEY) ?? '{}') as { fadeOut?: boolean }).fadeOut ?? true;
  } catch {
    return true;
  }
}

export const useSleepTimer = create<SleepTimerState>()((set) => ({
  deadline: null,
  minutes: null,
  afterTrack: false,
  fadeOut: loadFadeOut(),
  start: (minutes) => set({ deadline: performance.now() + minutes * 60_000, minutes, afterTrack: false }),
  startAfterTrack: () => set({ deadline: null, minutes: null, afterTrack: true }),
  cancel: () => set({ deadline: null, minutes: null, afterTrack: false }),
  setFadeOut: (fadeOut) => {
    set({ fadeOut });
    try {
      localStorage.setItem(KEY, JSON.stringify({ fadeOut }));
    } catch {
      // Not kept; it still applies for this visit.
    }
  },
}));

/** How long the running timer has left, or null. */
export function remainingMs(now = performance.now()): number | null {
  const { deadline } = useSleepTimer.getState();
  return deadline === null ? null : Math.max(0, deadline - now);
}

/**
 * Turns the volume down over the last 30 seconds, then pauses
 * (PlaybackService.watchSleepTimer). The fade scales the player's volume, so
 * the user's own setting is left as it was and comes back once it's over.
 */
export function sleepTick(now = performance.now()): void {
  const { deadline, fadeOut, cancel } = useSleepTimer.getState();
  if (deadline === null) return;
  const remaining = deadline - now;
  if (remaining > 0) return setSleepGain(sleepFadeVolume(remaining, SLEEP_TIMER.fadeOutMs, fadeOut));
  // Paused first, then the volume is given back: the other way round, the song would jump back up for a moment.
  const player = usePlayer.getState();
  if (player.mixing || getAudio()?.paused === false) player.togglePlay();
  cancel();
}

let interval: ReturnType<typeof setInterval> | null = null;

useSleepTimer.subscribe((s) => {
  setPauseAfterTrack(s.afterTrack);
  if (s.deadline === null) {
    setSleepGain(1);
    if (interval !== null) clearInterval(interval);
    interval = null;
  } else {
    // The playing element's timeupdate drives the fade; this covers the deadline passing while nothing plays.
    interval ??= setInterval(sleepTick, 1000);
  }
});
onPlaybackTick(() => sleepTick());
// The player moves on to the next song without playing it (state/player.ts); the timer is spent.
onTrackEnded(() => {
  if (useSleepTimer.getState().afterTrack) useSleepTimer.getState().cancel();
});
