import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ChevronRight, Download, History, Plus } from 'lucide-react';
import { useState, type ComponentType, type ReactNode } from 'react';
import { SiSpotify } from 'react-icons/si';
import { useNavigate } from 'react-router';
import { Dialog } from '@/components/Dialog';
import { PageHeader } from '@/components/PageHeader';
import { PrefetchLink } from '@/components/PrefetchLink';
import { SkeletonBlock } from '@/components/Skeleton';
import { LibraryReplayCards } from '@/components/music/LibraryReplayCards';
import { CircleLockup, SquareLockup } from '@/components/music/Lockups';
import { NeedsExtension } from '@/components/music/States';
import { createPlaylist } from '@/data/music/api';
import type { LibrarySection } from '@/data/music/library';
import { isPinned, pinnedFirst, usePinnedPlaylists } from '@/data/music/pinnedPlaylists';
import { useLibrary, useSignedIn, useYouTubeReady } from '@/data/music/queries';
import { spotifyPlaylistId } from '@/data/music/spotifyLink';
import type { MusicItem } from '@/data/music/types';
import { useRefreshYtAuth, useSetConnected, useYtAuth } from '@/extension/useYtAuth';
import { offlineSupported } from '@/playback/offline/store';
import { SIGN_IN_URL } from '@/shell/AccountButton';

/** A Library shelf's row never runs past this many cards (LibraryScreen.kt LIBRARY_ROW_MAX_ITEMS); Show all has the rest. */
const ROW_MAX = 5;

// ---- Folder links ------------------------------------------------------------------

interface FolderLink {
  to: string;
  title: string;
  Icon: ComponentType<{ className?: string }>;
}

/**
 * The folders as a plain list (LibraryScreen.kt LibraryLinkList): a 24px
 * glyph, 16px on, the name, a chevron, hairlines between that start under the
 * text. Rows are at least 54px.
 */
function FolderLinks({ links }: { links: FolderLink[] }) {
  return (
    <ul className="pt-1 pb-[22px]">
      {links.map(({ to, title, Icon }) => (
        <li key={to} className="group/item">
          <PrefetchLink
            to={to}
            className="mx-[calc(var(--body-gutter)-10px)] flex min-h-[54px] items-center gap-4 rounded-row px-2.5 hover:bg-quinary"
          >
            <Icon className="size-6 flex-none text-primary" />
            {/* The hairline runs under the text and chevron only, and not under the last row. */}
            <span className="flex min-h-[54px] min-w-0 flex-1 items-center gap-2 border-b-[0.5px] border-outline group-last/item:border-b-0">
              <span className="flex-1 truncate type-body text-primary">{title}</span>
              <ChevronRight className="size-5 flex-none text-on-surface-variant opacity-60" />
            </span>
          </PrefetchLink>
        </li>
      ))}
    </ul>
  );
}

// ---- Shelves -----------------------------------------------------------------------

/** A shelf's heading (HomeScreen.kt SectionHeader): 16px medium, and Show all when the row is cut short. */
function ShelfHeader({ title, showAll }: { title: string; showAll: string | null }) {
  return (
    <div className="flex items-center px-(--body-gutter) py-2.5">
      <h2 className="flex-1 type-title-2-emphasized">{title}</h2>
      {showAll && (
        <PrefetchLink to={showAll} className="rounded-pill px-2.5 py-1 type-callout-medium text-on-surface-variant hover:bg-quinary hover:text-primary">
          Show all
        </PrefetchLink>
      )}
    </div>
  );
}

// The phone's shelf row: fixed 150px cards (SHELF_CARD_WIDTH), 12 apart (LIBRARY_GRID_SPACING), scrolling sideways past the window.
const shelfRow = 'flex gap-3 overflow-x-auto px-(--body-gutter) pb-[26px] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden';
const shelfCard = 'w-[150px] flex-none';

/** A tile that makes something rather than opening it (HomeScreen.kt NewShelfCard): a 12px-round surface square, a glyph, then two lines. */
function MakeTile({ title, subtitle, icon, onClick }: { title: string; subtitle: string; icon: ReactNode; onClick: () => void }) {
  return (
    <li className={shelfCard}>
      <button type="button" className="group/tile flex w-full flex-col text-left" onClick={onClick}>
        <span className="grid aspect-square w-full place-items-center rounded-card bg-surface-variant text-primary transition-[filter] group-hover/tile:brightness-125">
          {icon}
        </span>
        <span className="mt-2.5 truncate type-title-3-emphasized text-primary">{title}</span>
        <span className="truncate type-callout text-on-surface-variant">{subtitle}</span>
      </button>
    </li>
  );
}

