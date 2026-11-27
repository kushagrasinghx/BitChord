import { Check } from 'lucide-react';
import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { MENU_MARGIN as MARGIN, placeMenu, type MenuPlacement } from './menuPlacement';

export type { MenuPlacement };

interface MenuProps {
  /** The element the menu opens from; focus goes back to it on Escape. */
  anchor: HTMLElement;
  placement?: MenuPlacement;
  /** Opens at this point instead (a right-click), growing from the corner nearest it. */
  point?: { x: number; y: number };
  /** The item a right-click lifts out of the page while its menu is up (HeldContextMenu.kt). */
  lift?: HTMLElement;
  onClose: () => void;
  label: string;
  /** At least as wide as the anchor (a pop-up button's menu). */
  matchWidth?: boolean;
  children: ReactNode;
}

/** IosMenu.kt IosMenuWidth: wide enough for a long label on one line or two. */
const MENU_WIDTH = 256;
/** A desktop menu's width, at the compact rows' 14px labels. */
const COMPACT_WIDTH = 240;

/**
 * A wide window driven by a mouse gets a desktop menu's density (32px rows,
 * 14px labels, the quick actions as a row of small buttons); a phone, a
 * tablet or a narrow window keeps the phone's 44px rows for a finger.
 */
const COMPACT_QUERY = '(min-width: 1000px) and (pointer: fine)';

/** IosMenu.kt's spring(DampingRatioNoBouncy, stiffness 600): critically damped, settled by ~320ms. */
const OPEN_EASE =
  'linear(0, 0.087, 0.257, 0.432, 0.583, 0.702, 0.792, 0.856, 0.902, 0.934, 0.956, 0.971, 0.981, 0.987, 0.992, 0.995, 1)';
/** HeldContextMenu.kt's lift, spring(dampingRatio 0.72, stiffness 420): a little past and back. */
const LIFT_EASE =
  'linear(0, 0.069, 0.223, 0.406, 0.582, 0.732, 0.848, 0.932, 0.987, 1.019, 1.034, 1.038, 1.036, 1.03, 1.023, 1.016, 1.011, 1.006, 1.003, 1.001, 0.999, 0.999, 1)';
/** Compose's FastOutSlowInEasing, which the menu closes on over 170ms. */
const CLOSE_EASE = 'cubic-bezier(0.4, 0, 0.2, 1)';

const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/*
 * IosMenu.kt's panel colours, dark (DarkMenu with blend effects off, as a
 * page has no additive blending) and light (LightMenu); color-scheme follows
 * the Theme setting (tokens.css), so light-dark() picks the side.
 */
const menuColours = {
  '--menu-fill': 'light-dark(rgb(246 246 247 / 0.72), rgb(28 28 30 / 0.6))',
  '--menu-rim': 'light-dark(rgb(0 0 0 / 0.06), rgb(255 255 255 / 0.1))',
  '--menu-label': 'light-dark(#1a1a1a, #fff)',
  '--menu-secondary': 'light-dark(rgb(26 26 26 / 0.55), rgb(255 255 255 / 0.7))',
  '--menu-separator': 'light-dark(rgb(0 0 0 / 0.09), rgb(255 255 255 / 0.1))',
  '--menu-pressed': 'light-dark(rgb(0 0 0 / 0.07), rgb(255 255 255 / 0.1))',
} as CSSProperties;
// IosMenuPopover's scrim: black at 30% over a dark page, 14% over a light one.
const scrimColour = 'light-dark(rgb(0 0 0 / 0.14), rgb(0 0 0 / 0.3))';
// A desktop menu barely dims the window: the menu, not the page, is what changed.
const compactScrimColour = 'light-dark(rgb(0 0 0 / 0.05), rgb(0 0 0 / 0.12))';
/** How long typed letters keep adding to one type-ahead search, as a native menu's do. */
const TYPE_AHEAD_MS = 600;

/**
 * The phone's iOS-style menu (IosMenu.kt IosMenuPopover): a 256px frosted
 * charcoal slab with a 30px corner over a dimmed page, growing out of the
 * corner nearest what opened it on a spring that doesn't overshoot, and
 * shrinking back into it over 170ms. A right-click passes [lift], and the
 * item lifts out of the page as a held one does (HeldContextMenu.kt).
 * Closes on Escape, a click outside, or a scroll of the page under it.
 */
