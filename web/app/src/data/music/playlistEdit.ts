import { playlistMoves } from '@bitchord/shared-kotlin';
import { useMutation, type InfiniteData } from '@tanstack/react-query';
import { queryClient } from '@/lib/queryClient';
import { getSignedInnertube } from '@/playback/youtube/innertube';
import { collect, runsText } from './api';
import type { HeaderInfo, PlaylistPage, Track } from './types';

// Rearranging, renaming and deleting the account's own playlists, with the
// requests Innertube.kt makes for them (movePlaylistItems, renamePlaylist,
// deletePlaylist).

type Raw = Record<string, unknown>;

/** InnertubeParser.NOT_EDITABLE: Liked Music, episodes, YouTube's own mixes, and an album's list. */
const NOT_EDITABLE = ['LM', 'SE', 'RD', 'OLAK', 'MPRE'];

/**
 * Whether the playlist page at [id] is one this account made, and so can be
 * rearranged, renamed and deleted (InnertubeParser.parsePlaylistOwned). Only
 * someone else's playlist offers Add to Library: the owner's header has no
 * such toggle. If YouTube disagrees, it refuses the edit and the page says so.
 */
export function isOwnedPlaylist(id: string, header: HeaderInfo, signedIn: boolean): boolean {
  const raw = id.replace(/^VL/, '');
  return signedIn && header.library === null && !NOT_EDITABLE.some((prefix) => raw.startsWith(prefix));
}

/** One of a playlist's entries as YouTube holds it now, for the reorder list. */
export interface PlaylistEntry {
  /** The entry's id within this playlist (the same song added twice is two entries): what a move names. */
  setVideoId: string;
  videoId: string;
  title: string;
  /** The row's second column as YouTube words it: the artists. */
  byline: string;
  thumbnail: string | null;
}

const obj = (value: unknown): Raw | undefined => (value && typeof value === 'object' && !Array.isArray(value) ? (value as Raw) : undefined);

/** InnertubeParser.continuationToken: a shelf's next page, in either shape YouTube sends it. */
function continuationToken(scope: unknown): string | null {
  const endpoint = obj(collect(scope, 'continuationItemRenderer')[0]?.continuationEndpoint);
  const commands = (obj(endpoint?.commandExecutorCommand)?.commands as unknown[] | undefined) ?? [];
  const command = obj(endpoint?.continuationCommand) ?? commands.map((c) => obj(obj(c)?.continuationCommand)).find(Boolean);
  const token = command?.token ?? collect(scope, 'nextContinuationData')[0]?.continuation;
  return typeof token === 'string' && token ? token : null;
}

function entryOf(row: Raw): PlaylistEntry {
  const data = obj(row.playlistItemData);
  const setVideoId = data?.playlistSetVideoId;
  // A move is made in these ids; an entry without one can't be put anywhere.
  if (typeof setVideoId !== 'string' || !setVideoId) throw new Error('A playlist entry came back without an id');
  const column = (i: number) => runsText(obj(obj((row.flexColumns as unknown[] | undefined)?.[i])?.musicResponsiveListItemFlexColumnRenderer)?.text);
  const thumbnails = obj(obj(obj(row.thumbnail)?.musicThumbnailRenderer)?.thumbnail)?.thumbnails as { url?: string }[] | undefined;
  return {
    setVideoId,
    videoId: typeof data?.videoId === 'string' ? data.videoId : '',
    title: column(0),
    byline: column(1),
    thumbnail: thumbnails?.at(-1)?.url ?? null,
  };
}

/**
 * One page of a playlist's own entries and the token for the next
 * (InnertubeParser.parsePlaylistShelf), from a raw `browse` answer: the first
 * page, or a continuation in either of its shapes. Rows from the Suggestions
 * shelf, and the page's own continuation (its related shelves), aren't the
 * playlist's and are left alone. Null when the answer holds no playlist.
 */
export function parseEntriesPage(root: unknown): { entries: PlaylistEntry[]; next: string | null } | null {
  const r = obj(root) ?? {};
  const appended = (r.onResponseReceivedActions as unknown[] | undefined)?.map((a) => obj(obj(a)?.appendContinuationItemsAction)).find(Boolean);
  const scope = obj(r.continuationContents) ?? appended ?? obj(obj(obj(r.contents)?.twoColumnBrowseResultsRenderer)?.secondaryContents);
  if (!scope) return null;
  const playlist = obj(scope.musicPlaylistShelfContinuation) ?? (scope === appended ? appended : collect(scope, 'musicPlaylistShelfRenderer')[0]);
  if (!playlist) return null;
  return { entries: collect(playlist, 'musicResponsiveListItemRenderer').map(entryOf), next: continuationToken(playlist) };
}

/** YtMusicRepository.MAX_PAGES: past this a playlist can't be listed whole, and a partial list can't be reordered safely. */
const MAX_PAGES = 10;

/**
 * Every entry of the playlist in YouTube's current order
 * (YtMusicRepository.playlistEntries). Fetched fresh rather than read off the
 * page, because moves are made relative to neighbours, and a list that stops
 * short of the end would send its last row "to the end" past rows it never saw.
 */
export async function fetchPlaylistEntries(id: string): Promise<PlaylistEntry[]> {
  const yt = await getSignedInnertube();
  const browseId = id.startsWith('VL') ? id : `VL${id}`;
  const entries = new Map<string, PlaylistEntry>();
  let response = await yt.actions.execute('/browse', { browseId, client: 'YTMUSIC' });
  for (let page = 1; ; page++) {
    const parsed = parseEntriesPage(response.data);
    if (!parsed) break;
    for (const entry of parsed.entries) if (!entries.has(entry.setVideoId)) entries.set(entry.setVideoId, entry);
    if (!parsed.next) break;
    if (page >= MAX_PAGES) throw new Error('This playlist is too long to reorder');
    response = await yt.actions.execute('/browse', { continuation: parsed.next, client: 'YTMUSIC' });
  }
  return [...entries.values()];
}

