import { Disc3, ListEnd, ListMusic, ListPlus, ListStart, MicVocal, Pin, PinOff, Play, Share, Shuffle } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { MenuItem } from '@/components/Menu';
import { isPinned, MAX_PINNED_PLAYLISTS, usePinnedPlaylists } from '@/data/music/pinnedPlaylists';
import { albumQuery, playlistQuery, useSignedIn } from '@/data/music/queries';
import type { MusicItem, Track } from '@/data/music/types';
import { queryClient } from '@/lib/queryClient';
import { usePlayer } from '@/state/player';
import { shareLink, useActionMenu } from './actionMenu';
import { artistHref, hrefFor } from './links';
import { playList, shuffleList, type ListContext } from './play';

/**
 * An album's or playlist's songs, from the cache its page fills (or fetched
 * as its page would), with the context they play under. A playlist's first
 * page of songs: the rest load as its page scrolls.
 */
async function loadRelease(item: MusicItem): Promise<{ tracks: Track[]; context: ListContext }> {
  if (item.kind === 'album') {
    const album = await queryClient.fetchQuery(albumQuery(item.id));
    return {
      tracks: album.tracks,
      context: { source: { title: album.title, type: 'album', id: album.id }, fallbackArtwork: album.thumbnail, fallbackArtists: album.artists },
    };
  }
  const playlist = await queryClient.fetchInfiniteQuery(playlistQuery(item.id));
  const tracks = playlist.pages.flatMap((page) => page.tracks).filter((t) => !t.unavailable);
  return { tracks, context: { source: { title: playlist.pages[0]?.header?.title ?? item.title, type: 'playlist', id: item.id } } };
}

/** The release's own YouTube Music link. */
const releaseLink = (item: MusicItem) =>
  item.kind === 'album'
    ? `https://music.youtube.com/browse/${item.id}`
    : `https://music.youtube.com/playlist?list=${item.id.replace(/^VL/, '')}`;

/**
 * Pin / Unpin for a playlist (BrowseActionsMenu.kt's pin row). The phone
 * toasts when a pin past the cap is refused; with no toasts here the row
 * says so up front and can't be chosen instead.
 */
export function PinMenuItem({ id, onClose }: { id: string; onClose: () => void }) {
  const ids = usePinnedPlaylists((s) => s.ids);
  const pinned = isPinned(ids, id);
  const full = !pinned && ids.length >= MAX_PINNED_PLAYLISTS;
  return (
    <MenuItem
      icon={pinned ? <PinOff /> : <Pin />}
      disabled={full}
      description={full ? `Only ${MAX_PINNED_PLAYLISTS} playlists can be pinned` : undefined}
      onSelect={() => {
        usePinnedPlaylists.getState().toggle(id);
        onClose();
      }}
    >
      {pinned ? 'Unpin' : 'Pin'}
    </MenuItem>
  );
}

/**
 * An album's or playlist's menu from its card (BrowseActionsMenu.kt): Play,
 * Shuffle, Add to playlist, Play next, Add to queue, open it, its artist,
 * Share, and Pin for a playlist. A card has no other way to queue a release without opening it,
 * which is what this menu is for.
 */
export function BrowseMenuItems({ item }: { item: MusicItem }) {
  const signedIn = useSignedIn();
  const navigate = useNavigate();
  const [copied, setCopied] = useState(false);
  const { close, openDialog } = useActionMenu.getState();
  const artist = item.artists.find((a) => a.id);
  const href = hrefFor(item);
  // Closed at once; the songs follow when they've loaded.
  const withSongs = (act: (tracks: Track[], context: ListContext) => void) => () => {
    close();
    void loadRelease(item).then(({ tracks, context }) => tracks.length && act(tracks, context));
  };
  return (
    <>
      <MenuItem icon={<Play />} onSelect={withSongs((tracks, context) => playList(tracks, 0, context))}>
        Play
      </MenuItem>
      <MenuItem icon={<Shuffle />} onSelect={withSongs(shuffleList)}>
        Shuffle
      </MenuItem>
      {signedIn && (
        // Kept up until the songs are in, since the picker needs them.
        <MenuItem icon={<ListPlus />} onSelect={() => void loadRelease(item).then(({ tracks }) => openDialog({ kind: 'playlists', tracks }))}>
          Add to playlist
        </MenuItem>
      )}
      <MenuItem icon={<ListStart />} onSelect={withSongs((tracks, { source }) => usePlayer.getState().playNext(tracks, source))}>
        Play next
      </MenuItem>
      <MenuItem icon={<ListEnd />} onSelect={withSongs((tracks, { source }) => usePlayer.getState().addToQueue(tracks, source))}>
        Add to queue
      </MenuItem>
      {href && (
        <MenuItem icon={item.kind === 'album' ? <Disc3 /> : <ListMusic />} onSelect={() => (close(), navigate(href))}>
          {item.kind === 'album' ? 'Go to album' : 'Go to playlist'}
        </MenuItem>
      )}
      {artist && (
        <MenuItem icon={<MicVocal />} description={artist.name} onSelect={() => (close(), navigate(artistHref(artist.id)!))}>
          Go to artist
        </MenuItem>
      )}
      <MenuItem
        icon={<Share />}
        onSelect={() => void shareLink(item.title, releaseLink(item)).then((how) => (how === 'copied' ? setCopied(true) : close()))}
      >
        {copied ? 'Link copied' : 'Share'}
      </MenuItem>
      {item.kind === 'playlist' && <PinMenuItem id={item.id} onClose={close} />}
    </>
  );
}
