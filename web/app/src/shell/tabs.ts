import type { Location } from 'react-router';
import { ExploreTabIcon, HomeTabIcon, LibraryTabIcon, RadioTabIcon, SearchTabIcon } from '@/components/icons/TabIcons';

/** The phone's tabs, in its order and with its names (MainActivity's BottomTab list). */
export const TABS = [
  { to: '/home', label: 'Home', Icon: HomeTabIcon },
  { to: '/explore', label: 'Explore', Icon: ExploreTabIcon },
  { to: '/radio', label: 'Radio', Icon: RadioTabIcon },
  { to: '/library', label: 'Library', Icon: LibraryTabIcon },
  { to: '/search', label: 'Search', Icon: SearchTabIcon },
] as const;

/**
 * The tab a path is the root of, or -1 for a page pushed over one (an album,
 * Settings, a library section). Only roots go without a back button, as
 * FrostedTopBar's onBack is null only on them.
 */
export function tabOf(pathname: string): number {
  return TABS.findIndex((tab) => tab.to === pathname);
}

// Pages that belong to no tab: the sidebar's own entries for them light instead.
const TABLESS = /^\/(settings|replay|extension)(\/|$)/;
const origins = new Map<string, number>();
let previous = -1;

/**
 * The tab the sidebar lights for a location: its own, or for a pushed page
 * the tab it was pushed from, as the phone's bar stays on the tab a page was
 * opened over. None for Settings, the Replay, the extension page, anything
 * opened from the sidebar's own rows (state `{ sidebar: true }`) or opened
 * directly, and whatever is pushed on from those. Remembered per history
 * entry, so Back lights the tab it lit before.
 */
export function originTab({ pathname, key, state }: Location): number {
  let tab = origins.get(key);
  if (tab === undefined) {
    const own = tabOf(pathname);
    const sidebar = (state as { sidebar?: boolean } | null)?.sidebar;
    tab = own >= 0 ? own : TABLESS.test(pathname) || sidebar ? -1 : previous;
    origins.set(key, tab);
  }
  previous = tab;
  return tab;
}

/** Android's Search tab tap clears the field and focuses it (MainActivity onTabSelected); the Search field listens. */
export { SEARCH_RESET_EVENT } from '@/components/music/searchReset';
