import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { YTMusic } from 'youtubei.js';
import { MAX_RECENT_SEARCHES, useSearchHistory } from './searchHistory';
import { mergeSearchPages, nextSearchPage } from './searchPages';
import type { MusicItem, SearchResults } from './types';

const response = (name: string) =>
  ({
    success: true,
    status_code: 200,
    data: JSON.parse(readFileSync(fileURLToPath(new URL(`../../../../fixtures/innertube/${name}.json`, import.meta.url)), 'utf8')),
  }) as never;
const actions = { session: { context: { client: {} } } } as never;

const item = (id: string): MusicItem => ({
  kind: 'song',
  id,
  title: id,
  subtitle: '',
  artists: [],
  album: null,
  thumbnail: null,
  wide: false,
  durationSec: null,
  explicit: false,
});
const page = (songs: string[]): SearchResults => ({ top: [], songs: songs.map(item), artists: [], albums: [], playlists: [], videos: [] });

describe('search continuation', () => {
  it('reads on from a filtered search', () => {
    expect(nextSearchPage(new YTMusic.Search(response('search-songs'), actions, true))).not.toBeNull();
  });

  it('ends on an empty continuation rather than failing', async () => {
    const last = { has_continuation: true, getContinuation: () => Promise.reject(new Error('Continuation did not have any content')) };
    const next = nextSearchPage(last as never);
    await expect(next!()).resolves.toMatchObject({ next: null, data: { songs: [] } });
  });

  it('merges pages, showing a repeated item once', () => {
    expect(mergeSearchPages([page(['a', 'b']), page(['b', 'c'])]).songs.map((s) => s.id)).toEqual(['a', 'b', 'c']);
  });
});

describe('recent searches', () => {
  it('puts a repeat at the top and keeps the newest', () => {
    const { record } = useSearchHistory.getState();
    for (let i = 0; i < MAX_RECENT_SEARCHES + 5; i++) record(item(`s${i}`));
    record(item('s10'));
    const ids = useSearchHistory.getState().items.map((i) => i.id);
    expect(ids).toHaveLength(MAX_RECENT_SEARCHES);
    expect(ids[0]).toBe('s10');
    expect(ids.filter((id) => id === 's10')).toHaveLength(1);
    expect(ids).not.toContain('s0');
  });
});
