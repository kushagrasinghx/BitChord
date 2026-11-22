import { useQuery } from '@tanstack/react-query';
import { ArrowUpDown, GripVertical, Pencil, Trash2, X } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { MenuHeader, MenuItem } from '@/components/Menu';
import { Artwork } from '@/components/music/Artwork';
import { DetailHeader, detailButton } from '@/components/music/DetailHeader';
import { DetailMenu } from '@/components/music/DetailMenu';
import { playList, shuffleList } from '@/components/music/play';
import { LoadError, LoadMoreError, NeedsExtension } from '@/components/music/States';
import { TrackList } from '@/components/music/TrackList';
import { sortTracks, useSongSorts } from '@/data/music/library';
import {
  fetchPlaylistEntries,
  isOwnedPlaylist,
  useDeletePlaylist,
  useRenamePlaylist,
  useReorderPlaylist,
  type PlaylistEntry,
} from '@/data/music/playlistEdit';
import { usePlaylist, useSignedIn, useYouTubeReady } from '@/data/music/queries';
import { useArtworkColor } from '@/lib/artworkColor';
import { usePageTint } from '@/lib/usePageTint';
import { PlaylistSkeleton } from '@/skeletons/PageSkeletons';
import { detailFooter } from './pageStyles';

const errorText = (error: unknown) => (error instanceof Error ? error.message : String(error));

/** Renaming, inside the playlist's menu: the name, and nothing sent until Save. */
function RenameForm({ id, title, onDone }: { id: string; title: string; onDone: () => void }) {
  const [name, setName] = useState(title);
  const rename = useRenamePlaylist(id);
  const trimmed = name.trim();
  return (
    <form
      className="flex w-[260px] flex-col gap-2 px-1 pb-1"
      onSubmit={(event) => {
        event.preventDefault();
        if (trimmed && trimmed !== title) rename.mutate(trimmed, { onSuccess: onDone });
      }}
    >
      <MenuHeader>Rename Playlist</MenuHeader>
      <input
        className="mx-1 rounded-[6px] bg-quaternary px-2 py-1 type-body text-primary outline-none focus-visible:ring-2 focus-visible:ring-key"
        aria-label="Playlist name"
        value={name}
        maxLength={150}
        autoFocus
        onFocus={(event) => event.currentTarget.select()}
        onChange={(event) => setName(event.target.value)}
      />
      {rename.isError && (
        <p className="px-1 type-callout text-destructive" role="alert">
          Couldn’t rename it: {errorText(rename.error)}
        </p>
      )}
      <div className="flex justify-end gap-2 px-1">
        <button type="button" className="rounded-[6px] px-2.5 py-1 type-body text-primary hover:bg-quinary" onClick={onDone}>
          Cancel
        </button>
        <button
          type="submit"
          className="rounded-[6px] bg-key-bg px-2.5 py-1 type-body-emphasized text-white disabled:opacity-40"
          disabled={!trimmed || trimmed === title || rename.isPending}
        >
          {rename.isPending ? 'Saving…' : 'Save'}
        </button>
      </div>
    </form>
  );
}

/** The account's own playlist: reorder, rename, and delete, which asks a second time as the phone does. */
function OwnerActions({ id, title, onClose, onReorder }: { id: string; title: string; onClose: () => void; onReorder: () => void }) {
  const [view, setView] = useState<'main' | 'rename' | 'confirm-delete'>('main');
  const remove = useDeletePlaylist(id);
  const navigate = useNavigate();
  if (view === 'rename') return <RenameForm id={id} title={title} onDone={onClose} />;
  return (
    <>
      <MenuItem icon={<ArrowUpDown size={15} />} onSelect={() => (onClose(), onReorder())}>
        Reorder Songs
      </MenuItem>
      <MenuItem icon={<Pencil size={15} />} onSelect={() => setView('rename')}>
        Rename Playlist
      </MenuItem>
      <MenuItem
        icon={<Trash2 size={15} />}
        destructive
        disabled={remove.isPending}
        onSelect={() => {
          if (view !== 'confirm-delete') return setView('confirm-delete');
          remove.mutate(undefined, {
            onSuccess: () => {
              onClose();
              navigate('/library/playlists', { replace: true });
            },
          });
        }}
      >
        {remove.isError
          ? 'Couldn’t delete it. Try again?'
          : view === 'confirm-delete'
            ? `Delete “${title}”? Click again to confirm.`
            : 'Delete Playlist'}
      </MenuItem>
    </>
  );
}

