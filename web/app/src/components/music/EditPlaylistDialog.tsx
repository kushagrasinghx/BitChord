import { Trash2, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Select } from '@/components/Select';
import { useDeletePlaylist, useEditPlaylist } from '@/data/music/queries';
import type { HeaderInfo, PlaylistPrivacy } from '@/data/music/types';
import { privacyOf } from './playlistPrivacy';

const PRIVACY: { value: PlaylistPrivacy; label: string }[] = [
  { value: 'PUBLIC', label: 'Public' },
  { value: 'UNLISTED', label: 'Unlisted' },
  { value: 'PRIVATE', label: 'Private' },
];

// The grouped inset rows of the app's settings and the Lyrics window: one
// rounded group, hairlines between rows, the label above each field.
const group = 'overflow-hidden rounded-[10px] bg-quinary';
const row = 'block px-4 py-[11px] [&+&]:border-t-[0.5px] [&+&]:border-t-divider';
const rowLabel = 'block type-callout-emphasized text-secondary';
const field = 'mt-1 w-full border-0 bg-transparent p-0 type-body text-primary outline-none placeholder:text-tertiary';
// The Lyrics window's pill buttons.
const button =
  'inline-flex items-center justify-center gap-1.5 rounded-[14px] px-3.5 py-1.5 type-callout-emphasized transition-[filter,background-color] duration-100 disabled:cursor-default';
const secondary = `${button} bg-quaternary text-primary enabled:hover:brightness-125 disabled:opacity-50`;
const primary = `${button} bg-key text-white enabled:hover:bg-key-rollover enabled:active:bg-key-pressed disabled:bg-quaternary disabled:text-tertiary`;

/**
 * Edit one of the user's own playlists: its name, description and privacy,
 * or delete it (after a second, explicit confirmation).
 */
export function EditPlaylistDialog({
  playlistId,
  info,
  onClose,
  onDeleted,
}: {
  playlistId: string;
  info: HeaderInfo;
  onClose: () => void;
  onDeleted: () => void;
}) {
  const [title, setTitle] = useState(info.title);
  const [description, setDescription] = useState(info.description ?? '');
  const initialPrivacy = privacyOf(info.subtitle);
  const [privacy, setPrivacy] = useState<PlaylistPrivacy>(initialPrivacy);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const edit = useEditPlaylist();
  const remove = useDeletePlaylist();
  const titleField = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    titleField.current?.focus();
    titleField.current?.select();
    return () => previous?.focus?.();
  }, []);

  const busy = edit.isPending || remove.isPending;
  const changes = {
    ...(title.trim() && title.trim() !== info.title ? { title: title.trim() } : {}),
    ...(description.trim() !== (info.description ?? '').trim() ? { description: description.trim() } : {}),
    ...(privacy !== initialPrivacy ? { privacy } : {}),
  };
  const changed = Object.keys(changes).length > 0;
  const error = (edit.error ?? remove.error) as Error | null;

  const save = () => {
    if (!changed || busy) return;
    edit.mutate({ playlistId, ...changes }, { onSuccess: onClose });
  };

  return createPortal(
    <div
      className="fixed inset-0 z-(--z-modal) grid animate-fade place-items-center bg-scrim"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget && !busy) onClose();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Edit playlist"
        // The same modal as Lyrics settings (report §8): page colour, 14px radius, zoom in.
        className="w-[min(460px,calc(100vw-32px))] animate-zoom rounded-[14px] bg-page px-5 pt-[18px] pb-5 shadow-glass"
        onKeyDown={(e) => {
          if (e.key === 'Escape' && !busy) {
            e.stopPropagation();
            onClose();
          }
        }}
      >
        <div className="mb-4 flex items-center justify-between">
          <h2 className="type-title-2-emphasized">{confirmDelete ? 'Delete Playlist' : 'Edit Playlist'}</h2>
          <button className="grid size-[30px] place-items-center rounded-full bg-[#333336] text-secondary" aria-label="Close" onClick={onClose} disabled={busy}>
            <X size={16} />
          </button>
        </div>

        {confirmDelete ? (
          <>
            <p className="type-body text-secondary">
              Delete <span className="text-primary">“{info.title}”</span> from your YouTube Music account? This can’t be undone.
            </p>
            {error && <p className="mt-3 type-callout text-[#ff6961]">{error.message}</p>}
            <div className="mt-5 flex justify-end gap-2">
              <button className={secondary} onClick={() => setConfirmDelete(false)} disabled={busy}>
                Cancel
              </button>
              <button
                className={`${button} bg-[#ff453a] text-white enabled:hover:brightness-110 disabled:opacity-50`}
                disabled={busy}
                onClick={() => remove.mutate({ playlistId }, { onSuccess: onDeleted })}
              >
                <Trash2 size={15} />
                {remove.isPending ? 'Deleting…' : 'Delete'}
              </button>
            </div>
          </>
        ) : (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              save();
            }}
          >
            <div className={group}>
              <label className={row}>
                <span className={rowLabel}>Name</span>
                <input ref={titleField} className={field} value={title} maxLength={150} placeholder="Playlist name" onChange={(e) => setTitle(e.target.value)} />
              </label>
              <label className={row}>
                <span className={rowLabel}>Description</span>
                <textarea
                  className={`${field} h-20 resize-none`}
                  value={description}
                  maxLength={5000}
                  placeholder="Optional"
                  onChange={(e) => setDescription(e.target.value)}
                />
              </label>
            </div>
            <div className={`${group} mt-3.5`}>
              <div className="flex items-center justify-between px-4 py-[9px] type-body text-primary">
                <span>Privacy</span>
                <Select value={privacy} options={PRIVACY} onChange={setPrivacy} label="Privacy" />
              </div>
            </div>
            {error && <p className="mt-3 type-callout text-[#ff6961]">{error.message}</p>}
            <div className="mt-5 flex items-center justify-between gap-2">
              <button
                type="button"
                className={`${button} -ms-3.5 text-[#ff6961] enabled:hover:bg-quinary disabled:opacity-50`}
                onClick={() => setConfirmDelete(true)}
                disabled={busy}
              >
                <Trash2 size={15} />
                Delete Playlist
              </button>
              <div className="flex gap-2">
                <button type="button" className={secondary} onClick={onClose} disabled={busy}>
                  Cancel
                </button>
                <button type="submit" className={primary} disabled={!changed || busy || !title.trim()}>
                  {edit.isPending ? 'Saving…' : 'Save'}
                </button>
              </div>
            </div>
          </form>
        )}
      </div>
    </div>,
    document.body,
  );
}
