import { useCallback, useEffect, useId, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

export interface ConfirmDialogProps {
  title: string;
  /** The explanation: text, or paragraphs. */
  children: ReactNode;
  confirmLabel: string;
  cancelLabel?: string;
  /**
   * Offers "Don't show this again" (or this label); its state is passed to
   * [onConfirm]. useRememberedConfirm keeps it.
   */
  dontShowAgain?: boolean | string;
  onConfirm: (dontShowAgain: boolean) => void;
  /** Cancel, Escape, or a click outside the card. */
  onCancel: () => void;
}

/**
 * A question before an action: the app's modal (scrim, 14px card, fade with
 * a slight zoom, as LyricsSettings), a title, an explanation and two buttons,
 * the confirming one focused. Rendered into <body>, so it can be placed
 * anywhere, including inside a label or a menu's parent.
 */
export function ConfirmDialog({
  title,
  children,
  confirmLabel,
  cancelLabel = 'Cancel',
  dontShowAgain,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const [skipNextTime, setSkipNextTime] = useState(false);
  const id = useId();

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onCancel();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onCancel]);

  return createPortal(
    <div className="fixed inset-0 z-(--z-modal) grid animate-fade place-items-center bg-scrim" onPointerDown={(e) => e.target === e.currentTarget && onCancel()}>
      <div
        className="flex w-[min(420px,calc(100vw-32px))] animate-zoom flex-col gap-3 rounded-[14px] bg-page p-5 shadow-glass"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={`${id}-title`}
        aria-describedby={`${id}-body`}
      >
        <h2 id={`${id}-title`} className="type-title-2-emphasized">
          {title}
        </h2>
        <div id={`${id}-body`} className="flex flex-col gap-2 type-body text-secondary">
          {children}
        </div>
        {dontShowAgain && (
          <label className="flex cursor-pointer items-center gap-2.5 type-callout text-secondary">
            <input type="checkbox" className="size-4 accent-key" checked={skipNextTime} onChange={(e) => setSkipNextTime(e.currentTarget.checked)} />
            {typeof dontShowAgain === 'string' ? dontShowAgain : 'Don’t show this again'}
          </label>
        )}
        <div className="mt-1 flex justify-end gap-2">
          <button className="rounded-[14px] bg-quinary px-3.5 py-1.5 type-callout-emphasized text-primary hover:bg-quaternary" onClick={onCancel}>
            {cancelLabel}
          </button>
          <button
            className="rounded-[14px] bg-key px-3.5 py-1.5 type-callout-emphasized text-white hover:opacity-90"
            onClick={() => onConfirm(skipNextTime)}
            autoFocus
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}

/**
 * A ConfirmDialog in front of an action until the user ticks "Don't show
 * this again", which is kept in localStorage under [storageKey]. `ask()`
 * opens the dialog (or, once dismissed, runs [action] at once); render
 * `dialog` next to whatever calls `ask`.
 */
export function useRememberedConfirm(
  storageKey: string,
  action: () => void,
  dialog: Omit<ConfirmDialogProps, 'dontShowAgain' | 'onConfirm' | 'onCancel'>,
): { ask: () => void; dialog: ReactNode } {
  const [open, setOpen] = useState(false);

  const ask = useCallback(() => {
    if (readFlag(storageKey)) action();
    else setOpen(true);
  }, [storageKey, action]);

  const element = open ? (
    <ConfirmDialog
      {...dialog}
      dontShowAgain
      onCancel={() => setOpen(false)}
      onConfirm={(skipNextTime) => {
        if (skipNextTime) writeFlag(storageKey);
        setOpen(false);
        action();
      }}
    />
  ) : null;

  return { ask, dialog: element };
}

function readFlag(key: string): boolean {
  try {
    return localStorage.getItem(key) === 'dismissed';
  } catch {
    return false;
  }
}

function writeFlag(key: string): void {
  try {
    localStorage.setItem(key, 'dismissed');
  } catch {
    // Private mode or full storage: it asks again next time.
  }
}
