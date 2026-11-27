import { ArrowUpLeft, Search, X } from 'lucide-react';
import { Fragment, useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { useSearchHistory } from '@/data/music/searchHistory';
import { useSuggestions } from '@/data/music/queries';
import type { MusicItem } from '@/data/music/types';
import { usePlayer } from '@/state/player';
import { hrefFor } from './links';
import { playTrack, toTrack } from './play';
import { SEARCH_RESET_EVENT } from './searchReset';
import { isPlayable, ItemRow, rowButton, SongActions } from './SearchRow';

type Option = { kind: 'query'; text: string } | { kind: 'item'; item: MusicItem };

/** The phone shows three completions, so the list stays short above the songs (SearchScreen.kt searchSuggestions). */
const MAX_COMPLETIONS = 3;
/** How long the Library source waits after a keystroke before it searches. */
const LIVE_DEBOUNCE_MS = 300;

export interface SearchFieldState {
  /** Nothing typed: the page is the recent searches, under the source switch. */
  empty: boolean;
  /** Mid-edit: the page is suggestions, and what sat under the field is put away. */
  suggesting: boolean;
}

/**
 * The search field at the head of the page and, while it is being typed in,
 * the suggestions that take the page's place (sharedUi/.../ui/components/
 * SearchField.kt and SearchScreen.kt). The field is the phone's: 46px tall,
 * r11, a flat surface-variant fill with no border, 16px text, a 32px
 * magnifier that is also the search button, and a 28px clear once there is
 * something to clear.
 *
 * Typing puts YouTube's completions in place of the results, each with a ↖
 * that fills the field without searching, then the songs, artists and albums
 * it already matches. Arrow keys move through them; Enter picks, or searches
 * the words as typed; Escape goes back to the results. A song goes on the
 * queue from its hover button or Shift+Enter, the phone's swipe.
 *
 * [controls] sit under the field (the source switch, the filters); [children]
 * is the page while nothing is being typed. With [live] (the Library source,
 * which has no completions) the field searches as it is typed instead.
 */
export function SearchField({
  initial,
  onSearch,
  placeholder,
  live = false,
  controls,
  children,
}: {
  initial: string;
  onSearch: (query: string) => void;
  placeholder: string;
  live?: boolean;
  controls?: (state: SearchFieldState) => ReactNode;
  children: ReactNode;
}) {
  const [value, setValue] = useState(initial);
  // An edit since the last search: what the suggestions answer.
  const [editing, setEditing] = useState(false);
  const [active, setActive] = useState(-1);
  const input = useRef<HTMLInputElement>(null);
  const listId = useId();
  const navigate = useNavigate();
  const typing = !live && editing && value.trim().length > 0;
  const suggestions = useSuggestions(typing ? value : '');
  const record = useSearchHistory((s) => s.record);

  useEffect(() => {
    setValue(initial);
    setEditing(false);
  }, [initial]);

  // An empty search page is waiting to be typed in.
  useEffect(() => {
    // Only on arriving: focus is the visitor's after that.
    if (!initial) input.current?.focus();
  }, []);

  // The shell's re-tapped Search tab (searchReset.ts).
  const searchRef = useRef(onSearch);
  searchRef.current = onSearch;
  useEffect(() => {
    const reset = () => {
      setValue('');
      setEditing(false);
      setActive(-1);
      searchRef.current('');
      input.current?.focus();
    };
    window.addEventListener(SEARCH_RESET_EVENT, reset);
    return () => window.removeEventListener(SEARCH_RESET_EVENT, reset);
  }, []);

  // The Library source searches as it is typed, once typing pauses.
  useEffect(() => {
    if (!live || !editing) return;
    const timer = setTimeout(() => searchRef.current(value.trim()), LIVE_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [live, editing, value]);

  const options: Option[] = typing
    ? [
        ...(suggestions.data?.queries ?? []).slice(0, MAX_COMPLETIONS).map((q) => ({ kind: 'query' as const, text: q.text })),
        ...(suggestions.data?.items ?? []).map((item) => ({ kind: 'item' as const, item })),
      ]
    : [];
  // Suggestions take the page only once there are some (SearchScreen.kt: a non-empty list means mid-edit).
  const suggesting = options.length > 0;

  const done = () => {
    setEditing(false);
    setActive(-1);
    // Picking is done typing, so the results get the keyboard (SearchScreen.kt clears focus).
    input.current?.blur();
  };

  const pick = (option: Option | undefined) => {
    if (!option) {
      if (!value.trim()) return;
      done();
      return onSearch(value.trim());
    }
    done();
    if (option.kind === 'query') {
      setValue(option.text);
      return onSearch(option.text);
    }
    record(option.item);
    const href = hrefFor(option.item);
    if (href) navigate(href);
    else playTrack(option.item, undefined, { title: value.trim(), type: 'search', id: value.trim() });
  };

  const fill = (text: string) => {
    setValue(text);
    setActive(-1);
    input.current?.focus();
  };

  const clear = () => {
    setValue('');
    setEditing(false);
    setActive(-1);
    onSearch('');
    // Clearing is an edit, not a dismissal: ready for the next search (SearchField.kt).
    input.current?.focus();
  };

  return (
    <>
      {/*
        Fixed at the head of the page while the results scroll under it (SearchScreen.kt).
        It sits in the shell's 52px top bar row, the field matching the bar's 44px circles edge for edge and stopping short
        of the bar's account pill, which the shell measures into --top-bar-trailing-width.
      */}
      <div className="sticky top-0 z-[calc(var(--z-default)+5)] flex flex-col gap-1 bg-page px-(--body-gutter) pt-1 pb-1.5">
        <form
          role="search"
          className="me-(--top-bar-trailing-width,0px) flex h-11 items-center gap-1 rounded-search bg-surface-variant ps-2 pe-3"
          onSubmit={(e) => {
            e.preventDefault();
            pick(options[active]);
          }}
        >
          <button
            type="submit"
            className="grid size-8 flex-none place-items-center rounded-full text-on-surface-variant enabled:hover:bg-quinary"
            aria-label="Search"
            disabled={!value.trim()}
          >
            <Search size={20} strokeWidth={2.2} />
          </button>
          <input
            ref={input}
            className="h-full min-w-0 flex-1 appearance-none border-0 bg-transparent p-0 type-body text-primary caret-primary outline-none placeholder:text-on-surface-variant placeholder:opacity-100 [&::-webkit-search-cancel-button]:hidden"
            type="search"
            value={value}
            placeholder={placeholder}
            aria-label="Search"
            role="combobox"
            aria-expanded={options.length > 0}
            aria-controls={listId}
            aria-activedescendant={active >= 0 ? `${listId}-${active}` : undefined}
            autoComplete="off"
            spellCheck={false}
            onChange={(e) => {
              const next = e.target.value;
              setValue(next);
              setActive(-1);
              if (next.trim()) setEditing(true);
              else clear();
            }}
            onKeyDown={(e) => {
              if (e.key === 'ArrowDown') {
                e.preventDefault();
                setActive((i) => Math.min(i + 1, options.length - 1));
              } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                setActive((i) => Math.max(i - 1, -1));
              } else if (e.key === 'Escape' && editing) {
                e.preventDefault();
                setValue(initial);
                done();
              } else if (e.key === 'Enter' && e.shiftKey) {
                const option = options[active];
                if (option?.kind !== 'item' || !isPlayable(option.item)) return;
                e.preventDefault();
                usePlayer.getState().addToQueue([toTrack(option.item)]);
              }
            }}
          />
          {value && (
            <button
              type="button"
              className="grid size-7 flex-none place-items-center rounded-full text-on-surface-variant hover:bg-quinary"
              aria-label="Clear search"
              onClick={clear}
            >
              <X size={18} strokeWidth={2.2} />
            </button>
          )}
        </form>
        {controls?.({ empty: !value, suggesting })}
      </div>
      {suggesting ? (
        <ul id={listId} role="listbox" aria-label="Suggestions" className="pt-2 pb-10">
          {options.map((option, i) => {
            const id = `${listId}-${i}`;
            const optionProps = {
              id,
              role: 'option',
              'aria-selected': i === active,
              onMouseDown: (e: React.MouseEvent) => e.preventDefault(),
              onMouseEnter: () => setActive(i),
            } as const;
            if (option.kind === 'query') {
              // The completions and the matches are parted by a faint hairline (SearchScreen.kt searchTypeaheadDropdown).
              const parted = options[i + 1]?.kind === 'item';
              return (
                <Fragment key={`q-${option.text}`}>
                <li
                  {...optionProps}
                  data-active={i === active || undefined}
                  className="mx-[calc(var(--body-gutter)-10px)] grid cursor-default grid-cols-[22px_minmax(0,1fr)_40px] items-center gap-x-4 rounded-row py-1.5 ps-2.5 pe-1 data-active:bg-quinary"
                  onClick={() => pick(option)}
                >
                  <Search size={22} strokeWidth={2} className="text-on-surface-variant" aria-hidden="true" />
                  <span className="truncate type-title-3-emphasized text-primary">{option.text}</span>
                  <button
                    type="button"
                    tabIndex={-1}
                    className={rowButton}
                    aria-label={`Edit “${option.text}”`}
                    title="Fill in the search field"
                    onClick={(e) => {
                      e.stopPropagation();
                      fill(option.text);
                    }}
                  >
                    <ArrowUpLeft />
                  </button>
                </li>
                {parted && <li role="presentation" className="mx-(--body-gutter) my-2 h-px scale-y-50 bg-outline/40" />}
                </Fragment>
              );
            }
            const { item } = option;
            return (
              <ItemRow
                key={`i-${item.id}`}
                item={item}
                onPick={() => pick(option)}
                active={i === active}
                option={optionProps}
                trailing={isPlayable(item) && <SongActions item={item} />}
              />
            );
          })}
        </ul>
      ) : (
        children
      )}
    </>
  );
}