const reorderRow =
  'group/entry grid h-[54px] cursor-grab grid-cols-[24px_40px_minmax(0,1fr)] items-center gap-3 rounded-[12px] px-2 outline-none hover:bg-tracklist-hover focus-visible:ring-2 focus-visible:ring-key active:cursor-grabbing data-dragging:bg-quinary data-dragging:opacity-60';

/**
 * Rearranging the account's playlist (ReorderPlaylistPage), drawn in place of
 * its track list: every entry as YouTube has it now, moved by dragging or by
 * Alt+Up and Alt+Down, and nothing sent until Save. Held here and saved as one
 * edit, because a drag passes through many positions on the way to the one
 * it was meant for, and each would otherwise be a request.
 */
function ReorderSongs({ id, onClose }: { id: string; onClose: () => void }) {
  const entries = useQuery({
    queryKey: ['music', 'playlist-entries', id],
    queryFn: () => fetchPlaylistEntries(id),
    staleTime: 0,
    gcTime: 0,
    refetchOnWindowFocus: false,
  });
  const reorder = useReorderPlaylist(id);
  const [moved, setMoved] = useState<PlaylistEntry[] | null>(null);
  const [dragging, setDragging] = useState<string | null>(null);
  const keyboardMoved = useRef<string | null>(null);
  const [announcement, setAnnouncement] = useState('');
  const list = useRef<HTMLOListElement>(null);
  const original = entries.data ?? [];
  const order = moved ?? original;
  const changed = moved !== null && moved.some((entry, i) => entry.setVideoId !== original[i]?.setVideoId);

  const move = (from: number, to: number) => {
    if (from === to || to < 0 || to >= order.length) return;
    const next = [...order];
    next.splice(to, 0, ...next.splice(from, 1));
    setMoved(next);
    setAnnouncement(`${next[to]!.title}, position ${to + 1} of ${next.length}`);
  };

  // A moved row is put back in the DOM, which can take the focus with it; the keyboard keeps its place.
  useEffect(() => {
    const id = keyboardMoved.current;
    keyboardMoved.current = null;
    if (id) list.current?.querySelector<HTMLElement>(`[data-entry="${CSS.escape(id)}"]`)?.focus();
  }, [order]);

  return (
    <section
      className="mx-(--body-gutter)"
      aria-label="Reorder songs"
      onKeyDown={(event) => event.key === 'Escape' && !reorder.isPending && onClose()}
    >
      <div className="flex flex-wrap items-center gap-3.5 border-b-[0.5px] border-b-divider pb-3">
        <button className={detailButton.circle} aria-label="Cancel" title="Cancel" onClick={onClose} disabled={reorder.isPending}>
          <X size={18} strokeWidth={2.2} />
        </button>
        <div className="flex min-w-0 flex-1 flex-col">
          <h2 className="type-title-2-emphasized">Reorder Songs</h2>
          <p className="type-callout text-secondary">Drag a song, or press Alt+Up or Alt+Down, to move it.</p>
        </div>
        {reorder.isError && (
          <p className="type-callout text-destructive" role="alert">
            Couldn’t save the new order
          </p>
        )}
        <button
          className={detailButton.pill}
          disabled={!changed || reorder.isPending}
          onClick={() => reorder.mutate({ original, reordered: order }, { onSuccess: onClose })}
        >
          {reorder.isPending ? 'Saving…' : 'Save'}
        </button>
      </div>
      {entries.isPending && <div className="flex justify-center py-10 type-callout text-secondary">Loading songs…</div>}
      {entries.isError && <LoadError error={entries.error} onRetry={() => void entries.refetch()} />}
      <ol ref={list} className="mt-2 flex flex-col" aria-label="Songs">
        {order.map((entry, i) => (
          <li
            key={entry.setVideoId}
            className={reorderRow}
            data-entry={entry.setVideoId}
            data-dragging={dragging === entry.setVideoId || undefined}
            tabIndex={0}
            aria-label={`${entry.title}, ${entry.byline}, position ${i + 1} of ${order.length}`}
            draggable={!reorder.isPending}
            onKeyDown={(event) => {
              if (!event.altKey || (event.key !== 'ArrowUp' && event.key !== 'ArrowDown') || reorder.isPending) return;
              event.preventDefault();
              const to = event.key === 'ArrowUp' ? i - 1 : i + 1;
              if (to < 0 || to >= order.length) return;
              keyboardMoved.current = entry.setVideoId;
              move(i, to);
            }}
            onDragStart={(event) => {
              event.dataTransfer.effectAllowed = 'move';
              event.dataTransfer.setData('text/plain', entry.title);
              setDragging(entry.setVideoId);
            }}
            // The held row trades places with each row it passes over, so the list shows where it will land.
            onDragOver={(event) => {
              if (!dragging) return;
              event.preventDefault();
              move(
                order.findIndex((e) => e.setVideoId === dragging),
                i,
              );
            }}
            onDrop={(event) => {
              event.preventDefault();
              setDragging(null);
            }}
            onDragEnd={() => setDragging(null)}
          >
            <GripVertical size={16} className="text-tertiary group-hover/entry:text-secondary" aria-hidden="true" />
            <Artwork src={entry.thumbnail} displayWidth={40} radius="var(--radius-artwork-small)" />
            <span className="flex min-w-0 flex-col">
              <span className="truncate type-body text-primary">{entry.title}</span>
              <span className="truncate type-callout text-secondary">{entry.byline}</span>
            </span>
          </li>
        ))}
      </ol>
      <p className="sr-only" aria-live="polite">
        {announcement}
      </p>
    </section>
  );
}

