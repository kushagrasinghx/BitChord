import { searchFilters, type SearchFilterOption } from '@bitchord/shared-kotlin';
import { useSearchParams } from 'react-router';
import { PrefetchLink } from '@/components/PrefetchLink';
import { Artwork } from '@/components/music/Artwork';
import { CategoryTiles } from '@/components/music/CategoryTiles';
import { hrefFor } from '@/components/music/links';
import { CircleLockup, SquareLockup, VideoLockup } from '@/components/music/Lockups';
import { playTrack, toTrack } from '@/components/music/play';
import { SearchField } from '@/components/music/SearchField';
import { Shelves } from '@/components/music/ShelfView';
import { TrackList } from '@/components/music/TrackList';
import { TrackMenu } from '@/components/music/TrackMenu';
import { EmptyState, LoadError, NeedsExtension } from '@/components/music/States';
import { useCategories, useSearch, useSignedIn, useYouTubeReady } from '@/data/music/queries';
import type { MusicItem, SearchResults, Shelf } from '@/data/music/types';
import { SkeletonBlock, SkeletonLine } from '@/components/Skeleton';
import { ShelfSkeleton } from '@/skeletons/ShelfSkeleton';
import { seeAllGrid, seeAllTracks } from './pageStyles';

/** The filter chips, the Android app's own list (Models.kt SearchFilter): All first, Library last and signed in only. */
const FILTERS = searchFilters();

const KIND_LABEL: Record<MusicItem['kind'], string> = {
  song: 'Song',
  video: 'Video',
  album: 'Album',
  playlist: 'Playlist',
  'spotify-playlist': 'Spotify Playlist',
  artist: 'Artist',
  episode: 'Episode',
  podcast: 'Podcast',
  unknown: '',
};

/** Apple's result shelves, in its order, from results grouped by kind. */
function shelvesFor(results: SearchResults): Shelf[] {
  const shelf = (title: string, items: MusicItem[], layout: Shelf['layout']): Shelf | null =>
    items.length ? { title, strapline: null, layout, items, seeAll: null } : null;
  return [
    shelf('Artists', results.artists, 'circle'),
    shelf('Albums', results.albums, 'square'),
    shelf('Songs', results.songs, 'tracks'),
    shelf('Playlists', results.playlists, 'square'),
    shelf('Videos', results.videos, 'video'),
  ].filter((s): s is Shelf => !!s);
}

const sectionTitle = 'mx-(--body-gutter) mt-3 type-title-2-emphasized';
// Top Results: 3 columns, gap 20px; 118px cards with 90px artwork.
const topGrid =
  'mx-(--body-gutter) mt-3 mb-8 grid grid-cols-3 gap-5 max-lg:grid-cols-2 max-[607px]:grid-cols-[minmax(0,1fr)] max-[607px]:gap-1';
// The card is a relative box and the play/link layer fills it: a playable
// item's track menu is a sibling over the card's bottom-right corner, because a
// button can't be nested inside the card's own link or button.
const topCard = 'group/top relative rounded-[8px] bg-quinary hover:bg-quaternary';
const top =
  'grid min-h-[118px] w-full grid-cols-[90px_minmax(0,1fr)] items-center gap-x-3.5 px-3 py-3.5 text-left';
const topMenu =
  'absolute right-2 bottom-2 z-3 grid size-7 place-items-center rounded-full text-key opacity-0 transition-opacity duration-100 ease-in group-hover/top:opacity-100 focus-visible:opacity-100 hover:bg-quinary data-open:opacity-100';
const topText = 'flex min-w-0 flex-col gap-0.5';

function TopResult({ item }: { item: MusicItem }) {
  const href = hrefFor(item);
  const playable = item.kind === 'song' || item.kind === 'video';
  const body = (
    <>
      <div className="w-[90px]">
        <Artwork src={item.thumbnail} displayWidth={90} radius={item.kind === 'artist' ? '50%' : 'var(--radius-artwork-small)'} />
      </div>
      <div className={playable ? `${topText} pr-9` : topText}>
        <span className="line-clamp-2 type-title-3">{item.title}</span>
        <span className="truncate type-callout text-secondary">
          {[KIND_LABEL[item.kind], item.kind === 'artist' ? null : item.artists.map((a) => a.name).join(', ')].filter(Boolean).join(' · ')}
        </span>
      </div>
    </>
  );
  return (
    <div className={topCard}>
      {href ? (
        <PrefetchLink to={href} className={top}>
          {body}
        </PrefetchLink>
      ) : (
        <button className={top} onClick={() => playTrack(item)}>
          {body}
        </button>
      )}
      {playable && <TrackMenu track={toTrack(item)} className={topMenu} />}
    </div>
  );
}

function ResultsSkeleton() {
  return (
    <div aria-busy="true">
      <div className={sectionTitle}>
        <SkeletonLine lineHeight={22} fontSize={17} width={130} />
      </div>
      <div className={topGrid} aria-hidden="true">
        {Array.from({ length: 6 }, (_, i) => (
          <div key={i} className={`${topCard} ${top}`}>
            <SkeletonBlock width={90} height={90} radius="var(--radius-artwork-small)" />
            <div className={topText}>
              <SkeletonLine lineHeight={20} fontSize={15} width="70%" />
              <SkeletonLine lineHeight={15} fontSize={12} width="40%" />
            </div>
          </div>
        ))}
      </div>
      <ShelfSkeleton kind="circle" />
      <ShelfSkeleton kind="square" />
    </div>
  );
}

