// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Track } from '@/data/music/types';

// Cache Storage, kept in a Map: enough of it for the store's put, match, matchAll and delete.
const entries = new Map<string, Response>();
const cache = {
  put: async (key: string, response: Response) => void entries.set(String(key), response),
  match: async (key: string) => entries.get(String(key))?.clone(),
  matchAll: async () => [...entries.values()].map((r) => r.clone()),
  delete: async (key: string) => entries.delete(String(key)),
};

const WEBM = new Uint8Array([0x1a, 0x45, 0xdf, 0xa3, 0, 0, 0, 0]);
const fetchTrackAudio = vi.fn<(videoId: string, signal?: AbortSignal) => Promise<{ bytes: ArrayBuffer }>>();
vi.mock('@/playback/automix/fetchAudio', () => ({ fetchTrackAudio: (id: string, signal?: AbortSignal) => fetchTrackAudio(id, signal) }));

const track = (videoId: string): Track => ({
  videoId,
  title: `Song ${videoId}`,
  artists: [{ name: 'Artist', id: null }],
  album: null,
  durationSec: 200,
  explicit: false,
  thumbnail: null,
  index: null,
  plays: null,
});

describe('downloading one song from its menu', () => {
  beforeEach(() => {
    entries.clear();
    vi.stubGlobal('caches', { open: async () => cache, delete: async () => (entries.clear(), true) });
    fetchTrackAudio.mockReset();
  });
  afterEach(() => vi.unstubAllGlobals());

  it('keeps the file where Library › Downloads lists it, and removes it again', async () => {
    fetchTrackAudio.mockResolvedValue({ bytes: WEBM.buffer });
    const { downloadSong, removeDownload, useDownloads } = await import('./downloads');
    const { offlineTracks } = await import('./store');

    const running = downloadSong(track('a'));
    expect(useDownloads.getState().active.a).toEqual({ kind: 'running' });
    await running;
    expect(useDownloads.getState().active.a).toBeUndefined();
    expect((await offlineTracks()).map((t) => t.videoId)).toEqual(['a']);

    await removeDownload('a');
    expect(await offlineTracks()).toEqual([]);
  });

  it('removes only the one song', async () => {
    fetchTrackAudio.mockResolvedValue({ bytes: WEBM.buffer });
    const { downloadSong, removeDownload } = await import('./downloads');
    const { offlineTracks } = await import('./store');
    await downloadSong(track('a'));
    await downloadSong(track('b'));
    await removeDownload('a');
    expect((await offlineTracks()).map((t) => t.videoId)).toEqual(['b']);
  });

  it('says why a download failed, once', async () => {
    fetchTrackAudio.mockRejectedValue(new Error('SABR only'));
    const { downloadSong, dismissFailure, useDownloads } = await import('./downloads');
    await downloadSong(track('c'));
    expect(useDownloads.getState().active.c).toEqual({ kind: 'failed', reason: 'SABR only' });
    dismissFailure('c');
    expect(useDownloads.getState().active.c).toBeUndefined();
  });

  it('goes back to Download when cancelled, keeping nothing', async () => {
    fetchTrackAudio.mockImplementation(
      (_, signal) => new Promise((_, reject) => signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))),
    );
    const { cancelDownload, downloadSong, useDownloads } = await import('./downloads');
    const { offlineTracks } = await import('./store');
    const running = downloadSong(track('d'));
    await vi.waitFor(() => expect(fetchTrackAudio).toHaveBeenCalled());
    cancelDownload('d');
    await running;
    expect(useDownloads.getState().active.d).toBeUndefined();
    expect(await offlineTracks()).toEqual([]);
  });
});
