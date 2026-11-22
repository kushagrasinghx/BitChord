import { ArrowUpRight, ListMusic, Settings } from 'lucide-react';
import { useLayoutEffect, useRef, useState } from 'react';
import { MdOutlineExtension } from 'react-icons/md';
import { useLocation, type NavLinkProps } from 'react-router';
import { PrefetchLink, PrefetchNavLink } from '@/components/PrefetchLink';
import { LogoMark } from '@/components/icons/LogoMark';
import { AllPlaylistsIcon } from '@/components/icons/NavIcons';
import { hrefFor } from '@/components/music/links';
import { config } from '@/config';
import { LIBRARY_SECTIONS } from '@/data/music/library';
import { useLibraryPlaylists, useSignedIn } from '@/data/music/queries';
import { useSpotifyPlaylists } from '@/data/spotify/queries';
import { useExtensionUpdate } from '@/extension/useExtensionUpdate';
import { useSpotifySignedIn } from '@/extension/useSpotifyAuth';
import { useUi } from '@/state/ui';
import { pillFrames, SPRING_MS } from './tabSpring';
import { SEARCH_RESET_EVENT, tabOf, TABS } from './tabs';

// A sidebar row; NavLink sets aria-current on the page's own row.
const item =
  'group flex h-9 items-center gap-2 mb-0.5 p-1 rounded-nav type-body-reduced text-primary hover:bg-quinary aria-[current=page]:bg-nav-selected';
// Apple's glyphs, drawn at 24px (their 24-unit box), which the user preferred to Apple's own 28px.
const icon = 'inline-grid size-6 flex-none place-items-center text-primary group-aria-[current=page]:text-key';
const navLabel = 'min-w-0 truncate';
// "Library", "Playlists" (report §1: 12px/15px 600, secondary, padding 4px 8px).
const sectionHeader = 'mt-4 px-2 py-1 type-callout-emphasized text-secondary';
const list = 'flex flex-col';

/**
 * Clicking the page already open takes it back to the top, as tapping the
 * active tab does on the phone (MainActivity onActiveTabTap). A long way down,
 * it jumps most of the way first, so the eased part is a screen, not a playlist.
 */
function scrollPageToTop() {
  const page = document.getElementById('scrollable-page');
  if (!page) return;
  const still = matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (!still && page.scrollTop > page.clientHeight * 3) page.scrollTop = page.clientHeight;
  page.scrollTo({ top: 0, behavior: still ? 'auto' : 'smooth' });
}

/**
 * A sidebar link, which is also the way back to the top of the page it opens.
 * Tabs swap instantly; anything else is pushed with the page crossfade, as the
 * phone's navigator only fades between a tab and what is pushed over it.
 */
function SidebarLink({ onClick, ...props }: NavLinkProps) {
  const { pathname } = useLocation();
  return (
    <PrefetchNavLink
      viewTransition={tabOf(String(props.to)) < 0}
      {...props}
      onClick={(event) => {
        if (pathname === props.to) scrollPageToTop();
        onClick?.(event);
      }}
    />
  );
}

/** A tab's row height plus the gap under it (h-9, mb-0.5): how far the pill moves per tab. */
const TAB_STEP = 38;
// The glyph's 1.08 pop rides the same spring as the pill (FloatingBottomBar.kt BottomBarItem), sampled for CSS.
const SPRING_EASE =
  'ease-[linear(0,0.032,0.111,0.217,0.336,0.456,0.57,0.673,0.762,0.837,0.898,0.945,0.98,1.005,1.022,1.032,1.037,1.038,1.037,1.034,1.03,1.025,1.02,1.016,1)]';

/**
 * Android's tabs as the top of the sidebar (FloatingBottomBar.kt): the chosen
 * one in the key colour, the rest onSurfaceVariant, tints crossing over 200ms,
 * and one pill of the key colour at 14% that travels between them on the
 * glass spring rather than a highlight per row. A page pushed over a tab keeps
 * that tab lit, as the phone's bar does under a pushed page.
 */
