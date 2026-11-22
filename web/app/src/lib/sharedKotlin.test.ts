import { artworkAt, bestPart, BestPartLine, bucketMinus, bucketPlus, playlistMoves, ReplayMonth, ReplayTrack, searchFilters } from '@bitchord/shared-kotlin';
import { describe, expect, it } from 'vitest';

// The Android app's own rules (shared/commonMain), run here as compiled JavaScript.
describe('shared Kotlin, from the web', () => {
  it('reorders a playlist with the fewest moves (PlaylistOrder.kt)', () => {
    const moves = playlistMoves(['a', 'b', 'c', 'd'], ['b', 'c', 'd', 'a']);
    expect(moves.map((m) => [m.setVideoId, m.before ?? null])).toEqual([['a', null]]);
  });

  it('upgrades a YouTube thumbnail for a large cover (Models.kt artworkAt)', () => {
    expect(artworkAt('https://i.ytimg.com/vi/abc/hqdefault.jpg', 544)).toBe('https://i.ytimg.com/vi/abc/maxresdefault.jpg');
  });

  it('lists the search filters with their YouTube Music params', () => {
    const library = searchFilters().find((f) => f.id === 'LIBRARY');
    expect(library?.params).toBe('agIYBA==');
    expect(library?.requiresAuth).toBe(true);
  });

  it("adds and subtracts months of listening in the phone's bytes (BucketMath.kt)", () => {
    const month = (ms: number) => new Int8Array(new TextEncoder().encode(JSON.stringify({ month: '2026-11', hours: Array(24).fill(0).map((_, h) => (h === 20 ? ms : 0)) })));
    const read = (b: Int8Array) => JSON.parse(new TextDecoder().decode(b));
    expect(read(bucketPlus(month(1_000), month(500))).hours[20]).toBe(1_500);
    expect(read(bucketMinus(month(1_500), month(1_000))!).hours[20]).toBe(500);
    expect(bucketMinus(month(1_000), month(1_000))).toBeNull();
    expect(() => bucketPlus(month(1), new Int8Array(new TextEncoder().encode('{"month":"2026-10"}')))).toThrow();
  });

  it('reads the months the web Replay records, so sync works on real data', () => {
    const song = new ReplayTrack('aaaaaaaaaaa', 'Song', 'Band', 'Record', null, null, null);
    const recorded = (ms: number) => {
      const month = new ReplayMonth('2026-11', null);
      month.record(song, ms, true, Date.UTC(2026, 10, 3), 21, 3);
      return new Int8Array(new TextEncoder().encode(month.json()));
    };
    const read = (b: Int8Array) => JSON.parse(new TextDecoder().decode(b));
    expect(read(bucketPlus(recorded(4_000), recorded(2_000))).tracks[0].ms).toBe(6_000);
    expect(read(bucketMinus(recorded(4_000), recorded(1_000))!).tracks[0].ms).toBe(3_000);
  });
});

// app/src/test/.../ReplayBestPartTest.kt's cases, answered by the same BestPart.kt.
describe('Best Part (BestPart.kt), from the web', () => {
  const segment = 2_400;
  const lyrics = [
    [10_000, 'Woke up early in the morning'], [14_000, 'Coffee on the window sill'], [18_000, 'Every road is getting longer'],
    [22_000, ''], [30_000, 'Hold on to me'], [34_000, 'Never let go'], [50_000, 'Streetlights humming out of tune'],
    [54_000, 'Somebody singing on the bus'], [70_000, 'Hold on to me'], [74_000, 'Never let go'],
    [110_000, 'Hold on, to me!'], [114_000, 'Never let go'],
  ].map(([t, s]) => new BestPartLine(t as number, s as string));
  // ReplayBestPartTest.decaying(40, 50) as YouTube's `next` answer, with the chapters entity it must skip.
  const decaying = Array.from({ length: 100 }, (_, i) => (i === 0 ? 1 : 0.6 - 0.003 * i) + (i >= 40 && i <= 50 ? 0.25 : 0));
  const next = (heat: number[]) =>
    JSON.stringify({
      frameworkUpdates: { entityBatchUpdate: { mutations: [
        { payload: { macroMarkersListEntity: { markersList: { markerType: 'MARKER_TYPE_CHAPTERS', markers: [] } } } },
        { payload: { macroMarkersListEntity: { markersList: { markerType: 'MARKER_TYPE_HEATMAP',
          markers: heat.map((h, i) => ({ startMillis: String(i * segment), durationMillis: String(segment), intensityScoreNormalized: h })) } } } },
      ] } },
    });

  it('trusts the curve, then the words, then the fallback', () => {
    expect(bestPart(next(decaying), lyrics, 240_000).source).toBe('HEATMAP');
    expect(bestPart(null, lyrics, 240_000).source).toBe('LYRICS');
    expect(bestPart(next(Array(100).fill(0.4)), lyrics, 240_000).source).toBe('LYRICS');
    const nothing = bestPart(null, [], 200_000);
    expect([nothing.source, nothing.startMs]).toEqual(['FALLBACK', 60_000]);
  });

  it('starts the chorus where the most repeated lines first come', () => {
    expect(bestPart(null, lyrics, 180_000).startMs).toBeLessThanOrEqual(30_000);
  });
});
