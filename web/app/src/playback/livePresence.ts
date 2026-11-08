import { config } from '@/config';

/**
 * Tells the BitChord server this tab is open, so it can count the web player
 * beside the phone and PC apps (`GET /api/stats/live`), as the apps do
 * (Presence.kt). One small POST every few minutes while the tab is open; the
 * server sets the interval in each reply. Closing the tab sends a
 * close so the count drops straight away; if that is lost, the server forgets
 * the tab a minute or two after its last ping.
 *
 * Only a random per-browser UUID and the platform are sent, never anything
 * about the listener or what they play.
 */

const ID_KEY = 'bitchord:presence-id';
const DEFAULT_INTERVAL_SEC = 300;
const MIN_INTERVAL_SEC = 60;
const MAX_INTERVAL_SEC = 3600;
const CLOSE_DEBOUNCE_MS = 2_000;

function installId(): string {
  try {
    const held = localStorage.getItem(ID_KEY);
    if (held) return held;
    const fresh = crypto.randomUUID();
    localStorage.setItem(ID_KEY, fresh);
    return fresh;
  } catch {
    // Storage blocked: an id per page load still counts this tab once.
    return crypto.randomUUID();
  }
}

export function startLivePresence(): () => void {
  const server = config.partyServerUrl.replace(/\/+$/, '');
  if (!server) return () => {};
  const endpoint = `${server}/api/presence`;
  const id = installId();
  let intervalSec = DEFAULT_INTERVAL_SEC;
  let announced = false;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let closeTimer: ReturnType<typeof setTimeout> | undefined;

  const send = async (open: boolean) => {
    // Set before sending: a ping that times out may still have been counted, so it still needs a close.
    announced = open;
    try {
      // keepalive lets the close outlive the page.
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id, platform: 'browser', open }),
        keepalive: true,
      });
      if (!response.ok) return;
      const reply = (await response.json()) as { intervalSec?: unknown };
      if (typeof reply.intervalSec === 'number') intervalSec = reply.intervalSec;
    } catch {
      // Offline or the server is down. The next ping is soon enough.
    }
  };

  const loop = async () => {
    await send(true);
    const base = Math.min(Math.max(intervalSec, MIN_INTERVAL_SEC), MAX_INTERVAL_SEC) * 1000;
    // ±10%, so a release that opens many tabs at once spreads out.
    timer = setTimeout(loop, base + (Math.random() * 0.2 - 0.1) * base);
  };

  const setOpen = (open: boolean) => {
    clearTimeout(timer);
    clearTimeout(closeTimer);
    timer = closeTimer = undefined;
    if (open) void loop();
    // A tab switch flickering is not a close.
    else if (announced) closeTimer = setTimeout(() => void send(false), CLOSE_DEBOUNCE_MS);
  };

  // Not tied to visibility: a background tab that is playing music is still listening.
  const onPageHide = () => {
    clearTimeout(timer);
    clearTimeout(closeTimer);
    if (announced) void send(false);
  };
  const onPageShow = () => {
    if (!announced) setOpen(true);
  };

  setOpen(true);
  window.addEventListener('pagehide', onPageHide);
  window.addEventListener('pageshow', onPageShow);
  return () => {
    window.removeEventListener('pagehide', onPageHide);
    window.removeEventListener('pageshow', onPageShow);
    onPageHide();
  };
}
