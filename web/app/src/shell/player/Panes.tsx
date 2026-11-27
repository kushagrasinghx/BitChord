import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useReducedMotion } from '@/data/spotify/canvas';
import type { Pane } from './ActionRow';

/** LandscapePlayer.kt LANDSCAPE_PANE_FADE_*: the new page fades in over 220ms after 90, the old out over 140. */
const IN_MS = 220;
const IN_DELAY_MS = 90;
const OUT_MS = 140;

function Page({ leaving, entering, className, children }: { leaving: boolean; entering: boolean; className: string; children: ReactNode }) {
  const el = useRef<HTMLDivElement>(null);
  const still = useReducedMotion();
  useEffect(() => {
    if (still || !el.current) return;
    const animation = leaving
      ? el.current.animate([{ opacity: 1 }, { opacity: 0 }], { duration: OUT_MS, fill: 'forwards' })
      : entering
        ? el.current.animate([{ opacity: 0 }, { opacity: 1 }], { duration: IN_MS, delay: IN_DELAY_MS, fill: 'backwards' })
        : null;
    return () => animation?.cancel();
  }, [leaving, entering, still]);
  return (
    <div ref={el} className={`[grid-area:1/1] ${className}`} inert={leaving}>
      {children}
    </div>
  );
}

/**
 * The right column's three pages, main, lyrics and queue, turned by a
 * crossfade (LandscapePlayer.kt's AnimatedContent): the left column never
 * moves, so the page turning is the whole transition. The page going away
 * stays drawn under the new one until it has faded.
 */
export function Panes({ pane, className, render }: { pane: Pane; className: (pane: Pane) => string; render: (pane: Pane) => ReactNode }) {
  const [shown, setShown] = useState(pane);
  const [leaving, setLeaving] = useState<Pane | null>(null);
  const [turned, setTurned] = useState(false);
  if (pane !== shown) {
    setLeaving(shown);
    setShown(pane);
    setTurned(true);
  }
  useEffect(() => {
    if (!leaving) return;
    const timer = setTimeout(() => setLeaving(null), OUT_MS);
    return () => clearTimeout(timer);
  }, [leaving]);

  return (
    <div className="grid min-h-0 min-w-0 flex-1 grid-cols-1 grid-rows-1">
      {leaving && leaving !== shown && (
        <Page key={leaving} leaving entering={false} className={className(leaving)}>
          {render(leaving)}
        </Page>
      )}
      <Page key={shown} leaving={false} entering={turned} className={className(shown)}>
        {render(shown)}
      </Page>
    </div>
  );
}
