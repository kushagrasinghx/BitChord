import { lazy, Suspense, useEffect, useLayoutEffect, useRef } from 'react';
import { resumeSession } from '@/party/listenTogether';
import { startPartySync } from '@/party/partySync';
import { useDiscordPresence } from '@/playback/discordPresence';
import { startHistoryReporting } from '@/playback/historyReporting';
import { startLivePresence } from '@/playback/livePresence';
import { startListeningRecorder } from '@/replay/recorder';
import { Outlet, useLocation } from 'react-router';
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
  const { pathname } = useLocation();
  const content = usePageFadeFallback(pathname);
  // A pushed page starts below the bar, so its back button never sits on its heading.
  const clearTopBar = tabOf(pathname) < 0 && !FULL_BLEED.test(pathname);

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
        <main
          id="scrollable-page"
          className="absolute inset-0 overflow-x-hidden overflow-y-auto pl-(--web-navigation-width) sm:[--top-bar-trailing-width:56px] [scrollbar-gutter:stable] data-tint:bg-(--page-tint) data-tint:transition-[background-color] data-tint:duration-300 data-tint:ease-in max-sm:pt-(--mobile-top-bar-height) max-sm:pl-0"
        >
          <TopBar />
          {/* Clears the floating pill: its height, its offset, and some air. */}
          <div ref={content} className={`min-h-full pb-[calc(var(--player-pill-height)+var(--player-bottom-offset)+40px)] ${clearTopBar ? 'sm:pt-13' : ''}`}>
            <Outlet />
          </div>
        </main>
        <PlayerBar />
        <SidePanel />
      </div>
      {banner}
      {fullScreen && (
        <Suspense fallback={null}>
          <FullScreenPlayer />
        </Suspense>
      )}
      <Onboarding />
    </div>
  );
}
