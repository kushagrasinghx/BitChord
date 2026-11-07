import { X } from 'lucide-react';
import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import { isApple, keyLabel, SHORTCUT_GROUPS } from './shortcuts';

const key = 'inline-grid h-6 min-w-6 place-items-center rounded-[6px] bg-quaternary px-1.5 type-callout-emphasized text-primary';

/** Every keyboard shortcut, opened with ? (and from Settings); styled like the Lyrics window. */
export function ShortcutSheet({ onClose }: { onClose: () => void }) {
  const apple = isApple(navigator.platform);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.stopPropagation();
        onClose();
      }
    };
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, [onClose]);

  return createPortal(
    <div className="fixed inset-0 z-(--z-modal) grid animate-fade place-items-center bg-scrim" onPointerDown={(e) => e.target === e.currentTarget && onClose()}>
      <div
        className="flex max-h-[min(720px,calc(100vh-48px))] w-[min(460px,calc(100vw-32px))] animate-zoom flex-col rounded-[14px] bg-page shadow-glass"
        role="dialog"
        aria-modal="true"
        aria-label="Keyboard shortcuts"
      >
        <header className="flex items-center justify-between px-5 pt-[18px] pb-2">
          <h2 className="type-title-2-emphasized">Keyboard Shortcuts</h2>
          <button className="grid size-[30px] place-items-center rounded-full bg-[#333336] text-secondary" aria-label="Close" onClick={onClose} autoFocus>
            <X size={16} strokeWidth={2.4} />
          </button>
        </header>
        <div className="overflow-y-auto px-5 pb-5">
          {SHORTCUT_GROUPS.map((group) => (
            <section key={group.title}>
              <h3 className="mx-1 mt-[18px] mb-1.5 type-subhead-emphasized text-secondary uppercase">{group.title}</h3>
              <dl className="overflow-hidden rounded-[10px] bg-quinary">
                {group.shortcuts.map((shortcut) => (
                  <div key={shortcut.label} className="flex items-center justify-between gap-4 px-3 py-2 [&+&]:border-t-[0.5px] [&+&]:border-t-divider">
                    <dt className="type-body text-primary">{shortcut.label}</dt>
                    <dd className="flex flex-none items-center gap-1">
                      {shortcut.keys.map((combo, i) => (
                        <span key={combo.join('+')} className="contents">
                          {i > 0 && <span className="px-1 type-callout text-secondary">or</span>}
                          {combo.map((k) => (
                            <kbd key={k} className={key}>
                              {keyLabel(k, apple)}
                            </kbd>
                          ))}
                        </span>
                      ))}
                    </dd>
                  </div>
                ))}
              </dl>
            </section>
          ))}
        </div>
      </div>
    </div>,
    document.body,
  );
}
