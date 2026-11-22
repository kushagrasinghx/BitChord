import { useQuery } from '@tanstack/react-query';
import { Navigate, useParams } from 'react-router';
import { PageHeader } from '@/components/PageHeader';
import { CircleLockup, SquareLockup, VideoLockup } from '@/components/music/Lockups';
import { EmptyState, LoadError, LoadMoreError, NeedsExtension } from '@/components/music/States';
import { TrackList } from '@/components/music/TrackList';
import { useLoadMore } from '@/components/music/useLoadMore';
import { SegmentedControl } from '@/components/ui/SegmentedControl';
import { isLibrarySection, LIBRARY_SECTIONS, type LibrarySection } from '@/data/music/library';
import { LIBRARY_SORTS, sortedForLibrary, useLibrarySort } from '@/data/music/librarySort';
import { isPinned, pinnedFirst, usePinnedPlaylists } from '@/data/music/pinnedPlaylists';
import { useLibrary, useSignedIn, useYouTubeReady } from '@/data/music/queries';
import type { Track } from '@/data/music/types';
import { useYtAuth } from '@/extension/useYtAuth';
import { offlineSupported, offlineTracks } from '@/playback/offline/store';
import { PlainSkeleton } from '@/skeletons/PageSkeletons';
import { seeAllTracks } from './pageStyles';

/**
 * A Show all grid (LibraryScreen.kt LibraryGridPage, Common.kt libraryGrid):
 * cards at least 140px, never fewer than two across nor more than five, 12px
 * apart across and 20px down.
 */
const libraryGrid =
  'mx-(--body-gutter) mt-5 mb-10 grid grid-cols-[repeat(auto-fill,minmax(min(max(140px,calc((100%_-_48px)/5)),calc((100%_-_12px)/2)),1fr))] gap-x-3 gap-y-5';

/** The title row, with the grid's order beside it when there is a grid to order. */
function Header({ title, sortable }: { title: string; sortable: boolean }) {
  const { sort, setSort } = useLibrarySort();
  return (
    <div className="flex flex-wrap items-end justify-between gap-x-6 gap-y-3 pe-(--body-gutter)">
      <PageHeader title={title} />
      {sortable && <SegmentedControl label="Sort" className="w-[220px]" options={LIBRARY_SORTS} value={sort} onChange={setSort} />}
    </div>
  );
}

/**
 * Downloads (LibraryPhoneParts.kt, the Downloads folder): the songs kept in
 * this browser, from a song menu's Download and from Settings › Keep Liked
 * Songs Downloaded (one store for both, as the phone's Downloads), newest
 * first. They play from the copy here, online or not.
 */
function Downloads() {
  const downloads = useQuery({ queryKey: ['offline', 'tracks'], queryFn: offlineTracks, enabled: offlineSupported() });
  if (!offlineSupported()) return <Navigate to="/library" replace />;
  if (downloads.isError) return <LoadError error={downloads.error} onRetry={() => void downloads.refetch()} />;
  if (!downloads.data) return <PlainSkeleton />;
  const tracks: Track[] = [...downloads.data]
    .sort((a, b) => b.savedAt - a.savedAt)
    .map((t) => ({
      videoId: t.videoId,
      title: t.title,
      artists: [{ name: t.author, id: null }],
      album: null,
      durationSec: t.durationSec,
      explicit: false,
      thumbnail: t.thumbnail,
      index: null,
      plays: null,
    }));
  return (
    <>
      <PageHeader title="Downloads" />
      {tracks.length ? (
        <div className={seeAllTracks}>
          <TrackList tracks={tracks} variant="playlist" source={{ title: 'Downloads', type: 'library', id: 'downloads' }} />
        </div>
      ) : (
        <EmptyState title="No downloads yet" detail="Choose Download in a song’s menu, or turn on Keep Liked Songs Downloaded in Settings, to keep songs on this device." />
      )}
    </>
  );
}

/** A library section's Show all (prompt §5.3): the Library hub's shelves, and its History folder. */
function Section({ section }: { section: LibrarySection }) {
  const { missing } = useYouTubeReady();
  const auth = useYtAuth();
  const signedIn = useSignedIn();
  const sort = useLibrarySort((s) => s.sort);
  const pins = usePinnedPlaylists((s) => s.ids);
  const library = useLibrary(section);
  const next = library.hasNextPage && !library.isFetchingNextPage && !library.isFetchNextPageError ? () => void library.fetchNextPage() : null;
  const loadMore = useLoadMore(next);

  const title = LIBRARY_SECTIONS[section].title;
  if (missing) return <NeedsExtension what="your library" />;
  // Signed out, the Library hub has the way to sign in.
  if (auth.state === 'ready' && !signedIn) return <Navigate to="/library" replace />;
  if (library.isError && !library.data) return <LoadError error={library.error} onRetry={() => void library.refetch()} />;
  if (!library.data) return <PlainSkeleton />;

  // Pins win over the default order; an explicit sort is the more deliberate choice and reorders them too (LibraryScreen.kt LibraryGridPage).
  const grid = sortedForLibrary(
    pinnedFirst(
      library.data.pages.flatMap((page) => page.data.grid),
      pins,
    ),
    sort,
  );
  const tracks = library.data.pages.flatMap((page) => page.data.tracks);
  return (
    <>
      <Header title={title} sortable={grid.length > 1} />
      {!grid.length && !tracks.length && <EmptyState title="Nothing here yet" />}
      {grid.length > 0 && (
        <ul className={libraryGrid}>
          {grid.map((item, i) => (
            <li key={`${item.id}-${i}`}>
              {item.kind === 'artist' ? <CircleLockup item={item} /> : item.wide ? <VideoLockup item={item} /> : <SquareLockup item={item} pinned={item.kind === 'playlist' && isPinned(pins, item.id)} />}
            </li>
          ))}
        </ul>
      )}
      {tracks.length > 0 && (
        <div className={seeAllTracks}>
          <TrackList tracks={tracks} variant="playlist" source={{ title, type: 'library', id: section }} onNearEnd={next ?? undefined} />
        </div>
      )}
      {grid.length > 0 && <div ref={loadMore} aria-hidden="true" />}
      {library.isFetchNextPageError && <LoadMoreError onRetry={() => void library.fetchNextPage()} />}
    </>
  );
}

/** /library/:section: Downloads, or one of the account's library sections. */
export default function LibraryPage() {
  const section = useParams().section ?? '';
  if (section === 'downloads') return <Downloads />;
  if (!isLibrarySection(section)) return <Navigate to="/library" replace />;
  return <Section key={section} section={section} />;
}
