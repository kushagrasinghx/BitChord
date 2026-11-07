import type { Track } from '@/data/music/types';

export type TrackSort = 'custom' | 'title' | 'artist' | 'album' | 'shortest' | 'longest';

export const TRACK_SORTS: readonly { value: TrackSort; label: string }[] = [
  { value: 'custom', label: 'Custom order' },
  { value: 'title', label: 'Title' },
  { value: 'artist', label: 'Artist' },
  { value: 'album', label: 'Album' },
  { value: 'shortest', label: 'Shortest first' },
  { value: 'longest', label: 'Longest first' },
];

/** Lower case with accents and marks dropped, so "Beyoncé" is found by "beyonce". */
function fold(text: string): string {
  return text.normalize('NFKD').replace(/\p{M}/gu, '').toLowerCase();
}

function artistLine(track: Track): string {
  return track.artists.map((artist) => artist.name).join(', ');
}

/** Songs whose title, artists or album hold every word of [query], in any order. */
export function filterTracks(tracks: readonly Track[], query: string): Track[] {
  const words = fold(query).split(/\s+/).filter(Boolean);
  if (!words.length) return [...tracks];
  return tracks.filter((track) => {
    const text = fold(`${track.title} ${artistLine(track)} ${track.album?.name ?? ''}`);
    return words.every((word) => text.includes(word));
  });
}

const collator = new Intl.Collator(undefined, { sensitivity: 'base', numeric: true });

/**
 * [tracks] in [sort] order, ties kept in the playlist's own order. Songs with
 * nothing to sort by (no album, no duration) go last either way.
 */
export function sortTracks(tracks: readonly Track[], sort: TrackSort): Track[] {
  if (sort === 'custom') return [...tracks];
  const text = (track: Track) => (sort === 'title' ? track.title : sort === 'artist' ? artistLine(track) : (track.album?.name ?? ''));
  return tracks
    .map((track, i) => ({ track, i }))
    .sort((a, b) => {
      if (sort === 'shortest' || sort === 'longest') {
        const x = a.track.durationSec;
        const y = b.track.durationSec;
        if (x == null || y == null) return x == null && y == null ? a.i - b.i : x == null ? 1 : -1;
        return (sort === 'shortest' ? x - y : y - x) || a.i - b.i;
      }
      const x = text(a.track);
      const y = text(b.track);
      if (!x || !y) return !x && !y ? a.i - b.i : !x ? 1 : -1;
      return collator.compare(x, y) || a.i - b.i;
    })
    .map(({ track }) => track);
}
