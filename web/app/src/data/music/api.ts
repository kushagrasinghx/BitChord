import type { YTMusic, YTNodes } from 'youtubei.js/web';
import type { Candidate } from '@/playback/queue/builder';
import { getInnertube, getMusicClient, getSignedInnertube } from '@/playback/youtube/innertube';
import { isExactArtistMatch, normalizedArtistName } from '@/lib/artistCredits';
import { LIBRARY_SECTIONS, LIKED_MUSIC_PLAYLIST, type LibrarySection } from './library';
import {
  mapAlbum,
  mapArtist,
  mapBrowse,
  mapCategories,
  mapHome,
  mapPlaylist,
  mapPlaylistTracks,
  mapSearch,
  mapShelves,
  mapSuggestions,
} from './mappers';
import type {
  AlbumPage,
  MusicItem,
  ArtistPage,
  BrowseLink,
  BrowsePage,
  CategoryGroup,
  PlaylistPage,
  SearchResults,
  Shelf,
  Suggestions,
  Track,
} from './types';

// The browse pages' requests, through YouTube.js's music namespace (prompt
// §5.1). Each returns a view model; continuations are passed back as opaque
// handles, so pages never see a YouTube.js object.

export interface Paged<T> {
  data: T;
  /** Fetches the next page; null when there isn't one. */
  next: (() => Promise<Paged<T>>) | null;
}

/**
 * YouTube often offers one more page than it has: the continuation comes back
 * empty and YouTube.js throws ("Continuation did not have any content"). That
 * is the end of the list, not a failure; anything else still is one.
 */
async function orEnd<T>(nextPage: () => Promise<Paged<T>>, empty: T): Promise<Paged<T>> {
  try {
    return await nextPage();
  } catch (error) {
    if (/did not have any content|no continuation/i.test(String(error))) return { data: empty, next: null };
    throw error;
  }
}

function homePage(feed: YTMusic.HomeFeed): Paged<Shelf[]> {
  return {
    data: mapHome(feed),
    next: feed.has_continuation ? () => orEnd(async () => homePage(await feed.getContinuation()), []) : null,
  };
}

export async function fetchHome(): Promise<Paged<Shelf[]>> {
  const yt = await getMusicClient();
  return homePage(await yt.music.getHomeFeed());
}

/** The New page: YouTube Music's Explore, minus its podcast and mood carousels (mappers drop those). */
export async function fetchExplore(): Promise<Shelf[]> {
  const yt = await getMusicClient();
  return mapShelves((await yt.music.getExplore()).sections);
}

/** Moods & genres, read raw because YouTube.js drops the tiles' colours. */
export async function fetchCategories(): Promise<CategoryGroup[]> {
  const yt = await getInnertube();
  const response = await yt.actions.execute('/browse', { browseId: 'FEmusic_moods_and_genres', client: 'YTMUSIC' });
  return mapCategories(response.data);
}

export async function fetchSearch(query: string): Promise<SearchResults> {
  const yt = await getMusicClient();
  return mapSearch(await yt.music.search(query));
}

/** YouTube Music's Songs shelf for [query]: what an import or a match is chosen from. */
export async function searchSongs(query: string): Promise<Track[]> {
  const yt = await getMusicClient();
  const search = await yt.music.search(query, { type: 'song' });
  const shelf = search.songs ?? search.contents?.find((c) => c.type === 'MusicShelf');
  return mapPlaylistTracks((shelf as { contents?: readonly unknown[] } | undefined)?.contents ?? []);
}

export async function fetchSuggestions(input: string): Promise<Suggestions> {
  const yt = await getInnertube();
  return mapSuggestions(await yt.music.getSearchSuggestions(input));
}

export async function fetchAlbum(id: string): Promise<AlbumPage> {
  const yt = await getMusicClient();
  return mapAlbum(id, await yt.music.getAlbum(id));
}

/** How many top songs an artist page lists, as on the phone. */
const ARTIST_TOP_SONGS = 20;

/**
 * An artist. The landing page only lists five songs, so its linked "Top
 * songs" playlist fills the list out (YtMusicRepository.artistPage); if that
 * fails, the five stand.
 */
