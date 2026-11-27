import { ChevronLeft } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { GlassDisc } from '@/components/ui/GlassDisc';
import { AccountButton } from './AccountButton';
import { tabOf } from './tabs';

/** Typing in a field shouldn't go back. */
function typing(target: EventTarget | null): boolean {
  const element = target as HTMLElement | null;
  return !!element && (element.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(element.tagName));
}

/**
 * Back: the page this one was pushed from, or Home when it was opened
 * directly and there is nothing in this app to go back to. Returns false when
 * it did nothing (a tab, opened directly), so the browser can have the gesture.
 */
function useGoBack() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  return () => {
    if (((window.history.state as { idx?: number } | null)?.idx ?? 0) > 0) void navigate(-1);
    else if (tabOf(pathname) < 0) void navigate('/home', { viewTransition: true });
    else return false;
    return true;
  };
}

/** Alt+←, Backspace outside a field, and the mouse's back button, as a desktop app takes them. */
function useBackShortcuts(goBack: () => boolean) {
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.defaultPrevented || typing(event.target)) return;
      const alt = event.altKey && event.key === 'ArrowLeft' && !event.ctrlKey && !event.metaKey && !event.shiftKey;
      const backspace = event.key === 'Backspace' && !event.altKey && !event.ctrlKey && !event.metaKey;
      if ((alt || backspace) && goBack()) event.preventDefault();
    };
    // The browser goes back on this button's mouseup unless it is cancelled there.
    const onMouseUp = (event: MouseEvent) => {
      if (event.button === 3 && goBack()) event.preventDefault();
    };
    window.addEventListener('keydown', onKey);
    window.addEventListener('mouseup', onMouseUp);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('mouseup', onMouseUp);
    };
  }, [goBack]);
}

/** True once the page under the bar has scrolled at all. */
function useScrolled(): boolean {
  const [scrolled, setScrolled] = useState(false);
  const { pathname } = useLocation();
  useEffect(() => {
    const page = document.getElementById('scrollable-page');
    if (!page) return;
    const update = () => setScrolled(page.scrollTop > 0);
    update();
    page.addEventListener('scroll', update, { passive: true });
    return () => page.removeEventListener('scroll', update);
  }, [pathname]);
  return scrolled;
}

/**
 * The phone's top bar (app/.../ui/components/FrostedTopBar.kt), floating over
 * the page: transparent and 52px tall, a 44px glass back circle at the leading
 * end of a pushed page, the account in a glass circle at the trailing end,
 * both on the page's gutter. Sticky at the top of the scrolling page, taking
 * no room in it.
 *
 * Under it, once the page scrolls, is TopFadeBlur: whatever passes beneath is
 * frosted, hardest at the top and easing out 16px below the bar (FADE_RUN),
 * with no tint, so it reads as the top edge of the window rather than a band.
 * Before it scrolls there is nothing under it to frost but the page's heading.
 */
export function TopBar() {
  const { pathname } = useLocation();
  const goBack = useGoBack();
  useBackShortcuts(goBack);
  const scrolled = useScrolled();

  return (
    <header className="pointer-events-none sticky top-0 z-[calc(var(--z-default)+10)] -mb-13 flex h-13 items-center justify-between px-(--body-gutter) max-sm:hidden">
      <div
        className="absolute inset-x-0 top-0 h-[68px] backdrop-blur-[24px] transition-opacity duration-200 [mask-image:linear-gradient(#000_60%,#0000)] data-[scrolled=false]:opacity-0"
        data-scrolled={scrolled}
        aria-hidden="true"
      />
      <div className="pointer-events-auto relative">
        {tabOf(pathname) < 0 && (
          <GlassDisc variant="back" label="Back" title="Back (Alt+←)" onClick={goBack}>
            <ChevronLeft size={26} strokeWidth={2.6} className="-translate-x-px" />
          </GlassDisc>
        )}
      </div>
      <div className="pointer-events-auto relative">
        <AccountButton />
      </div>
    </header>
  );
}
