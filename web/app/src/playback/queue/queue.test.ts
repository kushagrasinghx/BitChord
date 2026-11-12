import { describe, expect, it } from 'vitest';
import type { Track } from '@/data/music/types';
import { artistSet, extendStation, isSameRecording, type Candidate } from './builder';
import {
  applyJump,
  asEntry,
  autoplaySectionStart,
  buildContextQueue,
  computeJumpOrder,
  playedUserQueueIndices,
  shuffledStartingOrder,
  shuffledUpcomingOrder,
  upcomingUserQueueIndices,
  userQueueInsertionIndex,
  type QueueEntry,
  type QueueTier,
} from './timeline';

function track(videoId: string, title = videoId, artist = 'Arijit Singh'): Track {
  return {
    videoId,
    title,
    artists: artist.split(/, /).map((name) => ({ name, id: null })),
    album: null,
    durationSec: 200,
    explicit: false,
    thumbnail: null,
    index: null,
    plays: null,
  };
}
const song = (id: string, title: string, artist?: string): Candidate => ({ track: track(id, title, artist), isVideo: false });
const video = (id: string, title: string, artist?: string): Candidate => ({ track: track(id, title, artist), isVideo: true });
const ids = (tracks: Track[]) => tracks.map((t) => t.videoId);

// Ported from app/src/test/…/QueueBuilderTest.kt
describe('extendStation (QueueBuilder)', () => {
  it('leaves out the music video cut of a mix', () => {
    const extra = extendStation([], [song('aaa', 'Dildaara (Stand By Me)', 'Vishal-Shekhar'), video('bbb', 'Lyrical Video: Dildara Song', 'Shafqat Amanat Ali'), song('ccc', 'Sajni')], 10);
    expect(ids(extra)).toEqual(['aaa', 'ccc']);
  });
  it('still plays a mix of nothing but videos', () => {
    expect(ids(extendStation([], [video('aaa', 'One', 'A'), video('bbb', 'Two', 'B')], 10))).toEqual(['aaa', 'bbb']);
  });
  it('treats the video cut as the same recording as its audio', () => {
    expect(isSameRecording(track('aaa', 'Kesariya'), track('bbb', 'Kesariya (Official Video)'))).toBe(true);
  });
  it('matches across a longer billing of the same credit', () => {
    expect(isSameRecording(track('aaa', 'Kesariya', 'Arijit Singh'), track('bbb', 'Kesariya | Official Video', 'Arijit Singh, Pritam'))).toBe(true);
  });
  it('keeps a remix apart from the original', () => {
    expect(isSameRecording(track('aaa', 'Kesariya'), track('bbb', 'Kesariya (Remix)'))).toBe(false);
  });
  it('keeps the same title by another artist apart', () => {
    expect(isSameRecording(track('aaa', 'Perfect', 'Ed Sheeran'), track('bbb', 'Perfect', 'One Direction'))).toBe(false);
  });
  it('matches a reordered credit', () => {
    const a = { ...track('aaa', 'Kalank (Duet)'), artists: [{ name: 'Pritam, Arijit Singh & Shilpa Rao', id: null }] };
    const b = { ...track('bbb', 'Kalank (Duet)'), artists: [{ name: 'Shilpa Rao, Arijit Singh, & Pritam', id: null }] };
    expect(isSameRecording(a, b)).toBe(true);
  });
  it('ignores topic channels and casing in a credit', () => {
    expect([...artistSet('Arijit Singh - Topic')]).toEqual(['arijit singh']);
    expect(new Set(artistSet('Pritam feat. Arijit Singh'))).toEqual(new Set(['pritam', 'arijit singh']));
  });
  it('drops the seed and anything already queued', () => {
    const seed = track('aaa', 'Kesariya');
    expect(ids(extendStation([seed], [{ track: seed, isVideo: false }, song('bbb', 'Tum Hi Ho'), song('aaa', 'Kesariya')], 10))).toEqual(['bbb']);
  });
  it('drops a duplicate that only differs by video cut', () => {
    expect(ids(extendStation([], [song('aaa', 'Channa Mereya'), song('bbb', 'Channa Mereya (Official Video)'), song('ccc', 'Ae Dil Hai Mushkil')], 10))).toEqual(['aaa', 'ccc']);
  });
  it('honours the limit', () => {
    const candidates = Array.from({ length: 10 }, (_, i) => song(`v${i}`, `Song ${i}`, `Artist ${i}`));
    expect(extendStation([], candidates, 3)).toHaveLength(3);
  });
  it('stops one artist taking over the station', () => {
    const candidates = Array.from({ length: 6 }, (_, i) => song(`v${i}`, `Song ${i}`, 'Badshah'));
    expect(extendStation([track('seed', 'Seed', 'Diljit Dosanjh')], candidates, 10)).toHaveLength(2);
  });
  it("gives the seed's own artist more room than the rest", () => {
    const candidates = Array.from({ length: 8 }, (_, i) => song(`v${i}`, `Song ${i}`, 'Diljit Dosanjh'));
    expect(extendStation([track('seed', 'Seed', 'Diljit Dosanjh')], candidates, 10)).toHaveLength(4);
  });
  it('counts a reordered credit as one artist for the cap', () => {
    const candidates = [song('v1', 'One', 'Pritam, Arijit Singh'), song('v2', 'Two', 'Arijit Singh, Pritam'), { track: { ...track('v3', 'Three'), artists: [{ name: 'Pritam & Arijit Singh', id: null }] }, isVideo: false }];
    expect(ids(extendStation([track('seed', 'Seed', 'Nucleya')], candidates, 10))).toEqual(['v1', 'v2']);
  });
});