export async function fetchArtist(id: string): Promise<ArtistPage> {
  const yt = await getMusicClient();
  const page = mapArtist(id, await yt.music.getArtist(id));
  const playlistId = page.topSongsSeeAll?.browseId;
  if (playlistId?.startsWith('VL') && page.topSongs.length < ARTIST_TOP_SONGS) {
    try {
      const { page: playlist } = await fetchPlaylist(playlistId);
      if (playlist.tracks.length > page.topSongs.length) page.topSongs = playlist.tracks.slice(0, ARTIST_TOP_SONGS);
    } catch {
      // The landing page's songs are still the right ones, just fewer.
    }
  }
  return page;
}

function playlistTracks(page: YTMusic.Playlist): Paged<Track[]> {
  return {
    data: mapPlaylistTracks(page.items),
    next: page.has_continuation ? () => orEnd(async () => playlistTracks(await page.getContinuation()), []) : null,
  };
}

/** The header and first page of tracks; `more` fetches the rest page by page. */
export async function fetchPlaylist(id: string): Promise<{ page: PlaylistPage; more: Paged<Track[]>['next'] }> {
  const yt = await getMusicClient();
  const playlist = await yt.music.getPlaylist(id);
  return { page: mapPlaylist(id, playlist), more: playlistTracks(playlist).next };
}

/** See All, moods and genres: whatever the browse id renders as. */
export async function fetchBrowse({ browseId, params }: BrowseLink): Promise<BrowsePage> {
  const yt = await getMusicClient();
  const { YTNodes } = await import('youtubei.js/web');
  const response = await yt.actions.execute('/browse', {
    browseId,
    ...(params && { params }),
    client: 'YTMUSIC',
    parse: true,
  });
  const memo = response.contents_memo;
  const sections = [
    ...(memo?.getType(YTNodes.Grid) ?? []),
    ...(memo?.getType(YTNodes.MusicCarouselShelf) ?? []),
    ...(memo?.getType(YTNodes.MusicShelf) ?? []),
    ...(memo?.getType(YTNodes.MusicPlaylistShelf) ?? []),
  ];
  const header = memo?.getType(YTNodes.MusicHeader)?.[0] ?? (response.header as unknown);
  return mapBrowse({ header, sections }, '');
}

// ---- Signed in (prompt §5.3) ------------------------------------------------------

type Raw = Record<string, unknown>;

/** Every object stored under [key], anywhere inside a raw response. */
function collect(root: unknown, key: string, out: Raw[] = []): Raw[] {
  if (Array.isArray(root)) for (const item of root) collect(item, key, out);
  else if (root && typeof root === 'object') {
    for (const [k, v] of Object.entries(root)) {
      if (k === key && v && typeof v === 'object' && !Array.isArray(v)) out.push(v as Raw);
      else collect(v, key, out);
    }
  }
  return out;
}

function runsText(node: unknown): string {
  const n = node as { runs?: { text?: string }[]; simpleText?: string } | undefined;
  return n?.runs?.map((r) => r.text ?? '').join('') ?? n?.simpleText ?? '';
}

/** One library page, a page of it at a time: grids continue as grids, song lists as song lists. */
export async function fetchLibrary(section: LibrarySection): Promise<Paged<BrowsePage>> {
  const yt = await getSignedInnertube();
  const { YTNodes } = await import('youtubei.js/web');
  const { title, browseId } = LIBRARY_SECTIONS[section];
  const response = await yt.actions.execute('/browse', { browseId, client: 'YTMUSIC', parse: true });
  const memo = response.contents_memo;
  const grids = memo?.getType(YTNodes.Grid) ?? [];
  const lists = [...(memo?.getType(YTNodes.MusicShelf) ?? []), ...(memo?.getType(YTNodes.MusicPlaylistShelf) ?? [])];
  const carousels = memo?.getType(YTNodes.MusicCarouselShelf) ?? [];
  const page = mapBrowse({ sections: [...grids, ...lists, ...carousels] }, title);
  // History is several lists ("Today", "Yesterday"); the rest is one grid or one list.
  const token = (grids[0]?.continuation ?? (lists.length === 1 ? lists[0]?.continuation : null)) || null;
  return { data: { ...page, title }, next: token ? () => orEnd(() => libraryContinuation(token, title), emptyPage(title)) : null };
}

const emptyPage = (title: string): BrowsePage => ({ title, shelves: [], grid: [], tracks: [] });

