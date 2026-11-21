import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { TOAST_ACTION_MS, TOAST_MS, useToasts } from '@/state/toasts';

/**
 * The toast above the floating player, centred over the page (not the
 * sidebar). It stays while hovered, and the live region is always mounted so
 * screen readers announce each new message.
 */
export function ToastHost() {
  const current = useToasts((state) => state.current);
  const dismiss = useToasts((state) => state.dismiss);
  const [hovered, setHovered] = useState(false);

  useEffect(() => {
    if (!current || hovered) return;
    const timer = window.setTimeout(() => dismiss(current.id), current.action ? TOAST_ACTION_MS : TOAST_MS);
    return () => window.clearTimeout(timer);
  }, [current, hovered, dismiss]);

  return (
    <div
      className="pointer-events-none fixed inset-x-0 bottom-[calc(var(--player-pill-height)+var(--player-bottom-offset)+12px)] z-(--z-contextual-menus) flex justify-center px-4 pl-[calc(var(--web-navigation-width)+16px)] max-sm:pl-4"
      role="status"
      aria-live="polite"
    >
      {current && (
        <div
          key={current.id}
          className="pointer-events-auto flex max-w-[min(420px,100%)] animate-menu-in items-center gap-3 rounded-[10px] bg-thick-material py-2 ps-3.5 pe-2 shadow-[0_0_0_0.5px_var(--vibrantDivider),0_10px_40px_var(--glassMaterialShadowColor)] [backdrop-filter:saturate(2.2)_blur(32px)]"
          onMouseEnter={() => setHovered(true)}
          onMouseLeave={() => setHovered(false)}
        >
          <span className={`min-w-0 truncate type-body ${current.error ? 'text-destructive' : 'text-primary'}`}>{current.message}</span>
          {current.action ? (
            <Link
              to={current.action.href}
              className="flex-none rounded-[6px] px-2 py-0.5 type-body-emphasized text-key hover:bg-quinary"
              onClick={() => dismiss(current.id)}
            >
              {current.action.label}
            </Link>
          ) : (
            <span className="w-1.5" />
          )}
        </div>
      )}
    </div>
  );
}
