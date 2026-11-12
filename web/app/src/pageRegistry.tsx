import { lazy, Suspense, type ComponentType, type ReactNode } from 'react';
import { matchPath } from 'react-router';
import { prefetchPageData } from '@/data/music/queries';
import {
  AlbumSkeleton,
  ArtistSkeleton,
  HomeSkeleton,
  NewSkeleton,
  PlainSkeleton,
  PlaylistSkeleton,
  SearchSkeleton,
} from '@/skeletons/PageSkeletons';

type PageModule = { default: ComponentType };

interface PageRoute {
  path: string;
  load: () => Promise<PageModule>;
  skeleton: ReactNode;
}

/**
 * Every page, lazy-loaded behind its own skeleton (prompt §3). `load` is kept
 * so links can warm a route's chunk on hover or focus, before the click.
 */
export const pages: PageRoute[] = [
  { path: '/home', load: () => import('@/pages/HomePage'), skeleton: <HomeSkeleton /> },
  { path: '/new', load: () => import('@/pages/NewPage'), skeleton: <NewSkeleton /> },
  { path: '/search', load: () => import('@/pages/SearchPage'), skeleton: <SearchSkeleton /> },
  { path: '/album/:id', load: () => import('@/pages/AlbumPage'), skeleton: <AlbumSkeleton /> },
  { path: '/artist/:id', load: () => import('@/pages/ArtistPage'), skeleton: <ArtistSkeleton /> },
  { path: '/playlist/:id', load: () => import('@/pages/PlaylistPage'), skeleton: <PlaylistSkeleton /> },
  { path: '/browse/:id', load: () => import('@/pages/BrowsePage'), skeleton: <PlainSkeleton /> },
  { path: '/library/:section', load: () => import('@/pages/LibraryPage'), skeleton: <PlainSkeleton /> },
  { path: '/spotify', load: () => import('@/pages/SpotifyPage'), skeleton: <PlainSkeleton /> },
  { path: '/spotify/playlist/:id', load: () => import('@/pages/SpotifyPlaylistPage'), skeleton: <PlaylistSkeleton /> },
  { path: '/settings', load: () => import('@/pages/SettingsPage'), skeleton: <PlainSkeleton /> },
  { path: '/settings/discord', load: () => import('@/pages/DiscordSettingsPage'), skeleton: <PlainSkeleton /> },
  { path: '/settings/listen-together', load: () => import('@/pages/ListenTogetherPage'), skeleton: <PlainSkeleton /> },
  { path: '/invite/:code', load: () => import('@/pages/ListenTogetherPage'), skeleton: <PlainSkeleton /> },
  { path: '/extension', load: () => import('@/pages/ExtensionPage'), skeleton: <PlainSkeleton /> },
  { path: '/debug', load: () => import('@/pages/DebugPage'), skeleton: <PlainSkeleton /> },
];

export const notFound: PageRoute = { path: '*', load: () => import('@/pages/NotFoundPage'), skeleton: <PlainSkeleton /> };

export function pageElement({ load, skeleton }: PageRoute) {
  const Page = lazy(load);
  return (
    <Suspense fallback={skeleton}>
      <Page />
    </Suspense>
  );
}

/** Warms the chunk, and the first query, for whatever page `href` leads to. Safe to call repeatedly. */
export function preloadRoute(href: string): void {
  const pathname = href.split(/[?#]/)[0] ?? href;
  const page = pages.find((p) => matchPath(p.path, pathname));
  void page?.load().catch(() => {
    // A failed prefetch is retried by the real navigation.
  });
  prefetchPageData(pathname);
}