function ShelfSkeleton() {
  return (
    <div className={shelfRow} aria-busy="true">
      {Array.from({ length: ROW_MAX }, (_, i) => (
        <div key={i} className={shelfCard} aria-hidden="true">
          <SkeletonBlock aspectRatio="1" radius="var(--radius-card)" />
        </div>
      ))}
    </div>
  );
}

/**
 * One of the account's collections as a shelf (LibraryScreen.kt
 * LibraryGridShelf): five cards at most, counting any tiles, with Show all
 * opening the rest as a grid. Playlists always shows, even empty, for the
 * tiles that make the first one; its tiles lead an empty row and follow a
 * full one.
 */
function AccountShelf({ section, title, tiles }: { section: LibrarySection; title: string; tiles?: ReactNode[] }) {
  const library = useLibrary(section);
  const pins = usePinnedPlaylists((s) => s.ids);
  const first = library.data?.pages[0]?.data;
  // Pinned playlists lead the cards, the make tiles still trailing them (LibraryScreen.kt pinnedFirst, PlaylistShelf).
  const items = pinnedFirst(first?.grid ?? [], pins);
  const extra = tiles?.length ?? 0;
  if (!library.data && !library.isPending) return null;
  if (library.data && !items.length && !extra) return null;
  const more = items.length + extra > ROW_MAX || !!library.hasNextPage;
  const card = (item: MusicItem, i: number) => (
    <li key={`${item.id}-${i}`} className={shelfCard}>
      {item.kind === 'artist' ? <CircleLockup item={item} width={150} /> : <SquareLockup item={item} width={150} pinned={item.kind === 'playlist' && isPinned(pins, item.id)} />}
    </li>
  );
  return (
    <section aria-label={title}>
      <ShelfHeader title={title} showAll={more ? `/library/${section}` : null} />
      {!library.data ? (
        <ShelfSkeleton />
      ) : (
        <ul className={shelfRow}>
          {!items.length && tiles}
          {items.slice(0, ROW_MAX - extra).map(card)}
          {items.length > 0 && tiles}
        </ul>
      )}
    </section>
  );
}

// ---- Making playlists --------------------------------------------------------------

const field = 'h-[46px] w-full border-0 rounded-search bg-surface-variant px-3.5 type-body text-primary outline-none placeholder:text-on-surface-variant';
const dialogButton = 'h-9 rounded-pill bg-key px-4 type-callout-emphasized text-on-key disabled:opacity-40';

/** New playlist (MainActivity's create dialog): a name, then a private YouTube Music playlist, opened. */
function NewPlaylistDialog({ onClose }: { onClose: () => void }) {
  const [title, setTitle] = useState('');
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const create = useMutation({
    mutationFn: () => createPlaylist(title.trim(), []),
    onSuccess: (id) => {
      void queryClient.invalidateQueries({ queryKey: ['music', 'library'] });
      navigate(`/playlist/${encodeURIComponent(`VL${id}`)}`);
    },
  });
  return (
    <Dialog
      title="New playlist"
      subtitle="Saved to YouTube Music"
      onClose={onClose}
      footer={
        <>
          {create.isError && <span className="mr-auto type-callout text-destructive">Couldn’t make the playlist.</span>}
          <button type="submit" form="new-playlist" className={dialogButton} disabled={!title.trim() || create.isPending}>
            {create.isPending ? 'Making…' : 'Create'}
          </button>
        </>
      }
    >
      <form
        id="new-playlist"
        onSubmit={(e) => {
          e.preventDefault();
          if (title.trim()) create.mutate();
        }}
      >
        <input className={field} placeholder="Playlist name" aria-label="Playlist name" value={title} maxLength={150} autoFocus onChange={(e) => setTitle(e.currentTarget.value)} />
      </form>
    </Dialog>
  );
}

/** Import Spotify, from a playlist link: its page matches the songs to YouTube Music and can save them as a playlist. */
function ImportSpotifyDialog({ onClose }: { onClose: () => void }) {
  const [link, setLink] = useState('');
  const navigate = useNavigate();
  const id = spotifyPlaylistId(link);
  return (
    <Dialog
      title="Import Spotify"
      subtitle="From a playlist link"
      onClose={onClose}
      footer={
        <button type="submit" form="import-spotify" className={dialogButton} disabled={!id}>
          Open
        </button>
      }
    >
      <form
        id="import-spotify"
        onSubmit={(e) => {
          e.preventDefault();
          if (id) navigate(`/spotify/playlist/${encodeURIComponent(id)}`);
        }}
      >
        <input
          className={field}
          placeholder="https://open.spotify.com/playlist/…"
          aria-label="Spotify playlist link"
          value={link}
          autoFocus
          spellCheck={false}
          onChange={(e) => setLink(e.currentTarget.value)}
        />
        {link.trim() && !id && <p className="mt-2 type-callout text-on-surface-variant">That doesn’t look like a Spotify playlist link.</p>}
      </form>
    </Dialog>
  );
}

