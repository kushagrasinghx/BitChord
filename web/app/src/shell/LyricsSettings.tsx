import { ArrowDown, ArrowUp, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { getBridge } from '@/extension/bridge';
import { LYRIC_EXTENSION_ORIGINS as EXTENSION_ORIGINS } from '@/lyrics/http';
import { useLyricsSettings } from '@/lyrics/settings';
import { sourceById } from '@/lyrics/sources';

const section = 'mx-1 mt-[18px] mb-1.5 type-subhead-emphasized uppercase text-secondary';
const note = 'mx-1 mb-2 type-callout text-secondary';
const group = 'overflow-hidden rounded-[10px] bg-quinary';
const row = 'flex items-center gap-2 py-2 pr-2.5 pl-3';
const toggle = 'flex min-w-0 flex-1 cursor-pointer items-center gap-2.5';
const checkbox = 'size-4 accent-key';
const moveButton = 'grid size-[26px] place-items-center rounded-[6px] text-secondary enabled:hover:bg-quaternary disabled:opacity-30';

/**
 * Lyric providers, as the desktop's settings have them: switch each on or
 * off, put them in order, prefer syllable timing, and the PaxSenix key. Plus
 * the one thing only the web needs: letting the extension reach the hosts a
 * page can't.
 */
export function LyricsSettings({ onClose }: { onClose: () => void }) {
  const { order, disabled, preferSyllables, paxsenixKey, setEnabled, move, setPreferSyllables, setPaxsenixKey, reset } =
    useLyricsSettings();
  const [granted, setGranted] = useState<string[] | null>(null);
  const [key, setKey] = useState(paxsenixKey);

  useEffect(() => {
    const check = () =>
      void getBridge()
        ?.call('lyrics.permissions', { origins: EXTENSION_ORIGINS })
        .then((r) => setGranted(r.granted))
        .catch(() => setGranted(null));
    check();
    window.addEventListener('focus', check);
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('focus', check);
      window.removeEventListener('keydown', onKey);
    };
  }, [onClose]);

  const missing = granted ? EXTENSION_ORIGINS.filter((o) => !granted.includes(o)) : [];

  return createPortal(
    // A modal (report §8): scrim, 14px card radius, fade with a slight zoom, 30px close circle.
    <div className="fixed inset-0 z-(--z-modal) grid animate-fade place-items-center bg-scrim" onPointerDown={(e) => e.target === e.currentTarget && onClose()}>
      <div
        className="flex max-h-[min(720px,calc(100vh-48px))] w-[min(520px,calc(100vw-32px))] animate-zoom flex-col rounded-[14px] bg-page shadow-glass"
        role="dialog"
        aria-modal="true"
        aria-label="Lyric settings"
      >
        <header className="flex items-center justify-between px-5 pt-[18px] pb-2">
          <h2 className="type-title-2-emphasized">Lyrics</h2>
          <button className="grid size-[30px] place-items-center rounded-full bg-[#333336] text-secondary" aria-label="Close" onClick={onClose}>
            <X size={16} />
          </button>
        </header>

        <div className="overflow-y-auto px-5 pb-5">
          <h3 className={section}>Providers</h3>
          <p className={note}>Asked all at once; the first in this order with word-timed lyrics wins.</p>
          <ol className={group}>
            {order.map((id, index) => {
              const source = sourceById(id)!;
              const on = !disabled.includes(id);
              return (
                <li key={id} className={`${row} [&+&]:border-t-[0.5px] [&+&]:border-t-divider`}>
                  <label className={toggle}>
                    <input type="checkbox" className={checkbox} checked={on} onChange={(e) => setEnabled(id, e.currentTarget.checked)} />
                    <span className="flex min-w-0 flex-col">
                      <span className="type-body-emphasized">{source.label}</span>
                      <span className="truncate type-callout text-secondary">{source.detail}</span>
                    </span>
                  </label>
                  <span className="flex gap-0.5">
                    <button className={moveButton} aria-label={`Move ${source.label} up`} disabled={index === 0} onClick={() => move(id, -1)}>
                      <ArrowUp size={14} />
                    </button>
                    <button className={moveButton} aria-label={`Move ${source.label} down`} disabled={index === order.length - 1} onClick={() => move(id, 1)}>
                      <ArrowDown size={14} />
                    </button>
                  </span>
                </li>
              );
            })}
          </ol>

          <label className={`${group} ${toggle} py-2 pr-2.5 pl-3`}>
            <input type="checkbox" className={checkbox} checked={preferSyllables} onChange={(e) => setPreferSyllables(e.currentTarget.checked)} />
            <span className="flex min-w-0 flex-col">
              <span className="type-body-emphasized">Prefer syllable timing</span>
              <span className="truncate type-callout text-secondary">Keep looking past a line-timed answer for a word-timed one.</span>
            </span>
          </label>

          <h3 className={section}>Extension access</h3>
          {granted === null ? (
            <p className={note}>KuGou, Musixmatch, Genius and PaxSenix need the BitChord extension.</p>
          ) : missing.length ? (
            <div className="flex items-center gap-3">
              <p className={note}>
                KuGou, Musixmatch, Genius and PaxSenix don’t let web pages ask them directly. Allow the extension to,
                once.
              </p>
              <button className="flex-none rounded-[14px] bg-key px-3.5 py-1.5 type-callout-emphasized text-white" onClick={() => void getBridge()?.call('lyrics.requestPermissions', { origins: missing })}>
                Allow
              </button>
            </div>
          ) : (
            <p className={note}>All providers are reachable.</p>
          )}

          <h3 className={section}>PaxSenix key</h3>
          <input
            className="w-full rounded-[10px] border-0 bg-quinary px-3 py-2 type-body text-primary"
            type="password"
            placeholder="For PaxSenix: Spotify and Musixmatch"
            value={key}
            onChange={(e) => setKey(e.currentTarget.value)}
            onBlur={() => setPaxsenixKey(key)}
            autoComplete="off"
          />

          <button className="mt-[18px] type-callout-emphasized text-key" onClick={reset}>
            Restore Defaults
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
