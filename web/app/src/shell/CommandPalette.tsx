import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { CornerDownLeft, Loader2, Music2, Search, ThumbsUp } from 'lucide-react';
import { useEffect, useId, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { Artwork } from '@/components/music/Artwork';
import { hrefFor } from '@/components/music/links';
import { playList, playTrack } from '@/components/music/play';
import { LIKED_MUSIC_PLAYLIST } from '@/data/music/library';
import { albumQuery, artistQuery, playlistQuery, searchQuery, useLibraryPlaylists, useSignedIn, useYouTubeReady } from '@/data/music/queries';
import type { MusicItem } from '@/data/music/types';
import { useSpotifyPlaylists } from '@/data/spotify/queries';
import { queryClient } from '@/lib/queryClient';
import { useRecents, type RecentItem } from '@/state/recents';
import { useUi } from '@/state/ui';
import { flattenResults, localMatches } from './paletteSearch';

const KIND_LABEL: Record<MusicItem['kind'], string> = {
  song: 'Song',
  video: 'Video',
  album: 'Album',
  playlist: 'Playlist',
  'spotify-playlist': 'Spotify',
  artist: 'Artist',
  episode: 'Episode',
  podcast: 'Podcast',
  unknown: '',
};

const LIKED_ID = `VL${LIKED_MUSIC_PLAYLIST}`;

interface Row {
  item: RecentItem;
  /** Searching shows each row's kind as a tag on the right, as Spotify does. */
  tag: boolean;
}
interface Section {
  title: string;
  rows: Row[];
}

const playable = (item: RecentItem) => item.kind === 'song' || item.kind === 'video';

function asMusicItem(item: RecentItem): MusicItem {
  return {
    artists: [],
    album: null,
    durationSec: null,
    explicit: false,
    wide: false,
    ...item,
  };
}

function rememberSong(item: RecentItem) {
  const artists = item.artists?.map((a) => a.name).join(', ');
  useRecents.getState().remember({ ...item, subtitle: ['Song', artists].filter(Boolean).join(' • ') });
}

/** Shift+Enter: start the list (or song) without opening its page. Falls back to opening it. */
async function playNow(item: RecentItem, open: () => void) {
  try {
    if (playable(item)) {
      rememberSong(item);
      return playTrack(asMusicItem(item));
    }
    if (item.kind === 'playlist') {
      const data = await queryClient.fetchInfiniteQuery(playlistQuery(item.id));
      const first = data.pages[0];
      const tracks = data.pages.flatMap((p) => p.tracks);
      if (!tracks.length) return open();
      useRecents.getState().remember(item);
      return playList(tracks, 0, { source: { title: first?.header?.title ?? item.title, type: 'playlist', id: item.id } });
    }
    if (item.kind === 'album') {
      const album = await queryClient.fetchQuery(albumQuery(item.id));
      if (!album.tracks.length) return open();
      useRecents.getState().remember(item);
      return playList(album.tracks, 0, {
        source: { title: album.title, type: 'album', id: album.id },
        fallbackArtwork: album.thumbnail,
        fallbackArtists: album.artists,
      });
    }
    if (item.kind === 'artist') {
      const artist = await queryClient.fetchQuery(artistQuery(item.id));
      if (!artist.topSongs.length) return open();
      useRecents.getState().remember(item);
      return playList(artist.topSongs, 0, { source: { title: `${artist.name}: Top Songs`, type: 'artist', id: artist.id } });
    }
    open();
  } catch {
    open();
  }
}

function useDebounced(value: string, ms: number) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), ms);
    return () => clearTimeout(timer);
  }, [value, ms]);
  return debounced;
}

