import { useQuery } from '@tanstack/react-query';
import { Loader2 } from 'lucide-react';
import { useEffect, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { fetchCredits } from '@/data/music/credits';
import { albumQuery, useYouTubeReady } from '@/data/music/queries';
import { queryClient } from '@/lib/queryClient';
import { fetchSongStats } from '@/data/music/songInfo';
import type { Track } from '@/data/music/types';

const stop = (event: React.SyntheticEvent) => event.stopPropagation();
// AlertRule: a hairline of the text colour at 14%.
const rule = 'h-[0.5px] bg-[color-mix(in_srgb,var(--systemPrimary)_14%,transparent)]';

/** A label on the left and its value on the right; a small spinner while it loads (SongInfoRow). */
function Row({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex min-h-11 items-center gap-3 px-4 py-2.5 type-[400_15px/18px]">
      <span className="text-primary/60">{label}</span>
      <span className="flex min-w-0 flex-1 justify-end text-end">{value}</span>
    </div>
  );
}

/**
 * Song info (SongInfoDialog.kt): the phone's 270px alert, with the album,
 * release date, views, likes and comments, then who made the song. Rows
 * YouTube gives no figure for are left out once everything has loaded.
 * Closes on Done, Escape or a click on the scrim.
 *
 * Opened from a track row's menu, it is still that row's child as far as
 * React events go, portal or not, so its clicks are kept from reaching the
 * row, as Dialog's are.
 */
export function SongInfo({ track, onClose }: { track: Track; onClose: () => void }) {
  const { ready } = useYouTubeReady();
  const card = useRef<HTMLDivElement>(null);
  useEffect(() => card.current?.focus(), []);
  const stats = useQuery({
    queryKey: ['music', 'song-stats', track.videoId],
    queryFn: () => fetchSongStats(track.videoId),
    enabled: ready,
    staleTime: 60 * 60_000,
  });
  const credits = useQuery({
    queryKey: ['music', 'credits', track.videoId],
    queryFn: () => fetchCredits(track.videoId),
    enabled: ready,
    staleTime: 60 * 60_000,
  });
  // No "Released on" line: the album's year, as the phone falls back to its header's.
  const albumId = track.album?.id ?? null;
  const year = useQuery({
    queryKey: ['music', 'album-year', albumId],
    queryFn: async () => (await queryClient.fetchQuery(albumQuery(albumId!))).subtitle.match(/\b(?:19|20)\d{2}\b/)?.[0] ?? null,
    enabled: ready && !!albumId && stats.isSuccess && !stats.data.releaseDate,
    staleTime: Infinity,
  });
  const loading = stats.isPending || year.fetchStatus === 'fetching';
  const failed = stats.isError || credits.isError;
  const spinner = <Loader2 size={14} className="animate-spin text-primary/50" aria-label="Loading" />;
  const rows: [string, string | null | undefined][] = [
    ['Album', track.album?.name],
    ['Release date', stats.data?.releaseDate ?? year.data],
    ['Views', stats.data?.views],
    ['Likes', stats.data?.likes],
    ['Comments', stats.data?.comments],
  ];
  const shown = rows.filter(([label, value]) => value || (loading && label !== 'Album'));
  const artist = track.artists.map((a) => a.name).join(', ');

  return createPortal(
    <div
      className="fixed inset-0 z-(--z-modal) grid animate-fade place-items-center bg-scrim"
      onPointerDown={(e) => {
        e.stopPropagation();
        if (e.target === e.currentTarget) onClose();
      }}
      onMouseDown={stop}
      onClick={stop}
      onDoubleClick={stop}
      onContextMenu={stop}
      onKeyDown={(e) => {
        if (e.key !== 'Escape') return;
        e.stopPropagation();
        onClose();
      }}
    >
      <div
        ref={card}
        tabIndex={-1}
        className="flex max-h-[calc(100vh-48px)] w-[270px] animate-zoom flex-col overflow-hidden rounded-group bg-surface text-primary outline-none"
        role="dialog"
        aria-modal="true"
        aria-label="Song info"
      >
        <header className="flex flex-col items-center px-4 py-[19px] text-center">
          <h2 className="type-[600_17px/21px]">Song info</h2>
          <p className="mt-1 line-clamp-2 type-[400_13px/17px]">{track.title}</p>
          {artist && <p className="truncate type-[400_13px/17px] text-primary/60">{artist}</p>}
        </header>
        <div className={rule} />
        {/* The figures, then the credits, scrolling when they run long. */}
        <div className="max-h-[264px] min-h-0 overflow-y-auto">
          {shown.map(([label, value], i) => (
            <div key={label}>
              {i > 0 && <div className={`${rule} ms-4`} />}
              <Row label={label} value={value ?? spinner} />
            </div>
          ))}
          {credits.data?.map((credit, i) => (
            <section key={`${credit.role}-${i}`} aria-label={credit.role}>
              <div className={`${rule} ms-4`} />
              <div className="px-4 py-2.5">
                <h3 className="type-[400_13px/17px] text-primary/60">{credit.role}</h3>
                <p className="mt-0.5 type-[400_15px/18px]">{credit.names.join(', ')}</p>
              </div>
            </section>
          ))}
          {failed && <p className="px-4 pt-2.5 pb-2.5 type-[400_12px/15px] text-red">Couldn’t load everything YouTube has on this song.</p>}
        </div>
        <div className={rule} />
        {failed && (
          <>
            <button
              className="h-11 type-[400_17px/21px] text-key active:bg-primary/10"
              onClick={() => void Promise.all([stats.refetch(), credits.refetch()])}
            >
              Try again
            </button>
            <div className={rule} />
          </>
        )}
        <button className="h-11 type-[600_17px/21px] text-key active:bg-primary/10" onClick={onClose}>
          Done
        </button>
      </div>
    </div>,
    document.body,
  );
}
