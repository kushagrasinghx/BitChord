import { useEffect, useMemo, useRef, useState } from 'react';
import { Navigate, useParams } from 'react-router';
import { PageHeader } from '@/components/PageHeader';
import { PrefetchLink } from '@/components/PrefetchLink';
import { Artwork } from '@/components/music/Artwork';
import { LibraryToolbar, useLibraryPrefs } from '@/components/music/LibraryToolbar';
import { hrefFor } from '@/components/music/links';
import { usePinMenu } from '@/components/music/PinMenu';
import { CircleLockup, SquareLockup, VideoLockup } from '@/components/music/Lockups';
import { EmptyState, LoadError, LoadMoreError, NeedsExtension } from '@/components/music/States';
import { TrackList } from '@/components/music/TrackList';
import { isLibrarySection, LIBRARY_SECTIONS } from '@/data/music/library';
import { creatorOf, filterItems, sortItems } from '@/data/music/libraryView';
import { useLibrary, useSignedIn, useYouTubeReady } from '@/data/music/queries';
import type { MusicItem } from '@/data/music/types';
import { useYtAuth } from '@/extension/useYtAuth';
import { PlainSkeleton } from '@/skeletons/PageSkeletons';
import { seeAllGrid, seeAllTracks } from './pageStyles';

/** Asks for the next page when [target] scrolls into view. */
function useLoadMore(onVisible: (() => void) | null) {
  const target = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const node = target.current;
    if (!node || !onVisible) return;
    const observer = new IntersectionObserver((entries) => entries.some((e) => e.isIntersecting) && onVisible(), {
      rootMargin: '600px 0px',
    });
    observer.observe(node);
    return () => observer.disconnect();
  }, [onVisible]);
  return target;
}

/** A playlist can be pinned to the sidebar from its right-click menu. */
function pinnable(item: MusicItem) {
  return item.kind === 'playlist' ? item : null;
}

/** The list layout's row: artwork, title, who made it. */
function LibraryRow({ item }: { item: MusicItem }) {
  const pinMenu = usePinMenu(pinnable(item));
  const creator = creatorOf(item);
  return (
    <li onContextMenu={pinMenu.onContextMenu}>
      {pinMenu.menu}
      <PrefetchLink
        to={hrefFor(item) ?? '#'}
        className="grid grid-cols-[48px_minmax(0,1fr)] items-center gap-3 rounded-[10px] p-1.5 hover:bg-tracklist-hover"
      >
        <Artwork src={item.thumbnail} displayWidth={48} radius={item.kind === 'artist' ? '50%' : 'var(--radius-artwork-small)'} />
        <span className="flex min-w-0 flex-col">
          <span className="truncate type-body text-primary">{item.title}</span>
          <span className="truncate type-callout text-secondary">{creator ? `${item.subtitle.split(' • ')[0]} • ${creator}` : item.subtitle}</span>
        </span>
      </PrefetchLink>
    </li>
  );
}

/** A grid lockup; playlists pin from their right-click menu. */
function GridItem({ item }: { item: MusicItem }) {
  const pinMenu = usePinMenu(pinnable(item));
  return (
    <li onContextMenu={pinMenu.onContextMenu}>
      {pinMenu.menu}
      {item.kind === 'artist' ? <CircleLockup item={item} /> : item.wide ? <VideoLockup item={item} /> : <SquareLockup item={item} />}
    </li>
  );
}

/** A library section (prompt §5.3): Recently Played, Artists, Albums, Songs, Made for You, All Playlists. */
export default function LibraryPage() {
  const section = useParams().section ?? '';
  const { missing } = useYouTubeReady();
  const auth = useYtAuth();
  const signedIn = useSignedIn();
  const library = useLibrary(isLibrarySection(section) ? section : 'playlists');
  const [query, setQuery] = useState('');
  const [prefs, setPrefs] = useLibraryPrefs(section);
  // Recently Played keeps its own order, the point of it; the other grids can be filtered and sorted.
  const arrangeable = section !== 'recent';
  const wantsAll = arrangeable && (query.trim() !== '' || prefs.sort !== 'recent');
  useEffect(() => setQuery(''), [section]);
  useEffect(() => {
    if (wantsAll && library.hasNextPage && !library.isFetchingNextPage && !library.isFetchNextPageError) void library.fetchNextPage();
  }, [wantsAll, library]);
  const allGrid = useMemo(() => library.data?.pages.flatMap((page) => page.data.grid) ?? [], [library.data]);
  const shownGrid = useMemo(
    () => (arrangeable ? filterItems(sortItems(allGrid, prefs.sort), query) : allGrid),
    [allGrid, arrangeable, prefs.sort, query],
  );
  const loadMore = useLoadMore(
    library.hasNextPage && !library.isFetchingNextPage && !library.isFetchNextPageError
      ? () => void library.fetchNextPage()
      : null,
  );

  if (!isLibrarySection(section)) return <Navigate to="/home" replace />;
  const title = LIBRARY_SECTIONS[section].title;
  if (missing) return <NeedsExtension what="your library" />;
  if (auth.state === 'ready' && !signedIn) {
    return (
      <>
        <PageHeader title={title} />
        <EmptyState
          title="Sign in to see your library"
          detail="BitChord uses the YouTube Music account this browser is signed in to. Sign in on music.youtube.com, then come back."
        />
      </>
    );
  }
  if (library.isError && !library.data) return <LoadError error={library.error} onRetry={() => void library.refetch()} />;
  if (!library.data) return <PlainSkeleton />;

  const grid = shownGrid;
  const tracks = library.data.pages.flatMap((page) => page.data.tracks);
  return (
    <>
      <PageHeader title={title} />
      {arrangeable && allGrid.length > 0 && (
        <LibraryToolbar what={title.replace(/^All /, '').toLowerCase()} query={query} onQuery={setQuery} prefs={prefs} onPrefs={setPrefs} />
      )}
      {!allGrid.length && !tracks.length && <EmptyState title="Nothing here yet" />}
      {query.trim() && allGrid.length > 0 && !grid.length && !library.hasNextPage && (
        <p className="py-10 text-center type-body text-secondary">Nothing matches “{query.trim()}”.</p>
      )}
      {grid.length > 0 &&
        (arrangeable && prefs.layout === 'list' ? (
          <ul className="mx-(--body-gutter) mt-4 mb-10 grid grid-cols-[repeat(auto-fill,minmax(300px,1fr))] gap-x-5">
            {grid.map((item, i) => (
              <LibraryRow key={`${item.id}-${i}`} item={item} />
            ))}
          </ul>
        ) : (
          <ul className={seeAllGrid} data-wide={grid.every((i) => i.wide) || undefined}>
            {grid.map((item, i) => (
              <GridItem key={`${item.id}-${i}`} item={item} />
            ))}
          </ul>
        ))}
      {tracks.length > 0 && (
        <div className={seeAllTracks}>
          <TrackList
            tracks={tracks}
            variant="playlist"
            source={{ title, type: 'library', id: section }}
            onNearEnd={
              library.hasNextPage && !library.isFetchingNextPage && !library.isFetchNextPageError
                ? () => void library.fetchNextPage()
                : undefined
            }
          />
        </div>
      )}
      {grid.length > 0 && <div ref={loadMore} aria-hidden="true" />}
      {library.isFetchNextPageError && <LoadMoreError onRetry={() => void library.fetchNextPage()} />}
    </>
  );
}
