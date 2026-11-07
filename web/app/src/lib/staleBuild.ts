/**
 * After a deploy, a tab opened before it still asks for the old build's page
 * chunks, which are gone: the import fails and the page can't render. The fix
 * is to load the new build, once.
 */

const STALE_MESSAGES = [
  'Failed to fetch dynamically imported module', // Chromium
  'error loading dynamically imported module', // Firefox
  'Importing a module script failed', // Safari
  'Unable to preload CSS', // Vite's own preload
];

/** Whether [error] is a chunk from an older build that's no longer there. */
export function isStaleBuildError(error: unknown): boolean {
  const message = error instanceof Error ? `${error.name} ${error.message}` : String(error ?? '');
  return error instanceof Error && error.name === 'ChunkLoadError' ? true : STALE_MESSAGES.some((m) => message.includes(m));
}

const KEY = 'bitchord:stale-build-reload';
/** A reload this recent didn't help (the chunk really is missing): don't loop. */
const AGAIN_AFTER_MS = 30_000;

/** Reloads the page to pick up the new build; false when it just tried, so the caller shows an error instead. */
export function reloadForNewBuild(now = Date.now(), storage: Pick<Storage, 'getItem' | 'setItem'> = sessionStorage, reload = () => location.reload()): boolean {
  const last = Number(storage.getItem(KEY) ?? 0);
  if (now - last < AGAIN_AFTER_MS) return false;
  storage.setItem(KEY, String(now));
  reload();
  return true;
}
