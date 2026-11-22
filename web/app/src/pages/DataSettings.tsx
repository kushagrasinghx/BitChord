import { useQuery } from '@tanstack/react-query';
import { ArchiveRestore, AudioLines, ChartColumn, CircleArrowDown, HardDrive } from 'lucide-react';
import { useEffect } from 'react';
import { PrefetchLink } from '@/components/PrefetchLink';
import { driveAvailable } from '@/data/backup/auth';
import { useSignedIn } from '@/data/music/queries';
import { useOfflineSettings, useOfflineStatus } from '@/playback/offline/settings';
import { offlineTracks, removeAllOffline } from '@/playback/offline/store';
import { usePremiumAudio } from '@/playback/youtube/premiumAudio';
import { Chevron, Group, RowGlyph, SettingsRow, Toggle, useMatches, type Glyph } from './SettingsRows';
import { settingsUi } from './settingsStyles';

/** "4.2 MB", "1.3 GB". */
export function formatBytes(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
  return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
}

/**
 * YouTube Premium Audio, the Audio quality card's row (SettingsSheet.kt).
 * Only a signed-in Premium session is offered the higher Opus rung, so signed
 * out it is greyed with the reason rather than hidden, as the phone treats
 * Dolby Atmos: a missing row reads as a feature BitChord doesn't have.
 */
export function PremiumAudioToggle() {
  const signedIn = useSignedIn();
  const on = usePremiumAudio((s) => s.on);
  const setOn = usePremiumAudio((s) => s.setOn);
  return (
    <Toggle
      icon={AudioLines}
      label="YouTube Premium Audio"
      detail={
        signedIn
          ? 'I have Premium: stream the higher-quality ~256 kbps Opus instead of the standard 160 kbps.'
          : 'Sign in with a YouTube Premium account to stream the higher-quality ~256 kbps Opus.'
      }
      keywords={['quality', 'opus', 'bitrate', 'kbps', 'streaming']}
      checked={on && signedIn}
      disabled={!signedIn}
      onChange={setOn}
    />
  );
}

/** Your Data (SettingsSheet.kt YOUR_DATA): the Replay, and Backup & Restore. */
export function YourDataSettings() {
  return (
    <Group header="Your Data">
      <LinkRow to="/replay" icon={ChartColumn} title="Replay" keywords={['stats', 'history', 'wrapped']} />
      <LinkRow
        to="/settings/backup"
        icon={ArchiveRestore}
        title="Backup & Restore"
        value={driveAvailable() ? 'File, Google Drive' : 'Backup file'}
        keywords={['backup', 'restore', 'export', 'import', 'google', 'drive', 'cloud', 'sync']}
      />
    </Group>
  );
}

/** A glyph row that opens a page of its own. */
function LinkRow({ to, icon, title, value, keywords }: { to: string; icon: Glyph; title: string; value?: string; keywords?: string[] }) {
  if (!useMatches(title, keywords)) return null;
  return (
    <PrefetchLink to={to} className={settingsUi.pressableRow}>
      <RowGlyph icon={icon} />
      <span className="min-w-0 flex-1">{title}</span>
      <span className={settingsUi.value}>
        {value}
        <Chevron />
      </span>
    </PrefetchLink>
  );
}

/** Downloads (SettingsSheet.kt DOWNLOADS): Keep Liked Songs Downloaded, what it takes up, and the way to take it back. */
export function DownloadsSettings() {
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
    if (!window.confirm('Remove every downloaded song from this device? Keep Liked Songs Downloaded is turned off too.')) return;
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
    <Group
      header="Downloads"
      footer={[...notes, u?.quota ? `BitChord can keep up to ${formatBytes(u.quota)} here.` : null].filter(Boolean).join(' ') || undefined}
    >
      <Toggle
        icon={CircleArrowDown}
        label="Keep Liked Songs Downloaded"
        keywords={['liked', 'favorites', 'offline', 'automatic']}
        detail={
          signedIn
            ? 'Your Liked Music, saved on this device to play without a connection. Checked every 12 hours while BitChord is open.'
            : 'Sign in to YouTube Music here to keep your liked songs downloaded.'
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
      <SettingsRow icon={HardDrive} title="Downloaded" keywords={['storage', 'offline', 'remove', 'space']}>
        <span className={settingsUi.value}>
          {!u ? '…' : u.count === 0 ? 'None' : `${u.count} ${u.count === 1 ? 'song' : 'songs'} · ${formatBytes(u.bytes)}`}
          {u && u.count > 0 && (
            <button className={settingsUi.inlineButton} onClick={() => void remove()}>
              Remove Downloads
            </button>
          )}
        </span>
      </SettingsRow>
    </Group>
  );
}