function Thumb({ item }: { item: RecentItem }) {
  if (item.id === LIKED_ID && !item.thumbnail) {
    return (
      <span className="grid size-11 place-items-center rounded-artwork-sm bg-linear-135 from-[#a259ff] to-[#f0629c] text-white">
        <ThumbsUp size={20} fill="currentColor" strokeWidth={0} />
      </span>
    );
  }
  if (!item.thumbnail) {
    return (
      <span className="grid size-11 place-items-center rounded-artwork-sm text-secondary">
        <Music2 size={20} />
      </span>
    );
  }
  return (
    <div className="w-11">
      <Artwork src={item.thumbnail} displayWidth={44} radius={item.kind === 'artist' ? '50%' : 'var(--radius-artwork-small)'} />
    </div>
  );
}

/**
 * The ⌘K quick search: with nothing typed, what was opened lately and the
 * user's own playlists; as they type, their matching playlists, then YouTube
 * Music's results. ↑/↓ move, Enter opens (or plays a song), Shift+Enter plays,
 * Esc closes.
 */
export function CommandPalette() {
  const open = useUi((s) => s.paletteOpen);
  // Stays mounted a moment after closing, for the exit animation.
  const [rendered, setRendered] = useState(open);
  useEffect(() => {
    if (open) return setRendered(true);
    const timer = setTimeout(() => setRendered(false), 140);
    return () => clearTimeout(timer);
  }, [open]);
  if (!open && !rendered) return null;
  return <Palette closing={!open} />;
}

