/**
 * Asks for a garbage collection now, where the runtime allows it: the desktop
 * app starts V8 with `--expose-gc` (web/desktop/src/main.ts); a browser has no
 * `gc`, so this does nothing there.
 *
 * For right after something large has been let go, such as a song's decoded
 * audio (~90 MB). A page that plays music allocates little else, so V8 can go
 * minutes before collecting it on its own, and until then the memory stays
 * with the app.
 */
export function collectGarbage(): void {
  (globalThis as { gc?: () => void }).gc?.();
}