async function libraryContinuation(token: string, title: string): Promise<Paged<BrowsePage>> {
  const yt = await getSignedInnertube();
  const response = await yt.actions.execute('/browse', { continuation: token, client: 'YTMUSIC', parse: true });
  const contents = response.continuation_contents as unknown as
    | { type: string; items?: unknown[]; contents?: unknown[]; continuation?: string | null }
    | undefined;
  const section =
    contents?.type === 'GridContinuation'
      ? { type: 'Grid', items: contents.items ?? [] }
      : { type: 'MusicShelf', contents: contents?.contents ?? [] };
  const next = contents?.continuation || null;
  return {
    data: mapBrowse({ sections: [section] }, title),
    next: next ? () => orEnd(() => libraryContinuation(next, title), emptyPage(title)) : null,
  };
}

/** The user's playlists for the sidebar, in YouTube's order (most recent activity first). */
export async function fetchLibraryPlaylists(): Promise<MusicItem[]> {
  const { data } = await fetchLibrary('playlists');
  return data.grid.filter((item) => item.kind === 'playlist');
}

export type LikeStatus = 'LIKE' | 'DISLIKE' | 'INDIFFERENT';

/** The thumbs state YouTube Music shows on its player for this track. */
export async function fetchLikeStatus(videoId: string): Promise<LikeStatus> {
  const yt = await getSignedInnertube();
  const response = await yt.actions.execute('/next', { videoId, client: 'YTMUSIC' });
  const button = collect((response.data as Raw).playerOverlays, 'likeButtonRenderer')[0];
  const status = button?.likeStatus;
  return status === 'LIKE' || status === 'DISLIKE' ? status : 'INDIFFERENT';
}

/** Likes a track (it joins Liked Music) or takes the like back. */
export async function setTrackLiked(videoId: string, liked: boolean): Promise<void> {
  const yt = await getSignedInnertube();
  await yt.actions.execute(liked ? '/like/like' : '/like/removelike', { target: { videoId }, client: 'YTMUSIC' });
}

/** Dislikes a track or takes the dislike back. */
export async function setTrackDisliked(videoId: string, disliked: boolean): Promise<void> {
  const yt = await getSignedInnertube();
  await yt.actions.execute(disliked ? '/like/dislike' : '/like/removelike', { target: { videoId }, client: 'YTMUSIC' });
}

/** Adds an album or playlist to the library, or removes it: YouTube saves both by liking the list. */
export async function setListSaved(playlistId: string, saved: boolean): Promise<void> {
  const yt = await getSignedInnertube();
  await yt.actions.execute(saved ? '/like/like' : '/like/removelike', { target: { playlistId }, client: 'YTMUSIC' });
}

export interface PlaylistOption {
  playlistId: string;
  title: string;
}

/**
 * The playlists a track can be added to, as YouTube Music's "Save to
 * playlist" lists them. YouTube doesn't say which already hold the track
 * (adding is deduplicated instead), and its "Liked Music" entry is really a
 * like, which Favorite already does, so it's left out.
 */
export async function fetchPlaylistOptions(videoId: string): Promise<PlaylistOption[]> {
  const yt = await getSignedInnertube();
  const response = await yt.actions.execute('/playlist/get_add_to_playlist', { videoIds: [videoId], client: 'YTMUSIC' });
  return collect(response.data, 'playlistAddToOptionRenderer')
    .map((option) => ({
      playlistId: typeof option.playlistId === 'string' ? option.playlistId : '',
      title: runsText(option.title),
    }))
    .filter((option) => option.playlistId && option.playlistId !== LIKED_MUSIC_PLAYLIST && option.title);
}

export async function addToPlaylist(playlistId: string, videoId: string): Promise<void> {
  const yt = await getSignedInnertube();
  await yt.actions.execute('/browse/edit_playlist', {
    playlistId: playlistId.replace(/^VL/, ''),
    actions: [{ action: 'ACTION_ADD_VIDEO', addedVideoId: videoId, dedupeOption: 'DEDUPE_OPTION_SKIP' }],
    client: 'YTMUSIC',
  });
}

/**
 * A new private playlist holding [videoIds], as YtMusicRepository.createPlaylist
 * makes one (InnerTube `playlist/create`). Returns its id, without the VL.
 */
