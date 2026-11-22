import { lastPlayedWindow } from '@bitchord/shared-kotlin';
import type { QueueEntry } from '@/playback/queue/timeline';
import { getPlayhead, restoreQueue, usePlayer } from '@/state/player';

/**
 * The queue, kept across visits (LastPlayed.kt, which the phone's widget and
 * Quick Settings tile play from): up to 25 played songs, the current one and
 * 50 to come. Opening BitChord puts it back, paused where it was left; Play,
 * or the media key, carries on.
 *
 * The queue is written when it changes. The position, which changes all the
 * time, only when playback pauses or the page is hidden or closed.
 */
const KEY = 'bitchord:last-queue';

export interface SavedQueue {
  queue: QueueEntry[];
  index: number;
  positionSec: number;
}

/** What's kept of [queue] playing at [index] (QueueHistoryWindow.kt's lastPlayedWindow); null for no queue. */
export function snapshot(queue: readonly QueueEntry[], index: number, positionSec: number): SavedQueue | null {
  if (index < 0 || index >= queue.length) return null;
  const [start, end] = lastPlayedWindow(queue.length, index) as [number, number];
  return { queue: queue.slice(start, end), index: index - start, positionSec: Math.max(0, positionSec) };
}

const TIERS = new Set(['context', 'user', 'autoplay']);

/** The saved queue, if it still reads as one: anything in localStorage can be stale or edited by hand. */
export function parseSaved(raw: string | null): SavedQueue | null {
  try {
    const saved = JSON.parse(raw ?? 'null') as Partial<SavedQueue> | null;
    if (!saved || !Array.isArray(saved.queue) || !Number.isInteger(saved.index)) return null;
    const valid = saved.queue.every(
      (e: Partial<QueueEntry> | null) =>
        !!e && typeof e.entryId === 'string' && TIERS.has(e.tier ?? '') && typeof e.track?.videoId === 'string' && Array.isArray(e.track.artists),
    );
    if (!valid || !saved.queue[saved.index!]) return null;
    return { queue: saved.queue, index: saved.index!, positionSec: Math.max(0, Number(saved.positionSec) || 0) };
  } catch {
    return null;
  }
}

/** Restores the last queue, then keeps it saved. Called once, at startup. */
export function keepLastQueue(): void {
  const saved = parseSaved(localStorage.getItem(KEY));
  if (saved) restoreQueue(saved.queue, saved.index, saved.positionSec);
  // Until the restored song plays, there's no element to read its position from.
  let unplayedSec = saved?.positionSec ?? 0;

  const save = () => {
    const { queue, index } = usePlayer.getState();
    const kept = snapshot(queue, index, getPlayhead()?.sec ?? unplayedSec);
    try {
      if (kept) localStorage.setItem(KEY, JSON.stringify(kept));
      else localStorage.removeItem(KEY);
    } catch {
      // Storage full or blocked: the next visit starts with an empty queue, as it did before.
    }
  };

  usePlayer.subscribe((s, prev) => {
    if (s.queue !== prev.queue || s.index !== prev.index) {
      if (s.index !== prev.index) unplayedSec = 0;
      save();
    } else if (s.status === 'paused' && prev.status !== 'paused') save();
  });
  window.addEventListener('pagehide', save);
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') save();
  });
}