export function Menu({ anchor, placement = 'below-end', point, lift, onClose, label, matchWidth, children }: MenuProps) {
  const root = useRef<HTMLDivElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const cutout = useRef<HTMLDivElement>(null);
  // Settled as it opens, like its position: a menu doesn't change density under the pointer.
  const [compact] = useState(() => window.matchMedia(COMPACT_QUERY).matches);
  const scrim = compact ? compactScrimColour : scrimColour;

  useLayoutEffect(() => {
    const node = panel.current!;
    const rootNode = root.current!;
    const veils = [...rootNode.querySelectorAll<HTMLElement>('[data-veil]')];
    const reduce = reducedMotion();
    const rect = anchor.getBoundingClientRect();
    const width = compact ? COMPACT_WIDTH : MENU_WIDTH;
    node.style.width = `${Math.min(matchWidth ? Math.max(width, rect.width) : width, window.innerWidth - 2 * MARGIN)}px`;
    const viewport = { width: window.innerWidth, height: window.innerHeight };
    const place = placeMenu(rect, { width: node.offsetWidth, height: node.scrollHeight }, viewport, placement, point);
    // Pinned by the edge it grows from, so a page of different rows (the sleep timer) keeps it there.
    const fromBottom = place.origin.endsWith('bottom');
    const height = Math.min(node.scrollHeight, place.maxHeight);
    Object.assign(node.style, {
      left: `${place.left}px`,
      top: fromBottom ? '' : `${place.top}px`,
      bottom: fromBottom ? `${viewport.height - place.top - height}px` : '',
      maxHeight: `${place.maxHeight}px`,
      transformOrigin: place.origin,
      visibility: 'visible',
    });

    // The held item, a little larger, in a window cut out of the scrim.
    let lifted: Animation | null = null;
    let scale = 1;
    if (lift && cutout.current) {
      const box = lift.getBoundingClientRect();
      // A desktop row barely lifts: a full-width row grown 4% would push past the page's edges.
      scale = 1 + (compact ? Math.min(0.01, 4 / Math.max(box.width, 1)) : Math.min(0.04, 12 / Math.max(box.width, 1)));
      const radius = getComputedStyle(lift).borderRadius;
      Object.assign(cutout.current.style, {
        left: `${box.left - ((scale - 1) * box.width) / 2}px`,
        top: `${box.top - ((scale - 1) * box.height) / 2}px`,
        width: `${box.width * scale}px`,
        height: `${box.height * scale}px`,
        borderRadius: radius === '0px' ? 'var(--radius-track-row)' : radius,
      });
      if (!reduce) lifted = lift.animate([{ scale: 1 }, { scale }], { duration: 440, easing: LIFT_EASE, fill: 'forwards' });
    }

    const grow = { duration: reduce ? 120 : 320, easing: reduce ? 'linear' : OPEN_EASE };
    if (reduce) node.animate([{ opacity: 0 }, { opacity: 1 }], grow);
    else {
      node.animate([{ scale: 0.3 }, { scale: 1 }], grow);
      // The phone's alpha is 2.5× the growth: opaque by the time it's 40% grown.
      node.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 60, easing: 'linear' });
    }
    for (const veil of veils) veil.animate([{ opacity: 0 }, { opacity: 1 }], grow);

    return () => {
      lifted?.cancel();
      if (lift && !reduce) lift.animate([{ scale }, { scale: 1 }], { duration: 170, easing: CLOSE_EASE });
      // The menu is already gone from React by now: a copy of it plays the way out.
      const ghost = rootNode.cloneNode(true) as HTMLElement;
      ghost.inert = true;
      ghost.setAttribute('aria-hidden', 'true');
      ghost.querySelectorAll<HTMLElement>('*').forEach((el) => (el.style.pointerEvents = 'none'));
      document.body.append(ghost);
      // React's strict mode unmounts and remounts once in development: the menu is still here then.
      queueMicrotask(() => {
        if (node.isConnected) return ghost.remove();
        const shrink = { duration: reduce ? 100 : 170, easing: CLOSE_EASE, fill: 'forwards' as const };
        const ghostPanel = ghost.querySelector<HTMLElement>('[role="menu"]')!;
        ghostPanel.animate(reduce ? [{ opacity: 1 }, { opacity: 0 }] : [{ scale: 1, opacity: 1 }, { opacity: 1, offset: 0.6 }, { scale: 0.3, opacity: 0 }], shrink);
        ghost.querySelectorAll<HTMLElement>('[data-veil]').forEach((veil) => veil.animate([{ opacity: 1 }, { opacity: 0 }], shrink));
        // A timer, not the animation's finish event, which a background tab holds back until it's shown.
        setTimeout(() => ghost.remove(), shrink.duration);
      });
    };
    // Measured once, as it opens: a menu doesn't follow its anchor.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    // The panel takes focus with nothing lit, as the phone's opens; the arrows go into the rows.
    panel.current?.focus({ preventScroll: true });
    let typed = '';
    let typedAt = 0;
    const onKey = (event: KeyboardEvent) => {
      const items = () => [...(panel.current?.querySelectorAll<HTMLElement>('[role^="menuitem"]:not(:disabled)') ?? [])];
      if (event.key === 'Escape') {
        event.stopPropagation();
        onClose();
        anchor.focus();
      } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault();
        const all = items();
        const index = all.indexOf(document.activeElement as HTMLElement);
        const down = event.key === 'ArrowDown';
        const next = index < 0 ? (down ? 0 : -1) : down ? index + 1 : index - 1;
        all[(next + all.length) % all.length]?.focus();
      } else if (event.key === 'Home' || event.key === 'End') {
        event.preventDefault();
        const all = items();
        all[event.key === 'Home' ? 0 : all.length - 1]?.focus();
      } else if (
        event.key.length === 1 &&
        !event.ctrlKey &&
        !event.metaKey &&
        !event.altKey &&
        !(event.target instanceof HTMLInputElement) &&
        panel.current?.contains(event.target as Node)
      ) {
        // Type-ahead: letters typed in quick succession go to the first row whose label starts with them.
        typed = event.timeStamp - typedAt < TYPE_AHEAD_MS ? typed + event.key.toLowerCase() : event.key.toLowerCase();
        typedAt = event.timeStamp;
        if (typed.trim() === '') return;
        event.preventDefault();
        const all = items();
        const from = all.indexOf(document.activeElement as HTMLElement);
        // One letter again and again steps through the rows starting with it, as a native menu does.
        const step = [...typed].every((c) => c === typed[0]) ? 1 : 0;
        const search = step ? typed.charAt(0) : typed;
        const start = Math.max(0, from + step);
        const order = [...all.slice(start), ...all.slice(0, start)];
        order.find((item) => menuLabel(item).startsWith(search))?.focus();
      }
    };
    const onScroll = (event: Event) => {
      if (!panel.current?.contains(event.target as Node)) onClose();
    };
    document.addEventListener('keydown', onKey, true);
    document.addEventListener('scroll', onScroll, true);
    window.addEventListener('resize', onClose);
    return () => {
      document.removeEventListener('keydown', onKey, true);
      document.removeEventListener('scroll', onScroll, true);
      window.removeEventListener('resize', onClose);
    };
  }, [anchor, onClose]);

  return createPortal(
    <div ref={root} className="contents">
      <div
        className="fixed inset-0 z-[calc(var(--z-contextual-menus)-1)]"
        style={lift ? undefined : { background: scrim }}
        data-veil={lift ? undefined : true}
        onPointerDown={onClose}
        onContextMenu={(event) => (event.preventDefault(), onClose())}
        aria-hidden="true"
      />
      {lift && (
        // Everything but the held item dims: the scrim is this window's shadow.
        <div
          ref={cutout}
          className="pointer-events-none fixed z-[calc(var(--z-contextual-menus)-1)]"
          style={{ boxShadow: `0 0 0 200vmax ${scrim}` }}
          data-veil
          aria-hidden="true"
        />
      )}
      <div
        ref={panel}
        className="invisible fixed z-(--z-contextual-menus) overflow-y-auto overscroll-contain rounded-menu bg-(--menu-fill) py-2 text-(--menu-label) data-compact:py-1.5 shadow-[inset_0_0_0_0.5px_var(--menu-rim),0_8px_24px_rgb(0_0_0/0.3)] [backdrop-filter:saturate(1.8)_blur(24px)]"
        style={menuColours}
        tabIndex={-1}
        role="menu"
        aria-label={label}
        data-compact={compact || undefined}
        onContextMenu={(event) => event.preventDefault()}
      >
        {children}
      </div>
    </div>,
    document.body,
  );
}

