import type { YTMusic, YTNodes } from 'youtubei.js/web';
import type {
  AlbumPage,
  AlbumRef,
  ArtistPage,
  ArtistRef,
  BrowseLink,
  BrowsePage,
  Category,
  CategoryGroup,
  HeaderInfo,
  ItemKind,
  MusicItem,
  PlaylistPage,
  SearchResults,
  Shelf,
  ShelfLayout,
  Suggestions,
  Track,
} from './types';

// YouTube.js node → view model. Every function tolerates missing fields: a
// node YouTube stops sending becomes an empty value, never a thrown error,
// and the fixture tests (mappers.test.ts) catch a mapping that goes empty.

type AnyNode = { type: string } & Record<string, unknown>;
type Thumb = { url: string; width: number; height: number };
type TextLike = { text?: string; runs?: { text: string; bold?: boolean }[] } | undefined | null;

const text = (value: TextLike | string | undefined): string =>
  typeof value === 'string' ? value : (value?.text ?? '').trim();

function largest(thumbs: Thumb[] | undefined | null): string | null {
  if (!thumbs?.length) return null;
  return [...thumbs].sort((a, b) => (b.width ?? 0) - (a.width ?? 0))[0]?.url ?? null;
}

function thumbnailOf(node: { thumbnail?: unknown }): string | null {
  const t = node.thumbnail as { contents?: Thumb[] } | Thumb[] | null | undefined;
  if (!t) return null;
  return largest(Array.isArray(t) ? t : t.contents);
}

function isWide(node: { thumbnail?: unknown }): boolean {
  const t = node.thumbnail as { contents?: Thumb[] } | Thumb[] | null | undefined;
  const first = (Array.isArray(t) ? t : t?.contents)?.[0];
  return !!first && first.width > first.height * 1.2;
}

function explicitOf(badges: unknown): boolean {
  if (!Array.isArray(badges)) return false;
  return badges.some((b: { label?: string; icon_type?: string }) =>
    /explicit/i.test(b?.label ?? '') || /EXPLICIT/.test(b?.icon_type ?? ''),
  );
}

function linkOf(endpoint: unknown): BrowseLink | null {
  const payload = (endpoint as { payload?: { browseId?: string; params?: string } } | undefined)?.payload;
  return payload?.browseId ? { browseId: payload.browseId, params: payload.params ?? null } : null;
}

function artistsOf(list: { name: string; channel_id?: string | undefined }[] | undefined): ArtistRef[] {
  return (list ?? []).filter((a) => a.name).map((a) => ({ name: a.name, id: a.channel_id ?? null }));
}

function kindOf(itemType: string | undefined, id: string | undefined): ItemKind {
  switch (itemType) {
    case 'song':
      return 'song';
    case 'video':
      return 'video';
    case 'album':
      return 'album';
    case 'playlist':
      return 'playlist';
    case 'artist':
    case 'library_artist':
      return 'artist';
    case 'non_music_track':
      return 'episode';
    case 'podcast_show':
      return 'podcast';
  }
  if (id?.startsWith('MPREb_')) return 'album';
  if (id?.startsWith('VL') || id?.startsWith('PL')) return 'playlist';
  if (id?.startsWith('UC')) return 'artist';
  return 'unknown';
}

/** The second line, as YouTube's own subtitle where it has one, else built from the parts. */
function subtitleFor(kind: ItemKind, artists: ArtistRef[], album: AlbumRef | null, given: string, extra: string | undefined) {
  if (given) return given;
  const names = artists.map((a) => a.name).join(', ');
  if (kind === 'song') return [names, album?.name].filter(Boolean).join(' • ');
  if (kind === 'video') return [names, extra].filter(Boolean).join(' • ');
  return names;
}

