import { ArrowDown, ArrowUp, CheckCircle2, ChevronLeft, Plus, Radio, Trash2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import { AddonManager } from '@/addons/addonManager';
import type { ConfiguredAddon } from '@/addons/types';
import { PageHeader } from '@/components/PageHeader';
import { PrefetchLink } from '@/components/PrefetchLink';
import { usePlayer } from '@/state/player';
import { Toggle } from './IntegrationSettings';
import { settingsUi } from './settingsStyles';

/**
 * Music Source Addons Page (/settings/addons and /settings/sources).
 * Allows managing custom audio sources and toggling Direct Stream mode.
 */
export default function AddonsPage() {
  const [addons, setAddons] = useState<ConfiguredAddon[]>([]);
  const [newUrl, setNewUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const directStream = usePlayer((s) => s.directStream);
  const { setDirectStream } = usePlayer.getState();

  const refresh = () => setAddons(AddonManager.getAddons());

  useEffect(() => {
    refresh();
  }, []);

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

  return (
    <div className={settingsUi.centered}>
      <div className="mx-(--body-gutter) pt-6">
        <PrefetchLink
          to="/settings"
          className="inline-flex items-center gap-1.5 text-secondary hover:text-primary type-callout-medium mb-2 transition-colors"
        >
          <ChevronLeft size={16} />
          Back to Settings
        </PrefetchLink>
      </div>
      <PageHeader title="Music Sources & Addons" />
      <div className={settingsUi.body}>
        <h2 className={settingsUi.sectionHeader}>Playback Mode</h2>
        <section className={settingsUi.group}>
          <Toggle
            label="Direct Stream"
            detail="Give addons priority and search them first from the first note. Plays lossless FLAC directly without mid-song quality switches. Switches to YouTube Opus if unavailable."
            checked={directStream}
            onChange={setDirectStream}
          />
        </section>

        <h2 className={settingsUi.sectionHeader}>Add New Source Addon</h2>
        <section className={`${settingsUi.group} p-4`}>
          <p className="type-callout text-secondary mb-3">
            Add a provider manifest URL (e.g. Hi-Res FLAC bridges, Plex, Jellyfin, or community addon manifests).
          </p>
          <form onSubmit={handleAdd} className="flex gap-2">
            <input
              type="url"
              placeholder="https://.../manifest.json"
              value={newUrl}
              onChange={(e) => setNewUrl(e.target.value)}
              className="flex-1 rounded-[8px] border-0 bg-quaternary px-3 py-2 type-body text-primary placeholder:text-secondary focus:ring-1 focus:ring-key"
              disabled={loading}
              required
            />
            <button
              type="submit"
              disabled={loading || !newUrl.trim()}
              className="flex items-center gap-1 rounded-[8px] bg-key px-3.5 py-2 type-callout-emphasized text-white disabled:opacity-50"
            >
              <Plus size={16} />
              {loading ? 'Adding…' : 'Add'}
            </button>
          </form>
          {error && <p className="mt-2 text-xs text-red-400">{error}</p>}
        </section>

        <h2 className={settingsUi.sectionHeader}>Configured Sources</h2>
        <section className={settingsUi.group}>
          {addons.length === 0 ? (
            <div className="p-4 text-center type-callout text-secondary">
              No custom source addons configured. Using YouTube Music Opus/AAC.
            </div>
          ) : (
            addons.map((addon, index) => {
              const inCooldown = AddonManager.isAddonInCooldown(addon.id);
              return (
                <div
                  key={addon.id}
                  className="flex items-center justify-between px-4 py-3 border-b border-quaternary/40 last:border-b-0"
                >
                  <label className="flex items-center gap-3 cursor-pointer min-w-0 flex-1">
                    <input
                      type="checkbox"
                      className="size-4 accent-key rounded cursor-pointer"
                      checked={addon.enabled}
                      onChange={(e) => handleToggle(addon.id, e.target.checked)}
                    />
                    <div className="flex flex-col min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="type-body-emphasized truncate">{addon.name}</span>
                        {addon.enabled && !inCooldown && (
                          <span className="flex items-center gap-1 text-[11px] font-medium text-emerald-400 bg-emerald-500/10 px-1.5 py-0.5 rounded">
                            <CheckCircle2 size={12} />
                            Active
                          </span>
                        )}
                        {inCooldown && (
                          <span className="text-[11px] font-medium text-amber-400 bg-amber-500/10 px-1.5 py-0.5 rounded">
                            Degraded (cooldown)
                          </span>
                        )}
                      </div>
                      <span className="type-callout text-secondary truncate text-xs">{addon.baseUrl}</span>
                    </div>
                  </label>

                  <div className="flex items-center gap-1 flex-none ml-2">
                    <button
                      className="grid size-7 place-items-center rounded-[6px] text-secondary hover:bg-quaternary disabled:opacity-30"
                      aria-label="Move Up"
                      disabled={index === 0}
                      onClick={() => handleMove(addon.id, -1)}
                      title="Prioritize this source"
                    >
                      <ArrowUp size={15} />
                    </button>
                    <button
                      className="grid size-7 place-items-center rounded-[6px] text-secondary hover:bg-quaternary disabled:opacity-30"
                      aria-label="Move Down"
                      disabled={index === addons.length - 1}
                      onClick={() => handleMove(addon.id, 1)}
                      title="Lower source priority"
                    >
                      <ArrowDown size={15} />
                    </button>
                    <button
                      className="grid size-7 place-items-center rounded-[6px] text-secondary hover:bg-red-500/20 hover:text-red-400"
                      aria-label="Remove"
                      onClick={() => handleRemove(addon.id)}
                      title="Remove source"
                    >
                      <Trash2 size={15} />
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </section>
        <p className={settingsUi.footer}>
          Sources are queried sequentially from top to bottom. If a source cannot provide a verified audio stream,
          BitChord falls back to high-quality YouTube Opus automatically.
        </p>
      </div>
    </div>
  );
}
