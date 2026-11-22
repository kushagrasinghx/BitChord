import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { YTMusic } from 'youtubei.js';
import { mapArtist, mapHome } from './mappers';
import { MOODS, moodById, moodFor } from './moods';
import { bestMoodShelves, buildTopPicks, findMixes, latestRelease, rankArtists, regionHints, weightedSample } from './topPicks';
import type { MusicItem, Shelf, Track } from './types';

// Captured responses (web/scripts/capture-fixtures.mjs), parsed as a live one would be.
const response = (name: string) =>
  ({
    success: true,
    status_code: 200,
    data: JSON.parse(readFileSync(fileURLToPath(new URL(`../../../../fixtures/innertube/${name}.json`, import.meta.url)), 'utf8')),
  }) as never;
const actions = { session: { context: { client: {} } } } as never;
const artistPage = mapArtist('UCtest', new YTMusic.Artist(response('browse-artist'), actions));

const track = (videoId: string, artist: string, id: string | null): Track => ({
  videoId,
  title: videoId,
  artists: [{ name: artist, id }],
  album: null,
  durationSec: null,
  explicit: false,
  thumbnail: null,
  index: null,
  plays: null,
});
const item = (id: string, title: string): MusicItem => ({
  kind: 'playlist',
  id,
  title,
  subtitle: '',
  artists: [],
  album: null,
  thumbnail: 'https://example.com/a.jpg',
  wide: false,
  durationSec: null,
  explicit: false,
});
const shelf = (title: string, items: MusicItem[]): Shelf => ({ title, strapline: null, layout: 'square', items, seeAll: null });

describe('top picks', () => {
  it('ranks artists by plays, leaning on the recent ones, and skips those without a channel', () => {
    const history = [track('1', 'B', 'UCb'), track('2', 'A', 'UCa'), track('3', 'A', 'UCa'), track('4', 'C', null), track('5', 'Various Artists', 'UCv')];
    expect(rankArtists(history)).toEqual([
      { id: 'UCa', name: 'A' },
      { id: 'UCb', name: 'B' },
    ]);
  });

  it('samples without repeats, the top always eligible', () => {
    expect(weightedSample(['a', 'b', 'c'], 3, () => 0)).toEqual(['a', 'b', 'c']);
    expect(weightedSample(['a', 'b'], 5)).toHaveLength(2);
  });

  it('tells the mixes apart by their titles, else takes the first as the Supermix', () => {
    const mixes = findMixes([shelf('Mixed for you', [item('VLRDTMAK1', 'My Supermix'), item('VLRDTMAK2', 'Discover Mix'), item('VLPL1', 'Not a mix')])]);
    expect(mixes.get('supermix')?.id).toBe('VLRDTMAK1');
    expect(mixes.get('discover')?.id).toBe('VLRDTMAK2');
    expect(findMixes([shelf('Mixes', [item('RDTMAKx', 'Mi Supermix en otro idioma')])]).get('supermix')?.id).toBe('RDTMAKx');
    expect(findMixes([shelf('Home', [item('VLPL1', 'A playlist')])]).size).toBe(0);
  });

  it('leads with the newest release on an artist page', () => {
    const lead = latestRelease([artistPage]);
    expect(lead?.kind).toBe('latest-release');
    expect(lead?.albumId).toMatch(/^MPRE/);
    expect(lead?.artistName).toBe(artistPage.name);
  });

  it('signed out, is the mood card alone, as the phone shows it', async () => {
    const home = mapHome(new YTMusic.HomeFeed(response('browse-home'), actions));
    const picks = await buildTopPicks({ shelves: home, history: [], artist: () => Promise.reject(new Error('unused')) });
    expect(picks.map((p) => p.kind)).toEqual(['mood']);
    expect(moodById(picks[0]!.mood!)?.core).toBe(true);
  });

  it('signed in, adds a station off the history and the artist cards off their pages', async () => {
    const history = [track('v1', artistPage.name, 'UCtest')];
    const picks = await buildTopPicks({ shelves: [], history, artist: () => Promise.resolve(artistPage) });
    expect(picks[0]?.kind).toBe('latest-release');
    expect(new Set(picks.map((p) => p.kind))).toEqual(new Set(['latest-release', 'personal-station', 'mood', 'artist-and-similar', 'discovery-station']));
    expect(picks.find((p) => p.kind === 'personal-station')?.seedVideoId).toBe('v1');
  });

  it('picks the mood shelves whose titles fit, the listener’s scene counting too', () => {
    const heartbreak = moodById('heartbreak')!;
    const shelves = [shelf('Sad pop', [item('VL1', 'a')]), shelf('Breakups & heartbreak', [item('VL2', 'b')]), shelf('Heartbreak (no playlists)', [])];
    expect(bestMoodShelves(shelves, heartbreak, []).map((s) => s.title)).toEqual(['Breakups & heartbreak']);
    expect(bestMoodShelves([shelf('Bollywood sad', [item('VL3', 'c')]), shelf('Sad', [item('VL4', 'd')])], moodById('feeling-blue')!, regionHints('en-IN')).map((s) => s.title)).toEqual([
      'Bollywood sad',
    ]);
  });

  it('suits the mood to the hour most of the time', () => {
    expect(moodFor(new Date(2026, 0, 1, 10), () => 0).id).toBe('focus');
    expect(moodFor(new Date(2026, 0, 1, 21), () => 0).id).toBe('love');
    expect(MOODS.filter((m) => m.core).map((m) => m.title)).toEqual(['Energy', 'Relax', 'Feeling Blue', 'Feel Good', 'Focus', 'Heartbreak', 'Love']);
  });
});