/**
 * A filtered search is one kind of thing, or the library's mix, so it is a
 * list rather than shelves, as on the phone: songs and videos as a track
 * list, the rest as a See All grid.
 */
function FilteredResults({ results, query }: { results: SearchResults; query: string }) {
  const tracks = [...results.songs, ...results.videos].map((item) => toTrack(item));
  const items = [...results.artists, ...results.albums, ...results.playlists];
  return (
    <>
      {items.length > 0 && (
        <ul className={seeAllGrid}>
          {items.map((item, i) => (
            <li key={`${item.id}-${i}`}>
              {item.kind === 'artist' ? <CircleLockup item={item} /> : item.wide ? <VideoLockup item={item} /> : <SquareLockup item={item} />}
            </li>
          ))}
        </ul>
      )}
      {tracks.length > 0 && (
        <div className={seeAllTracks}>
          <TrackList tracks={tracks} variant="playlist" source={{ title: query, type: 'search', id: query }} />
        </div>
      )}
    </>
  );
}

function Results({ query, filter }: { query: string; filter: SearchFilterOption }) {
  const search = useSearch(query, filter.params);
  if (search.isError) return <LoadError error={search.error} onRetry={() => void search.refetch()} />;
  if (!search.data) return <ResultsSkeleton />;
  const results = search.data;
  const shelves = shelvesFor(results);
  if (!results.top.length && !shelves.length) {
    return <EmptyState title="No Results" detail={`Try a new search for something other than “${query}”.`} />;
  }
  if (filter.params) return <FilteredResults results={results} query={query} />;
  return (
    <>
      {results.top.length > 0 && (
        <section aria-label="Top Results">
          <h2 className={sectionTitle}>Top Results</h2>
          <div className={topGrid}>
            {results.top.map((item) => (
              <TopResult key={item.id} item={item} />
            ))}
          </div>
        </section>
      )}
      <Shelves shelves={shelves} />
    </>
  );
}

function Landing() {
  const categories = useCategories();
  if (categories.isError) return <LoadError error={categories.error} onRetry={() => void categories.refetch()} />;
  if (!categories.data) {
    return (
      <div className="mx-(--body-gutter) mt-[46px] mb-8 grid grid-cols-4 gap-5" aria-busy="true">
        {Array.from({ length: 12 }, (_, i) => (
          <SkeletonBlock key={i} aspectRatio="16 / 9" radius="var(--radius-artwork-large)" />
        ))}
      </div>
    );
  }
  return <CategoryTiles groups={categories.data.map((g) => ({ ...g, title: g.title || 'Browse Categories' }))} />;
}

/**
 * The filter chips under the field, as a segmented control (report §6's scope
 * control): one kind of result, or the account's library.
 */
function FilterBar({ filters, selected, onSelect }: { filters: SearchFilterOption[]; selected: SearchFilterOption; onSelect: (filter: SearchFilterOption) => void }) {
  return (
    <div className="flex max-w-full overflow-x-auto rounded-[9px] bg-segmented p-0.5" role="radiogroup" aria-label="Search filter">
      {filters.map((filter) => (
        <button
          key={filter.id}
          className="h-6 flex-none rounded-[7px] px-3 type-callout text-primary aria-checked:bg-segmented-selected aria-checked:shadow-[0_3px_8px_#0000001f,0_3px_1px_#0000000a]"
          role="radio"
          aria-checked={filter === selected}
          onClick={() => onSelect(filter)}
        >
          {filter.label}
        </button>
      ))}
    </div>
  );
}

/** Search (report §6): a sticky bar with the field and filters, then categories or results. */
export default function SearchPage() {
  const { missing } = useYouTubeReady();
  const signedIn = useSignedIn();
  const [params, setParams] = useSearchParams();
  const query = params.get('q') ?? '';
  const filters = FILTERS.filter((f) => signedIn || !f.requiresAuth);
  // Unfiltered unless the URL names a filter this visitor has (Library needs an account).
  const filter = filters.find((f) => f.id === params.get('filter')) ?? filters[0]!;
  const search = (q: string, f = filter) => setParams(q ? { q, ...(f.params && { filter: f.id }) } : {}, { replace: f !== filter });

  return (
    <>
      {/*
        The sticky scope bar holding the field (report §6: 62px, padding 12px 0). Its glass sits on
        a layer behind it, not on the bar: an element with a backdrop-filter is a backdrop root, so
        the suggestions inside it could only blur the bar itself and showed the page underneath unblurred.
      */}
      <div className="sticky top-0 z-[calc(var(--z-default)+5)] flex flex-col items-center gap-3 px-(--body-gutter) py-3 before:absolute before:inset-0 before:-z-1 before:bg-header-material before:[backdrop-filter:saturate(180%)_blur(20px)]">
        <SearchField initial={query} onSearch={search} />
        {query && !missing && <FilterBar filters={filters} selected={filter} onSelect={(f) => search(query, f)} />}
      </div>
      {missing ? <NeedsExtension what="and search YouTube Music" /> : query ? <Results query={query} filter={filter} /> : <Landing />}
    </>
  );
}
