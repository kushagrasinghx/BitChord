import { infiniteQueryOptions, useInfiniteQuery } from '@tanstack/react-query';
import type { YTMusic } from 'youtubei.js/web';
import { getMusicClient } from '@/playback/youtube/innertube';
import type { Paged } from './api';
import { mapSearch } from './mappers';
import { useYouTubeReady } from './queries';
import type { SearchResults } from './types';

type Continuable = Pick<YTMusic.Search, 'has_continuation' | 'getContinuation'>;

const EMPTY: SearchResults = { top: [], songs: [], artists: [], albums: [], playlists: [], videos: [] };

/**
 * The pages after a filtered search's first: YouTube Music sends a filtered
 * search one shelf at a time, and the phone reads on as the list nears its end
 * (SearchScreen.kt, onLoadMore). The mixed All page is one page, so it has no
 * next. A continuation that comes back empty is the end of the list.
 */
export function nextSearchPage(search: Continuable): Paged<SearchResults>['next'] {
  if (!search.has_continuation) return null;
  return async () => {
    try {
      const more = await search.getContinuation();
      return { data: mapSearch({ contents: more.contents ? [more.contents] : [] }), next: nextSearchPage(more) };
    } catch (error) {
      if (/did not have any content|no continuation/i.test(String(error))) return { data: EMPTY, next: null };
      throw error;
    }
  };
}

/** A search's first page, narrowed by a filter's params (Models.kt SearchFilter) when given. */
export async function fetchSearchPage(query: string, params?: string | null): Promise<Paged<SearchResults>> {
  const yt = await getMusicClient();
  if (!params) return { data: mapSearch(await yt.music.search(query)), next: null };
  // YouTube.js only builds params for its own five types; Library's are YouTube Music's own.
  const { YTMusic } = await import('youtubei.js/web');
  const search = new YTMusic.Search(await yt.actions.execute('/search', { query, params, client: 'YTMUSIC' }), yt.actions, true);
  return { data: mapSearch(search), next: nextSearchPage(search) };
}

type SearchPageParam = Paged<SearchResults>['next'];

export const searchPagesQuery = (query: string, params?: string | null) =>
  infiniteQueryOptions({
    queryKey: ['music', 'search-pages', query.trim(), params ?? null],
    queryFn: ({ pageParam }: { pageParam: SearchPageParam }) => (pageParam ? pageParam() : fetchSearchPage(query.trim(), params)),
    initialPageParam: null as SearchPageParam,
    getNextPageParam: (last) => last.next ?? undefined,
    staleTime: 10 * 60_000,
  });

/** A search and the pages after it, read on demand. */
export function useSearchPages(query: string, params?: string | null) {
  const { ready } = useYouTubeReady();
  return useInfiniteQuery({ ...searchPagesQuery(query, params), enabled: ready && query.trim().length > 0 });
}

/** Every page so far, as one set of results; an item a later page repeats is shown once. */
export function mergeSearchPages(pages: readonly SearchResults[]): SearchResults {
  const [first = EMPTY, ...rest] = pages;
  const merged: SearchResults = { ...first };
  for (const page of rest) {
    for (const key of ['songs', 'artists', 'albums', 'playlists', 'videos'] as const) {
      const seen = new Set(merged[key].map((i) => i.id));
      merged[key] = [...merged[key], ...page[key].filter((i) => !seen.has(i.id))];
    }
  }
  return merged;
}
