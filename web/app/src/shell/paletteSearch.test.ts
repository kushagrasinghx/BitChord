import { describe, expect, it } from 'vitest';
import type { MusicItem, SearchResults } from '@/data/music/types';
import { withRecent, RECENTS_LIMIT, type RecentItem } from '@/state/recents';
import { flattenResults, localMatches, matches } from './paletteSearch';

const item = (kind: MusicItem['kind'], id: string, title: string, subtitle = ''): MusicItem => ({
  kind, id, title, subtitle, artists: [], album: null, thumbnail: null, wide: false, durationSec: null, explicit: false,
});

describe('matches', () => {
  it('needs every word, in any order, ignoring case and accents', () => {
    expect(matches(item('playlist', 'a', 'Nepali Rock', 'Playlist • Salil'), 'rock nep')).toBe(true);
    expect(matches(item('song', 'b', 'Bípul Chettri'), 'bipul')).toBe(true);
    expect(matches(item('playlist', 'c', 'Nepali Rock'), 'nepali emo')).toBe(false);
    expect(matches(item('playlist', 'c', 'Nepali Rock'), '   ')).toBe(false);
  });
});

describe('localMatches', () => {
  it('puts titles that start with the query first and drops repeats', () => {
    const list = [item('playlist', '1', 'Heavy Nepali'), item('playlist', '2', 'Nepali Rock'), item('playlist', '2', 'Nepali Rock')];
    expect(localMatches(list, 'nepali').map((i) => i.id)).toEqual(['2', '1']);
  });
});

describe('flattenResults', () => {
  it('lists each result once, top results first, without what the library already shows', () => {
    const song = item('song', 's1', 'Abhagi');
    const pl = item('playlist', 'p1', 'Nepali Emo');
    const results: SearchResults = { top: [pl], songs: [song], artists: [], albums: [], playlists: [pl, item('playlist', 'p2', 'Mine')], videos: [] };
    expect(flattenResults(results, new Set(['p2'])).map((i) => i.id)).toEqual(['p1', 's1']);
  });
});

describe('withRecent', () => {
  it('moves a reopened item to the front and keeps the list short', () => {
    const r = (id: string): RecentItem => ({ kind: 'playlist', id, title: id, subtitle: '', thumbnail: null });
    let list: RecentItem[] = [];
    for (let i = 0; i < RECENTS_LIMIT + 3; i++) list = withRecent(list, r(String(i)));
    list = withRecent(list, r('5'));
    expect(list).toHaveLength(RECENTS_LIMIT);
    expect(list[0]?.id).toBe('5');
    expect(list.filter((x) => x.id === '5')).toHaveLength(1);
  });
});
