import { LayoutGrid, List, Search, X } from 'lucide-react';
import { useState } from 'react';
import { Select } from '@/components/Select';
import { LIBRARY_SORTS, type LibrarySort } from '@/data/music/libraryView';

export type LibraryLayout = 'grid' | 'list';

interface Prefs {
  sort: LibrarySort;
  layout: LibraryLayout;
}

const KEY = 'bitchord:library-view';
const DEFAULT: Prefs = { sort: 'recent', layout: 'grid' };

function readAll(): Record<string, Prefs> {
  try {
    return (JSON.parse(localStorage.getItem(KEY) ?? '{}') as Record<string, Prefs>) ?? {};
  } catch {
    return {};
  }
}

/** Each library section remembers its own sort and layout. */
export function useLibraryPrefs(section: string): [Prefs, (change: Partial<Prefs>) => void] {
  const [all, setAll] = useState(readAll);
  const saved = all[section];
  const prefs: Prefs = {
    sort: LIBRARY_SORTS.some((s) => s.value === saved?.sort) ? saved!.sort : DEFAULT.sort,
    layout: saved?.layout === 'list' ? 'list' : 'grid',
  };
  return [
    prefs,
    (change) => {
      const next = { ...readAll(), [section]: { ...prefs, ...change } };
      try {
        localStorage.setItem(KEY, JSON.stringify(next));
      } catch {
        // Storage unavailable: the choice lasts for this visit.
      }
      setAll(next);
    },
  ];
}

const toggle =
  'grid size-8 place-items-center rounded-full text-secondary hover:bg-quinary hover:text-primary aria-pressed:bg-quaternary aria-pressed:text-primary';

/** Above a library section's grid: filter it, sort it, grid or list (Spotify's Your Library controls). */
export function LibraryToolbar({
  what,
  query,
  onQuery,
  prefs,
  onPrefs,
}: {
  /** "playlists", "albums": for the field's placeholder. */
  what: string;
  query: string;
  onQuery: (query: string) => void;
  prefs: Prefs;
  onPrefs: (change: Partial<Prefs>) => void;
}) {
  return (
    <div className="mx-(--body-gutter) mt-4 flex flex-wrap items-center justify-between gap-3">
      <div className="relative flex w-[min(280px,100%)] items-center">
        <Search size={14} strokeWidth={2.4} className="pointer-events-none absolute left-2.5 text-secondary" aria-hidden="true" />
        {/* Not type="search": the / shortcut focuses the page's search field, and this isn't it. */}
        <input
          className="h-8 w-full rounded-pill border-0 bg-quinary ps-8 pe-8 type-body text-primary outline-none placeholder:text-tertiary focus:bg-quaternary"
          type="text"
          role="searchbox"
          value={query}
          placeholder={`Filter ${what}`}
          aria-label={`Filter ${what}`}
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
        <Select value={prefs.sort} options={LIBRARY_SORTS} onChange={(sort) => onPrefs({ sort })} label="Sort by" />
        <div className="ms-1 flex gap-0.5" role="group" aria-label="Layout">
          <button className={toggle} aria-label="Grid" aria-pressed={prefs.layout === 'grid'} onClick={() => onPrefs({ layout: 'grid' })}>
            <LayoutGrid size={16} strokeWidth={2.2} />
          </button>
          <button className={toggle} aria-label="List" aria-pressed={prefs.layout === 'list'} onClick={() => onPrefs({ layout: 'list' })}>
            <List size={16} strokeWidth={2.2} />
          </button>
        </div>
      </div>
    </div>
  );
}
