// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { asEntry, type QueueEntry, type QueueTier } from '@/playback/queue/timeline';

vi.mock('@/extension/useExtension', () => ({ extensionQueryKey: ['extension'] }));
vi.mock('@/lib/queryClient', () => ({ queryClient: { getQueryData: () => null } }));
vi.mock('@/playback/lastQueue', () => ({ keepLastQueue: () => {} }));

const { usePlayer, restoreQueue } = await import('./player');

const entry = (id: string, tier: QueueTier = 'context'): QueueEntry =>
  asEntry({ videoId: id, title: id, artists: [], album: null, durationSec: 200, explicit: false, thumbnail: null, index: null, plays: null }, tier, null, id);
const ids = () => usePlayer.getState().queue.map((e) => e.entryId);

// The queue screen's remove and Clear, with Undo (QueueScreenUndo.kt).
describe('queue undo', () => {
  beforeEach(() => {
    vi.useRealTimers();
    usePlayer.setState({ queue: ['a', 'b', 'c', 'd'].map((id) => entry(id)).concat(entry('u1', 'user')), index: 1, undoNotice: null, autoplay: false });
  });

  it('removes rows by entry, never the playing one, and puts them back', () => {
    usePlayer.getState().removeEntries(['a', 'b', 'd']);
    expect(ids()).toEqual(['b', 'c', 'u1']);
    expect(usePlayer.getState().index).toBe(0);
    expect(usePlayer.getState().undoNotice?.message).toBe('Removed 2 songs');
    usePlayer.getState().undo();
    expect(ids()).toEqual(['a', 'b', 'c', 'd', 'u1']);
    expect(usePlayer.getState().index).toBe(1);
    expect(usePlayer.getState().undoNotice).toBeNull();
  });

  it('names a single song', () => {
    usePlayer.getState().removeEntries(['c']);
    expect(usePlayer.getState().undoNotice?.message).toBe('Removed c');
  });

  it('takes back a Clear', () => {
    usePlayer.getState().clearUpNext();
    expect(ids()).toEqual(['a', 'b', 'c', 'd']);
    expect(usePlayer.getState().undoNotice?.message).toBe('Queue cleared');
    usePlayer.getState().undo();
    expect(ids()).toEqual(['a', 'b', 'c', 'd', 'u1']);
  });

  it('is withdrawn once anything else moves the queue', () => {
    usePlayer.getState().removeEntries(['c']);
    usePlayer.getState().move(2, 3);
    expect(usePlayer.getState().undoNotice).toBeNull();
    usePlayer.getState().undo();
    expect(ids()).toEqual(['a', 'b', 'u1', 'd']);
  });

  it('expires after four and a half seconds', () => {
    vi.useFakeTimers();
    usePlayer.getState().removeEntries(['c']);
    vi.advanceTimersByTime(4_400);
    expect(usePlayer.getState().undoNotice).not.toBeNull();
    vi.advanceTimersByTime(200);
    expect(usePlayer.getState().undoNotice).toBeNull();
  });
});

describe('restoreQueue', () => {
  it('puts a saved queue back paused, only when nothing has started', () => {
    usePlayer.setState({ queue: [], index: -1, nowPlaying: null, status: 'idle' });
    restoreQueue([entry('a'), entry('b')], 1, 30);
    expect(usePlayer.getState()).toMatchObject({ index: 1, status: 'paused', nowPlaying: { videoId: 'b' } });
    restoreQueue([entry('x')], 0, 0);
    expect(ids()).toEqual(['a', 'b']);
  });
});