export function mapResponsiveItem(node: YTNodes.MusicResponsiveListItem): MusicItem | null {
  // Library artist rows have no id of their own in YouTube.js, only a link to
  // MPLAUC…, the library's view of the UC… channel; they open as the artist.
  const linked = linkOf(node.endpoint)?.browseId;
  const id = node.id ?? (linked?.startsWith('MPLAUC') ? linked.slice(4) : linked);
  if (!id) return null;
  const kind = kindOf(node.item_type, id);
  const artists = artistsOf(node.artists ?? node.authors ?? (node.author ? [node.author] : undefined));
  const album: AlbumRef | null = node.album ? { name: node.album.name, id: node.album.id ?? null } : null;
  const title = (kind === 'artist' ? node.name : undefined) ?? node.title ?? node.name ?? '';
  return {
    kind,
    id,
    title,
    subtitle: subtitleFor(kind, artists, album, text(node.subtitle), node.views),
    artists,
    album,
    thumbnail: thumbnailOf(node),
    wide: kind === 'video' || isWide(node),
    durationSec: node.duration?.seconds ?? null,
    explicit: explicitOf(node.badges),
  };
}

export function mapTwoRowItem(node: YTNodes.MusicTwoRowItem): MusicItem | null {
  const id = node.id ?? linkOf(node.endpoint)?.browseId ?? (node.endpoint?.payload as { videoId?: string })?.videoId;
  if (!id) return null;
  const kind = kindOf(node.item_type, id);
  const artists = artistsOf(node.artists ?? (node.author ? [node.author] : undefined));
  return {
    kind,
    id,
    title: text(node.title),
    subtitle: text(node.subtitle),
    artists,
    album: null,
    thumbnail: thumbnailOf(node),
    wide: isWide(node),
    durationSec: null,
    explicit: explicitOf(node.badges),
  };
}

export function mapItem(node: unknown): MusicItem | null {
  const n = node as AnyNode | null;
  if (n?.type === 'MusicResponsiveListItem') return mapResponsiveItem(n as unknown as YTNodes.MusicResponsiveListItem);
  if (n?.type === 'MusicTwoRowItem') return mapTwoRowItem(n as unknown as YTNodes.MusicTwoRowItem);
  return null;
}

export function mapTrack(node: unknown): Track | null {
  const n = node as YTNodes.MusicResponsiveListItem;
  if ((n as unknown as AnyNode)?.type !== 'MusicResponsiveListItem' || !n.id) return null;
  if (n.item_type && n.item_type !== 'song' && n.item_type !== 'video') return null;
  const index = Number.parseInt(text(n.index), 10);
  const plays = n.flex_columns
    ?.map((c) => text(c.title))
    .find((t) => /\b(plays|views)$/i.test(t));
  return {
    videoId: n.id,
    title: n.title ?? '',
    artists: artistsOf(n.artists ?? n.authors),
    album: n.album ? { name: n.album.name, id: n.album.id ?? null } : null,
    durationSec: n.duration?.seconds ?? null,
    explicit: explicitOf(n.badges),
    thumbnail: thumbnailOf(n),
    index: Number.isFinite(index) ? index : null,
    plays: plays ?? n.views ?? null,
  };
}

/** Which layout a shelf of these items gets (report §2). */
function layoutFor(items: MusicItem[], fromList: boolean): ShelfLayout {
  if (!items.length) return 'square';
  const first = items[0]!;
  if (fromList && (first.kind === 'song' || first.kind === 'video')) return 'tracks';
  if (items.every((i) => i.kind === 'artist')) return 'circle';
  if (items.filter((i) => i.wide).length > items.length / 2) return 'video';
  return 'square';
}

export function mapShelf(node: unknown): Shelf | null {
  const n = node as AnyNode;
  if (n?.type === 'MusicCarouselShelf') {
    const shelf = n as unknown as YTNodes.MusicCarouselShelf;
    const items = shelf.contents.map(mapItem).filter((i): i is MusicItem => !!i);
    if (!items.length) return null;
    const fromList = shelf.contents[0]?.type === 'MusicResponsiveListItem';
    return {
      title: text(shelf.header?.title),
      strapline: text(shelf.header?.strapline) || null,
      layout: layoutFor(items, fromList),
      items,
      seeAll: linkOf(shelf.header?.more_content?.endpoint),
    };
  }
  if (n?.type === 'MusicShelf') {
    const shelf = n as unknown as YTNodes.MusicShelf;
    const items = shelf.contents.map(mapItem).filter((i): i is MusicItem => !!i);
    if (!items.length) return null;
    return {
      title: text(shelf.title),
      strapline: null,
      layout: layoutFor(items, true),
      items,
      seeAll: linkOf(shelf.endpoint) ?? linkOf(shelf.bottom_button?.endpoint),
    };
  }
  return null;
}

