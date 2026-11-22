import { searchFilters, type SearchFilterOption } from '@bitchord/shared-kotlin';
import { ListPlus, Play, Shuffle, X } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { useSearchParams } from 'react-router';
import { PrefetchLink } from '@/components/PrefetchLink';
import { SkeletonBlock, SkeletonLine } from '@/components/Skeleton';
import { Artwork } from '@/components/music/Artwork';
import { hrefFor } from '@/components/music/links';
import { playList, playTrack, shuffleList, toTrack } from '@/components/music/play';
import { PlaylistPicker } from '@/components/music/PlaylistPicker';
import { SearchField } from '@/components/music/SearchField';
import { isPlayable, ItemRow, rowButton, SongActions, subtitleOf } from '@/components/music/SearchRow';
import { LoadError, LoadMoreError, NeedsExtension } from '@/components/music/States';
import { itemContextMenu } from '@/components/music/actionMenu';
import { TrackMenu } from '@/components/music/TrackMenu';
import { useLoadMore } from '@/components/music/useLoadMore';
import { SegmentedControl } from '@/components/ui/SegmentedControl';
import { fetchPlaylist } from '@/data/music/api';
import { albumQuery, artistQuery, useSignedIn, useYouTubeReady } from '@/data/music/queries';
import { useSearchHistory } from '@/data/music/searchHistory';
import { mergeSearchPages, useSearchPages } from '@/data/music/searchPages';
import type { MusicItem, SearchResults, Track } from '@/data/music/types';
import { queryClient } from '@/lib/queryClient';
import type { QueueSource } from '@/playback/queue/timeline';

/** The Android app's own filter list (Models.kt SearchFilter): YouTube's categories, then Library, signed in only. */
const FILTERS = searchFilters();
const LIBRARY = FILTERS.find((f) => f.id === 'LIBRARY')!;
/** The chips under the field: YouTube's categories. Library is a source of its own, picked before typing. */
const CATEGORIES = FILTERS.filter((f) => f !== LIBRARY);

const SOURCES = [
  { value: 'youtube', label: 'YouTube' },
  { value: 'library', label: 'Library' },
] as const;

const searchSource = (query: string): QueueSource => ({ title: query, type: 'search', id: query });

/** The phone's MessageState (Common.kt): one grey sentence, centred, with room around it. */
function Message({ children }: { children: ReactNode }) {
  return <p className="px-[calc(var(--body-gutter)+6px)] py-12 text-center type-body text-on-surface-variant">{children}</p>;
}

/**
 * What was found by searching lately (SearchScreen.kt recentSearches), in
 * place of the results while the field is empty: a 22px heading with Clear,
 * then the songs, albums and artists picked before, each with a ✕. Opening
 * one doesn't move it up the list; only a new pick does.
 */
function RecentSearches() {
  const { items, remove, clear } = useSearchHistory();
  if (!items.length) return <Message>Search millions of songs on YouTube Music.</Message>;
  return (
    <section aria-labelledby="recent-searches" className="pb-10">
      <div className="flex items-center px-(--body-gutter) py-2.5">
        <h2 id="recent-searches" className="flex-1 type-title-1-emphasized">
          Recent searches
        </h2>
        <button type="button" className="rounded-pill px-2.5 py-1 type-title-3-emphasized text-key hover:bg-quinary" onClick={clear}>
          Clear
        </button>
      </div>
      <ul>
        {items.map((item) => (
          <ItemRow
            key={item.id}
            item={item}
            href={hrefFor(item)}
            onPick={() => isPlayable(item) && playTrack(item, undefined, searchSource(item.title))}
            trailing={
              <button type="button" className={rowButton} aria-label={`Remove “${item.title}” from recent searches`} onClick={() => remove(item.id)}>
                <X />
              </button>
            }
          />
        ))}
      </ul>
    </section>
  );
}

/** An album's, playlist's or artist's songs, for the top result's Play and Shuffle. */
async function tracksOf(item: MusicItem): Promise<Track[]> {
  if (item.kind === 'album') return (await queryClient.fetchQuery(albumQuery(item.id))).tracks;
  if (item.kind === 'artist') return (await queryClient.fetchQuery(artistQuery(item.id))).topSongs;
  return (await fetchPlaylist(item.id)).page.tracks;
}

// Material's OutlinedButton as the phone draws it: a 40px pill, a hairline, a glyph and a 14px label.
const outlined =
  'inline-flex h-10 items-center gap-1.5 rounded-pill border border-outline ps-4 pe-5 type-callout-medium text-primary hover:bg-quinary disabled:opacity-40 [&_svg]:size-[18px]';

