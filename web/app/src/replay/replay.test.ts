import { replayCharts, replayYear, ReplayMonth, ReplayTally, ReplayTrack } from '@bitchord/shared-kotlin';
import { describe, expect, it } from 'vitest';
import { meshDimmed, meshSlots, meshTuned, rgbOf } from './colors';
import { formatDay, formatHour, formatListening } from './format';
import { periodMonths } from './periods';
import { cardContent } from './year/content';

const track = (id: string, artist: string, album: string | null = null) =>
  new ReplayTrack(id, `Song ${id}`, artist, album, null, null, `https://i.ytimg.com/vi/${id}/hqdefault.jpg`);

/** A month with [plays] of each track ([ms] each, counted as a play), on [day] at 9 pm. */
function month(key: string, day: number, ...plays: [ReturnType<typeof track>, number][]): string {
  const open = new ReplayMonth(key, null);
  for (const [t, ms] of plays) open.record(t, ms, true, Date.UTC(2026, 0, 1), 21, day);
  return open.json();
}

// The phone's ListeningRecorder rule, run by the same Kotlin (ListeningTally).
describe('the tally', () => {
  it('anchors on a track’s first tick, caps a step at 8 s and counts a play once', () => {
    const tally = new ReplayTally();
    expect(tally.sample('a', 0, 40_000)).toBeNull();
    expect(tally.sample('a', 60_000, 40_000)?.playedMs).toBe(8_000);
    const steps = [1, 2, 3, 4, 5].map((i) => tally.sample('a', 60_000 + i * 5_000, 40_000)!);
    // 8 s, then 5 s a tick: past the 30 s floor on the fifth, and only then.
    expect(steps.map((s) => s.countsAsPlay)).toEqual([false, false, false, false, true]);
    expect(steps.filter((s) => s.flush)).toHaveLength(1);
  });
});

describe('months', () => {
  it('are kept in the phone’s JSON, and read back from it', () => {
    const json = month('2026-11', 3, [track('aaaaaaaaaaa', 'Band & Guest', 'Record'), 5_000]);
    const stored = JSON.parse(json);
    expect(Object.keys(stored)).toEqual(['version', 'month', 'tracks', 'artists', 'albums', 'hours', 'days', 'trackDays', 'artistDays', 'albumDays']);
    expect(stored.artists[0].name).toBe('Band');
    expect(stored.days).toEqual({ 3: 5_000 });
    expect(stored.hours[21]).toBe(5_000);
    // Reopened and added to, as the recorder does across a reload.
    const again = new ReplayMonth('2026-11', json);
    again.record(track('aaaaaaaaaaa', 'Band & Guest', 'Record'), 1_000, false, 0, 21, 3);
    expect(JSON.parse(again.json()).tracks[0].ms).toBe(6_000);
  });

  it('read a month the phone wrote', () => {
    const phone =
      '{"version":1,"month":"2026-11","tracks":[{"id":"v","title":"T","artist":"A","album":null,"albumId":null,"artistId":null,"art":null,' +
      '"ms":5000,"plays":1,"last":9,"days":{"2026-11-03":{"ms":5000,"plays":1}}}],"artists":[],"albums":[],' +
      '"hours":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,5000,0,0,0],"days":{"3":5000},"trackDays":null,"artistDays":null,"albumDays":null}';
    const charts = replayCharts([phone], null, null);
    expect(charts.songs.map((s) => [s.videoId, s.ms, s.plays])).toEqual([['v', 5_000, 1]]);
    expect(charts.busiestDay).toBe('2026-11-03');
  });

  it('add up into charts, a week counting only its own days', () => {
    const a = track('aaaaaaaaaaa', 'Band');
    const b = track('bbbbbbbbbbb', 'Other');
    const early = month('2026-11', 2, [a, 9_000]);
    const late = month('2026-11', 10, [b, 4_000]);
    // Two devices' copies of one month are both added (a peer's month beside this browser's).
    const all = replayCharts([early, late], null, null);
    expect(all.songs.map((s) => s.videoId)).toEqual(['aaaaaaaaaaa', 'bbbbbbbbbbb']);
    expect(all.totalMs).toBe(13_000);
    const week = replayCharts([early, late], '2026-11-09', '2026-11-15');
    expect(week.songs.map((s) => s.videoId)).toEqual(['bbbbbbbbbbb']);
  });
});

