// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { line } from '@/lyrics/model';
import { bestPartFor, heatmapFor } from './bestPart';

const lookupLyrics = vi.fn();
vi.mock('@/lyrics/repository', () => ({ lookupLyrics: (...args: unknown[]) => lookupLyrics(...args) }));

const segment = 2_400;
// ReplayBestPartTest.decaying(40, 50): a clear bump a little way in.
const decaying = Array.from({ length: 100 }, (_, i) => (i === 0 ? 1 : 0.6 - 0.003 * i) + (i >= 40 && i <= 50 ? 0.25 : 0));
const next = (heat: number[] | null) =>
  JSON.stringify({
    frameworkUpdates: {
      entityBatchUpdate: {
        mutations: heat
          ? [
              {
                payload: {
                  macroMarkersListEntity: {
                    markersList: {
                      markerType: 'MARKER_TYPE_HEATMAP',
                      markers: heat.map((h, i) => ({ startMillis: String(i * segment), durationMillis: String(segment), intensityScoreNormalized: h })),
                    },
                  },
                },
              },
            ]
          : [],
      },
    },
  });

const fetchMock = vi.fn();
const answer = (body: string) => fetchMock.mockResolvedValueOnce(new Response(body, { status: 200 }));

beforeEach(() => {
  localStorage.clear();
  fetchMock.mockReset();
  lookupLyrics.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('Best Part (BestParts.kt, on the web)', () => {
  it('asks youtube.com once as a simple request, then keeps the curve', async () => {
    answer(next(decaying));
    expect((await heatmapFor('aaaaaaaaaaa'))?.intensity.length).toBe(100);
    expect(await heatmapFor('aaaaaaaaaaa')).not.toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0]! as [string, RequestInit];
    expect(url).toBe('https://www.youtube.com/youtubei/v1/next?prettyPrint=false');
    expect(init.headers).toEqual({ 'Content-Type': 'text/plain;charset=UTF-8' });
    expect(init.credentials).toBe('omit');
    expect(JSON.parse(init.body as string)).toMatchObject({ videoId: 'aaaaaaaaaaa', context: { client: { clientName: 'WEB' } } });
  });

  it('starts at the bump in the curve', async () => {
    answer(next(decaying));
    expect((await bestPartFor('bbbbbbbbbbb', 240_000))?.source).toBe('HEATMAP');
  });

  it('keeps "no curve" for a week, and asks again after', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    answer(next(null));
    expect(await heatmapFor('ccccccccccc')).toBeNull();
    expect(await heatmapFor('ccccccccccc')).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    vi.setSystemTime(Date.now() + 8 * 24 * 3600 * 1000);
    answer(next(decaying));
    expect(await heatmapFor('ccccccccccc')).not.toBeNull();
  });

  it('remembers nothing when the network fails, and falls back to the chorus or a fixed point', async () => {
    fetchMock.mockRejectedValue(new TypeError('offline'));
    expect((await bestPartFor('ddddddddddd', 200_000))?.source).toBe('FALLBACK');
    lookupLyrics.mockResolvedValue({
      synced: true,
      lines: [
        line(10_000, 'Woke up early'),
        line(30_000, 'Hold on to me'),
        line(34_000, 'Never let go'),
        line(50_000, 'Streetlights'),
        line(70_000, 'Hold on to me'),
        line(74_000, 'Never let go'),
      ],
    });
    const chorus = await bestPartFor('ddddddddddd', 200_000, { title: 'Song', artist: 'Artist' });
    expect(chorus?.source).toBe('LYRICS');
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('has no curve to ask for without a YouTube video', async () => {
    expect(await heatmapFor('content://song/1')).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