export function mapShelves(sections: readonly unknown[] | undefined): Shelf[] {
  return (sections ?? []).map(mapShelf).filter((s): s is Shelf => !!s && !!s.title);
}

// ---- Pages ---------------------------------------------------------------------

export function mapHome(feed: Pick<YTMusic.HomeFeed, 'sections'>): Shelf[] {
  return mapShelves(feed.sections);
}

/** The "Add to Library" toggle among a header's buttons: a ToggleButton whose endpoint likes a playlist. */
function libraryToggle(header: YTNodes.MusicResponsiveHeader | null | undefined): HeaderInfo['library'] {
  for (const button of (header?.buttons ?? []) as unknown as AnyNode[]) {
    if (button.type !== 'ToggleButton') continue;
    const target = (endpoint: unknown) =>
      (endpoint as { payload?: { target?: { playlistId?: string } } } | undefined)?.payload?.target?.playlistId;
    const playlistId = target(button.endpoint) ?? target(button.toggled_endpoint);
    if (playlistId) return { playlistId, saved: button.is_toggled === true };
  }
  return null;
}

function headerInfo(header: YTNodes.MusicResponsiveHeader | null | undefined): HeaderInfo {
  const strapline = header?.strapline_text_one as unknown as { text?: string; runs?: { text: string; endpoint?: unknown }[] } | undefined;
  const artists: ArtistRef[] = (strapline?.runs ?? [])
    .filter((run) => run.text.trim() && !/^[•,&]$|^ (•|&) $/.test(run.text.trim()))
    .map((run) => ({ name: run.text.trim(), id: linkOf(run.endpoint)?.browseId ?? null }))
    .filter((a) => a.name !== '•' && a.name !== ',' && a.name !== '&');
  return {
    title: text(header?.title),
    subtitle: text(header?.subtitle),
    meta: text(header?.second_subtitle),
    description: text(header?.description?.description as TextLike) || null,
    thumbnail: thumbnailOf(header ?? {}),
    artists,
    explicit: explicitOf(header?.subtitle_badge),
    library: libraryToggle(header),
  };
}

export function mapAlbum(id: string, album: Pick<YTMusic.Album, 'header' | 'contents' | 'sections' | 'url'>): AlbumPage {
  const listId = album.url ? new URL(album.url).searchParams.get('list') : null;
  return {
    id,
    ...headerInfo(album.header as YTNodes.MusicResponsiveHeader | undefined),
    tracks: album.contents.map(mapTrack).filter((t): t is Track => !!t),
    playlistId: listId,
    more: mapShelves(album.sections),
  };
}

export function mapPlaylist(id: string, playlist: { header?: unknown; items: readonly unknown[] }): PlaylistPage {
  const header = playlist.header as YTNodes.MusicResponsiveHeader | undefined;
  return {
    id,
    ...headerInfo(header?.type === 'MusicResponsiveHeader' ? header : undefined),
    tracks: playlist.items.map(mapTrack).filter((t): t is Track => !!t),
  };
}

export function mapPlaylistTracks(items: readonly unknown[]): Track[] {
  return items.map(mapTrack).filter((t): t is Track => !!t);
}

export function mapArtist(id: string, artist: Pick<YTMusic.Artist, 'header' | 'sections'>): ArtistPage {
  const header = artist.header as (YTNodes.MusicImmersiveHeader | YTNodes.MusicVisualHeader) | undefined;
  const sections = artist.sections as unknown as AnyNode[];
  const topShelf = sections.find((s) => s.type === 'MusicShelf') as YTNodes.MusicShelf | undefined;
  const subscribe = (header as YTNodes.MusicImmersiveHeader | undefined)?.subscription_button as
    | { subscriber_count?: TextLike; short_subscriber_count?: TextLike }
    | null
    | undefined;
  return {
    id,
    name: text(header?.title),
    description: header && 'description' in header ? text(header.description) || null : null,
    heroImage: thumbnailOf(header ?? {}),
    audience: text(subscribe?.subscriber_count) || null,
    topSongs: (topShelf?.contents ?? []).map(mapTrack).filter((t): t is Track => !!t),
    topSongsSeeAll: linkOf(topShelf?.endpoint) ?? linkOf(topShelf?.bottom_button?.endpoint),
    shelves: mapShelves(sections.filter((s) => s.type !== 'MusicShelf')),
  };
}

