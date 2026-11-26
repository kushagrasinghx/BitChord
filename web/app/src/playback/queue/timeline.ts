import {
  queueAutoplaySectionStart,
  queueJumpOrder,
  queuePlayedUserIndices,
  queueSectionedOrder,
  queueUpcomingUserIndices,
} from '@bitchord/shared-kotlin';
import type { Track } from '@/data/music/types';

/**
 * The three-tier queue's rules, as plain list edits, so the web player orders
 * a queue exactly as the phone and the desktop do. The index rules (jumps,
 * sections, Clear) are QueueTimeline.kt and QueueSections.kt themselves, from
 * shared/commonMain. The entry-building half stays here: Kotlin builds Songs
 * with its own source types and shuffles with its own Random, where the web
 * keeps QueueEntry objects and tests pass their own random.
 *
 * - **context**: the album, playlist or one-off song the user started.
 * - **user**: tracks queued by hand ("Play Next", "Add to Queue").
 * - **autoplay**: the radio that plays on once the rest runs out.
 *
 * The Kotlin side has a QueueWindow as well. It feeds Media3 a bounded window
 * of a long queue, which the web engine has no use for: it loads one track at
 * a time, so the whole timeline is just this array.
 */

export type QueueTier = 'context' | 'user' | 'autoplay';

export type QueueSourceType = 'album' | 'playlist' | 'artist' | 'library' | 'search' | 'home' | 'radio' | 'queue' | 'other';

/** Where a queue (or a track in it) was started from in the UI. */
export interface QueueSource {
  title: string;
  type: QueueSourceType;
  id?: string | null;
}

export interface QueueEntry {
  /** Assigned once when the track enters the queue and never changed (QueueTimeline.asQueueEntry). */
  entryId: string;
  track: Track;
  tier: QueueTier;
  source: QueueSource | null;
}

/** Past this point in a track, back restarts it instead of going to the previous one (QueueSections.kt). */
export const BACK_RESTARTS_AFTER_SEC = 10;

let counter = 0;
const newEntryId = () => `${Date.now().toString(36)}-${(counter++).toString(36)}-${Math.random().toString(36).slice(2, 6)}`;

export function asEntry(track: Track, tier: QueueTier, source: QueueSource | null, entryId?: string): QueueEntry {
  return { entryId: entryId ?? newEntryId(), track, tier, source };
}

function upcomingUserQueue(timeline: readonly QueueEntry[], currentIndex: number): QueueEntry[] {
  if (currentIndex < 0 || currentIndex >= timeline.length) return [];
  return timeline.slice(currentIndex + 1).filter((e) => e.tier === 'user');
}

/**
 * Starting an album, playlist or artist: [preceding context] + [selected] +
 * [the user's upcoming queue, kept] + [following context]. Playback starts at
 * the selected track; the preceding ones stay behind it, for Back and for
 * repeat-all to come round to.
 */
export function buildContextQueue(
  current: readonly QueueEntry[],
  currentIndex: number,
  tracks: readonly Track[],
  selectedIndex: number,
  source: QueueSource,
): { timeline: QueueEntry[]; startIndex: number } {
  if (!tracks.length) return { timeline: [], startIndex: 0 };
  const kept = upcomingUserQueue(current, currentIndex);
  const entries = tracks.map((t) => asEntry(t, 'context', source));
  const at = Math.min(Math.max(selectedIndex, 0), entries.length - 1);
  return {
    timeline: [...entries.slice(0, at), entries[at]!, ...kept, ...entries.slice(at + 1)],
    startIndex: at,
  };
}

/** A one-off song (Search, Home, Explore): [the song] + [the user's upcoming queue, kept]. */
export function buildOneOffQueue(
  current: readonly QueueEntry[],
  currentIndex: number,
  track: Track,
  source: QueueSource,
): QueueEntry[] {
  return [asEntry(track, 'context', source), ...upcomingUserQueue(current, currentIndex)];
}

/**
 * Where hand-queued tracks go: "Play Next" at the head of the user queue
 * (straight after the current track), "Add to Queue" at its tail, ahead of
 * the context and AutoPlay.
 */
export function userQueueInsertionIndex(timeline: readonly QueueEntry[], currentIndex: number, isNext: boolean): number {
  if (!timeline.length) return 0;
  const start = Math.min(Math.max(currentIndex + 1, 0), timeline.length);
  if (isNext) return start;
  for (let i = start; i < timeline.length; i++) if (timeline[i]!.tier !== 'user') return i;
  return timeline.length;
}

export interface JumpPlan {
  isPureSeek: boolean;
  /** The new timeline, as indices into the old one; indices left out are dropped. */
  order: number[];
  newIndex: number;
  /** An AutoPlay track tapped becomes the new context: a station pivot. */
  promoteTarget: boolean;
}

const range = (from: number, to: number) => Array.from({ length: Math.max(0, to - from) }, (_, i) => from + i);

/**
 * Tapping a row in Up Next (QueueTimeline.computeJumpOrder): back or a context
 * track is a seek (hand-queued tracks skipped over are pinned after it), a
 * hand-queued track drops the ones bypassed, an AutoPlay track becomes the
 * context.
 */