interface MenuItemProps {
  onSelect: () => void;
  icon?: ReactNode;
  /** A radio-style choice: the check stands in the glyph column, as the phone's pickers mark theirs. */
  checked?: boolean | null;
  destructive?: boolean;
  disabled?: boolean;
  /** A dimmer second line, as "Go to album" names the album. */
  description?: ReactNode;
  /** A value at the trailing edge. */
  trailing?: ReactNode;
  children: ReactNode;
}

/** A row's label for type-ahead: a quick action's name, or the row's own words. */
const menuLabel = (item: HTMLElement) => (item.title || item.textContent || '').trim().toLowerCase();

/**
 * One row (IosMenu.kt IosMenuRow): 44px, a 28px glyph column with a 21px
 * glyph, then the label at 17/21. Pointing at it, or pressing it, fills a
 * tile with a 14px corner inset 8 from the panel's sides. In a compact
 * (desktop) menu: 32px, a 17px glyph and the label at 14/18, inset 6.
 */
export function MenuItem({ onSelect, icon, checked, destructive, disabled, description, trailing, children }: MenuItemProps) {
  const glyph = checked ? <Check size={19} strokeWidth={2.4} /> : icon;
  return (
    <button
      className="mx-2 flex min-h-11 w-[calc(100%-16px)] items-center gap-2.5 rounded-[14px] py-[11px] ps-3 pe-3.5 text-start enabled:hover:bg-(--menu-pressed) enabled:active:bg-(--menu-pressed) focus-visible:bg-(--menu-pressed) disabled:opacity-40 data-destructive:text-destructive in-data-compact:mx-1.5 in-data-compact:min-h-8 in-data-compact:w-[calc(100%-12px)] in-data-compact:gap-2 in-data-compact:rounded-[10px] in-data-compact:py-1.5 in-data-compact:ps-2 in-data-compact:pe-2.5 [&_svg]:size-[21px] in-data-compact:[&_svg]:size-[17px]"
      role={checked === undefined ? 'menuitem' : 'menuitemradio'}
      aria-checked={checked === undefined ? undefined : !!checked}
      data-destructive={destructive || undefined}
      disabled={disabled}
      onClick={onSelect}
    >
      {(glyph || checked !== undefined) && <span className="grid w-7 flex-none place-items-center in-data-compact:w-5">{glyph}</span>}
      <span className="flex min-w-0 flex-1 flex-col gap-px">
        <span className="line-clamp-2 type-[400_17px/21px] tracking-[-0.3px] in-data-compact:type-[400_14px/18px] in-data-compact:tracking-[-0.1px]">{children}</span>
        {description && <span className="truncate type-[400_14px/17px] text-(--menu-secondary) in-data-compact:type-[400_12px/15px]">{description}</span>}
      </span>
      {trailing && <span className="flex-none type-[400_15px/18px] text-(--menu-secondary) in-data-compact:type-[400_13px/16px] [&_svg]:size-4 in-data-compact:[&_svg]:size-3.5">{trailing}</span>}
    </button>
  );
}

