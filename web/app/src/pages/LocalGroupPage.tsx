import { localArtistPageId, localGroup, localGroupKind, localUseTrackTags } from '@bitchord/shared-kotlin';
import { useEffect, useMemo } from 'react';
import { Navigate, useParams } from 'react-router';
import { DetailHeader } from '@/components/music/DetailHeader';
import { DetailSearchButton, DetailSearchField, NothingMatches, useDetailFilter } from '@/components/music/DetailSearchField';
import { playList, shuffleList } from '@/components/music/play';
import { EmptyState } from '@/components/music/States';
import { TrackList } from '@/components/music/TrackList';
import { LOCAL_FOLDER_ID } from '@/data/local/ids';
import { startLocalLibrary, useLocalLibrary, useLocalView } from '@/data/local/library';
import type { HeaderInfo, Track } from '@/data/music/types';
import type { QueueSourceType } from '@/playback/queue/timeline';
import { useArtworkColor } from '@/lib/artworkColor';
import { usePageTint } from '@/lib/usePageTint';
import { PlainSkeleton } from '@/skeletons/PageSkeletons';
import { detailFooter } from './pageStyles';

const KIND = {
  album: { label: 'Album', source: 'album', variant: 'album' },
  artist: { label: 'Artist', source: 'artist', variant: 'playlist' },
  // A directory plays like a playlist (LocalGroups.kt typeOf).
  folder: { label: 'Folder', source: 'playlist', variant: 'playlist' },
} as const satisfies Record<string, { label: string; source: QueueSourceType; variant: 'album' | 'playlist' }>;

/** "12 songs • 48 min". */
function countLine(tracks: Track[]): string {
  const sec = tracks.reduce((sum, t) => sum + (t.durationSec ?? 0), 0);
  const h = Math.floor(sec / 3600);
  const m = Math.round((sec % 3600) / 60);
  const length = sec ? (h ? `${h} hr ${m} min` : `${m} min`) : '';
  return [`${tracks.length} ${tracks.length === 1 ? 'song' : 'songs'}`, length].filter(Boolean).join(' • ');
}

/**
 * One album, artist or directory of Local Music (DetailScreen.kt opening a
 * LocalGroups page): rebuilt from the index by its id alone, as the phone
 * does, so the player's "Playing from" and a row's credit can open it. The
 * release header and track list are the catalogue's own; an album numbers
 * its rows by their tags when every one is the album's (LocalGroups.kt useTrackTags).
 */
export default function LocalGroupPage() {
  const id = decodeURIComponent(useParams().id ?? '');
  const kind = localGroupKind(id) as keyof typeof KIND | null;
  const loaded = useLocalLibrary((s) => s.loaded);
  const view = useLocalView();
  useEffect(() => void startLocalLibrary(), []);
  const group = useMemo(() => localGroup(id, view.rows), [id, view.rows]);
  const tracks = useMemo(() => {
    const rows = group?.songs ?? [];
    const own = rows.map((i) => view.tracks[i]!);
    const tagged = kind === 'album' && localUseTrackTags(rows.map((i) => view.rows[i]!));
    return kind === 'album' && !tagged ? own.map((t) => ({ ...t, index: null })) : own;
  }, [group, kind, view]);
  const filter = useDetailFilter(tracks);
  const shown = filter.matches;
  const first = group?.songs[0] !== undefined ? view.files[group.songs[0]] : undefined;
  usePageTint(useArtworkColor(group?.thumbnailUrl));

  if (!kind) return <Navigate to="/library/local" replace />;
  if (!loaded) return <PlainSkeleton />;
  if (!group || !tracks.length) return <EmptyState title="No tracks here" detail="Nothing in your Local Music folder belongs here any more." />;

  const { label, source: sourceType, variant } = KIND[kind];
  // The album artist tag where there is one ("Various Artists"), else the first track's (LocalGroups.kt album);
  // linked only when it is someone's Artists page.
  const credit = kind === 'album' ? (first?.albumArtist?.trim() || group.subtitle) : '';
  const linked = view.tracks.some((t) => t.artists[0]?.name === credit);
  const info: HeaderInfo = {
    title: group.title,
    subtitle: [kind === 'folder' ? group.subtitle || label : label, kind === 'album' ? first?.year : null].filter(Boolean).join(' • '),
    meta: countLine(tracks),
    description: null,
    thumbnail: group.thumbnailUrl ?? null,
    artists: credit ? [{ name: credit, id: linked ? localArtistPageId(LOCAL_FOLDER_ID, credit) : null }] : [],
    explicit: false,
    library: null,
  };
  const context = { source: { title: group.title, type: sourceType, id } };
  return (
    <>
      <DetailHeader
        info={info}
        canPlay={shown.length > 0}
        onPlay={() => playList(shown, 0, context)}
        onShuffle={() => shuffleList(shown, context)}
        extra={<DetailSearchButton filter={filter} />}
      />
      <DetailSearchField filter={filter} />
      {shown.length === 0 && <NothingMatches query={filter.query} />}
      <TrackList tracks={shown} variant={variant} source={context.source} />
      <footer className={detailFooter}>
        <p>{info.meta}</p>
        <p>On this device</p>
      </footer>
    </>
  );
}
