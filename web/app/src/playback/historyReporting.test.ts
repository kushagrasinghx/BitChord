// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { create } from 'zustand';

const pings: string[] = [];
const ended = new Set<() => void>();
const audio = { currentTime: 0 };
const player = create<{ nowPlaying: { videoId: string } | null; status: string }>()(() => ({ nowPlaying: null, status: 'idle' }));

vi.mock('@/state/player', () => ({
  usePlayer: player,
  getAudio: () => audio,
  onTrackEnded: (listener: () => void) => {
    ended.add(listener);
    return () => ended.delete(listener);
  },
}));
vi.mock('@/extension/bridge', () => ({
  getBridge: () => ({
    call: async (_: string, { url }: { url: string }) => {
      pings.push(url);
      return { status: 204 };
    },
  }),
}));
vi.mock('@/extension/useExtension', () => ({ extensionQueryKey: ['extension'] }));
vi.mock('@/extension/useYtAuth', () => ({ ensureYtAuth: async () => ({ signedIn: true }) }));
vi.mock('@/lib/queryClient', () => ({
  queryClient: { getQueryData: () => ({ info: { capabilities: ['yt.stats'] } }), invalidateQueries: async () => {} },
}));
vi.mock('youtubei.js/web', () => ({ Constants: { CLIENTS: { YTMUSIC: { VERSION: '1.0' } } } }));
vi.mock('./youtube/innertube', () => ({
  getSignedInnertube: async () => ({
    session: { player: { signature_timestamp: 1 }, context: { client: { hl: 'en', gl: 'US', visitorData: 'v' } } },
    actions: {
      execute: async () => ({
        data: {
          playbackTracking: {
            videostatsPlaybackUrl: { baseUrl: 'https://s.youtube.com/api/stats/playback' },
            videostatsWatchtimeUrl: { baseUrl: 'https://s.youtube.com/api/stats/watchtime' },
          },
        },
      }),
    },
  }),
}));

const { startHistoryReporting } = await import('./historyReporting');
const playbacks = () => pings.filter((u) => u.includes('/playback')).length;

describe('history reporting', () => {
  let stop: (() => void) | null = null;
  afterEach(() => stop?.());

  it('closes a play that ran to its end, so a repeat-one loop is a new play', async () => {
    stop = startHistoryReporting();
    player.setState({ nowPlaying: { videoId: 'abcdefghijk' }, status: 'playing' });
    await vi.waitFor(() => expect(playbacks()).toBe(1));

    audio.currentTime = 212;
    ended.forEach((listener) => listener());
    await vi.waitFor(() => expect(pings.some((u) => u.includes('/watchtime') && u.includes('final=1') && u.includes('et=212'))).toBe(true));

    // Repeat one: the same video starts over.
    audio.currentTime = 0;
    player.setState({ status: 'buffering' });
    player.setState({ status: 'playing' });
    await vi.waitFor(() => expect(playbacks()).toBe(2));
  });
});
