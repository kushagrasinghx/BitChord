import { ArrowDown, ArrowUp, CheckCircle2, Globe, Loader2, Plus, Trash2, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { AddonManager } from '@/addons/addonManager';
import type { ConfiguredAddon } from '@/addons/types';
import { usePlayer } from '@/state/player';

const section = 'mx-1 mt-[18px] mb-1.5 type-subhead-emphasized uppercase text-secondary';
const note = 'mx-1 mb-2 type-callout text-secondary';
const group = 'overflow-hidden rounded-[10px] bg-quinary';
const row = 'flex items-center gap-2 py-2 pr-2.5 pl-3';
const toggle = 'flex min-w-0 flex-1 cursor-pointer items-center gap-2.5';
const checkbox = 'size-4 accent-key';
const moveButton = 'grid size-[26px] place-items-center rounded-[6px] text-secondary enabled:hover:bg-quaternary disabled:opacity-30';

export function SourcesSettings({ onClose }: { onClose: () => void }) {
  const [addons, setAddons] = useState<ConfiguredAddon[]>([]);
  const [newUrl, setNewUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const directStream = usePlayer((s) => s.directStream);
  const setDirectStream = usePlayer((s) => s.setDirectStream);

  const refresh = () => setAddons(AddonManager.getAddons());

  useEffect(() => {
    refresh();
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newUrl.trim() || loading) return;
    setLoading(true);
    setError(null);
    try {
      await AddonManager.addAddon(newUrl);
      setNewUrl('');
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to connect to addon manifest');
    } finally {
      setLoading(false);
    }
  };

  const handleToggle = (id: string, enabled: boolean) => {
    AddonManager.toggleAddon(id, enabled);
    refresh();
  };

  const handleMove = (id: string, delta: number) => {
    AddonManager.moveAddon(id, delta);
    refresh();
  };

  const handleRemove = (id: string) => {
    AddonManager.removeAddon(id);
    refresh();
  };

  return createPortal(
    <div
      className="fixed inset-0 z-(--z-modal) grid animate-fade place-items-center bg-scrim"
      onPointerDown={(e) => e.target === e.currentTarget && onClose()}
    >
      <div
        className="flex max-h-[min(720px,calc(100vh-48px))] w-[min(520px,calc(100vw-32px))] animate-zoom flex-col rounded-[14px] bg-page shadow-glass"
        role="dialog"
        aria-modal="true"
        aria-label="Source Addons"
      >
        <header className="flex items-center justify-between px-5 pt-[18px] pb-2">
          <h2 className="type-title-2-emphasized">Music Source Addons</h2>
          <button
            className="grid size-[30px] place-items-center rounded-full bg-[#333336] text-secondary"
            aria-label="Close"
            onClick={onClose}
          >
            <X size={16} />
          </button>
        </header>

        <div className="overflow-y-auto px-5 pb-5">
          <h3 className={section}>Playback Mode</h3>
          <div className={`${group} p-3 mb-4 flex items-center justify-between gap-3`}>
            <div className="flex flex-col">
              <span className="type-body-emphasized">Direct Stream</span>
              <span className="text-xs text-secondary">
                Search and stream from addons first from note 1. Switches to YouTube Opus if unavailable.
              </span>
            </div>
            <input
              type="checkbox"
              className={checkbox}
              checked={directStream}
              onChange={(e) => setDirectStream(e.currentTarget.checked)}
            />
          </div>

          <h3 className={section}>Add New Source Addon</h3>
          <p className={note}>Enter the server or manifest URL (e.g. Plex, Jellyfin, or community addon manifest).</p>

          <form onSubmit={handleAdd} className="flex gap-2 mb-3">
            <input
              type="url"
              placeholder="https://.../manifest.json"
              value={newUrl}
              onChange={(e) => setNewUrl(e.target.value)}
              className="flex-1 rounded-[10px] border-0 bg-quinary px-3 py-2 type-body text-primary placeholder:text-secondary focus:ring-1 focus:ring-key"
              disabled={loading}
              required
            />
            <button
              type="submit"
              disabled={loading || !newUrl.trim()}
              className="flex items-center gap-1.5 rounded-[10px] bg-key px-3.5 py-2 type-callout-emphasized text-white disabled:opacity-50"
            >
              {loading ? <Loader2 size={16} className="animate-spin" /> : <Plus size={16} />}
              Add
            </button>
          </form>

          {error && <p className="mx-1 mb-2 text-xs text-red-400">{error}</p>}

          <h3 className={section}>Configured Sources</h3>
          <p className={note}>Sources are checked sequentially from top to bottom before falling back to YouTube Music.</p>

          {addons.length === 0 ? (
            <div className={`${group} p-4 text-center text-secondary type-callout`}>
              No custom source addons added yet. Defaulting to YouTube Music.
            </div>
          ) : (
            <ol className={group}>
              {addons.map((addon, index) => (
                <li key={addon.id} className={`${row} [&+&]:border-t-[0.5px] [&+&]:border-t-divider`}>
                  <label className={toggle}>
                    <input
                      type="checkbox"
                      className={checkbox}
                      checked={addon.enabled}
                      onChange={(e) => handleToggle(addon.id, e.currentTarget.checked)}
                    />
                    <span className="flex min-w-0 flex-col">
                      <span className="flex items-center gap-1.5 type-body-emphasized">
                        {addon.name}
                        <CheckCircle2 size={14} className="text-green-400" />
                      </span>
                      <span className="truncate text-xs text-secondary">{addon.baseUrl}</span>
                    </span>
                  </label>

                  <span className="flex items-center gap-0.5">
                    <button
                      className={moveButton}
                      aria-label="Move Up"
                      disabled={index === 0}
                      onClick={() => handleMove(addon.id, -1)}
                    >
                      <ArrowUp size={14} />
                    </button>
                    <button
                      className={moveButton}
                      aria-label="Move Down"
                      disabled={index === addons.length - 1}
                      onClick={() => handleMove(addon.id, 1)}
                    >
                      <ArrowDown size={14} />
                    </button>
                    <button
                      className={`${moveButton} hover:text-red-400`}
                      aria-label="Remove"
                      onClick={() => handleRemove(addon.id)}
                    >
                      <Trash2 size={14} />
                    </button>
                  </span>
                </li>
              ))}
            </ol>
          )}

          <div className="mt-4 flex items-center gap-2 px-1 text-xs text-secondary">
            <Globe size={14} />
            <span>YouTube Music remains the active fallback for all tracks.</span>
          </div>
        </div>
      </div>
    </div>,
    document.body,
  );
}
