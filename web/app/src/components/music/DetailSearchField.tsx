import { Search, X } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { matchesSearch } from '@/data/music/detail';
import type { Track } from '@/data/music/types';
import { detailButton } from './DetailHeader';

/**
 * An album's or playlist's in-page filter (DetailScreen.kt DetailSearchField
 * and its top-bar search button). Ctrl+F (⌘F) opens it while the page is up,
 * in place of the browser's find and ahead of the player's own Ctrl+F, which
 * goes to Search: on a list the filter is the find that's meant.
 */
export function useDetailFilter(tracks: Track[]) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => {
    // On the document, so it runs before the shell's shortcut listener on the window.
    const onKey = (event: KeyboardEvent) => {
      if (!(event.ctrlKey || event.metaKey) || event.altKey || event.key.toLowerCase() !== 'f') return;
      event.preventDefault();
      setOpen(true);
      input.current?.focus();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);

  const close = useCallback(() => {
    setOpen(false);
    setQuery('');
  }, []);
  const matches = useMemo(() => (query.trim() ? tracks.filter((track) => matchesSearch(track, query)) : tracks), [tracks, query]);
  return { open, query, setQuery, close, toggle: () => (open ? close() : setOpen(true)), input, matches };
}

type DetailFilter = ReturnType<typeof useDetailFilter>;

/** The top bar's search button, among the header's small circles. */
export function DetailSearchButton({ filter }: { filter: DetailFilter }) {
  return (
    <button
      className={detailButton.circle}
      aria-label="Search this list"
      title="Search this list (Ctrl+F)"
      aria-pressed={filter.open}
      onClick={filter.toggle}
    >
      <Search size={17} strokeWidth={2.2} />
    </button>
  );
}

/**
 * The filter box under the header (DetailSearchField): 46px tall, r12, the
 * surface at 60% with a 10% white hairline, an 18px glyph 10px before the
 * text. Live as you type: the list is already loaded, so there's nothing for
 * Enter to wait for. Its × clears, and on an empty field closes, as does Escape.
 */
export function DetailSearchField({ filter }: { filter: DetailFilter }) {
  const { open, query, setQuery, close, input } = filter;
  if (!open) return null;
  return (
    <div className="mx-(--body-gutter) mb-2.5 flex h-[46px] max-w-[640px] items-center gap-2.5 rounded-card border-[0.5px] border-[#ffffff1a] bg-[color-mix(in_srgb,var(--surface)_60%,transparent)] ps-3 pe-2 focus-within:border-[color-mix(in_srgb,var(--systemPrimary)_30%,transparent)]">
      <Search size={18} className="flex-none text-secondary" aria-hidden="true" />
      <input
        ref={input}
        // Not type="search": the shell's "/" shortcut looks for that field to focus.
        type="text"
        role="searchbox"
        className="min-w-0 flex-1 border-0 bg-transparent p-0 type-body text-primary caret-key outline-none placeholder:text-secondary"
        placeholder="Search this list"
        aria-label="Search this list"
        value={query}
        autoFocus
        onChange={(event) => setQuery(event.target.value)}
        onKeyDown={(event) => {
          if (event.key !== 'Escape') return;
          event.stopPropagation();
          close();
        }}
      />
      <button
        className="grid size-[30px] flex-none place-items-center rounded-full text-secondary hover:bg-quinary hover:text-primary"
        aria-label={query ? 'Clear search' : 'Close search'}
        onClick={() => (query ? (setQuery(''), input.current?.focus()) : close())}
      >
        <X size={17} />
      </button>
    </div>
  );
}

/** In place of the rows when the filter leaves none (DetailScreen.kt, nothing_matches). */
export function NothingMatches({ query }: { query: string }) {
  return <p className="mx-(--body-gutter) py-12 text-center type-body text-secondary">Nothing here matches “{query.trim()}”</p>;
}