// Ported from app/src/test/…/QueueSectionsTest.kt ("." user, "~" AutoPlay)
describe('autoplaySectionStart (QueueSections)', () => {
  const start = (queue: string, currentIndex: number) =>
    autoplaySectionStart([...queue].map((c): QueueTier => (c === '~' ? 'autoplay' : 'user')), currentIndex);
  it("starts where AutoPlay's tracks do", () => expect(start('...~~~', 0)).toBe(3));
  it('sits at the end of a queue with no AutoPlay', () => expect(start('....', 1)).toBe(4));
  it('leaves AutoPlay tracks already played above it', () => expect(start('..~~~~', 4)).toBe(5));
  it('closes the queue on the mix’s last track', () => expect(start('..~~', 3)).toBe(4));
  it('keeps a track put in by hand mid-mix above it', () => expect(start('~~~.~~', 2)).toBe(4));
  it('is at the top of an empty queue', () => {
    expect(start('', 0)).toBe(0);
    expect(start('', -1)).toBe(0);
  });
});

/** "c" context, "u" user, "a" AutoPlay; entry ids are the letter plus position. */
function timeline(spec: string): QueueEntry[] {
  const tierOf = { c: 'context', u: 'user', a: 'autoplay' } as const;
  return [...spec].map((c, i) => asEntry(track(`${c}${i}`), tierOf[c as 'c' | 'u' | 'a'], null, `${c}${i}`));
}
const order = (entries: QueueEntry[]) => entries.map((e) => e.entryId).join(' ');

describe('QueueTimeline', () => {
  it('builds a context queue around the picked track, keeping the user queue', () => {
    const current = timeline('cuuc');
    const { timeline: built, startIndex } = buildContextQueue(current, 0, [track('x'), track('y'), track('z')], 1, { title: 'Album', type: 'album' });
    expect(startIndex).toBe(1);
    expect(built.map((e) => e.track.videoId)).toEqual(['x', 'y', 'u1', 'u2', 'z']);
  });

  it('inserts Play Next after the current track and Add to Queue after the user queue', () => {
    const t = timeline('cuucc');
    expect(userQueueInsertionIndex(t, 0, true)).toBe(1);
    expect(userQueueInsertionIndex(t, 0, false)).toBe(3);
  });

  it('jumps back as a pure seek', () => {
    expect(computeJumpOrder(['context', 'context', 'context'], 2, 0)).toMatchObject({ isPureSeek: true, newIndex: 0 });
  });

  it('jumps ahead in the context as a seek when no hand-queued track is skipped', () => {
    expect(computeJumpOrder(['context', 'context', 'context'], 0, 2)).toMatchObject({ isPureSeek: true, newIndex: 2 });
  });

  it('pins skipped hand-queued tracks after a context target', () => {
    const t = timeline('cucccu');
    const jumped = applyJump(t, 0, 3)!;
    expect(order(jumped.timeline)).toBe('c0 c2 c3 u1 u5 c4');
    expect(jumped.timeline[jumped.index]!.entryId).toBe('c3');
  });

  it('drops the hand-queued tracks bypassed to reach a later one', () => {
    const jumped = applyJump(timeline('cuuucca'), 0, 3)!;
    expect(order(jumped.timeline)).toBe('c0 u3 c4 c5 a6');
    expect(jumped.index).toBe(1);
  });

  it('promotes a tapped AutoPlay track to the context, keeping the user queue', () => {
    const jumped = applyJump(timeline('ccuaaa'), 0, 4)!;
    expect(order(jumped.timeline)).toBe('c0 a4 u2 a5');
    expect(jumped.timeline[1]!.tier).toBe('context');
  });

  it('clears only the upcoming user queue', () => {
    expect(upcomingUserQueueIndices(['user', 'context', 'user', 'autoplay', 'user'], 1)).toEqual([2, 4]);
  });

  it('prunes played hand-queued rows once back in the context', () => {
    expect(playedUserQueueIndices(['context', 'user', 'context'], 2)).toEqual([1]);
    expect(playedUserQueueIndices(['context', 'user', 'context'], 1)).toEqual([]);
  });

  it('starts a shuffled queue with the pick, then the user queue in order', () => {
    const t = timeline('cccuuc');
    const started = shuffledStartingOrder(t, 1, () => 0);
    expect(started.slice(0, 3).map((e) => e.entryId)).toEqual(['c1', 'u3', 'u4']);
    expect(new Set(started.map((e) => e.entryId))).toEqual(new Set(t.map((e) => e.entryId)));
  });

  it('never returns an unchanged shuffle', () => {
    const identity = () => 0.999999; // Fisher-Yates with j = i keeps the order
    const result = shuffledUpcomingOrder(['context', 'context', 'context'], identity);
    expect(result).not.toEqual([0, 1, 2]);
  });
});
