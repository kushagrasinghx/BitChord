import { useEffect, useRef } from 'react';

/**
 * Asks for the next page when the returned element scrolls within 600px of
 * view, as the phone's lists read on a few rows before their end. Pass null
 * while there is no next page, or one is already on its way.
 */
export function useLoadMore(onVisible: (() => void) | null) {
  const target = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const node = target.current;
    if (!node || !onVisible) return;
    const observer = new IntersectionObserver((entries) => entries.some((e) => e.isIntersecting) && onVisible(), {
      rootMargin: '600px 0px',
    });
    observer.observe(node);
    return () => observer.disconnect();
  }, [onVisible]);
  return target;
}
