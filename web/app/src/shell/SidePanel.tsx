import { lazy, Suspense } from 'react';
import { useUi } from '@/state/ui';
import { UpNext } from './UpNext';

// am-lyrics is large; it loads the first time the panel opens (prompt §3).
const LyricsPanel = lazy(() => import('./LyricsPanel').then((m) => ({ default: m.LyricsPanel })));

/** Up Next / Lyrics glass panel (report §7). */
export function SidePanel() {
  const panel = useUi((s) => s.sidePanel);
  // Hidden under the full-screen player, which has its own lyrics: one am-lyrics at a time.
  const fullScreen = useUi((s) => s.fullScreen);
  if (!panel || fullScreen) return null;

  return (
    <aside
      // From 1440px the page narrows beside it (AppShell), so it is a column like the sidebar, not a sheet over the page.
      className="absolute inset-y-0 right-0 z-(--z-web-chrome) flex w-(--side-panel-width) animate-slide-in flex-col glass-material shadow-glass max-sm:w-full min-[1440px]:shadow-none"
      aria-label={panel === 'up-next' ? 'Up Next' : 'Lyrics'}
    >
      {panel === 'up-next' ? (
        <UpNext />
      ) : (
        <Suspense fallback={null}>
          <LyricsPanel />
        </Suspense>
      )}
    </aside>
  );
}
