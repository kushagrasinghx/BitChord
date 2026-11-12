import {
  infiniteQueryOptions,
  queryOptions,
  useInfiniteQuery,
  useMutation,
  useQuery,
  type InfiniteData,
} from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import type { DetectedExtension } from '@bitchord/shared/bridge';
import { extensionQueryKey, useExtension } from '@/extension/useExtension';
import { useYtAuth, ytAuthSnapshot } from '@/extension/useYtAuth';
import { queryClient } from '@/lib/queryClient';
import {
  addToPlaylist,
  fetchAlbum,
  fetchArtist,
  fetchBrowse,
  fetchCategories,
  fetchExplore,
  fetchHome,
  fetchLibrary,
  fetchLibraryPlaylists,
  fetchLikeStatus,
  fetchPlaylist,
  fetchPlaylistOptions,
  fetchSearch,
  fetchSuggestions,
  setListSaved,
  setTrackLiked,
  type LikeStatus,
  type Paged,
} from './api';
import { isLibrarySection, LIKED_MUSIC_PLAYLIST, type LibrarySection } from './library';
import type { AlbumPage, BrowseLink, BrowsePage, HeaderInfo, PlaylistPage, Shelf, Track } from './types';

// TanStack Query for every browse request (prompt §1: caching, dedupe,
// retries). YouTube requests need the extension, so every query waits for it;
// pages render their install prompt instead when it's missing.

const STALE = 10 * 60_000;

export const albumQuery = (id: string) =>
  queryOptions({ queryKey: ['music', 'album', id], queryFn: () => fetchAlbum(id), staleTime: STALE });
export const artistQuery = (id: string) =>
  queryOptions({ queryKey: ['music', 'artist', id], queryFn: () => fetchArtist(id), staleTime: STALE });
export const exploreQuery = () =>
  queryOptions({ queryKey: ['music', 'explore'], queryFn: fetchExplore, staleTime: STALE });
export const categoriesQuery = () =>
  queryOptions({ queryKey: ['music', 'categories'], queryFn: fetchCategories, staleTime: 60 * 60_000 });
export const searchQuery = (query: string) =>
  queryOptions({ queryKey: ['music', 'search', query], queryFn: () => fetchSearch(query), staleTime: STALE });
export const browseQuery = (link: BrowseLink) =>
  queryOptions({ queryKey: ['music', 'browse', link.browseId, link.params], queryFn: () => fetchBrowse(link), staleTime: STALE });

type HomePageParam = (() => Promise<Paged<Shelf[]>>) | null;
export const homeQuery = () =>
  infiniteQueryOptions({
    queryKey: ['music', 'home'],
    queryFn: ({ pageParam }: { pageParam: HomePageParam }) => (pageParam ? pageParam() : fetchHome()),
    initialPageParam: null as HomePageParam,
    getNextPageParam: (last) => last.next ?? undefined,
    staleTime: STALE,
  });

interface PlaylistChunk {
  header: PlaylistPage | null;
  tracks: Track[];
  next: (() => Promise<Paged<Track[]>>) | null;
}
type PlaylistPageParam = (() => Promise<Paged<Track[]>>) | null;
export const playlistQuery = (id: string) =>
  infiniteQueryOptions({
    queryKey: ['music', 'playlist', id],
    queryFn: async ({ pageParam }: { pageParam: PlaylistPageParam }): Promise<PlaylistChunk> => {
      if (pageParam) {
        const page = await pageParam();
        return { header: null, tracks: page.data, next: page.next };
      }
      const { page, more } = await fetchPlaylist(id);
      return { header: page, tracks: page.tracks, next: more };
    },
    initialPageParam: null as PlaylistPageParam,
    getNextPageParam: (last) => last.next ?? undefined,
    staleTime: STALE,
  });

