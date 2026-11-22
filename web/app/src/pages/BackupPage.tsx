import { CalendarClock, Cloud, CloudUpload, FileDown, FileUp, History, Laptop, MonitorSmartphone, RefreshCw, User, type LucideIcon } from 'lucide-react';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { PageHeader } from '@/components/PageHeader';
import { driveAvailable } from '@/data/backup/auth';
import { exportBackup, importBackup, suggestedName } from '@/data/backup/backupFile';
import {
  chooseNewDevice,
  clearResult,
  defaultDeviceName,
  disconnect,
  dismissForkNotice,
  needsDevice,
  refreshDevices,
  removeDevice,
  rename,
  restoreAs,
  setFrequency,
  signIn,
  startDriveSync,
  syncNow,
  useDriveBackup,
  type BackupFrequency,
} from '@/data/backup/driveBackup';
import type { RemoteDevice, Step } from '@/data/backup/replaySync';
import { formatBytes } from './DataSettings';
import { ChoiceRow, FieldRow, Group, RowGlyph } from './SettingsRows';
import { settingsUi } from './settingsStyles';

const error = 'mx-1 mt-2 type-callout text-red';
const primary = 'mt-4 rounded-pill bg-key-bg px-4 py-1.5 type-body-emphasized text-on-key disabled:opacity-45';

const FREQUENCIES: { value: BackupFrequency; label: string }[] = [
  { value: 'OFF', label: 'Off' },
  { value: 'DAILY', label: 'Daily' },
  { value: 'WEEKLY', label: 'Weekly' },
  { value: 'MONTHLY', label: 'Monthly' },
];

const formatWhen = (ms: number) => new Date(ms).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
const months = (n: number) => `${n} ${n === 1 ? 'month' : 'months'}`;
const messageOf = (e: unknown) => (e instanceof Error ? e.message : String(e));

/** A row with its glyph, a title, an optional line under it, and whatever goes on the right. */
function Row({ icon, title, detail, children }: { icon: LucideIcon; title: ReactNode; detail?: ReactNode; children?: ReactNode }) {
  return (
    <div className={settingsUi.glyphRow}>
      <RowGlyph icon={icon} />
      <span className={settingsUi.rowText}>
        <span>{title}</span>
        {detail && <span className={settingsUi.rowDetail}>{detail}</span>}
      </span>
      {children && <span className={`${settingsUi.value} flex-none`}>{children}</span>}
    </div>
  );
}

/**
 * Settings › Backup & Restore (the phone's Backup and Restore, with its Google
 * Drive backup page folded in): a backup file to keep or move, and, where the
 * site has a Google client set up, this browser as one more device in the
 * phone's Drive backup.
 */
export default function BackupPage() {
  return (
    <div className={settingsUi.centered}>
      <PageHeader title="Backup & Restore" />
      <div className={settingsUi.body}>
        <BackupFileSection />
        {driveAvailable() && <DriveSection />}
      </div>
    </div>
  );
}

