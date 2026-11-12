import type { PartyMember, PartyPlayback, PartyQueue, PartyTrack } from './models';

/**
 * Where [videoId] is in the party's queue: PartyQueueReading.kt.
 *
 * A party queue keeps its history, so the playing track can be in it twice.
 * The state frame's own index wins when it names the track; otherwise the
 * nearest copy, looking forward first. Never `indexOf`: that took the first
 * copy from the top and played the wrong part of the queue.
 */
export function partyQueueIndexOf(queue: PartyQueue, playback: PartyPlayback, videoId: string | null | undefined): number {
  if (!videoId) return -1;
  const items = queue.items;
  const hint = playback.queueSeq === queue.seq ? playback.queueIndex : queue.index;
  if (hint >= 0 && hint < items.length && items[hint]!.videoId === videoId) return hint;
  let best = -1;
  let bestDistance = Number.MAX_SAFE_INTEGER;
  items.forEach((item, i) => {
    if (item.videoId !== videoId) return;
    const distance = i >= hint ? i - hint : (hint - i) * 2 + 1;
    if (distance < bestDistance) {
      best = i;
      bestDistance = distance;
    }
  });
  return best;
}

/** What the party plays after [videoId], or nothing when the queue doesn't hold it. */
export function partyUpcomingAfter(queue: PartyQueue, playback: PartyPlayback, videoId: string | null | undefined): PartyTrack[] {
  const at = partyQueueIndexOf(queue, playback, videoId);
  return at < 0 ? [] : queue.items.slice(at + 1);
}

export interface QueueMoveDelta {
  fromIndex: number;
  toIndex: number;
  videoId: string;
}

/** Whether [after] is [before] with exactly one item moved (PartySync.detectSingleMove). */
export function detectSingleMove(before: string[], after: string[], baseOffset = 0): QueueMoveDelta | null {
  if (before.length !== after.length || !before.length || before.every((id, i) => id === after[i])) return null;
  const counts = (list: string[]) => list.reduce((m, id) => m.set(id, (m.get(id) ?? 0) + 1), new Map<string, number>());
  const a = counts(before);
  const b = counts(after);
  if (a.size !== b.size || [...a].some(([id, n]) => b.get(id) !== n)) return null;
  for (let from = 0; from < before.length; from++) {
    const item = before[from]!;
    const without = before.filter((_, i) => i !== from);
    for (let to = 0; to < before.length; to++) {
      if (from === to) continue;
      const simulated = [...without.slice(0, to), item, ...without.slice(to)];
      if (simulated.every((id, i) => id === after[i])) return { fromIndex: baseOffset + from, toIndex: baseOffset + to, videoId: item };
    }
  }
  return null;
}

/**
 * The one member who tops up AutoPlay (Autoplay.autoplaySupplierId): the
 * host while connected, else the lowest connected member id; nobody stands in
 * for a host who has locked the party.
 */
export function autoplaySupplierId(members: PartyMember[], hostOnlyControl: boolean): string | null {
  const host = members.find((m) => m.isHost && m.connected)?.memberId ?? null;
  if (hostOnlyControl) return host;
  if (host) return host;
  const connected = members.filter((m) => m.connected).map((m) => m.memberId).sort();
  return connected[0] ?? null;
}