/**
 * YouTube Music's search page is a top-result card followed by single-item
 * sections of every kind, interleaved. Apple groups results by kind, so they
 * are grouped here, in the order YouTube ranked them.
 */
export function mapSearch(search: { contents?: readonly unknown[] | null }): SearchResults {
  const results: SearchResults = { top: [], songs: [], artists: [], albums: [], playlists: [], videos: [] };
  const seen = new Set<string>();
  const add = (item: MusicItem | null) => {
    if (!item || seen.has(item.id)) return;
    seen.add(item.id);
    const bucket =
      item.kind === 'song' ? results.songs
      : item.kind === 'artist' ? results.artists
      : item.kind === 'album' ? results.albums
      : item.kind === 'playlist' ? results.playlists
      : item.kind === 'video' ? results.videos
      : null;
    bucket?.push(item);
  };

  for (const section of (search.contents ?? []) as AnyNode[]) {
    if (section.type === 'MusicCardShelf') {
      const card = section as unknown as YTNodes.MusicCardShelf;
      const link = linkOf(card.on_tap);
      const videoId = (card.on_tap?.payload as { videoId?: string } | undefined)?.videoId;
      const id = link?.browseId ?? videoId;
      if (id) {
        const subtitle = text(card.subtitle);
        const kind = kindOf(/^Artist/i.test(subtitle) ? 'artist' : /^Album|^Single|^EP/i.test(subtitle) ? 'album' : /^Playlist/i.test(subtitle) ? 'playlist' : videoId ? (/^Video/i.test(subtitle) ? 'video' : 'song') : undefined, id);
        const item: MusicItem = {
          kind,
          id,
          title: text(card.title),
          subtitle,
          artists: [],
          album: null,
          thumbnail: thumbnailOf(card),
          wide: false,
          durationSec: null,
          explicit: explicitOf(card.subtitle_badges),
        };
        results.top.push(item);
        add(item);
      }
      for (const inner of card.contents ?? []) add(mapItem(inner));
      continue;
    }
    for (const node of ((section.contents as unknown[] | undefined) ?? [])) add(mapItem(node));
  }

  // Apple's Top Results: the card, then the leading result of each kind.
  for (const group of [results.songs, results.albums, results.artists, results.playlists]) {
    const first = group.find((i) => !results.top.some((t) => t.id === i.id));
    if (first && results.top.length < 6) results.top.push(first);
  }
  return results;
}

export function mapSuggestions(sections: readonly unknown[]): Suggestions {
  const out: Suggestions = { queries: [], items: [] };
  for (const section of sections as AnyNode[]) {
    for (const node of ((section.contents as AnyNode[] | undefined) ?? [])) {
      if (node.type === 'SearchSuggestion') {
        const suggestion = (node as unknown as YTNodes.SearchSuggestion).suggestion as unknown as {
          text?: string;
          runs?: { text: string; bold?: boolean }[];
        };
        const runs = suggestion.runs ?? [{ text: suggestion.text ?? '', bold: false }];
        // YouTube bolds the part that was typed.
        const typed = runs.filter((r) => r.bold).map((r) => r.text).join('');
        const rest = runs.filter((r) => !r.bold).map((r) => r.text).join('');
        out.queries.push({ text: suggestion.text ?? typed + rest, typed, rest });
      } else {
        const item = mapItem(node);
        if (item) out.items.push(item);
      }
    }
  }
  return out;
}

/** ARGB integer (YouTube's stripe colour) → CSS. */
export function argbToCss(argb: number): string {
  const r = (argb >>> 16) & 0xff;
  const g = (argb >>> 8) & 0xff;
  const b = argb & 0xff;
  return `rgb(${r}, ${g}, ${b})`;
}

