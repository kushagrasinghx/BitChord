import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { YTMusic } from 'youtubei.js';
import { matchesSearch, recentLabel, topRelease } from './detail';
import { mapAlbum, mapArtist } from './mappers';
import type { MusicItem, Shelf } from './types';

const fixture = (name: string) =>
  JSON.parse(readFileSync(fileURLToPath(new URL(`../../../../fixtures/innertube/${name}.json`, import.meta.url)), 'utf8'));
const response = (name: string) => ({ success: true, status_code: 200, data: fixture(name) }) as never;
const actions = { session: { context: { client: {} } } } as never;

const item = (id: string, subtitle: string): MusicItem => ({
  kind: 'album',
  id,
  title: id,
  subtitle,
  artists: [],
  album: null,
  thumbnail: null,
  wide: false,
  durationSec: null,
  explicit: false,
});
const shelf = (items: MusicItem[]): Shelf => ({ title: '', strapline: null, layout: 'square', items, seeAll: null });

describe('topRelease', () => {
  it('takes the newest release by year, skipping what is not a release', () => {
    const shelves = [
      shelf([item('MPREa', 'Album • 2019'), item('VLplaylist', 'Playlist • 2024')]),
      shelf([item('MPREb', 'Single • 2023'), item('MPREc', 'EP • 2023')]),
    ];
    // 2023 twice: YouTube's order breaks the tie.
    expect(topRelease(shelves)?.id).toBe('MPREb');
    expect(topRelease([shelf([item('VLx', 'Playlist • 2024')])])).toBeNull();
  });

  it('finds a release on a real artist page', () => {
    const artist = mapArtist('UCtest', new YTMusic.Artist(response('browse-artist'), actions));
    expect(topRelease(artist.shelves)?.id).toMatch(/^MPRE/);
  });

  it('labels it by kind', () => {
    expect(recentLabel(item('MPREa', 'Single • 2023'))).toBe('Recent Single');
    expect(recentLabel(item('MPREa', 'EP • 2023'))).toBe('Recent EP');
    expect(recentLabel(item('MPREa', 'Album • 2023'))).toBe('Recent Album');
  });
});

describe('matchesSearch', () => {
  const album = mapAlbum('MPREb_test', new YTMusic.Album(response('browse-album')));
  const track = { ...album.tracks[0]!, title: 'Café del Mar', artists: [{ name: 'Energy 52', id: null }], album: { name: 'Classics', id: null } };

  it('matches title, artist or album, ignoring case and accents', () => {
    expect(matchesSearch(track, 'cafe')).toBe(true);
    expect(matchesSearch(track, 'ENERGY')).toBe(true);
    expect(matchesSearch(track, 'classic')).toBe(true);
    expect(matchesSearch(track, '  ')).toBe(true);
    expect(matchesSearch(track, 'nothing like it')).toBe(false);
  });
});
