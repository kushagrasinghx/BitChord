import { Pencil } from 'lucide-react';
import { useCallback, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { DetailHeader, detailButton } from '@/components/music/DetailHeader';
import { EditPlaylistDialog } from '@/components/music/EditPlaylistDialog';
import { playList, shuffleList } from '@/components/music/play';
import { LoadError, LoadMoreError, NeedsExtension } from '@/components/music/States';
import { TrackList } from '@/components/music/TrackList';
import { useMovePlaylistItem, usePlaylist, useRemoveFromPlaylist, useSignedIn, useYouTubeReady } from '@/data/music/queries';
import { useArtworkColor } from '@/lib/artworkColor';
import { usePageTint } from '@/lib/usePageTint';
import { PlaylistSkeleton } from '@/skeletons/PageSkeletons';
import { detailFooter } from './pageStyles';

/** A playlist (report §14): header, then the artwork track list, loading on as you scroll. */
export default function PlaylistPage() {
  const id = decodeURIComponent(useParams().id ?? '');
  const { missing } = useYouTubeReady();
  const playlist = usePlaylist(id);
  const header = playlist.data?.pages[0]?.header ?? null;
  const signedIn = useSignedIn();
  const navigate = useNavigate();
  const [editing, setEditing] = useState(false);
  const removeTrack = useRemoveFromPlaylist();
  const moveTrack = useMovePlaylistItem();
  usePageTint(useArtworkColor(header?.thumbnail));

  const { hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage } = playlist;
  const onNearEnd = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage && !isFetchNextPageError) void fetchNextPage();
  }, [hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage]);

  if (missing) return <NeedsExtension />;
  if (playlist.isError && !playlist.data) return <LoadError error={playlist.error} onRetry={() => void playlist.refetch()} />;
  if (!playlist.data || !header) return <PlaylistSkeleton />;

  const tracks = playlist.data.pages.flatMap((page) => page.tracks);
  const context = { source: { title: header.title, type: 'playlist' as const, id } };
  // Only the user's own playlists: YouTube marks them with its editable header.
  const editable = signedIn && header.editable;
  return (
    <>
      <DetailHeader
        info={header}
        canPlay={tracks.length > 0}
        onPlay={() => playList(tracks, 0, context)}
        onShuffle={() => shuffleList(tracks, context)}
        extra={
          editable && (
            <button className={detailButton.circle} aria-label="Edit playlist" title="Edit playlist" onClick={() => setEditing(true)}>
              <Pencil size={16} strokeWidth={2.2} />
            </button>
          )
        }
      />
      <TrackList
        tracks={tracks}
        variant="playlist"
        onNearEnd={onNearEnd}
        source={context.source}
        onRemove={editable ? (track) => removeTrack.mutate({ playlistId: id, videoId: track.videoId }) : undefined}
        onMove={editable ? (from, to) => moveTrack.mutate({ playlistId: id, videoId: tracks[from]!.videoId, from, to }) : undefined}
      />
      {editing && (
        <EditPlaylistDialog
          playlistId={id}
          info={header}
          onClose={() => setEditing(false)}
          onDeleted={() => {
            setEditing(false);
            navigate('/library/playlists');
          }}
        />
      )}
      {isFetchingNextPage && <div className="flex justify-center py-5 type-callout text-secondary">Loading more songs…</div>}
      {playlist.isFetchNextPageError && <LoadMoreError onRetry={() => void fetchNextPage()} />}
      <footer className={detailFooter}>
        <p>{header.meta}</p>
      </footer>
    </>
  );
}
