import { describe, expect, it } from 'vitest';
import { filterPlaylists } from './playlistFilter';

describe('filterPlaylists', () => {
  const lists = [{ title: 'Nepali Rock' }, { title: 'Heavvvvvvy' }, { title: 'rock classics' }];
  it('keeps every playlist for an empty search', () => {
    expect(filterPlaylists(lists, '  ')).toEqual(lists);
  });
  it('matches every word, in any case and order', () => {
    expect(filterPlaylists(lists, 'ROCK')).toEqual([lists[0], lists[2]]);
    expect(filterPlaylists(lists, 'rock nep')).toEqual([lists[0]]);
    expect(filterPlaylists(lists, 'jazz')).toEqual([]);
  });
});
