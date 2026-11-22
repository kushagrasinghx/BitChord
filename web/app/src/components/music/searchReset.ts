/**
 * Re-tapping the Search tab while on Search empties the field and puts the
 * cursor in it, ready for the next search (app/.../MainActivity.kt,
 * searchFocusRequested; SearchField.kt's clear does the same). The shell's
 * tab fires this; the field, when it is on screen, answers it. Its own small
 * module so the shell can fire it without loading the Search page.
 */
export const SEARCH_RESET_EVENT = 'bitchord:search-reset';

export function resetSearch(): void {
  window.dispatchEvent(new Event(SEARCH_RESET_EVENT));
}
