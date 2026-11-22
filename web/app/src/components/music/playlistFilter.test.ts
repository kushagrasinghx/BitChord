import { describe, expect, it } from 'vitest';
import { filterPlaylists } from './playlistFilter';

// app/src/test/.../PlaylistFilterTest.kt's cases, for the port of PlaylistFilter.kt.
describe('Find a playlist', () => {
  const names = ['Nepali Rock', 'Road Trip', 'rock classics', 'Beyoncé mix'];
  const find = (query: string) => filterPlaylists(names, query, (name) => name);

  it('shows every playlist for a blank search', () => expect(find('  ')).toEqual(names));

  it('matches every word, in any order and case', () => {
    expect(find('ROCK')).toEqual(['Nepali Rock', 'rock classics']);
    expect(find('rock nep')).toEqual(['Nepali Rock']);
    expect(find('jazz')).toEqual([]);
  });

  it('ignores accents', () => expect(find('beyonce')).toEqual(['Beyoncé mix']));
});
