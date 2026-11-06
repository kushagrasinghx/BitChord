import { create } from 'zustand';
import { isMobileBrowser } from '@/lib/platform';

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
}
type WebInstallNavigator = Navigator & { install?: () => Promise<unknown> };
type LaunchWindow = Window & {
  launchQueue?: { setConsumer(consumer: (params: { targetURL?: string }) => void): void };
};

interface PwaState {
  /** A desktop browser that can install the site, and it isn't already in its own window. */
  canInstall: boolean;
  /** Set when a new build is waiting; calling it reloads into that build. */
  applyUpdate: (() => void) | null;
}

export const usePwa = create<PwaState>(() => ({ canInstall: false, applyUpdate: null }));

let installEvent: BeforeInstallPromptEvent | null = null;

/** Chrome 158+'s navigator.install(), else the captured beforeinstallprompt. */
export async function install(): Promise<void> {
  const nav = navigator as WebInstallNavigator;
  try {
    if (nav.install) await nav.install();
    else await installEvent?.prompt();
  } catch {
    // Dismissed (AbortError) or blocked by policy: the button stays for another try.
  }
}

/** Call once, before render. Does nothing in dev or where service workers don't exist. */
export function startPwa(navigate: (to: string) => void): void {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator)) return;

  // Playback needs the extension, which mobile browsers can't install, so
  // mobile keeps the Android app link and the browser's own install UI.
  const offerInstall = !isMobileBrowser() && !matchMedia('(display-mode: standalone)').matches;
  if (offerInstall) {
    if ('install' in navigator) usePwa.setState({ canInstall: true });
    window.addEventListener('beforeinstallprompt', (event) => {
      event.preventDefault();
      installEvent = event as BeforeInstallPromptEvent;
      usePwa.setState({ canInstall: true });
    });
    window.addEventListener('appinstalled', () => usePwa.setState({ canInstall: false }));
  }

  // launch_handler focus-existing: another launch or a shortcut focuses this
  // window (the song keeps playing) and lands here instead of opening a second player.
  (window as LaunchWindow).launchQueue?.setConsumer(({ targetURL }) => {
    if (!targetURL) return;
    const url = new URL(targetURL);
    const to = url.pathname + url.search + url.hash;
    if (to !== location.pathname + location.search + location.hash) navigate(to);
  });

  register().catch(() => {
    // The site works without a service worker (private modes, blocked storage).
  });
}

async function register(): Promise<void> {
  // Read before registering: a first install's clients.claim() also fires controllerchange.
  const hadController = !!navigator.serviceWorker.controller;
  const registration = await navigator.serviceWorker.register('/sw.js');
  let chosen = false;
  const offer = (worker: ServiceWorker) =>
    usePwa.setState({
      applyUpdate: () => {
        chosen = true;
        worker.postMessage('skip-waiting');
      },
    });

  // With no controller this is the first install, so there is nothing to update from.
  if (registration.waiting && navigator.serviceWorker.controller) offer(registration.waiting);
  registration.addEventListener('updatefound', () => {
    const worker = registration.installing;
    worker?.addEventListener('statechange', () => {
      if (worker.state === 'installed' && navigator.serviceWorker.controller) offer(worker);
    });
  });

  // The tab that chose the update reloads now; other tabs offer a plain reload.
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (chosen) location.reload();
    else if (hadController) usePwa.setState({ applyUpdate: () => location.reload() });
  });

  // A player tab can stay open for days; check for a deploy when it comes back into view.
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') registration.update().catch(() => {});
  });
}
