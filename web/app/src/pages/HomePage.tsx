import { useQuery } from '@tanstack/react-query';
import { useEffect, useRef, type ReactNode } from 'react';
import { LogoMark } from '@/components/icons/LogoMark';
import { ShelfView, TrackShelf } from '@/components/music/ShelfView';
import { SignInBanner } from '@/components/music/SignInBanner';
import { LoadError, LoadMoreError, NeedsExtension } from '@/components/music/States';
import { FindYourMoodShelf, TopPicksShelf } from '@/components/music/TopPicksShelf';
import { artistQuery, useHome, useLibrary, useSignedIn, useYouTubeReady } from '@/data/music/queries';
import { buildTopPicks } from '@/data/music/topPicks';
import type { Track } from '@/data/music/types';
import { useYtAuth } from '@/extension/useYtAuth';
import { queryClient } from '@/lib/queryClient';
import { ReplayButton } from '@/replay/ReplayButton';
import { FeedSkeleton } from '@/skeletons/PageSkeletons';
import { ShelfSkeleton } from '@/skeletons/ShelfSkeleton';

const RECENT_TITLE = 'Recently Played';
const RECENT_LIMIT = 20;
const RECENT_SOURCE = { title: RECENT_TITLE, type: 'home' as const };

/** An artist's page through the artist route's own cache, so a Top Picks card warms the page it leads to. */
const artistPage = (id: string) => queryClient.fetchQuery(artistQuery(id));

/**
 * The account's listening history, newest first, one row per song
 * (YtMusicRepository.recentlyPlayed): the first 20 for the shelf, all of the
 * first page for Top Picks. Signed in only; the same query as Library's
 * Recently Played, so either page warms the other.
 */
function useHistory(): { tracks: Track[]; settled: boolean } {
  const signedIn = useSignedIn();
  const history = useLibrary('recent');
  const seen = new Set<string>();
  const tracks = (history.data?.pages[0]?.data.tracks ?? []).filter((t) => !seen.has(t.videoId) && seen.add(t.videoId));
  return { tracks, settled: !signedIn || history.isSuccess || history.isError };
}

/**
 * The page's heading (HomeScreen.kt HomeTitle): BitChord's wordmark, 0.9 of
 * the 34px title's size tall, and the way into Replay 14px after it. The
 * "Home" title itself is hidden on the phone; it stays for screen readers.
 */
function HomeTitle() {
  return (
    <div className="mx-(--body-gutter) flex items-center gap-3.5 pt-8 pb-3.5">
      <h1 className="sr-only">Home</h1>
      <LogoMark size={(31 * 730) / 484} className="text-primary" />
      <ReplayButton />
    </div>
  );
}

/**
 * Home, in the phone's order (HomeScreen.kt): the wordmark, the sign-in card
 * while signed out, Top Picks for You, then YouTube Music's feed shelf after
 * shelf, more as you scroll, with Find Your Mood after the first.
 */
export default function HomePage() {
  const { ready, missing } = useYouTubeReady();
  const auth = useYtAuth();
  const signedIn = useSignedIn();
  const home = useHome();
  const history = useHistory();
  const sentinel = useRef<HTMLDivElement>(null);
  const shelves = home.data?.pages[0]?.data;
  // Built once the feed it reads its mixes from is in, and once more if the account changes.
  const picks = useQuery({
    queryKey: ['music', 'top-picks', signedIn],
    queryFn: () => buildTopPicks({ shelves: shelves ?? [], history: history.tracks, artist: artistPage }),
    enabled: ready && auth.state !== 'checking' && !!shelves && history.settled,
    staleTime: 60 * 60_000,
  });

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
        <HomeTitle />
        <NeedsExtension />
      </>
    );
  }

  const topPicks = picks.data?.length ? (
    <TopPicksShelf
      picks={picks.data}
      listenerName={auth.state === 'ready' ? (auth.auth.active?.name ?? null) : null}
      history={history.tracks}
      artist={artistPage}
    />
  ) : (
    !picks.isError && <ShelfSkeleton kind="top-picks" />
  );
  const lead = (
    <>
      <HomeTitle />
      <SignInBanner />
      {topPicks}
    </>
  );

  // Only a failed first page replaces the feed; a later one is shown under it.
  if (home.isError && !home.data) {
    return (
      <>
        <HomeTitle />
        <SignInBanner />
        <LoadError error={home.error} onRetry={() => void home.refetch()} />
      </>
    );
  }
  if (!home.data) {
    return (
      <div aria-busy="true">
        <HomeTitle />
        <SignInBanner />
        <ShelfSkeleton kind="top-picks" />
        <FeedSkeleton />
      </div>
    );
  }

  // YouTube's feed can carry its own, older Recently Played; the history wins, as on the phone.
  const recent = history.tracks.slice(0, RECENT_LIMIT);
  const feed: ReactNode[] = [
    ...(recent.length ? [<TrackShelf key="recent" title={RECENT_TITLE} to="/library/recent" tracks={recent} source={RECENT_SOURCE} />] : []),
    ...home.data.pages
      .flatMap((page) => page.data)
      .filter((shelf) => !(recent.length > 0 && shelf.title.trim().toLowerCase() === RECENT_TITLE.toLowerCase()))
      .map((shelf, i) => <ShelfView key={`${shelf.title}-${i}`} shelf={shelf} />),
  ];
  // Find Your Mood follows the lead shelf, as on Apple Music's Home.
  feed.splice(Math.min(1, feed.length), 0, <FindYourMoodShelf key="find-your-mood" />);
  return (
    <>
      {lead}
      {feed}
      <div ref={sentinel} />
      {home.isFetchingNextPage && <ShelfSkeleton kind="square" />}
      {home.isFetchNextPageError && <LoadMoreError onRetry={() => void home.fetchNextPage()} />}
    </>
  );
}