/**
 * Innertube.editPlaylist: one or more edits, applied together. YouTube refuses
 * with a status in the body rather than an HTTP error (a playlist the account
 * only saved is the usual reason), so the body is checked too.
 */
async function editPlaylist(id: string, actions: Raw[]): Promise<void> {
  const yt = await getSignedInnertube();
  const response = await yt.actions.execute('/browse/edit_playlist', {
    playlistId: id.replace(/^VL/, ''),
    actions,
    client: 'YTMUSIC',
  });
  const status = (response.data as { status?: string } | undefined)?.status;
  if (status && status !== 'STATUS_SUCCEEDED') throw new Error(`YouTube Music refused the edit (${status})`);
}

/** YtMusicRepository.MOVE_BATCH: a large rearrangement goes as several requests, not one oversized one. */
const MOVE_BATCH = 50;

/**
 * The edits that rearrange [current] into [target], both entries' ids, as
 * Innertube.movePlaylistItems sends them: the fewest moves (PlaylistOrder.kt),
 * each before its new successor or, with none, to the end, in batches.
 */
export function moveBatches(current: string[], target: string[]): Raw[][] {
  const actions = playlistMoves(current, target).map((move) => ({
    action: 'ACTION_MOVE_VIDEO_BEFORE',
    setVideoId: move.setVideoId,
    ...(move.before != null && { movedSetVideoIdSuccessor: move.before }),
  }));
  const batches: Raw[][] = [];
  for (let i = 0; i < actions.length; i += MOVE_BATCH) batches.push(actions.slice(i, i + MOVE_BATCH));
  return batches;
}

export async function reorderPlaylist(id: string, current: string[], target: string[]): Promise<void> {
  for (const batch of moveBatches(current, target)) await editPlaylist(id, batch);
}

export async function renamePlaylist(id: string, title: string): Promise<void> {
  await editPlaylist(id, [{ action: 'ACTION_SET_PLAYLIST_NAME', playlistName: title }]);
}

export async function deletePlaylist(id: string): Promise<void> {
  const yt = await getSignedInnertube();
  await yt.actions.execute('/playlist/delete', { playlistId: id.replace(/^VL/, ''), client: 'YTMUSIC' });
}

// ---- The open page, kept in step -----------------------------------------------------

/** The playlist query's cached pages (queries.ts playlistQuery). */
type PlaylistData = InfiniteData<{ header: PlaylistPage | null; tracks: Track[]; next: unknown }>;
const playlistKey = (id: string) => ['music', 'playlist', id];

/**
 * The page's loaded rows in [order], each entry matched to a row of its song
 * in turn (the same song twice is two rows). Null when the page doesn't hold
 * exactly those entries, as when it hasn't loaded them all yet.
 */
export function reorderedTracks(tracks: Track[], order: PlaylistEntry[]): Track[] | null {
  if (tracks.length !== order.length) return null;
  const rows = new Map<string, Track[]>();
  for (const track of tracks) rows.set(track.videoId, [...(rows.get(track.videoId) ?? []), track]);
  const out = order.map((entry) => rows.get(entry.videoId)?.shift());
  return out.every((t) => t !== undefined) ? out : null;
}

/**
 * Saves a new running order (MainViewModel.reorderPlaylist), and puts the
 * open page into it straight away rather than after a re-fetch, which the
 * feed may answer with the old order for a while.
 */
export function useReorderPlaylist(id: string) {
  return useMutation({
    mutationFn: ({ original, reordered }: { original: PlaylistEntry[]; reordered: PlaylistEntry[] }) =>
      reorderPlaylist(
        id,
        original.map((e) => e.setVideoId),
        reordered.map((e) => e.setVideoId),
      ),
    onSuccess: (_data, { reordered }) => {
      const data = queryClient.getQueryData<PlaylistData>(playlistKey(id));
      const tracks = data && reorderedTracks(data.pages.flatMap((chunk) => chunk.tracks), reordered);
      if (!data || !tracks) return void queryClient.invalidateQueries({ queryKey: playlistKey(id) });
      // Same page sizes, so each page's handle on the next stays where it was.
      let start = 0;
      const pages = data.pages.map((chunk) => ({ ...chunk, tracks: tracks.slice(start, (start += chunk.tracks.length)) }));
      queryClient.setQueryData<PlaylistData>(playlistKey(id), { ...data, pages });
    },
  });
}

/** Renames the playlist, and says so on its page and in the sidebar (MainViewModel.renamePlaylist). */
export function useRenamePlaylist(id: string) {
  return useMutation({
    mutationFn: (title: string) => renamePlaylist(id, title),
    onSuccess: (_data, title) => {
      queryClient.setQueryData<PlaylistData>(playlistKey(id), (data) =>
        data && { ...data, pages: data.pages.map((chunk) => (chunk.header ? { ...chunk, header: { ...chunk.header, title } } : chunk)) },
      );
      void queryClient.invalidateQueries({ queryKey: ['music', 'library'] });
    },
  });
}

/** Deletes the playlist; its page has nothing left to show, and the library no longer lists it. */
export function useDeletePlaylist(id: string) {
  return useMutation({
    mutationFn: () => deletePlaylist(id),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: playlistKey(id) });
      void queryClient.invalidateQueries({ queryKey: ['music', 'library'] });
    },
  });
}
