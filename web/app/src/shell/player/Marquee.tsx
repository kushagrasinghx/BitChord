import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { useReducedMotion } from '@/data/spotify/canvas';

/** PlayerControls.kt MARQUEE_DP_PER_SEC: unhurried, not a ticker. */
const PX_PER_SEC = 26;
/** MARQUEE_GAP: clear air between the line's tail and the copy chasing it round. */
const GAP = 48;
/** MARQUEE_REST_MS: how long a line sits back at its start between passes. */
const REST_MS = 5000;
/** SongText.kt LINE_FADE: how far the line fades out at an edge it runs past. */
const FADE = 36;

// The start fade is animated in step with the crawl, which needs it to be a typed, interpolable property.
let registered = false;
function registerFade() {
  if (registered) return;
  registered = true;
  try {
    CSS.registerProperty({ name: '--marquee-fade-start', syntax: '<length>', inherits: false, initialValue: '0px' });
  } catch {
    // Already registered (a hot reload), or not supported: the start edge just doesn't fade.
  }
}

/**
 * One line that crawls in place, and only when it is too long for its box
 * (PlayerControls.kt MarqueeText). The line is drawn twice, 48px apart, and
 * the pair slides left by one copy plus the gap at 26px/s, so the second copy
 * lands exactly where the first began and the reset can't be seen; then it
 * rests 5s. It always fades out at its end, and at its start too while it is
 * away from rest. Reduced motion holds it still, faded at its end.
 *
 * `leading` (the explicit badge) sits outside the crawl. `onCrawl` says
 * whether it is crawling, so the artist line can wait behind the title.
 */
export function Marquee({
  children,
  leading,
  delayMs = 0,
  onCrawl,
  className = '',
}: {
  children: ReactNode;
  leading?: ReactNode;
  delayMs?: number;
  onCrawl?: (crawling: boolean) => void;
  className?: string;
}) {
  const box = useRef<HTMLDivElement>(null);
  const line = useRef<HTMLDivElement>(null);
  const track = useRef<HTMLDivElement>(null);
  const still = useReducedMotion();
  const [width, setWidth] = useState<{ line: number; box: number } | null>(null);
  const overflows = !!width && width.line > width.box + 0.5;
  const crawls = overflows && !still;

  useLayoutEffect(() => {
    const measure = () => {
      if (box.current && line.current) setWidth({ line: line.current.scrollWidth, box: box.current.clientWidth });
    };
    measure();
    const observer = new ResizeObserver(measure);
    if (box.current) observer.observe(box.current);
    if (line.current) observer.observe(line.current);
    return () => observer.disconnect();
  }, []);

  useEffect(() => onCrawl?.(crawls), [crawls, onCrawl]);

  useEffect(() => {
    const el = track.current;
    const clip = box.current;
    if (!crawls || !el || !clip || !width) return;
    registerFade();
    const travel = width.line + GAP;
    const crawlMs = Math.max(400, (travel / PX_PER_SEC) * 1000);
    const total = crawlMs + REST_MS;
    const end = crawlMs / total;
    const fadeIn = Math.min(end / 2, (FADE / travel) * end);
    const timing = { duration: total, delay: delayMs, iterations: Infinity, easing: 'linear' } as const;
    const rtl = getComputedStyle(el).direction === 'rtl' ? -1 : 1;
    const slide = el.animate(
      [
        { transform: 'translateX(0)', offset: 0 },
        { transform: `translateX(${-travel * rtl}px)`, offset: end },
        { transform: 'translateX(0)', offset: end },
        { transform: 'translateX(0)', offset: 1 },
      ],
      timing,
    );
    const fade = clip.animate(
      [
        { '--marquee-fade-start': '0px', offset: 0 },
        { '--marquee-fade-start': `${FADE}px`, offset: fadeIn },
        { '--marquee-fade-start': `${FADE}px`, offset: end - fadeIn },
        { '--marquee-fade-start': '0px', offset: end },
        { '--marquee-fade-start': '0px', offset: 1 },
      ],
      timing,
    );
    return () => {
      slide.cancel();
      fade.cancel();
    };
  }, [crawls, width, delayMs]);

  return (
    <div className={`flex min-w-0 items-center ${className}`}>
      {leading && <span className="me-1.5 flex flex-none">{leading}</span>}
      <div
        ref={box}
        className="min-w-0 flex-1 overflow-hidden"
        style={
          overflows
            ? {
                maskImage: `linear-gradient(to right, transparent, #000 var(--marquee-fade-start, 0px), #000 calc(100% - ${FADE}px), transparent)`,
              }
            : undefined
        }
      >
        <div ref={track} className="flex w-max whitespace-nowrap">
          <div ref={line} className="w-max">
            {children}
          </div>
          {crawls && (
            // The copy chasing it round: seen, not read twice.
            <div className="w-max" style={{ marginInlineStart: GAP }} aria-hidden="true" inert>
              {children}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
