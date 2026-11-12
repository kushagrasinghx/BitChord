/**
 * What the browse pages render, independent of YouTube.js's node classes.
 * Mappers (mappers.ts) build these from yt.music responses; pages never touch
 * a YouTube.js node directly.
 */

export type ItemKind = 'song' | 'video' | 'album' | 'playlist' | 'spotify-playlist' | 'artist' | 'episode' | 'podcast' | 'unknown';

export interface ArtistRef {
  name: string;
  /** Channel id (UC…), when YouTube links the name. */
  id: string | null;
}

export interface AlbumRef {
  name: string;
  /** Album browse id (MPREb_…). */
  id: string | null;
}

/** Anything a shelf, grid or search result can hold. */
export interface MusicItem {
  kind: ItemKind;
  /** videoId for songs and videos; browse id for albums (MPREb_), playlists (VL…) and artists (UC…). */
  id: string;
  title: string;
  /** The second line exactly as YouTube words it ("Album • Radiohead", "Single • 2021"). */
  subtitle: string;
  artists: ArtistRef[];
  album: AlbumRef | null;
  /** The largest thumbnail; size it for display with sizedArtwork(). */
  thumbnail: string | null;
  /** 16:9 artwork (videos), as opposed to square. */
  wide: boolean;
  durationSec: number | null;
  explicit: boolean;
}

/** How a shelf lays out (report §2 shelf types). */
export type ShelfLayout = 'tracks' | 'square' | 'video' | 'circle';

export interface BrowseLink {
  browseId: string;
  params: string | null;
}

export interface Shelf {
  title: string;
  /** Small text above the title, when YouTube gives one ("Start radio based on a song"). */
  strapline: string | null;
  layout: ShelfLayout;
  items: MusicItem[];
  /** Where "See All" goes, when there's more. */
  seeAll: BrowseLink | null;
}

/** A song in a track list (album, playlist, artist top songs). */
export interface Track {
  videoId: string;
  title: string;
  artists: ArtistRef[];
  album: AlbumRef | null;
  durationSec: number | null;
  explicit: boolean;
  thumbnail: string | null;
  /** The album track number, when the list is an album. */
  index: number | null;
  /** "19M plays", where YouTube shows it. */
  plays: string | null;
  /**
   * A row from another catalogue (a Spotify playlist) that has no YouTube
   * Music version yet: still being looked for, or not found. Shown, never played.
   */
  unavailable?: 'matching' | 'missing';
}

export interface HeaderInfo {
  title: string;
  /** "Album • 1997", "Playlist • 2020". */
  subtitle: string;
  /** "23 songs • 1 hour, 32 minutes". */
  meta: string;
  description: string | null;
  thumbnail: string | null;
  /** Album artist or playlist author. */
  artists: ArtistRef[];
  explicit: boolean;
  /**
   * The header's "Add to Library" toggle, when YouTube offers one: the list
   * it saves and whether it already is. Only meaningful signed in.
   */
  library: { playlistId: string; saved: boolean } | null;
}

export interface AlbumPage extends HeaderInfo {
  id: string;
  tracks: Track[];
  /** For playing the whole album later (the OLAK5uy… list). */
  playlistId: string | null;
  more: Shelf[];
}

export interface PlaylistPage extends HeaderInfo {
  id: string;
  tracks: Track[];
}

export interface ArtistPage {
  id: string;
  name: string;
  description: string | null;
  /** The hero image, wide. */
  heroImage: string | null;
  /** "206M monthly audience" or the subscriber count. */
  audience: string | null;
  topSongs: Track[];
  topSongsSeeAll: BrowseLink | null;
  shelves: Shelf[];
}

export interface HomePage {
  shelves: Shelf[];
}

/** A Browse Categories tile (Moods & genres). */
export interface Category {
  title: string;
  /** CSS colour from YouTube's stripe colour. */
  color: string;
  link: BrowseLink;
}

export interface CategoryGroup {
  title: string;
  categories: Category[];
}

export interface SearchResults {
  /** YouTube's top result card, plus the first of each kind, Apple-style. */
  top: MusicItem[];
  songs: MusicItem[];
  artists: MusicItem[];
  albums: MusicItem[];
  playlists: MusicItem[];
  videos: MusicItem[];
}

export interface Suggestions {
  /** Text completions; `typed` is the part matching the input (shown primary), the rest secondary. */
  queries: { text: string; typed: string; rest: string }[];
  items: MusicItem[];
}

/** A generic browse page (See All, a mood or genre). */
export interface BrowsePage {
  title: string;
  shelves: Shelf[];
  /** A flat grid, when the page is one. */
  grid: MusicItem[];
  tracks: Track[];
}