function TabList({ onNavigate }: { onNavigate: () => void }) {
  const { pathname } = useLocation();
  const current = tabOf(pathname);
  const [shown, setShown] = useState(current);
  if (current >= 0 && current !== shown) setShown(current);
  const pill = useRef<HTMLLIElement>(null);
  const settled = useRef(shown);

  useLayoutEffect(() => {
    const el = pill.current;
    const from = settled.current;
    settled.current = shown;
    if (!el || from < 0 || shown < 0 || from === shown || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    // The new position is already committed: start from the old tab, or from wherever the pill is
    // mid-flight, so a second click turns it around rather than restarting it.
    const flying = el.getAnimations();
    const y = flying.length ? new DOMMatrix(getComputedStyle(el).transform).m42 : from * TAB_STEP;
    flying.forEach((a) => a.cancel());
    el.animate(pillFrames(y, shown * TAB_STEP, TAB_STEP), { duration: SPRING_MS });
  }, [shown]);

  return (
    <ul className={`${list} relative`}>
      {shown >= 0 && (
        <li
          ref={pill}
          className="pointer-events-none absolute inset-x-0 top-0 h-9 rounded-nav bg-key/14"
          style={{ transform: `translateY(${shown * TAB_STEP}px)` }}
          aria-hidden="true"
        />
      )}
      {TABS.map(({ to, label, Icon }, index) => {
        const selected = index === shown;
        return (
          <li key={to}>
            <SidebarLink
              to={to}
              className={`relative flex h-9 items-center gap-2 mb-0.5 p-1 rounded-nav type-body-reduced transition-colors duration-200 hover:bg-quinary ${selected ? 'text-key' : 'text-on-surface-variant'}`}
              onClick={() => {
                // Search tapped on Search starts a new search (MainActivity onTabSelected).
                if (to === '/search' && pathname === '/search') window.dispatchEvent(new Event(SEARCH_RESET_EVENT));
                onNavigate();
              }}
            >
              <Icon size={24} className={`flex-none transition-[scale] duration-[360ms] motion-reduce:transition-none ${SPRING_EASE} ${selected ? 'scale-108' : ''}`} />
              <span className={navLabel}>{label}</span>
            </SidebarLink>
          </li>
        );
      })}
    </ul>
  );
}

/** The account's playlists (report §1), once YouTube requests are made as the user. */
function LibraryNav({ onNavigate }: { onNavigate: () => void }) {
  const playlists = useLibraryPlaylists();
  return (
    <>
      <h2 className={sectionHeader}>Playlists</h2>
      <ul className={list}>
        <li>
          <SidebarLink to="/library/playlists" className={item} onClick={onNavigate}>
            <AllPlaylistsIcon size={24} className={icon} />
            <span className={navLabel}>{LIBRARY_SECTIONS.playlists.title}</span>
          </SidebarLink>
        </li>
        {playlists.data?.map((playlist) => (
          <li key={playlist.id}>
            <SidebarLink to={hrefFor(playlist) ?? '/library/playlists'} className={item} onClick={onNavigate}>
              <span className={icon}>
                <ListMusic size={17} strokeWidth={1.8} />
              </span>
              <span className={navLabel}>{playlist.title}</span>
            </SidebarLink>
          </li>
        ))}
      </ul>
    </>
  );
}

/** Spotify's playlists, once the extension can read them as the user (prompt §7.1). */
function SpotifyNav({ onNavigate }: { onNavigate: () => void }) {
  const playlists = useSpotifyPlaylists();
  return (
    <>
      <h2 className={sectionHeader}>Spotify</h2>
      <ul className={list}>
        <li>
          <SidebarLink to="/spotify" end className={item} onClick={onNavigate}>
            <AllPlaylistsIcon size={24} className={icon} />
            <span className={navLabel}>All Spotify Playlists</span>
          </SidebarLink>
        </li>
        {playlists.data?.map((playlist) => (
          <li key={playlist.id}>
            <SidebarLink to={`/spotify/playlist/${encodeURIComponent(playlist.id)}`} className={item} onClick={onNavigate}>
              <span className={icon}>
                <ListMusic size={17} strokeWidth={1.8} />
              </span>
              <span className={navLabel}>{playlist.name}</span>
            </SidebarLink>
          </li>
        ))}
      </ul>
    </>
  );
}

/** The left glass sidebar (report §1). */
export function Sidebar() {
  const drawerOpen = useUi((s) => s.drawerOpen);
  const setDrawerOpen = useUi((s) => s.setDrawerOpen);
  const update = useExtensionUpdate();
  const signedIn = useSignedIn();
  const spotifySignedIn = useSpotifySignedIn();
  const close = () => setDrawerOpen(false);

  return (
    <>
      {/* Below 484px the sidebar is a drawer opened from the top bar's menu button. */}
      <nav
        className="absolute inset-y-0 left-0 z-(--z-web-chrome) flex w-(--web-navigation-width) flex-col glass-material max-sm:w-[min(300px,82vw)] max-sm:-translate-x-full max-sm:shadow-glass max-sm:transition-transform max-sm:duration-(--duration-panel) max-sm:ease-panel max-sm:data-[open=true]:translate-none"
        data-open={drawerOpen}
        aria-label="Main"
      >
        <div className="mt-2 ml-2 flex h-18 items-center px-5">
          <PrefetchLink to="/home" className="inline-flex items-center gap-2 rounded-[6px]" aria-label="BitChord, Home" onClick={close}>
            {/* White on dark, as Apple's own wordmark; it follows the label colour so it stays visible in light. */}
            <LogoMark size={22} className="text-primary" />
            <span className="type-[700_19px/24px] tracking-[-0.01em]">BitChord</span>
          </PrefetchLink>
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto pr-3 pl-5 [scrollbar-width:none]">
          <TabList onNavigate={close} />
          {signedIn && <LibraryNav onNavigate={close} />}
          {spotifySignedIn && <SpotifyNav onNavigate={close} />}
        </div>
        <div className="flex flex-col gap-2 pt-3 pr-3 pb-5 pl-5">
          <SidebarLink to="/extension" className={item} onClick={close}>
            {/* Chrome's own toolbar glyph for extensions (Material "extension"). */}
            <span className={icon}>
              <MdOutlineExtension size={18} />
            </span>
            <span className={navLabel}>BitChord Extension</span>
            {update.state === 'available' && (
              <span className="ml-auto rounded-[10px] bg-key px-[7px] py-0.5 type-subhead-emphasized text-on-key" title={`Version ${update.latest.version} is available`}>
                Update
              </span>
            )}
          </SidebarLink>
          <div className="flex items-center justify-between">
            <a className="inline-flex items-center gap-1 ps-1 type-callout text-primary hover:underline" href={config.androidAppUrl} target="_blank" rel="noreferrer">
              <span>Get the Android app</span>
              <ArrowUpRight size={14} strokeWidth={2} />
            </a>
            <SidebarLink to="/settings" className="grid size-7 place-items-center rounded-nav text-secondary hover:bg-quinary hover:text-primary aria-[current=page]:bg-quinary aria-[current=page]:text-primary" aria-label="Settings" title="Settings" onClick={close}>
              <Settings size={17} strokeWidth={1.9} />
            </SidebarLink>
          </div>
        </div>
      </nav>
      {drawerOpen && <div className="absolute inset-0 z-[calc(var(--z-web-chrome)-1)] hidden bg-scrim max-sm:block" onClick={close} aria-hidden="true" />}
    </>
  );
}
