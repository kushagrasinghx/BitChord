import { queueHistoryTrimCount } from '@bitchord/shared-kotlin';
import type { QueueEntry } from './timeline';

/**
 * The 25 played entries kept behind the current one, as the phone's player
 * keeps them (QueueHistoryWindow.kt; PlaybackService trims on every track
 * change). Older ones leave the queue; on repeat-all they go round to the
 * end instead, so the loop still holds the whole queue. Null when nothing
 * has fallen out yet.
 */
export function trimmedHistory(
  queue: readonly QueueEntry[],
  index: number,
  repeatAll: boolean,
): { queue: QueueEntry[]; index: number } | null {
  const expired = queueHistoryTrimCount(index);
  if (expired <= 0) return null;
  const kept = queue.slice(expired);
  return { queue: repeatAll ? [...kept, ...queue.slice(0, expired)] : kept, index: index - expired };
}
