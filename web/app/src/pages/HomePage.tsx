import { useEffect, useRef } from 'react';
import { PageHeader } from '@/components/PageHeader';
import { ItemShelf, Shelves, TrackShelf } from '@/components/music/ShelfView';
import { LoadError, LoadMoreError, NeedsExtension } from '@/components/music/States';
import { useHome, useLibrary, useLibraryPlaylists, useYouTubeReady } from '@/data/music/queries';
import type { MusicItem, Track } from '@/data/music/types';
import { spotifyPlaylistItem } from '@/data/spotify/api';
import { useSpotifyDaylist, useSpotifyPlaylists } from '@/data/spotify/queries';
import { HomeSkeleton } from '@/skeletons/PageSkeletons';
import { ShelfSkeleton } from '@/skeletons/ShelfSkeleton';

const RECENT_TITLE = 'Recently Played';
const RECENT_LIMIT = 20;
const RECENT_SOURCE = { title: RECENT_TITLE, type: 'home' as const };

/**
 * The account's listening history, newest first, one row per song
 * (YtMusicRepository.recentlyPlayed): the first 20, signed in only. The same
 * query as Library's Recently Played, so either page warms the other.
 */
function useRecentlyPlayed(): Track[] {
  const history = useLibrary('recent');
  const first = history.data?.pages[0]?.data.tracks ?? [];
  const seen = new Set<string>();
  return first.filter((t) => !seen.has(t.videoId) && seen.add(t.videoId)).slice(0, RECENT_LIMIT);
}

/** How many of the library's playlists and albums the home rows show; See All has the rest. */
const LIBRARY_SHELF_LIMIT = 20;
/** YouTube's own library shelf on Home, which the rows above replace. */
const LIBRARY_FEED_TITLE = 'from your library';

/** The account's own playlists and saved albums, as Spotify's Home leads with: signed in only. */
function useLibraryShelves(): { playlists: MusicItem[]; albums: MusicItem[] } {
  const playlists = useLibraryPlaylists();
  const albums = useLibrary('albums');
  return {
    playlists: (playlists.data ?? []).slice(0, LIBRARY_SHELF_LIMIT),
    albums: (albums.data?.pages[0]?.data.grid ?? []).filter((item) => item.kind === 'album').slice(0, LIBRARY_SHELF_LIMIT),
  };
}

/** Spotify, once connected: the daylist first (when Spotify has made one), then the library's playlists. */
function useSpotifyShelf(): MusicItem[] {
  const daylist = useSpotifyDaylist();
  const playlists = useSpotifyPlaylists();
  return [...(daylist.data ? [daylist.data] : []), ...(playlists.data ?? []).map(spotifyPlaylistItem)].slice(0, LIBRARY_SHELF_LIMIT);
}

/** YouTube Music's home feed, as Apple's Home: shelf after shelf, more as you scroll. */
export default function HomePage() {
  const { missing } = useYouTubeReady();
  const home = useHome();
  const recent = useRecentlyPlayed();
  const library = useLibraryShelves();
  const spotify = useSpotifyShelf();
  const sentinel = useRef<HTMLDivElement>(null);

  // Loads the next shelves before the bottom comes into view; after a failed
  // page it waits for Try Again rather than retrying on every scroll.
  useEffect(() => {
    const el = sentinel.current;
    if (!el || !home.hasNextPage || home.isFetchNextPageError) return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry?.isIntersecting && !home.isFetchingNextPage) void home.fetchNextPage();
      },
      { root: document.getElementById('scrollable-page'), rootMargin: '600px' },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [home.hasNextPage, home.isFetchingNextPage, home.isFetchNextPageError, home.fetchNextPage]);

  if (missing) {
    return (
      <>
        <PageHeader title="Home" />
        <NeedsExtension />
      </>
    );
  }
  // Only a failed first page replaces the feed; a later one is shown under it.
  if (home.isError && !home.data) {
    return (
      <>
        <PageHeader title="Home" />
        <LoadError error={home.error} onRetry={() => void home.refetch()} />
      </>
    );
  }
  if (!home.data) return <HomeSkeleton />;

  // YouTube's feed can carry its own, older Recently Played, and a "From your
  // library" mix of what Your Playlists and Your Albums show: ours win.
  const ownLibrary = library.playlists.length > 0 || library.albums.length > 0;
  const shelves = home.data.pages.flatMap((page) => page.data).filter((shelf) => {
    const title = shelf.title.trim().toLowerCase();
    if (recent.length > 0 && title === RECENT_TITLE.toLowerCase()) return false;
    return !(ownLibrary && title === LIBRARY_FEED_TITLE);
  });
  return (
    <>
      <PageHeader title="Home" />
      {recent.length > 0 && <TrackShelf title={RECENT_TITLE} to="/library/recent" tracks={recent} source={RECENT_SOURCE} />}
      {library.playlists.length > 0 && <ItemShelf title="Your Playlists" to="/library/playlists" items={library.playlists} />}
      {library.albums.length > 0 && <ItemShelf title="Your Albums" to="/library/albums" items={library.albums} />}
      {spotify.length > 0 && <ItemShelf title="From Spotify" to="/spotify" items={spotify} />}
      <Shelves shelves={shelves} />
      <div ref={sentinel} />
      {home.isFetchingNextPage && <ShelfSkeleton kind="square" />}
      {home.isFetchNextPageError && <LoadMoreError onRetry={() => void home.fetchNextPage()} />}
    </>
  );
}