function Palette({ closing }: { closing: boolean }) {
  const setOpen = useUi((s) => s.setPaletteOpen);
  const navigate = useNavigate();
  const listId = useId();
  const input = useRef<HTMLInputElement>(null);
  const list = useRef<HTMLDivElement>(null);
  const [value, setValue] = useState('');
  // Nothing is selected until an arrow key (or the pointer) picks a row; Enter alone searches.
  const [active, setActive] = useState(-1);
  const [starting, setStarting] = useState<string | null>(null);
  // Where the pointer last was. Chrome sends mousemove when rows shift under a
  // still pointer (new results, scrolling); only a real move picks a row.
  const pointer = useRef<{ x: number; y: number } | null>(null);
  const query = useDebounced(value.trim(), 200);
  const typing = value.trim().length > 0;

  const { ready } = useYouTubeReady();
  const signedIn = useSignedIn();
  const recents = useRecents((s) => s.items);
  const library = useLibraryPlaylists();
  const spotify = useSpotifyPlaylists();
  const search = useQuery({ ...searchQuery(query), enabled: ready && query.length > 0, placeholderData: keepPreviousData });

  // The focus to give back on close, and the page under the palette held still.
  useLayoutEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    input.current?.focus();
    return () => previous?.focus?.();
  }, []);

  const libraryItems = useMemo<RecentItem[]>(() => {
    const own: RecentItem[] = (library.data ?? []).map((p) => (p.id === LIKED_ID && !p.subtitle ? { ...p, subtitle: 'Auto playlist' } : p));
    if (signedIn && !own.some((p) => p.id === LIKED_ID)) {
      own.unshift({ kind: 'playlist', id: LIKED_ID, title: 'Liked Music', subtitle: 'Auto playlist', thumbnail: null });
    }
    const fromSpotify: RecentItem[] = (spotify.data ?? []).map((p) => ({
      kind: 'spotify-playlist',
      id: p.id,
      title: p.name,
      subtitle: ['Spotify Playlist', p.owner].filter(Boolean).join(' • '),
      thumbnail: p.imageUrl,
    }));
    return [...own, ...fromSpotify];
  }, [library.data, spotify.data, signedIn]);

  const sections = useMemo<Section[]>(() => {
    if (!typing) {
      return [
        { title: 'Recently opened', rows: recents.map((item) => ({ item, tag: false })) },
        { title: 'Your library', rows: libraryItems.map((item) => ({ item, tag: false })) },
      ].filter((s) => s.rows.length);
    }
    const mine = localMatches([...recents, ...libraryItems], value);
    const results = search.data && query ? flattenResults(search.data, new Set(mine.map((m) => m.id))) : [];
    return [
      { title: 'Your library', rows: mine.map((item) => ({ item, tag: true })) },
      { title: 'YouTube Music', rows: results.map((item) => ({ item, tag: true })) },
    ].filter((s) => s.rows.length);
  }, [typing, recents, libraryItems, value, search.data, query]);

  const rows = useMemo(() => sections.flatMap((s) => s.rows), [sections]);
  const searching = typing && (value.trim() !== query || search.isFetching);

  // Keep the selection on the first row as the list changes under it.
  useEffect(() => setActive(-1), [value]);
  useEffect(() => {
    if (active >= rows.length) setActive(-1);
  }, [rows.length, active]);
  useEffect(() => {
    list.current?.querySelector(`[data-index="${active}"]`)?.scrollIntoView({ block: 'nearest' });
  }, [active]);

  const close = () => setOpen(false);

  const openItem = (item: RecentItem) => {
    close();
    const href = hrefFor(item);
    if (href) return navigate(href);
    rememberSong(item);
    playTrack(asMusicItem(item));
  };

  const play = async (item: RecentItem) => {
    if (playable(item)) {
      close();
      return void playNow(item, () => openItem(item));
    }
    setStarting(`${item.kind}:${item.id}`);
    await playNow(item, () => openItem(item));
    setStarting(null);
    close();
  };

  const pick = (index: number, playIt: boolean) => {
    if (starting || closing) return;
    const row = rows[index];
    if (!row) {
      // No row picked: Enter runs the full search for what was typed.
      const q = value.trim();
      if (!q) return;
      close();
      return void navigate(`/search?q=${encodeURIComponent(q)}`);
    }
    if (playIt) void play(row.item);
    else openItem(row.item);
  };

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActive((i) => (rows.length ? (i + 1 >= rows.length ? 0 : i + 1) : -1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((i) => (rows.length ? (i <= -1 ? rows.length - 1 : i - 1) : -1));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      pick(active, e.shiftKey);
    } else if (e.key === 'Escape') {
      e.preventDefault();
      e.stopPropagation();
      close();
    } else if (e.key === 'Tab') {
      // Focus stays in the field; Tab moves the selection like the arrows.
      e.preventDefault();
      setActive((i) => (rows.length ? (e.shiftKey ? (i <= -1 ? rows.length - 1 : i - 1) : i + 1 >= rows.length ? 0 : i + 1) : -1));
    }
  };

  let index = -1;
  let empty: string | null = null;
  if (!rows.length) {
    if (typing) empty = searching ? null : ready ? `No results for “${value.trim()}”` : 'Your playlists only: search YouTube Music with the BitChord extension.';
    else empty = signedIn ? 'Start typing to search YouTube Music.' : 'Search YouTube Music. Sign in to see your playlists here.';
  }

  return (
    <div
      className={`fixed inset-0 z-(--z-modal) flex justify-center bg-scrim pt-[12vh] max-sm:pt-0 ${closing ? 'pointer-events-none animate-scrim-out' : 'animate-scrim-in'}`}
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) close();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Quick search"
        className={`${closing ? 'animate-palette-out' : 'animate-palette-in'} flex h-fit max-h-[min(680px,76vh)] w-[min(680px,92vw)] flex-col overflow-hidden rounded-[18px] border border-[#00000033] bg-thick-material shadow-[inset_0_0_0_1px_#ffffff26,0_20px_70px_#00000080] [backdrop-filter:blur(60px)_saturate(2.2)] max-sm:h-full max-sm:max-h-none max-sm:w-full max-sm:rounded-none origin-top transition-[height] duration-200`}
        onKeyDown={onKeyDown}
      >
        <div className="flex items-center gap-3 border-b border-divider px-5 py-4">
          {searching ? (
            <Loader2 size={20} className="flex-none animate-spin text-secondary" aria-hidden="true" />
          ) : (
            <Search size={20} strokeWidth={2.2} className="flex-none text-secondary" aria-hidden="true" />
          )}
          <input
            ref={input}
            className="min-w-0 flex-1 appearance-none border-0 bg-transparent p-0 type-[400_20px/26px] text-primary shadow-none ring-0 outline-none focus:ring-0 focus:outline-none placeholder:text-tertiary"
            value={value}
            placeholder="Search your music, history and YouTube Music"
            aria-label="Quick search"
            role="combobox"
            aria-expanded="true"
            aria-controls={listId}
            aria-activedescendant={active >= 0 && rows.length ? `${listId}-${active}` : undefined}
            autoComplete="off"
            spellCheck={false}
            onChange={(e) => setValue(e.target.value)}
          />
          <kbd className="rounded-[5px] border border-divider px-1.5 py-0.5 type-callout text-secondary max-sm:hidden">esc</kbd>
        </div>

        <div ref={list} id={listId} role="listbox" aria-label="Results" className="min-h-0 flex-1 overflow-y-auto px-2 pb-2">
          {empty && <p className="px-4 py-10 text-center type-body text-secondary">{empty}</p>}
          {sections.map((section) => (
            <div key={`${section.title}-${typing ? query : ''}`} role="group" aria-label={section.title}>
              <h3 className="animate-row-in px-3 pt-4 pb-1.5 type-callout-emphasized text-secondary">{section.title}</h3>
              {section.rows.map(({ item, tag }) => {
                index += 1;
                const i = index;
                const selected = i === active;
                const busy = starting === `${item.kind}:${item.id}`;
                return (
                  <div
                    key={`${section.title}-${item.kind}-${item.id}`}
                    id={`${listId}-${i}`}
                    data-index={i}
                    role="option"
                    aria-selected={selected}
                    data-active={selected || undefined}
                    className="group/row grid animate-row-in cursor-default grid-cols-[44px_minmax(0,1fr)_auto] items-center gap-x-3.5 rounded-[10px] px-3 py-2 transition-[background-color,transform] duration-150 ease-out data-active:bg-quaternary active:scale-[0.99]"
                    style={{ animationDelay: `${Math.min(i, 10) * 18}ms` }}
                    onMouseMove={(e) => {
                      const last = pointer.current;
                      pointer.current = { x: e.clientX, y: e.clientY };
                      if (!last || (last.x === e.clientX && last.y === e.clientY)) return;
                      if (!selected) setActive(i);
                    }}
                    onMouseDown={(e) => e.preventDefault()}
                    onClick={(e) => pick(i, e.shiftKey)}
                  >
                    <Thumb item={item} />
                    <div className="flex min-w-0 flex-col">
                      <span className="truncate type-body-emphasized text-primary">{item.title}</span>
                      <span className="truncate type-callout text-secondary">{item.subtitle || KIND_LABEL[item.kind]}</span>
                    </div>
                    <div className="flex items-center gap-2">
                      {tag && KIND_LABEL[item.kind] && (
                        <span className="rounded-[5px] bg-quinary px-2 py-1 type-callout text-secondary">{KIND_LABEL[item.kind]}</span>
                      )}
                      {busy ? (
                        <Loader2 size={17} className="animate-spin text-secondary" aria-label="Starting" />
                      ) : (
                        <CornerDownLeft size={17} className="-translate-x-1 text-secondary opacity-0 transition-[opacity,translate] duration-150 ease-out group-data-active/row:translate-x-0 group-data-active/row:opacity-100" aria-hidden="true" />
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          ))}
        </div>

        <div className="flex flex-wrap items-center gap-x-5 gap-y-1 border-t border-divider px-5 py-2.5 type-callout text-secondary max-sm:hidden">
          <Hint keys={['↑', '↓']} label="to move" />
          <Hint keys={['Enter']} label={active < 0 && typing ? 'to search' : 'to open'} />
          <Hint keys={['Shift', 'Enter']} label="to play" />
          <Hint keys={['Esc']} label="to close" />
        </div>
      </div>
    </div>
  );
}

function Hint({ keys, label }: { keys: string[]; label: string }) {
  return (
    <span className="inline-flex items-center gap-1">
      {keys.map((k) => (
        <kbd key={k} className="rounded-[4px] border border-divider px-1.5 py-px type-callout text-secondary">
          {k}
        </kbd>
      ))}
      <span className="ml-0.5">{label}</span>
    </span>
  );
}