/** IosMenuSeparator: a 1px rule inset 18px, with air either side (17px in all). */
export function MenuSeparator() {
  return (
    <div className="grid h-[17px] place-items-center px-[18px] in-data-compact:h-[9px] in-data-compact:px-3" role="separator">
      <div className="h-px w-full bg-(--menu-separator)" />
    </div>
  );
}

/** A non-interactive heading inside a menu. */
export function MenuHeader({ children }: { children: ReactNode }) {
  return <div className="px-5 pt-1.5 pb-1 type-[600_14px/17px] text-(--menu-secondary) in-data-compact:px-4 in-data-compact:type-[600_12px/15px]">{children}</div>;
}

/**
 * IosMenuQuickActions: the actions reached for most, as tiles across the top,
 * 66px tall; in a compact menu, one 32px row of small icon buttons.
 */
export function MenuQuickActions({ children }: { children: ReactNode }) {
  return <div className="flex h-[66px] gap-1 px-2.5 in-data-compact:h-8 in-data-compact:gap-0.5 in-data-compact:px-1.5">{children}</div>;
}

/**
 * One tile (IosMenuQuickAction): a 23px glyph over a 12px label, a
 * 16px-cornered press fill. Compact, the 17px glyph alone: the label stays
 * for screen readers and shows as the pointer's tooltip.
 */
export function MenuQuickAction({
  icon,
  label,
  active,
  disabled,
  onSelect,
}: {
  icon: ReactNode;
  label: string;
  /** Lit: a rating that's set. */
  active?: boolean;
  disabled?: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      className="flex min-w-0 flex-1 flex-col items-center justify-center gap-1.5 rounded-2xl px-1 py-2 enabled:hover:bg-(--menu-pressed) enabled:active:bg-(--menu-pressed) focus-visible:bg-(--menu-pressed) disabled:text-(--menu-secondary) in-data-compact:rounded-[10px] in-data-compact:py-0 [&_svg]:size-[23px] in-data-compact:[&_svg]:size-[17px]"
      role="menuitem"
      title={label}
      aria-pressed={active}
      disabled={disabled}
      onClick={onSelect}
    >
      {icon}
      <span className="max-w-full truncate type-[500_12px/15px] in-data-compact:sr-only">{label}</span>
    </button>
  );
}
