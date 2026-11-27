import { useMemo } from 'react';
import { useParams } from 'react-router';
import { AboutSection, DetailHeader } from '@/components/music/DetailHeader';
import { DetailMenu } from '@/components/music/DetailMenu';
import { DetailSearchButton, DetailSearchField, NothingMatches, useDetailFilter } from '@/components/music/DetailSearchField';
import { AddToPlaylistsButton } from '@/components/music/PlaylistPicker';
import { playList, shuffleList } from '@/components/music/play';
import { Shelves } from '@/components/music/ShelfView';
import { LoadError, NeedsExtension } from '@/components/music/States';
import { TrackList } from '@/components/music/TrackList';
import { useAlbumMotion } from '@/data/apple/motionArt';
import { sortTracks, useSongSorts } from '@/data/music/library';
import { useAlbum, useYouTubeReady } from '@/data/music/queries';
import { useArtworkColor } from '@/lib/artworkColor';
import { usePageTint } from '@/lib/usePageTint';
import { AlbumSkeleton } from '@/skeletons/PageSkeletons';
import { detailFooter } from './pageStyles';

/** An album (DetailScreen.kt, a release): header, About, the filter, numbered tracks, footer, "more" shelves. */
export default function AlbumPage() {
  const id = decodeURIComponent(useParams().id ?? '');
  const { missing } = useYouTubeReady();
  const album = useAlbum(id);
  usePageTint(useArtworkColor(album.data?.thumbnail));
  const motion = useAlbumMotion(album.data?.title, album.data?.artists.map((a) => a.name).join(', '));
  const sort = useSongSorts((s) => s.sorts[id] ?? 'DEFAULT');
  const setSort = useSongSorts((s) => s.setSort);
  // Shown, played and shuffled in the chosen order; each row keeps its own track number.
  const tracks = useMemo(() => sortTracks(album.data?.tracks ?? [], sort), [album.data?.tracks, sort]);
  // The filter narrows what's shown and played.
  const filter = useDetailFilter(tracks);
  const shown = filter.matches;

  if (missing) return <NeedsExtension />;
  if (album.isError) return <LoadError error={album.error} onRetry={() => void album.refetch()} />;
  if (!album.data) return <AlbumSkeleton />;

  const data = album.data;
  const artists = data.artists.map((a) => a.name).join(', ');
  // Album tracks carry no artist or artwork of their own; the queue credits the album's.
  const context = {
    source: { title: data.title, type: 'album' as const, id: data.id },
    fallbackArtwork: data.thumbnail,
    fallbackArtists: data.artists,
  };
  return (
    <>
      <DetailHeader
        info={data}
        canPlay={shown.length > 0}
        motion={motion}
        onPlay={() => playList(shown, 0, context)}
        onShuffle={() => shuffleList(shown, context)}
        extra={
          <>
            <DetailSearchButton filter={filter} />
            <AddToPlaylistsButton tracks={tracks} />
            <DetailMenu label={data.title} sort={sort} onSort={(next) => setSort(id, next)} />
          </>
        }
      />
      <AboutSection title="About the album" text={data.description} />
      <DetailSearchField filter={filter} />
      {shown.length === 0 && tracks.length > 0 && <NothingMatches query={filter.query} />}
      <TrackList
        tracks={shown}
        variant="album"
        fallbackArtwork={data.thumbnail}
        fallbackArtists={data.artists}
        source={context.source}
      />
      <footer className={detailFooter}>
        <p>{data.subtitle}</p>
        <p>{data.meta}</p>
        {artists && <p>{artists}</p>}
      </footer>
      {data.more.length > 0 && (
        // "More By" and friends sit on the alternate shelf background (report §3, section--alternate).
        <div className="bg-shelf pb-5">
          <Shelves shelves={data.more} />
        </div>
      )}
    </>
  );
}
