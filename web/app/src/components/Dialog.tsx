import { X } from 'lucide-react';
import { useEffect, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

const stop = (event: React.SyntheticEvent) => event.stopPropagation();

/**
 * A modal (report §8), as the lyric settings draw one: scrim, 14px card radius,
 * fade with a slight zoom, 30px close circle. Closes on Escape or a click on the
 * scrim.
 *
 * Opened from a track row's menu, it is still that row's child as far as React
 * events go, portal or not, so its clicks are kept from reaching the row: a
 * double click on a checkbox would otherwise play the track.
 */
export function Dialog({
  title,
  subtitle,
  onClose,
  footer,
  children,
}: {
  title: string;
  subtitle?: string;
  onClose: () => void;
  footer?: ReactNode;
  children: ReactNode;
}) {
  const card = useRef<HTMLDivElement>(null);
  useEffect(() => {
    // Into the dialog, unless something in it (a search field) took focus already.
    if (!card.current?.contains(document.activeElement)) card.current?.focus();
  }, []);

  return createPortal(
    <div
      className="fixed inset-0 z-(--z-modal) grid animate-fade place-items-center bg-scrim"
      onPointerDown={(e) => {
        e.stopPropagation();
        if (e.target === e.currentTarget) onClose();
      }}
      onMouseDown={stop}
      onClick={stop}
      onDoubleClick={stop}
      onKeyDown={(e) => {
        // Escape is this dialog's, not the full-screen player's under it.
        if (e.key !== 'Escape') return;
        e.stopPropagation();
        onClose();
      }}
    >
      <div
        ref={card}
        tabIndex={-1}
        className="flex max-h-[min(720px,calc(100vh-48px))] w-[min(520px,calc(100vw-32px))] animate-zoom flex-col rounded-[14px] bg-page shadow-glass outline-none"
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <header className="flex items-start justify-between gap-3 px-5 pt-[18px] pb-2">
          <div className="flex min-w-0 flex-col">
            <h2 className="type-title-2-emphasized">{title}</h2>
            {subtitle && <p className="truncate type-callout text-secondary">{subtitle}</p>}
          </div>
          <button className="grid size-[30px] flex-none place-items-center rounded-full bg-[#333336] text-secondary" aria-label="Close" onClick={onClose}>
            <X size={16} />
          </button>
        </header>
        <div className="overflow-y-auto px-5 pb-5">{children}</div>
        {footer && <footer className="flex items-center justify-end gap-3 border-t-[0.5px] border-t-divider px-5 py-3">{footer}</footer>}
      </div>
    </div>,
    document.body,
  );
}
