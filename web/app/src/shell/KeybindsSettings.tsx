import { X } from 'lucide-react';
import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import { bindingLabel, SHORTCUT_DEFINITIONS } from './keybinds';

const section = 'mx-1 mt-[18px] mb-1.5 type-subhead-emphasized uppercase text-secondary';
const note = 'mx-1 mb-2 type-callout text-secondary';
const group = 'overflow-hidden rounded-[10px] bg-quinary';
const row = 'flex items-center justify-between gap-3 py-2 pr-3 pl-3';

/** A key pill: the shortcut an action answers to. */
function KeyPill({ label }: { label: string }) {
  return (
    <span className="min-w-[72px] flex-none rounded-[8px] bg-quaternary px-2.5 py-1 text-center type-callout-emphasized text-primary">
      {label}
    </span>
  );
}

/**
 * The playback shortcuts (Settings › Keybinds), listed for reference: the
 * rebindable playback shortcuts and the structural ones (⌘/Ctrl+arrows for
 * transport, ⌘/Ctrl+F and "/" for search, Esc).
 */
export function KeybindsSettings({ onClose }: { onClose: () => void }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return createPortal(
    <div className="fixed inset-0 z-(--z-modal) grid animate-fade place-items-center bg-scrim" onPointerDown={(e) => e.target === e.currentTarget && onClose()}>
      <div
        className="flex max-h-[min(720px,calc(100vh-48px))] w-[min(520px,calc(100vw-32px))] animate-zoom flex-col rounded-[14px] bg-page shadow-glass"
        role="dialog"
        aria-modal="true"
        aria-label="Keyboard shortcuts"
      >
        <header className="flex items-center justify-between px-5 pt-[18px] pb-2">
          <h2 className="type-title-2-emphasized">Keybinds</h2>
          <button className="grid size-[30px] place-items-center rounded-full bg-[#333336] text-secondary" aria-label="Close" onClick={onClose}>
            <X size={16} />
          </button>
        </header>

        <div className="overflow-y-auto px-5 pb-5">
          <p className={note}>Shortcuts don’t fire while you’re typing, and a mouse click never leaves a button focused to re-trigger.</p>
          <h3 className={section}>Playback</h3>
          <ul className={group}>
            {SHORTCUT_DEFINITIONS.map((def, index) => (
              <li key={def.action} className={`${row} ${index > 0 ? 'border-t-[0.5px] border-t-divider' : ''}`}>
                <span className="type-body">{def.label}</span>
                <span className="flex items-center gap-1.5">
                  {def.bindings.map((binding) => (
                    <KeyPill key={`${binding.key}-${binding.shift}`} label={bindingLabel(binding)} />
                  ))}
                </span>
              </li>
            ))}
          </ul>

          <h3 className={section}>Fixed</h3>
          <ul className={group}>
            {[
              ['Search', '⌘/Ctrl+F or /'],
              ['Next / Previous', '⌘/Ctrl+→ / ←'],
              ['Volume', '⌘/Ctrl+↑ / ↓'],
              ['Close', 'Esc'],
            ].map(([label, keys], index) => (
              <li key={label} className={`${row} ${index > 0 ? 'border-t-[0.5px] border-t-divider' : ''}`}>
                <span className="type-body">{label}</span>
                <span className="type-callout text-secondary">{keys}</span>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>,
    document.body,
  );
}
