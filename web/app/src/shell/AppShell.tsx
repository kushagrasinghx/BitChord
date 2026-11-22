import { lazy, Suspense, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { resumeSession } from '@/party/listenTogether';
import { startPartySync } from '@/party/partySync';
import { useDiscordPresence } from '@/playback/discordPresence';
import { startHistoryReporting } from '@/playback/historyReporting';
import { startLivePresence } from '@/playback/livePresence';
import { startListeningRecorder } from '@/replay/recorder';
import { Outlet, useLocation, useNavigationType } from 'react-router';
import { PrefetchLink } from '@/components/PrefetchLink';
import { config } from '@/config';
import { useExtension } from '@/extension/useExtension';
import { isMobileBrowser } from '@/lib/platform';
import { useUi } from '@/state/ui';
import { Onboarding } from '@/onboarding/Onboarding';
import { useKeyboardShortcuts } from './useKeyboardShortcuts';
import { Banner, bannerButtonClass } from './Banner';
import { MobileTopBar } from './MobileTopBar';
import { PlayerBar } from './PlayerBar';
import { SidePanel } from './SidePanel';
import { Sidebar } from './Sidebar';
import { tabOf } from './tabs';
import { TopBar } from './TopBar';
import './pageTransition.css';

const mobile = isMobileBrowser();

// Artwork-led pages run their art up under the top bar, as the phone's do (FrostedTopBar's floating chrome).
const FULL_BLEED = /^\/artist\//;

/**
 * Where the browser has no view transitions, a pushed or popped page fades in
 * over the same 180ms (pageTransition.css); tab to tab stays instant.
 */
function usePageFadeFallback(pathname: string) {
  const content = useRef<HTMLDivElement>(null);
  const previous = useRef(pathname);
  useLayoutEffect(() => {
    const from = previous.current;
    previous.current = pathname;
    if (from === pathname || 'startViewTransition' in document || (tabOf(from) >= 0 && tabOf(pathname) >= 0)) return;
    content.current?.animate({ opacity: [0, 1] }, { duration: 180, easing: 'cubic-bezier(0.4, 0, 0.2, 1)' });
  }, [pathname]);
  return content;
}

const scrollOf = new Map<string, number>();
const tabScroll: number[] = [];

/**
 * One scrolling page serves every tab, so each place keeps its own scroll:
 * Back returns to where that page was left, a tab to where it was left (the
 * phone keeps a scroll state per tab), and anything newly pushed starts at the top.
 */
function useScrollMemory() {
  const location = useLocation();
  const type = useNavigationType();
  const page = useRef<HTMLElement>(null);
  useLayoutEffect(() => {
    const el = page.current;
    if (!el) return;
    const tab = tabOf(location.pathname);
    const target = type === 'POP' ? (scrollOf.get(location.key) ?? 0) : tab >= 0 ? (tabScroll[tab] ?? 0) : 0;
    el.scrollTop = target;    // A page drawn from cache can lay out a frame or two late; finish the jump once it is tall enough.
    let frames = 0;
    let raf = 0;
    const settle = () => {
      if (el.scrollHeight - el.clientHeight >= target) el.scrollTop = target;
      else if (frames++ < 30) raf = requestAnimationFrame(settle);
    };
    if (el.scrollTop !== target) raf = requestAnimationFrame(settle);
    const save = () => {
      scrollOf.set(location.key, el.scrollTop);
      if (tab >= 0) tabScroll[tab] = el.scrollTop;
    };
    el.addEventListener('scroll', save, { passive: true });
    return () => {
      cancelAnimationFrame(raf);
      el.removeEventListener('scroll', save);
    };
    // Once per history entry: the navigation type belongs to the same change.
  }, [location.key]);

  return page;
}

// It carries am-lyrics; loaded the first time it opens, as the side panel's lyrics are.
const FullScreenPlayer = lazy(() => import('./FullScreenPlayer').then((m) => ({ default: m.FullScreenPlayer })));

/**
 * The app frame (report §1): glass sidebar, the scrolling page, the floating
 * player pill, the side panel, and the bottom band when there is one.
 */
export function AppShell() {
  useKeyboardShortcuts();
  useDiscordPresence();
  // Listen Together: pick a party back up after a reload, and keep the player in step with it.
  useEffect(() => {
    resumeSession();
    return startPartySync();
  }, []);
  // Plays into the account's YouTube Music history (Settings › Share Listening Activity).
  useEffect(() => startHistoryReporting(), []);
  // Counts this tab in the "listening now" figure beside the phone and PC apps.
  useEffect(() => startLivePresence(), []);
  // Counts what plays here for the Replay, as the phone's ListeningRecorder does.
  useEffect(() => startListeningRecorder(), []);
  const extension = useExtension();
  const bannerDismissed = useUi((s) => s.installBannerDismissed);
  const dismissBanner = useUi((s) => s.dismissInstallBanner);
  const fullScreen = useUi((s) => s.fullScreen);
  // From 1440px an open side panel takes its own column and the page narrows beside it; below, it floats over.
  const panelOpen = useUi((s) => s.sidePanel !== null) && !fullScreen;
  const page = useScrollMemory();
  const { pathname } = useLocation();
  const content = usePageFadeFallback(pathname);
  // A pushed page starts below the bar, so its back button never sits on its heading.
  const clearTopBar = tabOf(pathname) < 0 && !FULL_BLEED.test(pathname);
  // Kept mounted from its first open: the player slides itself away before it goes.
  const [playerLoaded, setPlayerLoaded] = useState(false);
  if (fullScreen && !playerLoaded) setPlayerLoaded(true);

  let banner = null;
  if (mobile) {
    banner = (
      <Banner
        title="BitChord is better in the app."
        detail="Phone browsers can’t run the BitChord extension. The Android app has everything."
        action={
          <a className={bannerButtonClass} href={config.androidAppUrl} target="_blank" rel="noreferrer">
            Get the App
          </a>
        }
      />
    );
  } else if (extension.state === 'missing' && !bannerDismissed) {
    banner = (
      <Banner
        title="Play YouTube Music with the BitChord extension."
        detail="Playback, YouTube Music sign-in, Spotify and Discord need the extension. Addons and lyrics work without it."
        action={
          <PrefetchLink className={bannerButtonClass} to="/extension">
            Get the Extension
          </PrefetchLink>
        }
        onDismiss={dismissBanner}
      />
    );
  }

  return (
    <div className="grid h-full grid-rows-[minmax(0,1fr)_auto]">
      {/* The side panel slides in from beyond the right edge; never let it scroll the page. */}
      <div className="relative min-h-0 overflow-hidden">
        <Sidebar />
        <MobileTopBar />
        {/* The page scrolls under the fixed sidebar and the floating player. --top-bar-trailing-width is the
            top bar's 44px account circle plus a 12px gap, which the Search field stops short of. */}
        {/* --top-bar-clearance is the room a pushed page leaves for the bar; a page's own backdrop can
            pull itself up by it to run under the bar (the Replay's tint does), so no seam shows below it. */}
        <main
          ref={page}
          id="scrollable-page"
          data-panel={panelOpen}
          className={`absolute inset-0 overflow-x-hidden overflow-y-auto pl-(--web-navigation-width) sm:[--top-bar-trailing-width:56px] [scrollbar-gutter:stable] data-tint:bg-(--page-tint) data-tint:transition-[background-color] data-tint:duration-300 data-tint:ease-in max-sm:pt-(--mobile-top-bar-height) max-sm:pl-0 min-[1440px]:data-[panel=true]:right-(--side-panel-width) ${clearTopBar ? 'sm:[--top-bar-clearance:52px]' : ''}`}
        >
          <TopBar />
          {/* Clears the floating pill: its height, its offset, and some air. */}
          <div ref={content} className="min-h-full pt-(--top-bar-clearance,0px) pb-[calc(var(--player-pill-height)+var(--player-bottom-offset)+40px)]">
            <Outlet />
          </div>
        </main>
        <PlayerBar />
        <SidePanel />
      </div>
      {banner}
      {playerLoaded && (
        <Suspense fallback={null}>
          <FullScreenPlayer />
        </Suspense>
      )}
      <Onboarding />
    </div>
  );
}
