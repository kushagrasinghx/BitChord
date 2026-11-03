import { describe, expect, it } from 'vitest';
import type { Track } from '@/data/music/types';
import { filterTracks, sortTracks } from './playlistView';

function song(title: string, artist: string, album: string | null, durationSec: number | null): Track {
  return {
    videoId: title,
    title,
    artists: [{ name: artist, id: null }],
    album: album ? { name: album, id: null } : null,
    durationSec,
    explicit: false,
    thumbnail: null,
    index: null,
    plays: null,
  };
}

const list = [
  song('Tum Hi Ho', 'Arijit Singh', 'Aashiqui 2', 262),
  song('Halo', 'Beyoncé', 'I Am... Sasha Fierce', 261),
  song('Kesariya', 'Arijit Singh', null, 268),
  song('Alag Aasmaan', 'Anuv Jain', 'Alag Aasmaan', null),
];
const titles = (tracks: Track[]) => tracks.map((t) => t.title);

describe('finding in a playlist', () => {
  it('matches title, artist and album, every word in any order', () => {
    expect(titles(filterTracks(list, 'arijit'))).toEqual(['Tum Hi Ho', 'Kesariya']);
    expect(titles(filterTracks(list, 'aashiqui tum'))).toEqual(['Tum Hi Ho']);
    expect(titles(filterTracks(list, 'sasha'))).toEqual(['Halo']);
  });
  it('ignores case and accents', () => {
    expect(titles(filterTracks(list, 'BEYONCE'))).toEqual(['Halo']);
  });
  it('keeps everything for an empty search, and nothing for no match', () => {
    expect(filterTracks(list, '  ')).toHaveLength(4);
    expect(filterTracks(list, 'jazz')).toEqual([]);
  });
});

describe('sorting a playlist', () => {
  it('keeps the playlist order for Custom order', () => {
    expect(titles(sortTracks(list, 'custom'))).toEqual(titles(list));
  });
  it('sorts by title and artist, ties in playlist order', () => {
    expect(titles(sortTracks(list, 'title'))).toEqual(['Alag Aasmaan', 'Halo', 'Kesariya', 'Tum Hi Ho']);
    expect(titles(sortTracks(list, 'artist'))).toEqual(['Alag Aasmaan', 'Tum Hi Ho', 'Kesariya', 'Halo']);
  });
  it('puts songs with no album or no duration last', () => {
    expect(sortTracks(list, 'album').at(-1)?.title).toBe('Kesariya');
    expect(titles(sortTracks(list, 'shortest'))).toEqual(['Halo', 'Tum Hi Ho', 'Kesariya', 'Alag Aasmaan']);
    expect(titles(sortTracks(list, 'longest'))).toEqual(['Kesariya', 'Tum Hi Ho', 'Halo', 'Alag Aasmaan']);
  });
  it('leaves the list it was given alone', () => {
    const copy = [...list];
    sortTracks(list, 'title');
    expect(list).toEqual(copy);
  });
});