export function computeJumpOrder(tiers: readonly QueueTier[], currentIndex: number, targetIndex: number): JumpPlan | null {
  const plan = queueJumpOrder([...tiers], currentIndex, targetIndex);
  if (!plan) return null;
  return { isPureSeek: plan.isPureSeek, order: plan.order, newIndex: plan.newIndex, promoteTarget: plan.promoteTarget };
}

/** Applies a jump plan; returns the new timeline and the index to play. */
export function applyJump(timeline: readonly QueueEntry[], currentIndex: number, targetIndex: number): { timeline: QueueEntry[]; index: number } | null {
  const plan = computeJumpOrder(
    timeline.map((e) => e.tier),
    currentIndex,
    targetIndex,
  );
  if (!plan) return null;
  if (plan.isPureSeek) return { timeline: [...timeline], index: plan.newIndex };
  const target = timeline[targetIndex]!;
  return {
    timeline: plan.order.map((i) => {
      const entry = timeline[i]!;
      if (i !== targetIndex || !plan.promoteTarget) return entry;
      return {
        ...entry,
        tier: 'context' as const,
        source: { title: target.source?.title || target.track.title, type: target.source?.type ?? 'queue', id: target.source?.id ?? null },
      };
    }),
    index: plan.newIndex,
  };
}

export interface QueueRow {
  entry: QueueEntry;
  /** Where it sits in the timeline: what jumpTo, removeAt and move take. */
  at: number;
}

/**
 * Up Next's rows (PlayerQueue.splitQueue): the songs already played, only
 * when history is shown, then what's next. The playing song is in neither:
 * it's the player's, not a row.
 */
export function splitQueue(
  timeline: readonly QueueEntry[],
  currentIndex: number,
  showHistory: boolean,
): { history: QueueRow[]; upcoming: QueueRow[] } {
  if (currentIndex < 0 || currentIndex >= timeline.length) return { history: [], upcoming: [] };
  const rows = timeline.map((entry, at) => ({ entry, at }));
  return { history: showHistory ? rows.slice(0, currentIndex) : [], upcoming: rows.slice(currentIndex + 1) };
}

/** The upcoming hand-queued rows: what "Clear" removes (QueueTimeline.upcomingUserQueueIndices). */
export const upcomingUserQueueIndices = (tiers: readonly QueueTier[], currentIndex: number): number[] =>
  queueUpcomingUserIndices([...tiers], currentIndex);

/** Played hand-queued rows, to prune once playback is back in the context (QueueTimeline.playedUserQueueIndices). */
export const playedUserQueueIndices = (tiers: readonly QueueTier[], currentIndex: number): number[] =>
  queuePlayedUserIndices([...tiers], currentIndex);

export function shuffled<T>(items: readonly T[], random: () => number = Math.random): T[] {
  const out = [...items];
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    [out[i], out[j]] = [out[j]!, out[i]!];
  }
  return out;
}

/** Never an unchanged order when two or more can move: an identity shuffle reads as the tap being ignored. */
export function avoidIdentityShuffle(original: readonly number[], shuffledOrder: number[]): number[] {
  if (original.length <= 1 || shuffledOrder.some((v, i) => v !== original[i])) return shuffledOrder;
  return [...shuffledOrder.slice(1), shuffledOrder[0]!];
}

/**
 * A queue started with shuffle on: the picked track leads, the user's queue
 * stays in order behind it, the context and AutoPlay are each shuffled.
 */
export function shuffledStartingOrder(entries: readonly QueueEntry[], startIndex: number, random?: () => number): QueueEntry[] {
  if (!entries.length) return [];
  const at = Math.min(Math.max(startIndex, 0), entries.length - 1);
  const rest = entries.filter((_, i) => i !== at);
  return [
    entries[at]!,
    ...rest.filter((e) => e.tier === 'user'),
    ...shuffled(rest.filter((e) => e.tier === 'context'), random),
    ...shuffled(rest.filter((e) => e.tier === 'autoplay'), random),
  ];
}

/** Shuffle applied to what is still to come, as a permutation of [upcoming]: the user queue never moves. */
export function shuffledUpcomingOrder(upcoming: readonly QueueTier[], random?: () => number): number[] {
  const of = (tier: QueueTier) => range(0, upcoming.length).filter((i) => upcoming[i] === tier);
  const context = of('context');
  const autoplay = of('autoplay');
  return [
    ...of('user'),
    ...avoidIdentityShuffle(context, shuffled(context, random)),
    ...avoidIdentityShuffle(autoplay, shuffled(autoplay, random)),
  ];
}

/** Puts restored upcoming rows back in their sections (QueueTimeline.sectionedOrder). */
export const sectionedOrder = (restored: readonly number[], upcoming: readonly QueueTier[]): number[] =>
  queueSectionedOrder([...restored], [...upcoming]);

/** The first AutoPlay row still to come: where a hand-queued track belongs and Up Next's heading goes (QueueSections.kt). */
export const autoplaySectionStart = (tiers: readonly QueueTier[], currentIndex: number): number =>
  queueAutoplaySectionStart([...tiers], currentIndex);
