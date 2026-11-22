import { useQuery } from '@tanstack/react-query';
import { ChevronRight } from 'lucide-react';
import { useEffect } from 'react';
import { PrefetchLink } from '@/components/PrefetchLink';
import { driveAvailable } from '@/data/backup/auth';
import { useSignedIn } from '@/data/music/queries';
import { useOfflineSettings, useOfflineStatus } from '@/playback/offline/settings';
import { offlineSupported, offlineTracks, removeAllOffline } from '@/playback/offline/store';
import { usePremiumAudio } from '@/playback/youtube/premiumAudio';
import { Toggle } from './IntegrationSettings';
import { settingsUi } from './settingsStyles';

/** "4.2 MB", "1.3 GB". */
export function formatBytes(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
  return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
}

/**
 * YouTube Premium Audio, with the account it is a claim about (the phone's
 * Account & Integrations): only shown signed in, since only a signed-in
 * Premium session is offered the higher Opus rung.
 */
export function PremiumAudioToggle() {
  const signedIn = useSignedIn();
  const on = usePremiumAudio((s) => s.on);
  const setOn = usePremiumAudio((s) => s.setOn);
  if (!signedIn) return null;
  return (
    <Toggle
      label="YouTube Premium Audio"
      detail="I have Premium: stream the higher-quality ~256 kbps Opus instead of the standard 160 kbps."
      checked={on}
      onChange={setOn}
    />
  );
}

/** Settings' Backup & Restore row and Downloads section. */
export function DataSettings() {
  return (
    <>
      <h2 className={settingsUi.sectionHeader}>Backup & Restore</h2>
      <section className={settingsUi.group}>
        <PrefetchLink to="/settings/backup" className={`${settingsUi.integrationPlain} ${settingsUi.linkRow}`}>
          <span className={settingsUi.integrationName}>Backup & Restore</span>
          <span className={settingsUi.value}>
            {driveAvailable() ? 'Backup file, Google Drive' : 'Backup file'}
            <ChevronRight size={16} />
          </span>
        </PrefetchLink>
      </section>
      {offlineSupported() && <Downloads />}
    </>
  );
}

/** Keep Liked Songs Downloaded (the phone's Downloads settings), what it takes up, and the way to take it back. */
function Downloads() {
  const signedIn = useSignedIn();
  const keepLiked = useOfflineSettings((s) => s.keepLiked);
  const setKeepLiked = useOfflineSettings((s) => s.setKeepLiked);
  const status = useOfflineStatus();
  const usage = useQuery({
    queryKey: ['offline', 'usage'],
    queryFn: async () => {
      const tracks = await offlineTracks();
      const estimate = await navigator.storage?.estimate?.().catch(() => undefined);
      return { count: tracks.length, bytes: tracks.reduce((sum, t) => sum + t.sizeBytes, 0), quota: estimate?.quota ?? null };
    },
  });
  const { refetch } = usage;
  // Counted again as each download lands.
  useEffect(() => void refetch(), [status.done, status.running, refetch]);

  const remove = async () => {
    if (!window.confirm('Remove every downloaded song from this browser? Keep Liked Songs Downloaded is turned off too.')) return;
    setKeepLiked(false);
    await removeAllOffline();
    void refetch();
  };

  const u = usage.data;
  const notes = [
    status.running && status.total > 0 ? `Downloading ${Math.min(status.done + 1, status.total)} of ${status.total}…` : null,
    status.skippedSabr > 0 ? `${status.skippedSabr} songs YouTube only streams in pieces (SABR) couldn’t be saved.` : null,
    status.failed > 0 ? `${status.failed} songs couldn’t be downloaded; they’re tried again next time.` : null,
    status.error,
  ].filter(Boolean);

  return (
    <>
      <h2 className={settingsUi.sectionHeader}>Downloads</h2>
      <section className={settingsUi.group}>
        <Toggle
          label="Keep Liked Songs Downloaded"
          detail={
            signedIn
              ? 'Your Liked Music, saved in this browser to play without a connection. Checked every 12 hours while BitChord is open.'
              : 'Sign in to YouTube Music in this browser to keep your liked songs downloaded.'
          }
          checked={keepLiked}
          onChange={(on) => {
            setKeepLiked(on);
            if (on) {
              void import('@/playback/offline/likedSync').then((m) => {
                m.startLikedSync();
                void m.checkLiked();
              });
            }
          }}
        />
        <div className={settingsUi.row}>
          <span>Downloaded</span>
          <span className={settingsUi.value}>
            {!u ? '…' : u.count === 0 ? 'None' : `${u.count} ${u.count === 1 ? 'song' : 'songs'} · ${formatBytes(u.bytes)}`}
            {u && u.count > 0 && (
              <button className={settingsUi.inlineButton} onClick={() => void remove()}>
                Remove Downloads
              </button>
            )}
          </span>
        </div>
      </section>
      <p className={settingsUi.footer}>
        {[...notes, u?.quota ? `This browser lets BitChord keep up to ${formatBytes(u.quota)}.` : null].filter(Boolean).join(' ')}
      </p>
    </>
  );
}
