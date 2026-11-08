// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { create } from 'zustand';

const calls: { track: unknown }[] = [];
let audio: (EventTarget & { currentTime: number; duration: number; playbackRate: number }) | null = null;
const player = create<{ nowPlaying: { videoId: string; title: string; artist: string } | null; status: string; queue: []; index: number; deck: number }>()(
  () => ({ nowPlaying: null, status: 'idle', queue: [], index: 0, deck: 0 }),
);

vi.mock('@/state/player', () => ({ usePlayer: player, getAudio: () => audio }));
vi.mock('@/extension/bridge', () => ({
  getBridge: () => ({
    call: async (_: string, payload: { track: unknown }) => {
      if (payload.track) calls.push(payload);
    },
  }),
}));
vi.mock('@/extension/useDiscord', () => ({ useDiscord: () => ({ state: 'checking' }), useDiscordOptions: create(() => ({ options: {} })) }));
vi.mock('@/lib/artwork', () => ({ sizedArtwork: (url: string) => url }));

const { startPublisher } = await import('./discordPresence');

describe('discord presence', () => {
  let stop: (() => void) | null = null;
  afterEach(() => {
    stop?.();
    vi.useRealTimers();
  });

  it('republishes on a seek when the decks were made after it started', async () => {
    vi.useFakeTimers();
    stop = startPublisher(); // connected before anything played: no element yet
    await vi.advanceTimersByTimeAsync(500);

    // The first play makes the decks; the deck counter only moves on a crossfade.
    audio = Object.assign(new EventTarget(), { currentTime: 0, duration: 200, playbackRate: 1 });
    player.setState({ nowPlaying: { videoId: 'abcdefghijk', title: 't', artist: 'a' }, status: 'playing' });
    await vi.advanceTimersByTimeAsync(500);
    expect(calls).toHaveLength(1);

    audio.currentTime = 90;
    audio.dispatchEvent(new Event('seeked'));
    await vi.advanceTimersByTimeAsync(500);
    expect(calls).toHaveLength(2);
  });

  it('republishes a seek that buffers before it plays again', async () => {
    vi.useFakeTimers();
    calls.length = 0;
    audio = Object.assign(new EventTarget(), { currentTime: 0, duration: 200, playbackRate: 1 });
    player.setState({ nowPlaying: { videoId: 'bcdefghijkl', title: 't', artist: 'a' }, status: 'playing' });
    stop = startPublisher();
    await vi.advanceTimersByTimeAsync(500);
    expect(calls).toHaveLength(1);

    // Every seek of the element goes waiting → playing, and the push must survive it.
    audio.currentTime = 90;
    audio.dispatchEvent(new Event('seeked'));
    player.setState({ status: 'buffering' });
    await vi.advanceTimersByTimeAsync(500);
    player.setState({ status: 'playing' });
    await vi.advanceTimersByTimeAsync(500);
    expect(calls).toHaveLength(2);
  });
});