/**
 * The All page's promoted hit (SearchScreen.kt TopResultCard): "Top result",
 * a 72px r10 sleeve beside its 20px title, then Play and, for a song, Playlist
 * (add it to one), or for an album, playlist or artist, Shuffle.
 */
function TopResultCard({ item, query }: { item: MusicItem; query: string }) {
  const signedIn = useSignedIn();
  const record = useSearchHistory((s) => s.record);
  const [picking, setPicking] = useState(false);
  const [busy, setBusy] = useState(false);
  const playable = isPlayable(item);
  const href = hrefFor(item);

  const playAll = async (shuffle: boolean) => {
    record(item);
    if (playable) return playTrack(item, undefined, searchSource(query));
    setBusy(true);
    try {
      const tracks = await tracksOf(item);
      const context = { source: { title: item.title, type: item.kind === 'artist' ? 'artist' : item.kind === 'album' ? 'album' : 'playlist', id: item.id } as QueueSource, fallbackArtwork: item.kind === 'album' ? item.thumbnail : null };
      if (shuffle) shuffleList(tracks, context);
      else playList(tracks, 0, context);
    } finally {
      setBusy(false);
    }
  };

  const heading = (
    <>
      <span className="w-[72px] flex-none">
        <Artwork src={item.thumbnail} displayWidth={72} radius={item.kind === 'artist' ? '50%' : 'var(--radius-sleeve)'} />
      </span>
      <span className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="line-clamp-2 type-title-large text-primary">{item.title}</span>
        <span className="truncate type-callout text-on-surface-variant">{subtitleOf(item)}</span>
      </span>
    </>
  );
  const open = 'flex min-w-0 flex-1 items-center gap-3.5 rounded-row text-left';
  return (
    <section aria-labelledby="top-result" className="px-(--body-gutter) pt-[18px] pb-2">
      <h2 id="top-result" className="type-callout-medium text-primary">
        Top result
      </h2>
      <div className="mt-2.5 flex items-center gap-2" onContextMenu={itemContextMenu(item)}>
        {href ? (
          <PrefetchLink to={href} className={open} onClick={() => record(item)}>
            {heading}
          </PrefetchLink>
        ) : (
          <button type="button" className={open} onClick={() => void playAll(false)}>
            {heading}
          </button>
        )}
        {playable && <TrackMenu track={toTrack(item)} className={rowButton} />}
      </div>
      <div className="mt-3 flex gap-2.5">
        <button type="button" className={outlined} disabled={busy} onClick={() => void playAll(false)}>
          <Play />
          Play
        </button>
        {!playable ? (
          <button type="button" className={outlined} disabled={busy} onClick={() => void playAll(true)}>
            <Shuffle />
            Shuffle
          </button>
        ) : (
          signedIn && (
            <button type="button" className={outlined} onClick={() => (record(item), setPicking(true))}>
              <ListPlus />
              Playlist
            </button>
          )
        )}
      </div>
      {picking && <PlaylistPicker tracks={[toTrack(item)]} onClose={() => setPicking(false)} />}
    </section>
  );
}

/** One run of results: a 14px heading (titleSmall) when the page mixes kinds, then the rows. */
function Section({ title, items, query }: { title: string | null; items: MusicItem[]; query: string }) {
  const record = useSearchHistory((s) => s.record);
  // Search hits are alternatives, not a running order: a song plays on its own, then AutoPlay (MainActivity.kt playRadio).
  const pick = (item: MusicItem) => {
    record(item);
    if (isPlayable(item)) playTrack(item, undefined, searchSource(query));
  };
  return (
    <section aria-label={title ?? undefined}>
      {title && <h2 className="px-(--body-gutter) pt-4 pb-1.5 type-callout-medium text-primary">{title}</h2>}
      <ul>
        {items.map((item, i) => (
          <ItemRow key={`${item.id}-${i}`} divided item={item} href={hrefFor(item)} onPick={() => pick(item)} trailing={isPlayable(item) && <SongActions item={item} />} />
        ))}
      </ul>
    </section>
  );
}

/** The phone's songListSkeleton: a few rows of grey art and two lines. */
function RowsSkeleton({ count = 8, round = false }: { count?: number; round?: boolean }) {
  return (
    <div aria-busy="true" className="pt-3">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="flex items-center gap-4 px-(--body-gutter) py-1.5" aria-hidden="true">
          <SkeletonBlock width={52} height={52} radius={round ? '50%' : 'var(--radius-row)'} />
          <div className="flex flex-1 flex-col gap-1">
            <SkeletonLine lineHeight={19} fontSize={16} width="45%" />
            <SkeletonLine lineHeight={17} fontSize={14} width="28%" />
          </div>
        </div>
      ))}
    </div>
  );
}

