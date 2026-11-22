// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { asEntry, type QueueEntry } from '@/playback/queue/timeline';

vi.mock('@/state/player', () => ({ usePlayer: { getState: () => ({ queue: [], index: -1 }), subscribe: () => () => {} }, getPlayhead: () => null, restoreQueue: () => {} }));

const { parseSaved, snapshot } = await import('./lastQueue');

const queue = (n: number): QueueEntry[] =>
  Array.from({ length: n }, (_, i) =>
    asEntry(
      { videoId: `v${i}`, title: `${i}`, artists: [], album: null, durationSec: null, explicit: false, thumbnail: null, index: null, plays: null },
      'context',
      null,
      `e${i}`,
    ),
  );

// QueueHistoryTest.kt's LastPlayed.window cases, through the same lastPlayedWindow.
describe('last queue snapshot (LastPlayed.kt)', () => {
  it('keeps 25 behind, the current song and 50 ahead, however long the queue', () => {
    const kept = snapshot(queue(10_000), 5_000, 42)!;
    expect(kept.queue[0]!.entryId).toBe('e4975');
    expect(kept.queue).toHaveLength(76);
    expect(kept.queue[kept.index]!.entryId).toBe('e5000');
    expect(kept.positionSec).toBe(42);
  });

  it('never keeps more than 25 played songs', () => {
    const kept = snapshot(queue(10_000), 9_999, 0)!;
    expect(kept.queue[0]!.entryId).toBe('e9974');
    expect(kept.queue).toHaveLength(26);
  });

  it('keeps nothing without a playing song', () => {
    expect(snapshot([], -1, 0)).toBeNull();
    expect(snapshot(queue(3), 3, 0)).toBeNull();
  });

  it('reads back what it wrote', () => {
    const kept = snapshot(queue(5), 2, 12.5)!;
    expect(parseSaved(JSON.stringify(kept))).toEqual(kept);
  });

  it('refuses anything that no longer reads as a queue', () => {
    const kept = snapshot(queue(5), 2, 0)!;
    expect(parseSaved(null)).toBeNull();
    expect(parseSaved('not json')).toBeNull();
    expect(parseSaved(JSON.stringify({ ...kept, index: 9 }))).toBeNull();
    expect(parseSaved(JSON.stringify({ ...kept, index: 1.5 }))).toBeNull();
    expect(parseSaved(JSON.stringify({ ...kept, queue: [...kept.queue, { entryId: 'x', tier: 'context' }] }))).toBeNull();
    expect(parseSaved(JSON.stringify({ ...kept, queue: kept.queue.map((e) => ({ ...e, tier: 'other' })) }))).toBeNull();
    expect(parseSaved(JSON.stringify({ ...kept, positionSec: 'later' }))!.positionSec).toBe(0);
  });
});
