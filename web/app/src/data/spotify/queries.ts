import { useQuery } from '@tanstack/react-query';
import { useSyncExternalStore } from 'react';
import type { SpotifyTrack } from '@bitchord/shared/bridge';
import type { Track } from '@/data/music/types';
import { useSpotifyAuth, useSpotifySignedIn } from '@/extension/useSpotifyAuth';
import { fetchSpotifyPlaylistInfo, fetchSpotifyPlaylists, fetchSpotifyTracks, matchToYouTube, pendingTrack } from './api';

export function useSpotifyPlaylists() {
  const signedIn = useSpotifySignedIn();
  return useQuery({
    queryKey: ['spotify', 'data', 'playlists'],
    queryFn: fetchSpotifyPlaylists,
    enabled: signedIn,
    staleTime: 5 * 60_000,
  });
}

export function useSpotifyPlaylistInfo(playlistId: string) {
  const signedIn = useSpotifySignedIn();
  return useQuery({
    queryKey: ['spotify', 'data', 'playlist', playlistId],
    queryFn: () => fetchSpotifyPlaylistInfo(playlistId),
    enabled: signedIn && !!playlistId,
    staleTime: 5 * 60_000,
  });
}

/** How many tracks are looked up on YouTube Music at once (MainViewModel.SPOTIFY_MATCH_PARALLELISM). */
const MATCH_PARALLELISM = 6;

export interface SpotifyRows {
  status: 'loading' | 'ready' | 'error';
  error: unknown;
  /** Spotify's rows, each replaced by its YouTube Music song as the matches arrive. */
  rows: Track[];
  total: number;
  /** How many rows still wait on a match. */
  matching: number;
}

/**
 * One playlist's rows and their matches (MainViewModel.openSpotifyPage). The
 * rows go up as soon as they are read, marked as still matching, and are
 * swapped in place as their YouTube Music songs are found. Kept per playlist
 * for the session, like the phone's hoisted page state, so going back to a
 * playlist doesn't look everything up again; matching pauses while nobody is
 * looking and resumes where it stopped.
 */
class PlaylistJob {
  state: SpotifyRows = { status: 'loading', error: null, rows: [], total: 0, matching: 0 };
  private source: SpotifyTrack[] = [];
  private readonly listeners = new Set<() => void>();
  private fetching = false;
  private readonly inFlight = new Set<number>();

  constructor(private readonly playlistId: string) {}

  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    this.start();
    return () => void this.listeners.delete(listener);
  };

  private emit(change: Partial<SpotifyRows>) {
    const rows = change.rows ?? this.state.rows;
    this.state = { ...this.state, ...change, matching: rows.filter((r) => r.unavailable === 'matching').length };
    this.listeners.forEach((l) => l());
  }

  retry() {
    if (this.state.status !== 'error') return;
    this.emit({ status: 'loading', error: null });
    this.start();
  }

  private start() {
    if (this.state.status === 'loading' && !this.fetching && !this.source.length) {
      this.fetching = true;
      fetchSpotifyTracks(this.playlistId, (soFar, total) => {
        this.source = soFar;
        // Rows already matched keep their match; new ones join as pending.
        const rows = soFar.map((t, i) => this.state.rows[i] ?? pendingTrack(t));
        this.emit({ rows, total });
        this.pump();
      })
        .then(() => this.emit({ status: 'ready' }))
        .catch((error: unknown) => this.emit({ status: this.source.length ? 'ready' : 'error', error }))
        .finally(() => (this.fetching = false));
      return;
    }
    this.pump();
  }

  /** Keeps up to MATCH_PARALLELISM lookups going, in order, while someone is looking. */
  private pump() {
    while (this.inFlight.size < MATCH_PARALLELISM && this.listeners.size > 0) {
      const index = this.state.rows.findIndex((r, i) => r.unavailable === 'matching' && !this.inFlight.has(i));
      if (index < 0) return;
      this.inFlight.add(index);
      const track = this.source[index]!;
      void matchToYouTube(track)
        .catch(() => null)
        .then((found) => {
          const rows = [...this.state.rows];
          rows[index] = found ?? pendingTrack(track, 'missing');
          this.emit({ rows });
        })
        .finally(() => {
          this.inFlight.delete(index);
          this.pump();
        });
    }
  }
}

const jobs = new Map<string, PlaylistJob>();

/** Per account and playlist: another account's rows are never shown. */
export function useSpotifyRows(playlistId: string): SpotifyRows & { retry: () => void } {
  const auth = useSpotifyAuth();
  const account = auth.state === 'ready' && auth.auth.signedIn ? (auth.auth.user?.name ?? '?') : null;
  const key = `${account}|${playlistId}`;
  let job = jobs.get(key);
  if (!job) {
    job = new PlaylistJob(playlistId);
    jobs.set(key, job);
  }
  const current = job;
  const state = useSyncExternalStore(account ? current.subscribe : noSubscribe, () => current.state);
  return { ...state, retry: () => current.retry() };
}

const noSubscribe = () => () => undefined;
