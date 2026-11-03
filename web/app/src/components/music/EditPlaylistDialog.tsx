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

const field =
  'w-full rounded-[8px] border border-divider bg-quinary px-3 py-2 type-body text-primary outline-none placeholder:text-tertiary focus:border-key';
const button = 'inline-flex h-8 items-center justify-center gap-1.5 rounded-[8px] px-3.5 type-body-emphasized disabled:opacity-50';

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
      className="fixed inset-0 z-(--z-modal) grid animate-fade place-items-center bg-scrim p-4"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget && !busy) onClose();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Edit playlist"
        className="w-[min(460px,100%)] animate-menu-in rounded-[16px] border border-[#00000033] bg-thick-material p-5 shadow-[inset_0_0_0_1px_#ffffff26,0_20px_70px_#00000080] [backdrop-filter:blur(60px)_saturate(2.2)]"
        onKeyDown={(e) => {
          if (e.key === 'Escape' && !busy) {
            e.stopPropagation();
            onClose();
          }
        }}
      >
        <div className="mb-4 flex items-center justify-between">
          <h2 className="type-title-3-emphasized">{confirmDelete ? 'Delete Playlist' : 'Edit Playlist'}</h2>
          <button className="grid size-7 place-items-center rounded-full text-secondary hover:bg-quinary" aria-label="Close" onClick={onClose} disabled={busy}>
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
              <button className={`${button} bg-quinary text-primary hover:bg-quaternary`} onClick={() => setConfirmDelete(false)} disabled={busy}>
                Cancel
              </button>
              <button
                className={`${button} bg-[#ff453a] text-white hover:brightness-110`}
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
            <label className="block type-callout-emphasized text-secondary">
              Name
              <input ref={titleField} className={`${field} mt-1.5`} value={title} maxLength={150} onChange={(e) => setTitle(e.target.value)} />
            </label>
            <label className="mt-3.5 block type-callout-emphasized text-secondary">
              Description
              <textarea
                className={`${field} mt-1.5 h-24 resize-none`}
                value={description}
                maxLength={5000}
                placeholder="Optional"
                onChange={(e) => setDescription(e.target.value)}
              />
            </label>
            <div className="mt-3.5 flex items-center justify-between type-callout-emphasized text-secondary">
              <span>Privacy</span>
              <Select value={privacy} options={PRIVACY} onChange={setPrivacy} label="Privacy" />
            </div>
            {error && <p className="mt-3 type-callout text-[#ff6961]">{error.message}</p>}
            <div className="mt-6 flex items-center justify-between gap-2">
              <button
                type="button"
                className={`${button} -ms-2 text-[#ff6961] hover:bg-quinary`}
                onClick={() => setConfirmDelete(true)}
                disabled={busy}
              >
                <Trash2 size={15} />
                Delete Playlist
              </button>
              <div className="flex gap-2">
                <button type="button" className={`${button} bg-quinary text-primary hover:bg-quaternary`} onClick={onClose} disabled={busy}>
                  Cancel
                </button>
                <button type="submit" className={`${button} bg-key text-white hover:brightness-110`} disabled={!changed || busy || !title.trim()}>
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
