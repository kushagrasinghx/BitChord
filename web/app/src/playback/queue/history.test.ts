import { describe, expect, it } from 'vitest';
import { trimmedHistory } from './history';
import { asEntry, type QueueEntry } from './timeline';

const queue = (n: number): QueueEntry[] =>
  Array.from({ length: n }, (_, i) =>
    asEntry(
      { videoId: `v${i}`, title: `${i}`, artists: [], album: null, durationSec: null, explicit: false, thumbnail: null, index: null, plays: null },
      'context',
      null,
      `e${i}`,
    ),
  );
const ids = (q: QueueEntry[]) => q.map((e) => e.entryId);

// QueueHistoryTest.kt's numbers, through the same queueHistoryTrimCount.
describe('trimmedHistory (QueueHistoryWindow.kt)', () => {
  it('keeps the first 25 played songs and drops the 26th back', () => {
    expect(trimmedHistory(queue(30), 25, false)).toBeNull();
    const trimmed = trimmedHistory(queue(30), 26, false)!;
    expect(trimmedHistory(queue(200), 100, false)!.index).toBe(25);
    expect(trimmed.index).toBe(25);
    expect(ids(trimmed.queue)).toEqual(ids(queue(30)).slice(1));
  });

  it('sends expired history round to the end on repeat-all, in order', () => {
    const trimmed = trimmedHistory(queue(30), 27, true)!;
    expect(trimmed.index).toBe(25);
    expect(ids(trimmed.queue)).toEqual([...ids(queue(30)).slice(2), 'e0', 'e1']);
    expect(trimmed.queue[trimmed.index]!.entryId).toBe('e27');
  });
});
