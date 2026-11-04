import { useEffect, useRef, useState, type ReactNode } from 'react';
import { MARQUEE, marqueePlan } from './marqueePlan';

/**
 * Text that scrolls when it's too long for its space, as Apple Music's
 * now-playing title does: it holds, scrolls left with a copy coming in behind
 * it, and the copy stops where the title began, so it loops without jumping
 * back. Text that fits stays still, hovering stops it (it's often a link),
 * and Reduce Motion keeps it still with the end faded. [watch] restarts it
 * from the start (a new song).
 */
export function Marquee({ children, watch }: { children: ReactNode; watch: unknown }) {
  const outer = useRef<HTMLSpanElement>(null);
  const track = useRef<HTMLSpanElement>(null);
  const text = useRef<HTMLSpanElement>(null);
  const [size, setSize] = useState({ text: 0, box: 0 });
  const [hovered, setHovered] = useState(false);

  useEffect(() => {
    const box = outer.current;
    const title = text.current;
    if (!box || !title) return;
    const measure = () => setSize({ text: Math.ceil(title.scrollWidth), box: box.clientWidth });
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(box);
    observer.observe(title);
    return () => observer.disconnect();
  }, [watch]);

  const plan = marqueePlan(size.text, size.box);
  useEffect(() => {
    const moving = track.current;
    const loop = marqueePlan(size.text, size.box);
    if (!moving || !loop || hovered || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const animation = moving.animate(loop.keyframes, { duration: loop.durationMs, iterations: Infinity, easing: 'linear' });
    return () => animation.cancel();
  }, [size.text, size.box, hovered, watch]);

  return (
    <span
      ref={outer}
      className="block min-w-0 overflow-hidden whitespace-nowrap"
      // Faded at the edge it scrolls in from, instead of an ellipsis that would scroll along.
      style={plan ? { maskImage: 'linear-gradient(to right, #000 calc(100% - 18px), transparent)' } : undefined}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      <span ref={track} className="inline-flex">
        <span ref={text}>{children}</span>
        {plan && (
          // The copy that follows the title in; read once, by the title.
          <span aria-hidden="true" style={{ paddingInlineStart: MARQUEE.gapPx }}>
            {children}
          </span>
        )}
      </span>
    </span>
  );
}
