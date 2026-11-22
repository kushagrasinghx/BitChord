import { detailSortOrder, groupMixes } from '@bitchord/shared-kotlin';
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { getSignedInnertube } from '@/playback/youtube/innertube';
import { mapTwoRowItem } from './mappers';
import type { MusicItem, Track } from './types';

/**
 * The library sections, signed in (prompt §5.3), in Apple's sidebar order
 * (report §1: Recently Added · Artists · Albums · Songs · Made for You). Each
 * is a YouTube Music library page; YouTube has no "recently added" view of its
 * own, so that slot is the listening history, which is what the clock means
 * there anyway.
 */
export const LIBRARY_SECTIONS = {
  recent: { title: 'Recently Played', browseId: 'FEmusic_history' },
  artists: { title: 'Artists', browseId: 'FEmusic_library_corpus_track_artists' },
  albums: { title: 'Albums', browseId: 'FEmusic_liked_albums' },
  songs: { title: 'Songs', browseId: 'FEmusic_liked_videos' },
  'made-for-you': { title: 'Made for You', browseId: 'FEmusic_mixed_for_you' },
  playlists: { title: 'All Playlists', browseId: 'FEmusic_liked_playlists' },
} as const;

export type LibrarySection = keyof typeof LIBRARY_SECTIONS;

export function isLibrarySection(value: string): value is LibrarySection {
  return Object.hasOwn(LIBRARY_SECTIONS, value);
}

/** YouTube's "Liked Music" auto-playlist. */
export const LIKED_MUSIC_PLAYLIST = 'LM';

// ---- Radio (MixesScreen) ------------------------------------------------------------

/** A "Mixed for you" card and its English title, which says what kind of mix it is (Models.kt KeyedMix). */
export interface KeyedMix {
  item: MusicItem;
  englishTitle: string;
}

/** Every card on "Mixed for you", as YtMusicRepository.mixedForYou reads it: each two-row card, wherever it sits. */
async function mixedForYou(english: boolean): Promise<MusicItem[]> {
  const yt = await getSignedInnertube();
  const { YTNodes } = await import('youtubei.js/web');
  const client = yt.session.context.client;
  const language = client.hl;
  // YouTube.js copies the session's context into a request while building it,
  // before its first await, so this one request asks in English and no other does.
  if (english) client.hl = 'en';
  const pending = yt.actions.execute('/browse', { browseId: LIBRARY_SECTIONS['made-for-you'].browseId, client: 'YTMUSIC', parse: true });
  client.hl = language;
  const response = await pending;
  return (response.contents_memo?.getType(YTNodes.MusicTwoRowItem) ?? [])
    .map(mapTwoRowItem)
    .filter((item): item is MusicItem => !!item);
}

/**
 * The cards beside their English titles (YtMusicRepository.mixedForYouKeyed).
 * YouTube translates the titles that say what kind of mix a card is, so in
 * another language the page is read a second time in English, to sort by,
 * matched up by playlist id. If that read fails, a card's own title stands in.
 */
export async function fetchMixes(): Promise<KeyedMix[]> {
  const yt = await getSignedInnertube();
  const english = yt.session.context.client.hl?.startsWith('en') ? null : mixedForYou(true).catch(() => null);
  const items = await mixedForYou(false);
  const titles = new Map((await english)?.map((item) => [item.id || item.title, item.title]));
  return items.map((item) => ({ item, englishTitle: titles.get(item.id || item.title) ?? item.title }));
}

export interface MoodMixes {
  /** The mood's English name, lower-case: stable across languages. */
  key: string;
  /** The row's heading, in the language the mixes are shown in. */
  name: string;
  items: MusicItem[];
}

/** The Radio page's three kinds of mix, as MixGroups.kt sorts them. */
export interface MixSections {
  /** The named mixes everyone gets (My Supermix, Discover Mix…), as tiles. */
  stations: MusicItem[];
  /** The My Mixes made from this listener's taste. */
  yours: MusicItem[];
  /** A row per mood, its Supermix first. */
  moods: MoodMixes[];
}

/** MixGroups.kt `groupMixes`, the Android app's own sorting, run on the web's cards. */
export function groupedMixes(mixes: KeyedMix[]): MixSections {
  const groups = groupMixes(
    mixes.map((m) => m.item.id),
    mixes.map((m) => m.item.title),
    mixes.map((m) => m.englishTitle),
  );
  const pick = (positions: number[]) => positions.map((i) => mixes[i]!.item);
  return {
    stations: pick(groups.stations),
    yours: pick(groups.yours),
    moods: groups.moods.map((mood) => ({ key: mood.key, name: mood.name, items: pick(mood.items) })),
  };
}

// ---- Song sorts (DetailScreen) ------------------------------------------------------

/**
 * The orders an album or playlist page can show its songs in (SongSort.kt),
 * as the phone's sort menu lists them. Track number is left out: it sorts by
 * a local file's tags, and the web has no local files.
 */
export const SONG_SORTS = [
  { value: 'DATE_ADDED_DESC', label: 'Date Added (Newest First)' },
  { value: 'DATE_ADDED_ASC', label: 'Date Added (Oldest First)' },
  { value: 'DEFAULT', label: 'Default Order' },
  { value: 'TITLE_ASC', label: 'Alphabetical (A to Z)' },
  { value: 'TITLE_DESC', label: 'Alphabetical (Z to A)' },
] as const;

export type SongSort = (typeof SONG_SORTS)[number]['value'];

/** Each page's chosen order, by its id, kept until changed (AppSettings `detail_song_sorts`). */
export const useSongSorts = create<{ sorts: Record<string, SongSort>; setSort: (id: string, sort: SongSort) => void }>()(
  persist((set) => ({ sorts: {}, setSort: (id, sort) => set((s) => ({ sorts: { ...s.sorts, [id]: sort } })) }), {
    name: 'bitchord:song-sorts',
    version: 1,
  }),
);

/**
 * [tracks] in [sort]'s order (DetailSort.kt `sortedForDetail`). A catalogue row
 * carries no date, so its place in the running order stands in for one: a
 * playlist is added to at the foot, and reversed that puts the newest on top.
 */
export function sortTracks(tracks: Track[], sort: SongSort): Track[] {
  if (sort === 'DEFAULT') return tracks;
  return detailSortOrder(
    tracks.map((t) => t.title),
    sort,
  ).map((i) => tracks[i]!);
}
