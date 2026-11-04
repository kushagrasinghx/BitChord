import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  checkSleepAfterTrack,
  formatSleepRemaining,
  setSleepTimerPauseHandler,
  SLEEP_TIMER_PRESETS,
  useSleepTimer,
} from './sleepTimer';

describe('sleepTimer', () => {
  let paused = false;

  beforeEach(() => {
    paused = false;
    useSleepTimer.getState().cancel();
    setSleepTimerPauseHandler(() => {
      paused = true;
    });
    vi.useFakeTimers();
  });

  afterEach(() => {
    useSleepTimer.getState().cancel();
    vi.useRealTimers();
  });

  it('initializes in an idle, disarmed state', () => {
    const state = useSleepTimer.getState();
    expect(state.deadline).toBeNull();
    expect(state.minutes).toBeNull();
    expect(state.afterTrack).toBe(false);
    expect(state.isRunning).toBe(false);
    expect(state.remainingMs()).toBeNull();
  });

  it('starts a timer for preset minutes and sets deadline correctly', () => {
    const now = 1000000;
    vi.setSystemTime(now);

    useSleepTimer.getState().start(15);
    const state = useSleepTimer.getState();

    expect(state.minutes).toBe(15);
    expect(state.afterTrack).toBe(false);
    expect(state.isRunning).toBe(true);
    expect(state.deadline).toBe(now + 15 * 60 * 1000);
    expect(state.remainingMs()).toBe(15 * 60 * 1000);
  });

  it('cancels an active timer and resets state', () => {
    useSleepTimer.getState().start(30);
    expect(useSleepTimer.getState().isRunning).toBe(true);

    useSleepTimer.getState().cancel();
    const state = useSleepTimer.getState();
    expect(state.deadline).toBeNull();
    expect(state.minutes).toBeNull();
    expect(state.afterTrack).toBe(false);
    expect(state.isRunning).toBe(false);
    expect(state.remainingMs()).toBeNull();
  });

  it('arms afterTrack and intercepts track advance', () => {
    useSleepTimer.getState().startAfterTrack();
    const state = useSleepTimer.getState();
    expect(state.afterTrack).toBe(true);
    expect(state.deadline).toBeNull();
    expect(state.isRunning).toBe(true);

    expect(paused).toBe(false);
    const intercepted = checkSleepAfterTrack();
    expect(intercepted).toBe(true);
    expect(paused).toBe(true);

    // After firing, it should be cancelled
    expect(useSleepTimer.getState().isRunning).toBe(false);
    expect(useSleepTimer.getState().afterTrack).toBe(false);

    // Subsequent track end should not be intercepted
    expect(checkSleepAfterTrack()).toBe(false);
  });

  it('fires pause when the duration timeout expires', () => {
    useSleepTimer.getState().start(15);
    expect(paused).toBe(false);

    // Advance 14 minutes: still running, not paused
    vi.advanceTimersByTime(14 * 60 * 1000);
    expect(paused).toBe(false);
    expect(useSleepTimer.getState().isRunning).toBe(true);

    // Advance the remaining 1 minute: fires pause and disarms
    vi.advanceTimersByTime(1 * 60 * 1000);
    expect(paused).toBe(true);
    expect(useSleepTimer.getState().isRunning).toBe(false);
    expect(useSleepTimer.getState().deadline).toBeNull();
  });

  it('formats remaining duration accurately', () => {
    expect(formatSleepRemaining(null)).toBeNull();
    expect(formatSleepRemaining(0)).toBe('0m');
    expect(formatSleepRemaining(30 * 1000)).toBe('30s');
    expect(formatSleepRemaining(65 * 1000)).toBe('1m');
    expect(formatSleepRemaining(120 * 1000)).toBe('2m');
    expect(formatSleepRemaining(15 * 60 * 1000)).toBe('15m');
    expect(formatSleepRemaining(45 * 60 * 1000)).toBe('45m');
  });

  it('provides matching presets list', () => {
    expect(SLEEP_TIMER_PRESETS).toEqual([15, 30, 45, 60]);
  });
});
