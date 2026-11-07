import { List, Rows3, Search, X } from 'lucide-react';
import { Select } from '@/components/Select';
import { TRACK_SORTS, type TrackSort } from './playlistView';

const toggle =
  'grid size-8 place-items-center rounded-full text-secondary hover:bg-quinary hover:text-primary aria-pressed:bg-quaternary aria-pressed:text-primary';

/** Above a playlist's songs: find in it, sort it, and list or compact rows (Spotify's playlist toolbar). */
export function PlaylistToolbar({
  query,
  onQuery,
  sort,
  onSort,
  compact,
  onCompact,
}: {
  query: string;
  onQuery: (query: string) => void;
  sort: TrackSort;
  onSort: (sort: TrackSort) => void;
  compact: boolean;
  onCompact: (compact: boolean) => void;
}) {
  return (
    <div className="mx-(--body-gutter) mb-2 flex flex-wrap items-center justify-between gap-3">
      <div className="relative flex w-[min(280px,100%)] items-center">
        <Search size={14} strokeWidth={2.4} className="pointer-events-none absolute left-2.5 text-secondary" aria-hidden="true" />
        {/* Not type="search": the / shortcut focuses the page's search field, and this isn't it. */}
        <input
          className="h-8 w-full rounded-pill border-0 bg-quinary ps-8 pe-8 type-body text-primary outline-none placeholder:text-tertiary focus:bg-quaternary"
          type="text"
          role="searchbox"
          value={query}
          placeholder="Find in playlist"
          aria-label="Find in playlist"
          autoComplete="off"
          spellCheck={false}
          onChange={(e) => onQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Escape' && query) {
              e.stopPropagation();
              onQuery('');
            }
          }}
        />
        {query && (
          <button className="absolute right-1.5 grid size-5 place-items-center rounded-full text-secondary hover:text-primary" aria-label="Clear" onClick={() => onQuery('')}>
            <X size={13} strokeWidth={2.6} />
          </button>
        )}
      </div>
      <div className="flex items-center gap-2">
        <span className="type-callout text-secondary">Sort by</span>
        <Select value={sort} options={TRACK_SORTS} onChange={onSort} label="Sort by" />
        <div className="ms-1 flex gap-0.5" role="group" aria-label="View">
          <button className={toggle} aria-label="List" aria-pressed={!compact} onClick={() => onCompact(false)}>
            <List size={16} strokeWidth={2.2} />
          </button>
          <button className={toggle} aria-label="Compact" aria-pressed={compact} onClick={() => onCompact(true)}>
            <Rows3 size={16} strokeWidth={2.2} />
          </button>
        </div>
      </div>
    </div>
  );
}