describe('periods', () => {
  const months = ['2025-12', '2026-10', '2026-11', '2026-12'];
  it('reads a week across the turn of a month, Monday to today', () => {
    // Tuesday 1 December 2026: the week began on Monday 30 November.
    expect(periodMonths('week', months, new Date(2026, 11, 1))).toEqual({ months: ['2026-11', '2026-12'], week: ['2026-11-30', '2026-12-01'] });
  });
  it('reads this month, this year, or everything', () => {
    const today = new Date(2026, 10, 22);
    expect(periodMonths('month', months, today).months).toEqual(['2026-11']);
    expect(periodMonths('year', months, today).months).toEqual(['2026-10', '2026-11', '2026-12']);
    expect(periodMonths('all', months, today).months).toEqual(months);
  });
});

describe('the year story', () => {
  // An album is filed by title and artist, so "Album 0" by each artist is two albums: six in all.
  const songs = 'abcdefgh'.split('').map((c, i) => track(c.repeat(11), i % 2 ? 'Second' : 'First', `Album ${i % 3}`));
  const year = replayYear([
    [month('2026-03', 14, ...songs.map((s, i): [typeof s, number] => [s, (10 - i) * 60_000]))],
    [month('2026-07', 1, [songs[7]!, 30 * 60_000])],
  ])!;

  it('plans the phone’s cards, keeping the number one for its own reveal', () => {
    // Two artists: too few for the top-artists card.
    expect(year.slides).toEqual(['OPENER', 'MINUTES', 'BUSIEST_DAY', 'CLOCK', 'ARTIST_REVEAL', 'SONG_REVEAL', 'TOP_SONGS', 'ALBUMS', 'MONTHS', 'DISCOVERY', 'SUMMARY']);
    const clip = (slide: string) => year.clips[year.slides.indexOf(slide)]?.videoId;
    expect(clip('SONG_REVEAL')).toBe(year.charts.songs[0]!.videoId);
    expect(clip('OPENER')).not.toBe(clip('SONG_REVEAL'));
    expect(year.monthTops.map((m) => m.month)).toEqual(['2026-03', '2026-07']);
    expect(year.personality).toBe('GOLDEN_HOUR');
  });

  it('says the same things the phone does', () => {
    const minutes = cardContent(year, 'MINUTES', 2026);
    expect(minutes.hero).toBe('82');
    expect(minutes.detail).toEqual(['That’s 1 hours of music.']);
    expect(cardContent(year, 'CLOCK', 2026).hero).toBe('9 PM');
    expect(cardContent(year, 'DISCOVERY', 2026).detail).toEqual(['8 songs', '2 artists', '6 albums']);
  });
});

describe('formatting', () => {
  it('says time as the phone does', () => {
    expect(formatListening(59 * 60_000)).toBe('59 min');
    expect(formatListening(125 * 60_000)).toBe('2h 5m');
    expect(formatListening(2_000 * 60_000)).toBe('2,000 min');
    expect([0, 9, 12, 15].map(formatHour)).toEqual(['midnight', '9 AM', 'midday', '3 PM']);
    expect(formatDay('2026-08-14')).toMatch(/^14 /);
  });
});

// The phone's palette mesh (MeshGradient.kt): four colours, tuned for white type.
describe('the palette mesh', () => {
  it('takes each quarter’s most colourful cell, in the blobs’ order', () => {
    // A 4×4 sleeve: red top left, green top right, blue bottom right, white bottom left.
    const cells = Array.from({ length: 16 }, (_, i) => {
      const top = i < 8;
      const left = i % 4 < 2;
      return top ? (left ? rgbOf(0xff0000) : rgbOf(0x00ff00)) : left ? rgbOf(0xffffff) : rgbOf(0x0000ff);
    });
    expect(meshSlots(cells)).toEqual([rgbOf(0xff0000), rgbOf(0x00ff00), rgbOf(0x0000ff), rgbOf(0xffffff)]);
  });

  it('holds a blob’s lightness to .28–.58 and dims the ground to .12', () => {
    expect(meshTuned(rgbOf(0xffffff))).toBe('hsl(0 0% 58%)');
    expect(meshTuned(rgbOf(0x000000))).toBe('hsl(0 0% 28%)');
    expect(meshDimmed(rgbOf(0xfa2d48))).toMatch(/^hsl\(352(\.\d+)? 95% 12%\)$/);
  });
});