/** Export and Import, in the phone's own file format. */
function BackupFileSection() {
  const picker = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [line, setLine] = useState<{ text: string; failed: boolean } | null>(null);

  const save = async () => {
    setBusy(true);
    try {
      const { text, months: n } = await exportBackup();
      const link = document.createElement('a');
      link.href = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
      link.download = suggestedName();
      link.click();
      setTimeout(() => URL.revokeObjectURL(link.href), 10_000);
      setLine({ text: `Saved ${link.download}, with your settings and ${months(n)} of listening.`, failed: false });
    } catch (e) {
      setLine({ text: `Couldn’t save the backup: ${messageOf(e)}`, failed: true });
    } finally {
      setBusy(false);
    }
  };

  const restore = async (file: File) => {
    if (!window.confirm(`Replace this device’s settings and listening with ${file.name}?`)) return;
    setBusy(true);
    try {
      const summary = await importBackup(await file.text());
      setLine({ text: `Restored ${months(summary.months)} of listening and ${summary.settings} settings${summary.from ? ` from BitChord ${summary.from}` : ''}.`, failed: false });
    } catch (e) {
      setLine({ text: messageOf(e), failed: true });
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <Group header="Backup File">
        <Row icon={FileUp} title="Export Backup" detail="Your settings and listening, as a file you keep.">
          <button className={settingsUi.inlineButton} disabled={busy} onClick={() => void save()}>
            Export
          </button>
        </Row>
        <Row icon={FileDown} title="Import Backup" detail="From BitChord on any device, or the Android app. Replaces what’s here.">
          <button className={settingsUi.inlineButton} disabled={busy} onClick={() => picker.current?.click()}>
            Import
          </button>
          <input
            ref={picker}
            type="file"
            accept=".json,application/json"
            hidden
            onChange={(e) => {
              const file = e.currentTarget.files?.[0];
              e.currentTarget.value = '';
              if (file) void restore(file);
            }}
          />
        </Row>
      </Group>
      {line ? (
        <p className={line.failed ? error : settingsUi.footer}>{line.text}</p>
      ) : (
        <p className={settingsUi.footer}>
          The same file the BitChord app makes. Settings this player doesn’t have are kept in it as they were, so a backup that
          passes through here still restores everything on your phone.
        </p>
      )}
    </>
  );
}

/** What a run is doing, in words (DriveBackupScreen's ProgressBlock). */
function stepText(step: Step): string {
  switch (step.kind) {
    case 'preparing':
      return 'Preparing…';
    case 'uploading':
      return `Uploading ${Math.max(1, Math.min(step.done + 1, step.total))} of ${Math.max(1, step.total)}…`;
    case 'downloading':
      return `Downloading from ${step.device}, ${Math.min(step.done + 1, step.total)} of ${step.total}…`;
    case 'restoring':
      return 'Restoring…';
  }
}

/** "Synced 12 Nov 2026, 21:04 · 14 months · 1.2 MB": enough to tell devices apart. */
function describe(device: RemoteDevice): string {
  return [
    device.updatedAt > 0 ? `Synced ${formatWhen(device.updatedAt)}` : null,
    months(device.months),
    device.sizeBytes > 0 ? formatBytes(device.sizeBytes) : null,
  ]
    .filter(Boolean)
    .join(' · ');
}

/** Google Drive backup (DriveBackupScreen and DriveDevicesScreen on the phone). */
function DriveSection() {
  const s = useDriveBackup();
  const [connectError, setConnectError] = useState<string | null>(null);
  const [name, setName] = useState(s.identity.deviceName);
  const running = s.progress !== null;
  const connected = s.account !== null;
  const choosing = needsDevice(s);
  const ready = connected && !choosing;
  const devices = s.devices.state === 'loaded' ? s.devices.list : [];
  const others = devices.filter((d) => d.deviceId !== s.identity.deviceId);

  useEffect(() => setName(s.identity.deviceName), [s.identity.deviceName]);
  // Looked up each time the page opens, so it never lists a device since removed or renamed.
  useEffect(() => {
    if (connected && !s.needsSignIn) void refreshDevices();
  }, [connected, s.needsSignIn]);

  const connect = (then?: () => void) => {
    setConnectError(null);
    signIn(then)
      .then(() => startDriveSync())
      .catch((e: unknown) => setConnectError(messageOf(e)));
  };

  const result = s.result;
  const resultLine =
    result?.kind === 'restored'
      ? `Restored as ${result.device}: ${months(result.summary.months)} of listening.`
      : result?.kind === 'failed'
        ? `${result.restoring ? 'Restore' : 'Sync'} failed: ${result.message}`
        : result?.kind === 'synced'
          ? 'Synced.'
          : s.lastError && `Sync failed: ${s.lastError}`;

  return (
    <>
      <Group header="Google Drive">
        <Row icon={History} title="Last Sync" detail={running ? stepText(s.progress!) : resultLine}>
          {s.lastSyncAt > 0 ? formatWhen(s.lastSyncAt) : 'Never'}
        </Row>
      </Group>
      <button
        className={primary}
        disabled={!ready || running}
        onClick={() => {
          clearResult();
          if (s.needsSignIn) connect(() => void syncNow());
          else void syncNow();
        }}
      >
        Sync Now
      </button>

      <Group header="Account">
        {!connected ? (
          <Row icon={Cloud} title="Connect Google Drive" detail="Back up to a hidden folder only BitChord can see.">
            <button className={settingsUi.inlineButton} onClick={() => connect()}>
              Connect
            </button>
          </Row>
        ) : s.needsSignIn ? (
          <Row icon={User} title={s.account} detail="Google’s sign-in lasts an hour here. Sign in again to sync.">
            <button className={settingsUi.inlineButton} onClick={() => connect()}>
              Sign In Again
            </button>
          </Row>
        ) : (
          <Row icon={User} title={s.accountName ?? s.account} detail={s.accountName ? s.account : undefined}>
            <button
              className={settingsUi.inlineButton}
              disabled={running}
              onClick={() => window.confirm('Disconnect Google Drive? Everything already in Drive stays there.') && disconnect()}
            >
              Disconnect
            </button>
          </Row>
        )}
      </Group>
      {connectError && <p className={error}>Couldn’t connect: {connectError}</p>}

      {connected && (
        <>
          <Group
            header="This Device"
            footer={
              choosing
                ? 'Your other devices’ listening adds to this one’s in Replay. Or restore as one of them, below.'
                : s.forkedTo
                  ? `Another device restored as this one, so this device carried on as ${s.forkedTo}.`
                  : 'How your other devices list this one.'
            }
          >
            {choosing ? (
              <Row icon={CloudUpload} title="Back Up as a New Device" detail={defaultDeviceName()}>
                <button className={settingsUi.inlineButton} disabled={running} onClick={chooseNewDevice}>
                  Back Up
                </button>
              </Row>
            ) : (
              <FieldRow icon={Laptop} title="Name">
                <input
                  className={settingsUi.input}
                  value={name}
                  maxLength={60}
                  disabled={running}
                  onChange={(e) => setName(e.currentTarget.value)}
                  onFocus={dismissForkNotice}
                  onBlur={() => (name.trim() ? rename(name) : setName(s.identity.deviceName))}
                  onKeyDown={(e) => e.key === 'Enter' && e.currentTarget.blur()}
                />
              </FieldRow>
            )}
          </Group>
        </>
      )}

      <Group
        header="Auto Sync"
        footer="While BitChord is open: when it opens, then on this schedule. Other devices’ listening comes down at least twice a day. Nothing syncs while BitChord is closed."
      >
        <ChoiceRow icon={CalendarClock} title="Back Up" options={FREQUENCIES} value={s.frequency} onChange={setFrequency} />
      </Group>

      {connected && !s.needsSignIn && (
        <>
          <Group header="Devices" footer="Every device’s listening adds up in Replay. Each one writes only its own.">
            {s.devices.state === 'failed' ? (
              <Row icon={RefreshCw} title={`Couldn’t list devices: ${s.devices.message}`}>
                <button className={settingsUi.inlineButton} onClick={() => void refreshDevices()}>
                  Try Again
                </button>
              </Row>
            ) : s.devices.state !== 'loaded' ? (
              <Row icon={MonitorSmartphone} title="Checking…" />
            ) : devices.length === 0 ? (
              <Row icon={MonitorSmartphone} title="No devices backed up yet" />
            ) : (
              devices.map((device) => {
                const mine = device.deviceId === s.identity.deviceId;
                return (
                  <Row key={device.deviceId} icon={MonitorSmartphone} title={device.name} detail={describe(device)}>
                    {mine && 'This device'}
                    <button
                      className={settingsUi.inlineButton}
                      disabled={running}
                      onClick={() =>
                        window.confirm(
                          mine
                            ? 'Delete this device’s backup from Google Drive? Its listening stays here, and auto sync turns off.'
                            : `Delete ${device.name}’s backup from Google Drive? Its listening leaves Replay on every device.`,
                        ) && void removeDevice(device)
                      }
                    >
                      Delete
                    </button>
                  </Row>
                );
              })
            )}
          </Group>

          <Group
            header="Restore"
            footer="Restoring makes this device that one, as when you move to a new phone. Use it to pick up where a device left off."
          >
            {others.length === 0 ? (
              <Row icon={MonitorSmartphone} title="No other devices to restore from" />
            ) : (
              others.map((device) => (
                <Row key={device.deviceId} icon={MonitorSmartphone} title={device.name} detail={describe(device)}>
                  <button
                    className={settingsUi.inlineButton}
                    disabled={running}
                    onClick={() =>
                      window.confirm(
                        `Restore as ${device.name}? Its listening and settings replace this device’s, and this device carries on as it.`,
                      ) && void restoreAs(device)
                    }
                  >
                    Restore
                  </button>
                </Row>
              ))
            )}
          </Group>
        </>
      )}
    </>
  );
}
