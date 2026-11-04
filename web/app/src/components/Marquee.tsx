import { useEffect, useRef, useState, type ReactNode } from 'react';
import { marqueePlan } from './marqueePlan';

/**
 * Text that scrolls when it's too long for its space, as Spotify's and Apple
 * Music's now-playing titles do: the start for a few seconds, a slow scroll to
 * the end, a pause, then back, again and again. Text that fits stays still,
 * hovering stops it (it's often a link), and Reduce Motion keeps it still
 * with the end faded. [watch] restarts it from the start (a new song).
 */
export function Marquee({ children, watch }: { children: ReactNode; watch: unknown }) {
  const outer = useRef<HTMLSpanElement>(null);
  const inner = useRef<HTMLSpanElement>(null);
  const [overflow, setOverflow] = useState(0);
  const [hovered, setHovered] = useState(false);

  useEffect(() => {
    const box = outer.current;
    const text = inner.current;
    if (!box || !text) return;
    const measure = () => setOverflow(Math.max(0, Math.ceil(text.scrollWidth - box.clientWidth)));
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(box);
    observer.observe(text);
    return () => observer.disconnect();
  }, [watch]);

  useEffect(() => {
    const text = inner.current;
    const plan = marqueePlan(overflow);
    if (!text || !plan || hovered || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const animation = text.animate(plan.keyframes, { duration: plan.durationMs, iterations: Infinity, easing: 'linear' });
    return () => animation.cancel();
  }, [overflow, hovered, watch]);

  return (
    <span
      ref={outer}
      className="block min-w-0 overflow-hidden whitespace-nowrap"
      // Faded at the edge it scrolls out of, instead of an ellipsis that would scroll along.
      style={overflow ? { maskImage: 'linear-gradient(to right, #000 calc(100% - 18px), transparent)' } : undefined}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      <span ref={inner} className="inline-block">
        {children}
      </span>
    </span>
  );
}
