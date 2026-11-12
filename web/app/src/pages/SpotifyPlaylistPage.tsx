import { useMutation } from '@tanstack/react-query';
import { ListPlus } from 'lucide-react';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { DetailHeader, detailButton } from '@/components/music/DetailHeader';
import { playList, shuffleList } from '@/components/music/play';
import { EmptyState, LoadError, NeedsExtension } from '@/components/music/States';
import { TrackList } from '@/components/music/TrackList';
import { createPlaylist } from '@/data/music/api';
import { useSignedIn } from '@/data/music/queries';
import type { HeaderInfo } from '@/data/music/types';
import { useSpotifyPlaylistInfo, useSpotifyRows } from '@/data/spotify/queries';
import { useExtension } from '@/extension/useExtension';
import { useSpotifySignedIn } from '@/extension/useSpotifyAuth';
import { useArtworkColor } from '@/lib/artworkColor';
import { usePageTint } from '@/lib/usePageTint';
import { PlaylistSkeleton } from '@/skeletons/PageSkeletons';
import { detailFooter } from './pageStyles';

/**
 * A Spotify playlist as an ordinary playlist page (MainViewModel.openSpotifyPage):
 * Spotify's rows go up at once, each swapped for its YouTube Music song as the
 * match arrives. Import keeps the result as a YouTube Music playlist
 * (SpotifyImportAlert), with the songs that found nothing left out and named.
 */
export default function SpotifyPlaylistPage() {
  const id = decodeURIComponent(useParams().id ?? '');
  const extension = useExtension();
  const signedIn = useSpotifySignedIn();
  const info = useSpotifyPlaylistInfo(id);
  const rows = useSpotifyRows(id);
  usePageTint(useArtworkColor(info.data?.imageUrl));

  if (extension.state === 'missing') return <NeedsExtension what="your Spotify playlists" />;
  if (!signedIn) return <EmptyState title="Sign in to Spotify" detail="Open Spotify in the sidebar to connect your account." />;
  if (rows.status === 'error' && !rows.rows.length) return <LoadError error={rows.error} onRetry={rows.retry} />;
  if (!info.data || (rows.status === 'loading' && !rows.rows.length)) return <PlaylistSkeleton />;

  const playable = rows.rows.filter((t) => !t.unavailable);
  const missing = rows.rows.filter((t) => t.unavailable === 'missing');
  const total = Math.max(rows.total, info.data.total);
  const meta = [
    `${total} ${total === 1 ? 'song' : 'songs'}`,
    rows.matching > 0 ? `finding on YouTube Music, ${rows.rows.length - rows.matching} of ${rows.rows.length}` : null,
    rows.matching === 0 && missing.length > 0 ? `${missing.length} not on YouTube Music` : null,
  ]
    .filter(Boolean)
    .join(' • ');
  const headerInfo: HeaderInfo = {
    title: info.data.name,
    subtitle: 'Spotify Playlist',
    meta,
    description: null,
    thumbnail: info.data.imageUrl,
    artists: info.data.owner ? [{ name: info.data.owner, id: null }] : [],
    explicit: false,
    library: null,
  };
  const context = { source: { title: info.data.name, type: 'playlist' as const, id: `spotify:${id}` } };

  return (
    <>
      <DetailHeader
        info={headerInfo}
        canPlay={playable.length > 0}
        onPlay={() => playList(playable, 0, context)}
        onShuffle={() => shuffleList(playable, context)}
        extra={<ImportButton title={info.data.name} videoIds={playable.map((t) => t.videoId)} ready={rows.matching === 0} />}
      />
      <TrackList tracks={rows.rows} variant="playlist" source={context.source} />
      <footer className={detailFooter}>
        <p>From Spotify. Songs are matched to YouTube Music by title, artist and length.</p>
      </footer>
    </>
  );
}

/** Keeps the matched songs as a private YouTube Music playlist; asks once before writing to the account. */
function ImportButton({ title, videoIds, ready }: { title: string; videoIds: string[]; ready: boolean }) {
  const ytSignedIn = useSignedIn();
  const navigate = useNavigate();
  const [confirming, setConfirming] = useState(false);
  const create = useMutation({
    mutationFn: () => createPlaylist(title, videoIds, 'Imported from Spotify with BitChord'),
    onSuccess: (playlistId) => navigate(`/playlist/${encodeURIComponent(`VL${playlistId}`)}`),
  });
  if (!ytSignedIn) return null;
  const label = !ready
    ? 'Import to YouTube Music (matching…)'
    : create.isPending
      ? 'Importing…'
      : create.isError
        ? 'Import failed. Try again'
        : confirming
          ? `Make a private playlist of ${videoIds.length} songs?`
          : 'Import to YouTube Music';
  return (
    <button
      className={confirming ? detailButton.pill : detailButton.circle}
      aria-label={label}
      title={label}
      disabled={!ready || !videoIds.length || create.isPending}
      onClick={() => (confirming ? (setConfirming(false), create.mutate()) : setConfirming(true))}
      onBlur={() => setConfirming(false)}
    >
      <ListPlus size={18} strokeWidth={2.2} />
      {confirming && <span>Make Playlist</span>}
    </button>
  );
}
