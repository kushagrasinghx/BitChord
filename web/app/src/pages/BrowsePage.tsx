import { useParams, useSearchParams } from 'react-router';
import { PageHeader } from '@/components/PageHeader';
import { CircleLockup, SquareLockup, VideoLockup } from '@/components/music/Lockups';
import { Shelves } from '@/components/music/ShelfView';
import { EmptyState, LoadError, NeedsExtension } from '@/components/music/States';
import { TrackList } from '@/components/music/TrackList';
import { useBrowse, useYouTubeReady } from '@/data/music/queries';
import { PlainSkeleton } from '@/skeletons/PageSkeletons';
import { seeAllGrid, seeAllTracks } from './pageStyles';

/** See All, moods and genres: a grid, a song list, or shelves, whichever the page is. */
export default function BrowsePage() {
  const browseId = decodeURIComponent(useParams().id ?? '');
  const [params] = useSearchParams();
  const { missing } = useYouTubeReady();
  const browse = useBrowse({ browseId, params: params.get('params') });

  if (missing) return <NeedsExtension />;
  if (browse.isError) return <LoadError error={browse.error} onRetry={() => void browse.refetch()} />;
  if (!browse.data) return <PlainSkeleton />;

  const page = browse.data;
  const title = page.title || 'Browse';
  const empty = !page.grid.length && !page.tracks.length && !page.shelves.length;
  return (
    <>
      <PageHeader title={title} />
      {empty && <EmptyState title="Nothing here yet" />}
      {page.grid.length > 0 && (
        <ul className={seeAllGrid} data-wide={page.grid.every((i) => i.wide) || undefined}>
          {page.grid.map((item, i) => (
            <li key={`${item.id}-${i}`}>
              {item.kind === 'artist' ? (
                <CircleLockup item={item} />
              ) : item.wide ? (
                <VideoLockup item={item} />
              ) : (
                <SquareLockup item={item} />
              )}
            </li>
          ))}
        </ul>
      )}
      {page.tracks.length > 0 && (
        <div className={seeAllTracks}>
          <TrackList tracks={page.tracks} variant="playlist" source={{ title, type: 'other', id: browseId }} />
        </div>
      )}
      {!page.grid.length && <Shelves shelves={page.shelves} />}
    </>
  );
}