/** True once the extension has answered; queries wait on it. */
export function useYouTubeReady(): { ready: boolean; missing: boolean } {
  const extension = useExtension();
  return {
    ready: extension.state === 'present' && extension.extension.info.capabilities.includes('yt'),
    missing: extension.state === 'missing',
  };
}

export function useAlbum(id: string) {
  const { ready } = useYouTubeReady();
  return useQuery({ ...albumQuery(id), enabled: ready });
}
export function useArtist(id: string) {
  const { ready } = useYouTubeReady();
  return useQuery({ ...artistQuery(id), enabled: ready });
}
export function useExplore() {
  const { ready } = useYouTubeReady();
  return useQuery({ ...exploreQuery(), enabled: ready });
}
export function useCategories() {
  const { ready } = useYouTubeReady();
  return useQuery({ ...categoriesQuery(), enabled: ready });
}
export function useSearch(query: string) {
  const { ready } = useYouTubeReady();
  return useQuery({ ...searchQuery(query), enabled: ready && query.trim().length > 0 });
}
export function useBrowse(link: BrowseLink) {
  const { ready } = useYouTubeReady();
  return useQuery({ ...browseQuery(link), enabled: ready });
}
export function useHome() {
  const { ready } = useYouTubeReady();
  return useInfiniteQuery({ ...homeQuery(), enabled: ready });
}
export function usePlaylist(id: string) {
  const { ready } = useYouTubeReady();
  return useInfiniteQuery({ ...playlistQuery(id), enabled: ready });
}

/** Typeahead: debounced, and anonymous like the app's (Innertube.searchTypeahead). */
export function useSuggestions(input: string) {
  const { ready } = useYouTubeReady();
  const [debounced, setDebounced] = useState(input);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(input.trim()), 150);
    return () => clearTimeout(timer);
  }, [input]);
  return useQuery({
    queryKey: ['music', 'suggestions', debounced],
    queryFn: () => fetchSuggestions(debounced),
    enabled: ready && debounced.length > 0,
    staleTime: STALE,
    placeholderData: (previous) => previous,
  });
}

/** Warms the first query of a page, for links' hover/focus prefetch (prompt §3). */
export function prefetchPageData(pathname: string): void {
  const detected = queryClient.getQueryData<DetectedExtension | null>(extensionQueryKey);
  if (!detected?.info.capabilities.includes('yt')) return;
  const [, kind, id] = pathname.split('/');
  if (!id) {
    if (kind === 'home') void queryClient.prefetchInfiniteQuery(homeQuery());
    if (kind === 'new') void queryClient.prefetchQuery(exploreQuery());
    return;
  }
  const decoded = decodeURIComponent(id);
  if (kind === 'album') void queryClient.prefetchQuery(albumQuery(decoded));
  else if (kind === 'artist') void queryClient.prefetchQuery(artistQuery(decoded));
  else if (kind === 'playlist') void queryClient.prefetchInfiniteQuery(playlistQuery(decoded));
  else if (kind === 'library' && isLibrarySection(decoded) && ytAuthSnapshot().signedIn) {
    void queryClient.prefetchInfiniteQuery(libraryQuery(decoded));
  }
}

// ---- Signed in (prompt §5.3) ------------------------------------------------------

/** True when YouTube requests are made as the user (the extension reports a chosen channel). */
export function useSignedIn(): boolean {
  const auth = useYtAuth();
  return auth.state === 'ready' && auth.auth.signedIn;
}

type LibraryPageParam = (() => Promise<Paged<BrowsePage>>) | null;
export const libraryQuery = (section: LibrarySection) =>
  infiniteQueryOptions({
    queryKey: ['music', 'library', section],
    queryFn: ({ pageParam }: { pageParam: LibraryPageParam }) => (pageParam ? pageParam() : fetchLibrary(section)),
    initialPageParam: null as LibraryPageParam,
    getNextPageParam: (last) => last.next ?? undefined,
    staleTime: 2 * 60_000,
  });

