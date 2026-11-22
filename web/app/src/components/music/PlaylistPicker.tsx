import { useMutation } from '@tanstack/react-query';
import { ListPlus, Search } from 'lucide-react';
import { useState } from 'react';
import { Dialog } from '@/components/Dialog';
import { useAddToPlaylist, usePlaylistOptions, useSignedIn } from '@/data/music/queries';
import type { Track } from '@/data/music/types';
import { detailButton } from './DetailHeader';
import { filterPlaylists } from './playlistFilter';

// Android's addSongsToPlaylists sends a playlist's new tracks 50 to a request.
const CHUNK = 50;

const note = 'px-1 py-3 type-callout text-secondary';
const row = 'flex cursor-pointer items-center gap-2.5 py-2 pr-2.5 pl-3 [&+&]:border-t-[0.5px] [&+&]:border-t-divider';

/**
 * Add to playlist, as Android's PlaylistPickerSheet: the account's playlists,
 * ticked rather than picked, because one track often belongs in more than one
 * list; Find a playlist narrows them. Any number of tracks: a track's menu
 * passes one, an album or playlist all of its own, the queue its selection.
 */
export function PlaylistPicker({ tracks, onClose }: { tracks: Track[]; onClose: () => void }) {
  // YouTube's list of playlists a track can go in; which track asks doesn't change it.
  const options = usePlaylistOptions(tracks[0]?.videoId ?? '', tracks.length > 0);
  const add = useAddToPlaylist();
  const [query, setQuery] = useState('');
  // By id: ticks on playlists the search hides are kept, so a search can't quietly drop one.
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
  const save = useMutation({
    mutationFn: async () => {
      const videoIds = [...new Set(tracks.map((t) => t.videoId))];
      // One after another, as on Android: YouTube is gentler with a person's pace than a burst.
      for (const playlistId of selected) {
        for (let i = 0; i < videoIds.length; i += CHUNK) {
          await add.mutateAsync({ playlistId, videoId: videoIds.slice(i, i + CHUNK) });
        }
      }
    },
    onSuccess: onClose,
  });

  const shown = filterPlaylists(options.data ?? [], query, (p) => p.title);
  const toggle = (id: string) =>
    setSelected((now) => {
      const next = new Set(now);
      if (!next.delete(id)) next.add(id);
      return next;
    });

  return (
    <Dialog
      title="Add to Playlist"
      subtitle={tracks.length === 1 ? tracks[0]!.title : `${tracks.length} songs`}
      onClose={onClose}
      footer={
        <>
          {save.isError && <span className="mr-auto type-callout text-destructive">Couldn’t add to every playlist.</span>}
          <button
            className="rounded-[14px] bg-key px-3.5 py-1.5 type-callout-emphasized text-on-key disabled:opacity-40"
            disabled={selected.size === 0 || save.isPending}
            onClick={() => save.mutate()}
          >
            {save.isPending
              ? 'Adding…'
              : selected.size === 0
                ? 'Add to Playlist'
                : `Add to ${selected.size} ${selected.size === 1 ? 'playlist' : 'playlists'}`}
          </button>
        </>
      }
    >
      <div className="relative mb-3 flex items-center">
        <Search size={15} strokeWidth={2.4} className="pointer-events-none absolute left-2.5 text-secondary" aria-hidden="true" />
        <input
          className="h-[34px] w-full rounded-[10px] border-0 bg-quinary pr-3 pl-8 type-body text-primary placeholder:text-tertiary"
          type="search"
          placeholder="Find a playlist"
          aria-label="Find a playlist"
          value={query}
          onChange={(e) => setQuery(e.currentTarget.value)}
          autoFocus
          autoComplete="off"
          spellCheck={false}
        />
      </div>
      {options.isPending ? (
        <p className={note}>Loading…</p>
      ) : options.isError ? (
        <p className={note}>Couldn’t load your playlists.</p>
      ) : !options.data.length ? (
        <p className={note}>No playlists yet.</p>
      ) : !shown.length ? (
        <p className={note}>No playlists match “{query.trim()}”</p>
      ) : (
        <ul className="overflow-hidden rounded-[10px] bg-quinary">
          {shown.map((option) => (
            <li key={option.playlistId} className={row}>
              <label className="flex min-w-0 flex-1 cursor-pointer items-center gap-2.5">
                <input
                  type="checkbox"
                  className="size-4 accent-key"
                  checked={selected.has(option.playlistId)}
                  onChange={() => toggle(option.playlistId)}
                />
                <span className="truncate type-body">{option.title}</span>
              </label>
            </li>
          ))}
        </ul>
      )}
    </Dialog>
  );
}

/**
 * Add to Playlist for a whole album or playlist, beside its header's Play and
 * Shuffle (DetailHeader's `extra`): every track listed goes to the playlists
 * ticked. Signed in only, as the account's playlists are.
 */
export function AddToPlaylistsButton({ tracks }: { tracks: Track[] }) {
  const signedIn = useSignedIn();
  const [open, setOpen] = useState(false);
  if (!signedIn) return null;
  return (
    <>
      <button className={detailButton.circle} aria-label="Add to Playlist" title="Add to Playlist" disabled={!tracks.length} onClick={() => setOpen(true)}>
        <ListPlus size={18} />
      </button>
      {open && <PlaylistPicker tracks={tracks} onClose={() => setOpen(false)} />}
    </>
  );
}
