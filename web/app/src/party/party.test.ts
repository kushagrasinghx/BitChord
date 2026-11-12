import { describe, expect, it } from 'vitest';
import { ServerClock } from './clock';
import { cleanCode, inviteUrl, parseInvite } from './invite';
import { EMPTY_PLAYBACK, type PartyMember, type PartyQueue, type PartyTrack } from './models';
import { autoplaySupplierId, detectSingleMove, partyQueueIndexOf, partyUpcomingAfter } from './queueReading';

// ServerClockTest.kt, case for case.
describe('ServerClock', () => {
  let now = 1_000;
  const clock = () => new ServerClock(() => now);

  it('knows nothing until a round trip completes', () => {
    const c = clock();
    expect(c.synced).toBe(false);
    expect(c.serverNowMs()).toBeNull();
  });

  it('takes the offset at the midpoint of the round trip', () => {
    const c = clock();
    c.record(100, 5_150, 200);
    expect(c.synced).toBe(true);
    expect(c.offsetMs).toBe(5_000);
    expect(c.roundTripMs).toBe(100);
  });

  it('believes the shortest round trip, not the most recent', () => {
    const c = clock();
    c.record(0, 5_010, 20);
    c.record(100, 5_400, 700);
    expect(c.offsetMs).toBe(5_000);
    expect(c.roundTripMs).toBe(20);
  });

  it('drops a stale best sample rather than pinning the session', () => {
    const c = clock();
    c.record(0, 5_010, 20);
    c.record(300_000, 306_200, 300_400);
    expect(c.offsetMs).toBe(6_000);
    expect(c.roundTripMs).toBe(400);
  });

  it('reads the server as this clock plus the offset', () => {
    const c = clock();
    c.record(100, 5_150, 200);
    now = 900;
    expect(c.serverNowMs()).toBe(5_900);
  });

  it('forgets everything on reset', () => {
    const c = clock();
    c.record(100, 5_150, 200);
    c.reset();
    expect(c.synced).toBe(false);
    expect(c.roundTripMs).toBe(0);
  });
});

const t = (videoId: string, fromAutoplay = false): PartyTrack => ({
  videoId,
  title: videoId,
  artist: '',
  thumbnailUrl: null,
  durationMs: null,
  fromAutoplay,
});

describe('party queue reading', () => {
  // The queue keeps history, so the playing track can be in it twice (the duplicate-track fix).
  const queue: PartyQueue = { seq: 3, index: 3, items: [t('a'), t('b'), t('c'), t('a'), t('d')] };

  it('trusts the state frame’s index when it names the track', () => {
    expect(partyQueueIndexOf(queue, { ...EMPTY_PLAYBACK, queueSeq: 3, queueIndex: 3 }, 'a')).toBe(3);
  });

  it('takes the nearest copy, looking forward first, never the first from the top', () => {
    expect(partyQueueIndexOf(queue, { ...EMPTY_PLAYBACK, queueSeq: 3, queueIndex: 2 }, 'a')).toBe(3);
    expect(partyQueueIndexOf({ ...queue, index: 4 }, { ...EMPTY_PLAYBACK, queueSeq: 9, queueIndex: 0 }, 'a')).toBe(3);
    expect(partyQueueIndexOf(queue, EMPTY_PLAYBACK, 'zz')).toBe(-1);
  });

  it('reads what comes after the playing copy', () => {
    expect(partyUpcomingAfter(queue, { ...EMPTY_PLAYBACK, queueSeq: 3, queueIndex: 3 }, 'a').map((x) => x.videoId)).toEqual(['d']);
  });

  it('detects a single move, and only that', () => {
    expect(detectSingleMove(['a', 'b', 'c', 'd'], ['a', 'c', 'd', 'b'])).toEqual({ fromIndex: 1, toIndex: 3, videoId: 'b' });
    expect(detectSingleMove(['a', 'b', 'c', 'd'], ['b', 'a', 'd', 'c'])).toBeNull();
    expect(detectSingleMove(['a', 'b'], ['a', 'b'])).toBeNull();
  });
});

describe('AutoPlay supplier', () => {
  const m = (memberId: string, isHost: boolean, connected: boolean): PartyMember => ({
    memberId,
    userId: '',
    displayName: memberId,
    avatarUrl: null,
    isHost,
    connected,
    joinedAtMs: 0,
    lastSeenMs: 0,
  });

  it('is the host while connected, else the lowest connected member', () => {
    expect(autoplaySupplierId([m('z', true, true), m('a', false, true)], false)).toBe('z');
    expect(autoplaySupplierId([m('z', true, false), m('c', false, true), m('b', false, true)], false)).toBe('b');
  });

  it('has no stand-in for a host who locked the party', () => {
    expect(autoplaySupplierId([m('z', true, false), m('a', false, true)], true)).toBeNull();
  });
});

describe('invites', () => {
  it('reads both link shapes and normalises the code', () => {
    expect(parseInvite('https://bitchord.kushagrasingh.in/invite/ab12cd')).toEqual({ code: 'AB12CD', serverUrl: null });
    expect(parseInvite('bitchord://party/AB12CD?server=jam.example.com')).toEqual({ code: 'AB12CD', serverUrl: 'https://jam.example.com' });
    expect(parseInvite('https://example.com/nope')).toBeNull();
    expect(cleanCode(' ab-12 cd ')).toBe('AB12CD');
  });

  it('links to a custom server’s own invite page', () => {
    expect(inviteUrl('ab12cd', null)).toBe('https://bitchord.kushagrasingh.in/invite/AB12CD');
    expect(inviteUrl('AB12CD', 'https://jam.example.com/')).toBe('https://jam.example.com/invite/AB12CD');
  });
});