/** A playlist (report §14): header, then the artwork track list, loading on as you scroll. */
export default function PlaylistPage() {
  const id = decodeURIComponent(useParams().id ?? '');
  const { missing } = useYouTubeReady();
  const signedIn = useSignedIn();
  const playlist = usePlaylist(id);
  const header = playlist.data?.pages[0]?.header ?? null;
  usePageTint(useArtworkColor(header?.thumbnail));
  const sort = useSongSorts((s) => s.sorts[id] ?? 'DEFAULT');
  const setSort = useSongSorts((s) => s.setSort);
  const [reordering, setReordering] = useState(false);

  const { hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage } = playlist;
  const onNearEnd = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage && !isFetchNextPageError) void fetchNextPage();
  }, [hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage]);
  // A sorted list is only right once it holds every song, so a sort loads the rest straight away.
  useEffect(() => {
    if (sort !== 'DEFAULT') onNearEnd();
  }, [sort, onNearEnd]);

  // Shown, played and shuffled in the chosen order.
  const pages = playlist.data?.pages;
  const tracks = useMemo(() => sortTracks(pages?.flatMap((page) => page.tracks) ?? [], sort), [pages, sort]);

  if (missing) return <NeedsExtension />;
  if (playlist.isError && !playlist.data) return <LoadError error={playlist.error} onRetry={() => void playlist.refetch()} />;
  if (!playlist.data || !header) return <PlaylistSkeleton />;

  const context = { source: { title: header.title, type: 'playlist' as const, id } };
  const owned = isOwnedPlaylist(id, header, signedIn);
  return (
    <>
      <DetailHeader
        info={header}
        canPlay={tracks.length > 0}
        onPlay={() => playList(tracks, 0, context)}
        onShuffle={() => shuffleList(tracks, context)}
        extra={
          <DetailMenu label={header.title} sort={sort} onSort={(next) => setSort(id, next)}>
            {(close) => owned && <OwnerActions id={id} title={header.title} onClose={close} onReorder={() => setReordering(true)} />}
          </DetailMenu>
        }
      />
      {reordering ? (
        <ReorderSongs id={id} onClose={() => setReordering(false)} />
      ) : (
        <>
          <TrackList tracks={tracks} variant="playlist" onNearEnd={onNearEnd} source={context.source} />
          {isFetchingNextPage && <div className="flex justify-center py-5 type-callout text-secondary">Loading more songs…</div>}
          {playlist.isFetchNextPageError && <LoadMoreError onRetry={() => void fetchNextPage()} />}
        </>
      )}
      <footer className={detailFooter}>
        <p>{header.meta}</p>
      </footer>
    </>
  );
}