export function useLibrary(section: LibrarySection) {
  const signedIn = useSignedIn();
  return useInfiniteQuery({ ...libraryQuery(section), enabled: signedIn });
}

/** The sidebar's playlist list. */
export function useLibraryPlaylists() {
  const signedIn = useSignedIn();
  return useQuery({
    queryKey: ['music', 'library', 'sidebar-playlists'],
    queryFn: fetchLibraryPlaylists,
    enabled: signedIn,
    staleTime: 2 * 60_000,
  });
}

const likeKey = (videoId: string) => ['music', 'like', videoId] as const;

export function useLikeStatus(videoId: string | null) {
  const signedIn = useSignedIn();
  return useQuery({
    queryKey: likeKey(videoId ?? ''),
    queryFn: () => fetchLikeStatus(videoId!),
    enabled: signedIn && !!videoId,
    staleTime: 5 * 60_000,
  });
}

/** Like or unlike, shown at once and put back if YouTube refuses. */
export function useSetLiked() {
  return useMutation({
    mutationFn: ({ videoId, liked }: { videoId: string; liked: boolean }) => setTrackLiked(videoId, liked),
    onMutate: ({ videoId, liked }) => {
      const previous = queryClient.getQueryData<LikeStatus>(likeKey(videoId));
      queryClient.setQueryData<LikeStatus>(likeKey(videoId), liked ? 'LIKE' : 'INDIFFERENT');
      return { previous };
    },
    onError: (_error, { videoId }, context) => queryClient.setQueryData(likeKey(videoId), context?.previous),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: ['music', 'library', 'songs'] });
      void queryClient.invalidateQueries({ queryKey: ['music', 'playlist', LIKED_MUSIC_PLAYLIST] });
      void queryClient.invalidateQueries({ queryKey: ['music', 'playlist', `VL${LIKED_MUSIC_PLAYLIST}`] });
    },
  });
}

/** Writes an album's or playlist's "in library" state into whichever cached page shows it. */
function markSaved(playlistId: string, saved: boolean) {
  const apply = <T extends { library: HeaderInfo['library'] }>(page: T | null | undefined): T | null | undefined =>
    page?.library?.playlistId === playlistId ? { ...page, library: { playlistId, saved } } : page;
  queryClient.setQueriesData<AlbumPage>({ queryKey: ['music', 'album'] }, (page) => apply(page) ?? undefined);
  queryClient.setQueriesData<InfiniteData<PlaylistChunk>>({ queryKey: ['music', 'playlist'] }, (data) =>
    data && { ...data, pages: data.pages.map((chunk) => ({ ...chunk, header: apply(chunk.header) ?? null })) },
  );
}

/** "Add to Library" on an album or playlist header. */
export function useSetListSaved() {
  return useMutation({
    mutationFn: ({ playlistId, saved }: { playlistId: string; saved: boolean }) => setListSaved(playlistId, saved),
    onMutate: ({ playlistId, saved }) => markSaved(playlistId, saved),
    onError: (_error, { playlistId, saved }) => markSaved(playlistId, !saved),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: ['music', 'library'] });
    },
  });
}

export function usePlaylistOptions(videoId: string, enabled: boolean) {
  const signedIn = useSignedIn();
  return useQuery({
    queryKey: ['music', 'playlist-options', videoId],
    queryFn: () => fetchPlaylistOptions(videoId),
    enabled: signedIn && enabled,
    staleTime: 0,
  });
}

export function useAddToPlaylist() {
  return useMutation({
    mutationFn: ({ playlistId, videoId }: { playlistId: string; videoId: string }) => addToPlaylist(playlistId, videoId),
    onSettled: (_data, _error, { playlistId }) => {
      void queryClient.invalidateQueries({ queryKey: ['music', 'playlist', playlistId] });
      void queryClient.invalidateQueries({ queryKey: ['music', 'playlist', `VL${playlistId.replace(/^VL/, '')}`] });
    },
  });
}
