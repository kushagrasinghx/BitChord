import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { searchFilters } from '@bitchord/shared-kotlin';
import { describe, expect, it } from 'vitest';
import { YTMusic } from 'youtubei.js';
import { mapSearch } from './mappers';

// Captured with the filter chips' own params (web/fixtures/README.md), read as fetchSearch reads a filtered search.
const response = (name: string) =>
  ({
    success: true,
    status_code: 200,
    data: JSON.parse(readFileSync(fileURLToPath(new URL(`../../../../fixtures/innertube/${name}.json`, import.meta.url)), 'utf8')),
  }) as never;
const actions = { session: { context: { client: {} } } } as never;
const filtered = (name: string) => mapSearch(new YTMusic.Search(response(name), actions, true));

describe('filtered search', () => {
  it('captured the fixtures with the params the chips send', () => {
    const params = Object.fromEntries(searchFilters().map((f) => [f.id, f.params]));
    expect(params.SONGS).toBe('EgWKAQIIAWoKEAkQChAFEAMQBA==');
    expect(params.ALBUMS).toBe('EgWKAQIYAWoKEAkQChAFEAMQBA==');
  });

  it('is one kind of result', () => {
    const songs = filtered('search-songs');
    expect(songs.songs.length).toBeGreaterThan(1);
    expect(songs.albums.length + songs.artists.length + songs.playlists.length).toBe(0);
    const albums = filtered('search-albums');
    expect(albums.albums.length).toBeGreaterThan(1);
    expect(albums.songs.length).toBe(0);
  });
});