/**
 * Moods & genres, from the raw browse response: YouTube.js drops the stripe
 * colour each button carries, and it's what colours the tiles.
 */
export function mapCategories(raw: unknown): CategoryGroup[] {
  const groups: CategoryGroup[] = [];
  const walk = (node: unknown) => {
    if (Array.isArray(node)) return node.forEach(walk);
    if (!node || typeof node !== 'object') return;
    const grid = (node as Record<string, unknown>).gridRenderer as
      | { header?: { gridHeaderRenderer?: { title?: { runs?: { text: string }[] } } }; items?: unknown[] }
      | undefined;
    if (grid?.items) {
      const categories: Category[] = [];
      for (const entry of grid.items) {
        const button = (entry as { musicNavigationButtonRenderer?: Record<string, unknown> }).musicNavigationButtonRenderer as
          | {
              buttonText?: { runs?: { text: string }[] };
              solid?: { leftStripeColor?: number };
              clickCommand?: { browseEndpoint?: { browseId?: string; params?: string } };
            }
          | undefined;
        const browse = button?.clickCommand?.browseEndpoint;
        if (!button || !browse?.browseId) continue;
        categories.push({
          title: button.buttonText?.runs?.map((r) => r.text).join('') ?? '',
          color: argbToCss(button.solid?.leftStripeColor ?? 0xff444444),
          link: { browseId: browse.browseId, params: browse.params ?? null },
        });
      }
      if (categories.length) {
        groups.push({ title: grid.header?.gridHeaderRenderer?.title?.runs?.map((r) => r.text).join('') ?? '', categories });
      }
      return;
    }
    Object.values(node).forEach(walk);
  };
  walk(raw);
  return groups;
}

/**
 * The cover a category tile borrows: the first real artwork on the page the
 * tile opens, as on the phone (YtMusicRepository.moodGenreArtwork). The
 * moods page itself carries no artwork, only titles and stripe colours, so
 * the tile takes its cover from the category's own shelves — playlists
 * first, then a See All grid, then track rows. The shape comes with it:
 * the URL as served is small, so the caller asks the CDN for the size it
 * displays (sizedArtwork) — for square covers, not wide video stills.
 */
export function firstBrowseArtwork(page: BrowsePage): { thumbnail: string; wide: boolean } | null {
  for (const shelf of page.shelves) {
    for (const item of shelf.items) if (item.thumbnail) return { thumbnail: item.thumbnail, wide: item.wide };
  }
  for (const item of page.grid) if (item.thumbnail) return { thumbnail: item.thumbnail, wide: item.wide };
  for (const track of page.tracks) if (track.thumbnail) return { thumbnail: track.thumbnail, wide: false };
  return null;
}

/** A See All, mood or genre page: shelves, or a single grid, or a track list. */
export function mapBrowse(page: { header?: unknown; sections?: readonly unknown[] }, fallbackTitle: string): BrowsePage {
  const sections = (page.sections ?? []) as AnyNode[];
  const grid: MusicItem[] = [];
  const tracks: Track[] = [];
  for (const section of sections) {
    if (section.type === 'Grid' || section.type === 'GridRenderer') {
      for (const item of (section.items as unknown[] | undefined) ?? (section.contents as unknown[] | undefined) ?? []) {
        const mapped = mapItem(item);
        if (mapped) grid.push(mapped);
      }
    } else if (section.type === 'MusicPlaylistShelf' || section.type === 'MusicShelf') {
      for (const item of (section.contents as unknown[] | undefined) ?? []) {
        const t = mapTrack(item);
        if (t) {
          tracks.push(t);
          continue;
        }
        // Library artists and albums arrive as list rows, not tiles.
        const other = mapItem(item);
        if (other && other.kind !== 'song' && other.kind !== 'video') grid.push(other);
      }
    }
  }
  const header = page.header as { title?: TextLike } | undefined;
  return {
    title: text(header?.title) || fallbackTitle,
    shelves: mapShelves(sections),
    grid,
    tracks,
  };
}