export async function createPlaylist(title: string, videoIds: string[], description = ''): Promise<string> {
  const yt = await getSignedInnertube();
  const response = await yt.actions.execute('/playlist/create', {
    title,
    description,
    privacyStatus: 'PRIVATE',
    videoIds,
    client: 'YTMUSIC',
  });
  const id = (response.data as { playlistId?: string } | undefined)?.playlistId;
  if (!id) throw new Error('YouTube Music did not make the playlist');
  return id;
}

// ---- AutoPlay (phase 6) -------------------------------------------------------------

/**
 * A music video rather than the catalogue song: YouTube marks each watch
 * endpoint with its type, ATV for the song and OMV/UGC for videos. (A radio
 * seeded on a video id is all videos; one seeded on a song is mostly songs.)
 */
function isMusicVideo(item: YTNodes.PlaylistPanelVideo): boolean {
  const type = (item.endpoint?.payload as { watchEndpointMusicSupportedConfigs?: { watchEndpointMusicConfig?: { musicVideoType?: string } } } | undefined)
    ?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType;
  return type !== undefined ? type !== 'MUSIC_VIDEO_TYPE_ATV' : !item.album;
}

/**
 * The radio YouTube Music would play after [videoId]: its "RDAMVM" mix, as
 * Innertube.next asks for it on the phone. Signed in it's the user's own
 * station. Music videos are flagged so the station builder can prefer the
 * catalogue cut (QueueBuilder).
 */
export async function fetchRadio(videoId: string): Promise<Candidate[]> {
  const yt = await getMusicClient();
  const panel = await yt.music.getUpNext(videoId, true);
  return (panel.contents as unknown as Raw[]).map(panelCandidate).filter((c): c is Candidate => c !== null);
}

/**
 * A watch-queue row's cuts. A wrapper pairs the two cuts of one track (song
 * and music video), with either as its primary.
 */
function panelCuts(raw: Raw): YTNodes.PlaylistPanelVideo[] {
  const cuts = (raw.type === 'PlaylistPanelVideoWrapper'
    ? [raw.primary, ...((raw.counterpart as unknown[] | undefined) ?? [])]
    : [raw]) as (YTNodes.PlaylistPanelVideo | null | undefined)[];
  return cuts.filter((c): c is YTNodes.PlaylistPanelVideo => c?.type === 'PlaylistPanelVideo');
}

/** One watch-queue row as a track: the catalogue song (the cut with an album). */
function panelCandidate(raw: Raw): Candidate | null {
  const panelVideos = panelCuts(raw);
  const item = panelVideos.find((c) => !isMusicVideo(c)) ?? panelVideos[0];
  if (!item?.video_id) return null;
  const thumbs = [...(item.thumbnail ?? [])].sort((a, b) => b.width - a.width);
  return {
    track: {
      videoId: item.video_id,
      title: item.title?.toString() ?? '',
      artists: (item.artists ?? []).map((a) => ({ name: a.name, id: a.channel_id ?? null })),
      album: item.album ? { name: item.album.name, id: item.album.id ?? null } : null,
      durationSec: item.duration?.seconds || null,
      explicit: (item.badges ?? []).some((b) => /explicit/i.test(JSON.stringify(b))),
      thumbnail: thumbs[0]?.url ?? null,
      index: null,
      plays: null,
    },
    isVideo: isMusicVideo(item),
  };
}

// ---- Player links ---------------------------------------------------------------

/**
 * The album and artist pages a track links out to (YtMusicRepository.trackLinks).
 * Queue rows from home cards often lack them; the track's own watch-queue
 * entry always names both.
 */
export async function fetchTrackLinks(videoId: string): Promise<Track | null> {
  const yt = await getMusicClient();
  const panel = await yt.music.getUpNext(videoId, false);
  // The row holding this video, by either of its cuts; its catalogue cut names the album.
  const row = (panel.contents as unknown as Raw[]).find((raw) => panelCuts(raw).some((cut) => cut.video_id === videoId));
  return (row && panelCandidate(row)?.track) ?? null;
}

/**
 * The page of the artist called exactly [name] (YtMusicRepository.findArtistPageId):
 * a name is not an identity, so only a result that answers to it exactly is
 * opened, never the nearest one.
 */
export async function findArtistPageId(name: string): Promise<string | null> {
  if (!normalizedArtistName(name)) return null;
  const results = await fetchSearch(name);
  return [...results.top, ...results.artists].find((item) => item.kind === 'artist' && isExactArtistMatch(item.title, name))?.id ?? null;
}