/** The mixed page's kinds, in the phone's order (SearchScreen.kt searchSections); videos are the web's "More". */
function sectionsOf(results: SearchResults): { title: string; items: MusicItem[] }[] {
  return [
    { title: 'Songs', items: results.songs },
    { title: 'Artists', items: results.artists },
    { title: 'Albums', items: results.albums },
    { title: 'Playlists', items: results.playlists },
    { title: 'Videos', items: results.videos },
  ].filter((s) => s.items.length > 0);
}

function Results({ query, filter }: { query: string; filter: SearchFilterOption }) {
  const search = useSearchPages(query, filter.params);
  const loadMore = useLoadMore(
    search.hasNextPage && !search.isFetchingNextPage && !search.isFetchNextPageError ? () => void search.fetchNextPage() : null,
  );
  if (search.isError && !search.data) return <LoadError error={search.error} onRetry={() => void search.refetch()} />;
  if (!search.data) return <RowsSkeleton round={filter.id === 'ARTISTS'} />;

  const results = mergeSearchPages(search.data.pages.map((p) => p.data));
  const all = [...results.songs, ...results.videos, ...results.artists, ...results.albums, ...results.playlists];
  if (!all.length) return <Message>Nothing matches “{query}”.</Message>;

  // A filtered page is one kind of thing (or the library's mix): one list that reads on to the end.
  if (filter.params) {
    return (
      <div className="pb-10">
        <Section title={null} items={all} query={query} />
        <div ref={loadMore} aria-hidden="true" />
        {search.isFetchingNextPage && <RowsSkeleton count={3} round={filter.id === 'ARTISTS'} />}
        {search.isFetchNextPageError && <LoadMoreError onRetry={() => void search.fetchNextPage()} />}
      </div>
    );
  }

  const top = results.top[0];
  const [first, ...rest] = sectionsOf(results);
  // On a wide window the top result sits beside the songs, as a tablet's
  // spare width allows; narrower, it heads the page above them.
  const songsBeside = top && first?.title === 'Songs';
  return (
    <div className="pb-10">
      {songsBeside ? (
        <div className="min-[1100px]:grid min-[1100px]:grid-cols-[minmax(0,2fr)_minmax(0,3fr)] min-[1100px]:items-start">
          <TopResultCard item={top} query={query} />
          <Section {...first} query={query} />
        </div>
      ) : (
        <>
          {top && <TopResultCard item={top} query={query} />}
          {first && <Section {...first} query={query} />}
        </>
      )}
      {rest.map((section) => (
        <Section key={section.title} {...section} query={query} />
      ))}
    </div>
  );
}

/**
 * Search (sharedUi/.../ui/screens/SearchScreen.kt): the field fixed at the
 * head of the page; under it, while it is empty, the source to search
 * (YouTube, or the account's library once signed in) and the recent
 * searches; while typing, suggestions; once searched, YouTube's categories as
 * a segmented control over the results. The query and filter live in the URL.
 */
export default function SearchPage() {
  const { missing } = useYouTubeReady();
  const signedIn = useSignedIn();
  const [params, setParams] = useSearchParams();
  const query = params.get('q') ?? '';
  const library = signedIn && params.get('filter') === LIBRARY.id;
  const filter = library ? LIBRARY : (CATEGORIES.find((f) => f.id === params.get('filter')) ?? CATEGORIES[0]!);
  const search = (q: string, f = filter) =>
    // A new filter, a live library search or a cleared field replaces the entry rather than piling up history.
    setParams({ ...(q && { q }), ...(f.params && { filter: f.id }) }, { replace: f !== filter || library || !q });

  return (
    <SearchField
      initial={query}
      onSearch={search}
      live={library}
      placeholder={library ? 'Songs, Artists and Albums' : 'Artists, Songs, Lyrics and More'}
      controls={({ empty, suggesting }) =>
        missing ? null : empty ? (
          // The source switch, up only while nothing is typed: once something is, the page is about the results.
          signedIn && (
            <SegmentedControl
              label="Search in"
              className="w-full max-w-[420px]"
              options={SOURCES}
              value={library ? 'library' : 'youtube'}
              onChange={(source) => search('', source === 'library' ? LIBRARY : CATEGORIES[0]!)}
            />
          )
        ) : (
          query &&
          !library &&
          !suggesting && (
            <SegmentedControl
              label="Search filter"
              className="w-full max-w-[640px]"
              options={CATEGORIES.map((f) => ({ value: f, label: f.label }))}
              value={filter}
              onChange={(f) => search(query, f)}
            />
          )
        )
      }
    >
      {missing ? (
        <NeedsExtension what="and search YouTube Music" />
      ) : query ? (
        <Results query={query} filter={filter} />
      ) : library ? (
        <Message>Search the songs, albums and playlists in your library.</Message>
      ) : (
        <RecentSearches />
      )}
    </SearchField>
  );
}
