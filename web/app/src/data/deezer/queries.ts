import { useQuery } from '@tanstack/react-query';
import { useSyncExternalStore } from 'react';
import type { DeezerTrack } from '@bitchord/shared/bridge';
import type { Track } from '@/data/music/types';
import { useDeezerAuth, useDeezerSignedIn } from '@/extension/useDeezerAuth';
import { fetchDeezerPlaylistInfo, fetchDeezerPlaylists, fetchDeezerTracks, matchToYouTube, pendingTrack } from './api';

export function useDeezerPlaylists() {
  const signedIn = useDeezerSignedIn();
  return useQuery({
    queryKey: ['deezer', 'data', 'playlists'],
    queryFn: fetchDeezerPlaylists,
    enabled: signedIn,
    staleTime: 5 * 60_000,
  });
}

export function useDeezerPlaylistInfo(playlistId: string) {
  const signedIn = useDeezerSignedIn();
  return useQuery({
    queryKey: ['deezer', 'data', 'playlist', playlistId],
    queryFn: () => fetchDeezerPlaylistInfo(playlistId),
    enabled: signedIn && !!playlistId,
    staleTime: 5 * 60_000,
  });
}

/** How many tracks are looked up on YouTube Music at once (MainViewModel.SPOTIFY_MATCH_PARALLELISM). */
const MATCH_PARALLELISM = 6;

export interface DeezerRows {
  status: 'loading' | 'ready' | 'error';
  error: unknown;
  /** Deezer's rows, each replaced by its YouTube Music song as the matches arrive. */
  rows: Track[];
  total: number;
  /** How many rows still wait on a match. */
  matching: number;
}

/**
 * One playlist's rows and their matches (MainViewModel.openDeezerPage). The
 * rows go up as soon as they are read, marked as still matching, and are
 * swapped in place as their YouTube Music songs are found.
 */
class PlaylistJob {
  state: DeezerRows = { status: 'loading', error: null, rows: [], total: 0, matching: 0 };
  private source: DeezerTrack[] = [];
  private readonly listeners = new Set<() => void>();
  private fetching = false;
  private readonly inFlight = new Set<number>();

  constructor(private readonly playlistId: string) {}

  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    this.start();
    return () => void this.listeners.delete(listener);
  };

  private emit(change: Partial<DeezerRows>) {
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
      fetchDeezerTracks(this.playlistId, (soFar, total) => {
        this.source = soFar;
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
export function useDeezerRows(playlistId: string): DeezerRows & { retry: () => void } {
  const auth = useDeezerAuth();
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
