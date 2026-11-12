import { useEffect } from 'react';
import type { ArtworkColor } from './artworkColor';

/**
 * Tints the whole scrolling page with the artwork's colour while a detail
 * page is open, as Apple's album and playlist pages do (report §3,
 * `has-theme-override`). The text colours that sit on it are switched in
 * tokens.css by `data-tint`.
 */
export function usePageTint(color: ArtworkColor | null) {
  useEffect(() => {
    const page = document.getElementById('scrollable-page');
    if (!page || !color) return;
    page.style.setProperty('--page-tint', color.background);
    page.dataset.tint = color.dark ? 'dark' : 'light';
    return () => {
      page.style.removeProperty('--page-tint');
      delete page.dataset.tint;
    };
  }, [color]);
}
