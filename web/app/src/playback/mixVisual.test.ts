// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { create } from 'zustand';

interface Fake {
  nowPlaying: { videoId: string; artworkUrl: string | null } | null;
  automixState: { state: string; startsInSec: number | null; nextKey: string | null; plan: { durationSec: number } | null } | null;
}
const player = create<Fake>()(() => ({ nowPlaying: null, automixState: null }));
vi.mock('@/state/player', () => ({ usePlayer: player }));
let clockMs = 0;
vi.spyOn(performance, 'now').mockImplementation(() => clockMs);

const { coverage, noiseField, revealAt, useMixReveal } = await import('./mixVisual');

const track = (videoId: string) => ({ videoId, artworkUrl: `https://lh3.googleusercontent.com/${videoId}` });
const scheduled = (startsInSec: number, durationSec: number) => ({ state: 'scheduled', startsInSec, nextKey: 'incoming', plan: { durationSec } });

describe('AutoMix cover dissolve', () => {
  it('dissolves across a blend handoff, inside the overlap, after a short lag', () => {
    // Scheduled well ahead (the incoming cover has had time to load), handed over 4 s into a 16 s blend.
    player.setState({ nowPlaying: track('outgoing'), automixState: scheduled(8, 16) });
    clockMs += 12_000;
    player.setState({ automixState: scheduled(-4, 16) });
    const swap = clockMs / 1000;
    player.setState({ nowPlaying: track('incoming') });
    const reveal = useMixReveal.getState().reveal!;
    expect(reveal.from).toContain('outgoing');
    expect(reveal.to).toContain('incoming');
    // AutoMixArtworkTuning: 0.6 s after the swap, 2.6 s long, well inside the 12 s left.
    expect(reveal.startSec - swap).toBeCloseTo(0.6, 1);
    expect(reveal.endSec - reveal.startSec).toBeCloseTo(2.6, 1);
    expect(revealAt(reveal, reveal.startSec)).toBe(0);
    expect(revealAt(reveal, reveal.endSec)).toBe(1);
  });

  it('cuts instead when too little of the overlap is left, and on a plain skip', () => {
    player.setState({ automixState: null });
    player.setState({ nowPlaying: track('outgoing'), automixState: scheduled(-15.5, 16) });
    player.setState({ nowPlaying: track('incoming') });
    expect(useMixReveal.getState().reveal).toBeNull();
    player.setState({ nowPlaying: track('other'), automixState: null });
    expect(useMixReveal.getState().reveal).toBeNull();
  });

  it('drops a dissolve whose mix was cancelled', () => {
    player.setState({ nowPlaying: track('outgoing'), automixState: scheduled(-4, 16) });
    player.setState({ nowPlaying: track('incoming') });
    expect(useMixReveal.getState().reveal).not.toBeNull();
    player.setState({ automixState: { state: 'playing', startsInSec: null, nextKey: null, plan: null } });
    expect(useMixReveal.getState().reveal).toBeNull();
  });

  it('sweeps the noise mask from nothing to everything', () => {
    const field = noiseField(32, 3.1, 7.7);
    expect(Math.min(...field)).toBeGreaterThanOrEqual(0);
    expect(Math.max(...field)).toBeLessThanOrEqual(1);
    // An organic field: neither flat nor noise from one pixel to the next.
    expect(Math.max(...field) - Math.min(...field)).toBeGreaterThan(0.4);
    expect(Math.abs(field[0]! - field[1]!)).toBeLessThan(0.2);
    expect(field.every((f) => coverage(f, 0) === 0)).toBe(true);
    expect(field.every((f) => coverage(f, 1) === 1)).toBe(true);
    const half = field.filter((f) => coverage(f, 0.5) > 0.5).length / field.length;
    expect(half).toBeGreaterThan(0.1);
    expect(half).toBeLessThan(0.9);
  });
});
