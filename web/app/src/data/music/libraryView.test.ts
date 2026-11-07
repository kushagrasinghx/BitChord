import { describe, expect, it } from 'vitest';
import { creatorOf, filterItems, pinnedFirst, sortItems } from './libraryView';
import type { MusicItem } from './types';

function list(id: string, title: string, subtitle: string, artists: string[] = []): MusicItem {
  return {
    kind: 'playlist',
    id,
    title,
    subtitle,
    artists: artists.map((name) => ({ name, id: null })),
    album: null,
    thumbnail: null,
    wide: false,
    durationSec: null,
    explicit: false,
  };
}

const items = [
  list('a', 'Road Trip', 'Playlist • Momo Snob'),
  list('b', 'Aashiqui 2', 'Album', ['Mithoon']),
  list('c', 'Chill Mix', 'Playlist • YouTube Music'),
];
const ids = (xs: MusicItem[]) => xs.map((x) => x.id);

describe('library view', () => {
  it('reads who made it from the artists or the second line', () => {
    expect(creatorOf(items[0]!)).toBe('Momo Snob');
    expect(creatorOf(items[1]!)).toBe('Mithoon');
    expect(creatorOf(list('d', 'X', 'Playlist'))).toBe('');
  });

  it('filters by title or creator', () => {
    expect(ids(filterItems(items, 'momo'))).toEqual(['a']);
    expect(ids(filterItems(items, 'MIX youtube'))).toEqual(['c']);
    expect(filterItems(items, '')).toHaveLength(3);
  });

  it('sorts alphabetically or by creator, Recents as given', () => {
    expect(ids(sortItems(items, 'recent'))).toEqual(['a', 'b', 'c']);
    expect(ids(sortItems(items, 'alphabetical'))).toEqual(['b', 'c', 'a']);
    expect(ids(sortItems(items, 'creator'))).toEqual(['b', 'a', 'c']);
  });

  it('puts pins first, in pin order, and ignores pins that are gone', () => {
    expect(ids(pinnedFirst(items, ['c', 'gone', 'a']))).toEqual(['c', 'a', 'b']);
    expect(ids(pinnedFirst(items, []))).toEqual(['a', 'b', 'c']);
  });
});
