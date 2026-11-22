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

/** Android's Search tab tap clears the field and focuses it (MainActivity onTabSelected); the Search field listens. */
export { SEARCH_RESET_EVENT } from '@/components/music/searchReset';
