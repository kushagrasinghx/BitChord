import type { DeezerPlaylist, DeezerTrack } from '@bitchord/shared/bridge';
import { best } from '@/data/match/trackMatcher';
import { searchSongs } from '@/data/music/api';
import type { Track } from '@/data/music/types';
import { candidateOf } from '@/data/spotify/api';
import { getBridge } from '@/extension/bridge';

/**
 * Deezer through the extension: the library, a playlist's tracks, and matching
 * each one to its YouTube Music song. Ports DeezerLibrary.kt's paging and
 * SpotifyImporter.matchTrack.
 */

export const LOVED_ID = 'loved';

function bridge() {
  const b = getBridge();
  if (!b) throw new Error('The BitChord extension is not installed');
  return b;
}

/** Every playlist in the library, Favorite Tracks first, however many pages it takes. */
export async function fetchDeezerPlaylists(): Promise<DeezerPlaylist[]> {
  const out: DeezerPlaylist[] = [];
  for (let offset = 0; ; ) {
    const page = await bridge().call('deezer.playlists', { offset }, { timeoutMs: 30_000 });
    out.push(...page.items);
    offset += page.raw;
    if (page.raw === 0 || offset >= page.total) break;
  }
  return out;
}

export function fetchDeezerPlaylistInfo(playlistId: string) {
  return bridge().call('deezer.playlist', { playlistId }, { timeoutMs: 30_000 });
}

/** Every track of a playlist; [onPage] gets the running list after each page, so a long one fills in. */
export async function fetchDeezerTracks(playlistId: string, onPage: (soFar: DeezerTrack[], total: number) => void): Promise<DeezerTrack[]> {
  const out: DeezerTrack[] = [];
  for (let offset = 0; ; ) {
    const page = await bridge().call('deezer.tracks', { playlistId, offset }, { timeoutMs: 30_000 });
    out.push(...page.items);
    offset += page.raw;
    onPage([...out], page.total);
    if (page.raw === 0 || offset >= page.total) break;
  }
  return out;
}

/** A Deezer row as a list row, before its YouTube Music version is known. */
export function pendingTrack(track: DeezerTrack, unavailable: 'matching' | 'missing' = 'matching'): Track {
  return {
    videoId: `deezer:${track.id}`,
    title: track.title,
    artists: track.artist ? track.artist.split(', ').map((name) => ({ name, id: null })) : [],
    album: track.album ? { name: track.album, id: null } : null,
    durationSec: track.durationMs > 0 ? Math.round(track.durationMs / 1000) : null,
    explicit: false,
    thumbnail: track.imageUrl,
    index: null,
    plays: null,
    unavailable,
  };
}

const matched = new Map<string, Track | null>();

/**
 * The YouTube Music song that is [track], or null if there is none:
 * the best candidate by title, artist, length and album, and failing that
 * the search's first hit.
 */
export async function matchToYouTube(track: DeezerTrack): Promise<Track | null> {
  if (matched.has(track.id)) return matched.get(track.id)!;
  const query = [track.title, track.artist].filter((s) => s.trim()).join(' ');
  const candidates = await searchSongs(query);
  const target = {
    title: track.title,
    artist: track.artist,
    durationSec: track.durationMs > 0 ? Math.round(track.durationMs / 1000) : null,
    album: track.album,
  };
  const scored = best(candidates.map((c) => ({ ...candidateOf(c), track: c })), target);
  const found = scored?.track ?? candidates[0] ?? null;
  const result = found && { ...found, thumbnail: found.thumbnail ?? track.imageUrl };
  matched.set(track.id, result);
  return result;
}
