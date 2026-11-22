// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';

const state = { gain: 1, pauseAfterTrack: false, paused: false, toggles: 0 };
const ended = new Set<() => void>();

vi.mock('@/state/player', () => ({
  usePlayer: {
    getState: () => ({
      mixing: false,
      togglePlay: () => {
        state.toggles++;
        state.paused = true;
      },
    }),
  },
  getAudio: () => ({ paused: state.paused }),
  setSleepGain: (gain: number) => void (state.gain = gain),
  setPauseAfterTrack: (on: boolean) => void (state.pauseAfterTrack = on),
  onPlaybackTick: () => () => {},
  onTrackEnded: (listener: () => void) => {
    ended.add(listener);
    return () => ended.delete(listener);
  },
}));

const { SLEEP_TIMER, sleepTick, useSleepTimer } = await import('./sleepTimer');
const { sleepFadeVolume } = await import('@bitchord/shared-kotlin');

describe('sleep timer (SleepTimer.kt)', () => {
  beforeEach(() => {
    useSleepTimer.getState().cancel();
    useSleepTimer.getState().setFadeOut(true);
    Object.assign(state, { gain: 1, paused: false, toggles: 0 });
  });

  it('offers the phone’s presets and custom range', () => {
    expect(SLEEP_TIMER.presets).toEqual([15, 30, 45, 60]);
    expect([SLEEP_TIMER.stepMinutes, SLEEP_TIMER.maxMinutes, SLEEP_TIMER.fadeOutMs]).toEqual([5, 720, 30_000]);
  });

  // SleepTimerTest.kt's calculateFadeVolume cases.
  it('fades linearly over the last thirty seconds, and not before or when off', () => {
    expect(sleepFadeVolume(45_000, 30_000, true)).toBe(1);
    expect(sleepFadeVolume(30_000, 30_000, true)).toBe(1);
    expect(sleepFadeVolume(15_000, 30_000, false)).toBe(1);
    expect(sleepFadeVolume(15_000, 30_000, true)).toBeCloseTo(0.5);
    expect(sleepFadeVolume(7_500, 30_000, true)).toBeCloseTo(0.25);
    expect(sleepFadeVolume(0, 30_000, true)).toBe(0);
    expect(sleepFadeVolume(-100, 30_000, true)).toBe(0);
  });

  it('turns the volume down as the deadline nears, then pauses and gives it back', () => {
    useSleepTimer.getState().start(15);
    const deadline = useSleepTimer.getState().deadline!;
    sleepTick(deadline - 60_000);
    expect(state.gain).toBe(1);
    sleepTick(deadline - 15_000);
    expect(state.gain).toBeCloseTo(0.5);
    sleepTick(deadline);
    expect(state.toggles).toBe(1);
    expect(useSleepTimer.getState().deadline).toBeNull();
    expect(state.gain).toBe(1);
  });

  it('keeps full volume to the end with the fade off', () => {
    useSleepTimer.getState().setFadeOut(false);
    useSleepTimer.getState().start(5);
    sleepTick(useSleepTimer.getState().deadline! - 1_000);
    expect(state.gain).toBe(1);
  });

  it('gives the volume back when turned off mid-fade', () => {
    useSleepTimer.getState().start(5);
    sleepTick(useSleepTimer.getState().deadline! - 10_000);
    expect(state.gain).toBeLessThan(1);
    useSleepTimer.getState().cancel();
    expect(state.gain).toBe(1);
    expect(state.toggles).toBe(0);
  });

  it('“after this song” arms the player and is spent when the song ends', () => {
    useSleepTimer.getState().startAfterTrack();
    expect(state.pauseAfterTrack).toBe(true);
    ended.forEach((listener) => listener());
    expect(useSleepTimer.getState().afterTrack).toBe(false);
    expect(state.pauseAfterTrack).toBe(false);
  });
});