// ---- Signing in --------------------------------------------------------------------

/**
 * Sign in the way the account button does (AccountButton.tsx): Google's own
 * sign-in in a new tab while the browser has no YouTube session, then
 * connecting BitChord to the session it finds.
 */
function useSignIn() {
  const status = useYtAuth();
  const connect = useSetConnected();
  const refresh = useRefreshYtAuth();
  const auth = status.state === 'ready' ? status.auth : null;
  return () => {
    if (!auth?.browserSignedIn) window.open(SIGN_IN_URL, '_blank', 'noreferrer');
    else if (!auth.connected) connect.mutate(true);
    else refresh.mutate();
  };
}

/** The signed-out foot of the page (LibraryScreen.kt, MessageState with Sign in). */
function SignInMessage() {
  const signIn = useSignIn();
  return (
    <div className="flex flex-col items-center gap-4 px-[calc(var(--body-gutter)+6px)] py-12 text-center">
      <p className="max-w-[460px] type-body text-on-surface-variant">
        Sign in to your Google account to see your liked songs, playlists, and YouTube Music history.
      </p>
      <button type="button" className="h-10 rounded-pill bg-key px-6 type-callout-medium text-on-key transition-[scale] duration-150 active:scale-[.97]" onClick={signIn}>
        Sign in
      </button>
    </div>
  );
}

// ---- The page ----------------------------------------------------------------------

/**
 * The Library (sharedUi/.../ui/screens/LibraryScreen.kt with the phone's
 * parts, app/.../ui/screens/LibraryPhoneParts.kt): the title, the Replay
 * cards, the folders, then the Playlists shelf, led by the tiles that make
 * one, and the account's other collections, each a row of five with Show all.
 * Signed out, the folders and tiles stay, and the account's shelves give way
 * to an invitation to sign in.
 */
export default function LibraryHubPage() {
  const { missing } = useYouTubeReady();
  const signedIn = useSignedIn();
  const auth = useYtAuth();
  const signIn = useSignIn();
  const [dialog, setDialog] = useState<'new' | 'spotify' | null>(null);

  const links: FolderLink[] = [
    ...(offlineSupported() ? [{ to: '/library/downloads', title: 'Downloads', Icon: Download }] : []),
    // The phone keeps history behind a top-bar button on this page; here it is one of the folders.
    ...(signedIn ? [{ to: '/library/recent', title: 'History', Icon: History }] : []),
    { to: '/spotify', title: 'Spotify', Icon: SiSpotify },
  ];
  const tiles = [
    <MakeTile
      key="new"
      title="New playlist"
      subtitle="Saved to YouTube Music"
      icon={<Plus className="size-[34px]" strokeWidth={2.2} />}
      onClick={() => (signedIn ? setDialog('new') : signIn())}
    />,
    <MakeTile
      key="spotify"
      title="Import Spotify"
      subtitle="From a playlist link"
      icon={<SiSpotify className="size-11" />}
      onClick={() => setDialog('spotify')}
    />,
  ];

  return (
    <div className="pb-10">
      <PageHeader title="Library" />
      <div className="pt-2">
        <LibraryReplayCards />
      </div>
      <FolderLinks links={links} />
      {signedIn ? (
        <>
          <AccountShelf section="playlists" title="Playlists" tiles={tiles} />
          <AccountShelf section="albums" title="Albums" />
          <AccountShelf section="artists" title="Artists" />
        </>
      ) : (
        <>
          <section aria-label="Playlists">
            <ShelfHeader title="Playlists" showAll={null} />
            <ul className={shelfRow}>{tiles}</ul>
          </section>
          {missing ? <NeedsExtension what="your library" /> : auth.state !== 'checking' && <SignInMessage />}
        </>
      )}
      {dialog === 'new' && <NewPlaylistDialog onClose={() => setDialog(null)} />}
      {dialog === 'spotify' && <ImportSpotifyDialog onClose={() => setDialog(null)} />}
    </div>
  );
}
