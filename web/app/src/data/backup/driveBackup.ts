import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { clearToken, currentToken, requestToken, revoke } from './auth';
import { DRIVE_STATE_KEY } from './autostart';
import type { Summary } from './backupFile';
import { profile, Unauthorized } from './drive';
import * as replay from './replaySync';
import type { DeviceIdentity, RemoteDevice, Step } from './replaySync';
import { clearPeers, clearSynced, hasSynced, peersChanged } from './store';

/**
 * Settings › Backup & Restore › Google Drive (DriveBackup.kt): this browser's
 * listening and settings kept in the listener's Drive under a device of its
 * own, and every other device's listening brought down to add into the Replay.
 * See replaySync.ts for how that merges without counting anything twice.
 *
 * A browser has no background jobs, so it syncs while the app is open: when it
 * opens, and on the chosen schedule after that. Kept apart from the settings
 * a backup carries, as the phone keeps it apart: a restore must not sign this
 * browser out of Drive or change which device it is.
 */

export type BackupFrequency = 'OFF' | 'DAILY' | 'WEEKLY' | 'MONTHLY';
export const FREQUENCY_DAYS: Record<BackupFrequency, number> = { OFF: 0, DAILY: 1, WEEKLY: 7, MONTHLY: 30 };

/** How stale other devices' listening may be when the app opens or comes back to the front (DriveBackup.CATCH_UP_MILLIS). */
const CATCH_UP_MS = 12 * 60 * 60_000;
const DAY_MS = 24 * 60 * 60_000;

type Devices =
  | { state: 'unknown' }
  | { state: 'checking' }
  | { state: 'loaded'; list: RemoteDevice[] }
  | { state: 'failed'; message: string };

/** How the last run started from Settings went, shown there once. */
type Result =
  | { kind: 'synced'; uploaded: number; downloaded: number }
  | { kind: 'restored'; summary: Summary; device: string }
  | { kind: 'failed'; message: string; restoring: boolean };

interface Saved {
  /** The Google account syncing to, or null before one is connected. */
  account: string | null;
  accountName: string | null;
  frequency: BackupFrequency;
  lastSyncAt: number;
  lastPullAt: number;
  /** Why the last attempt failed, until one succeeds. */
  lastError: string | null;
  /** The token ran out or was refused, and only a click can ask for another. */
  needsSignIn: boolean;
  identity: DeviceIdentity;
  /** Set when another device restored as this one and it carried on under this name. */
  forkedTo: string | null;
}

interface DriveState extends Saved {
  progress: Step | null;
  devices: Devices;
  result: Result | null;
}

const INITIAL: Saved = {
  account: null,
  accountName: null,
  frequency: 'DAILY',
  lastSyncAt: 0,
  lastPullAt: 0,
  lastError: null,
  needsSignIn: false,
  identity: { installId: '', deviceId: null, deviceName: '', adoptedManifestAt: null },
  forkedTo: null,
};

export const useDriveBackup = create<DriveState>()(
  persist((): DriveState => ({ ...INITIAL, progress: null, devices: { state: 'unknown' }, result: null }), {
    name: DRIVE_STATE_KEY,
    version: 1,
    partialize: ({ progress: _p, devices: _d, result: _r, ...saved }) => saved,
  }),
);

const get = useDriveBackup.getState;
const save = useDriveBackup.setState;

/** Connected, but not yet told which device this browser is. */
export const needsDevice = (s: Saved = get()) => s.account !== null && s.identity.deviceId === null;
const ready = (s: Saved = get()) => s.account !== null && !needsDevice(s);

/** "BitChord Web (Chrome on Windows)": enough to tell this browser apart in the phone's device list. */
export function defaultDeviceName(): string {
  const ua = navigator.userAgent;
  if (location.hostname === 'tauri.localhost') return 'BitChord for Windows';
  const os = /Windows/.test(ua) ? 'Windows' : /Mac OS X/.test(ua) ? 'Mac' : /CrOS/.test(ua) ? 'ChromeOS' : /Linux/.test(ua) ? 'Linux' : '';
  const browser = /Edg\//.test(ua) ? 'Edge' : /Firefox\//.test(ua) ? 'Firefox' : /Chrome\//.test(ua) ? 'Chrome' : /Safari\//.test(ua) ? 'Safari' : 'Browser';
  return `BitChord Web (${browser}${os && ` on ${os}`})`;
}

class NeedsSignIn extends Error {
  constructor() {
    super('Sign in to Google Drive again to continue');
  }
}

const messageOf = (error: unknown) => (error instanceof Error ? error.message : String(error));

/** Runs [block] with the live token; without one, or with one Drive refuses, the run stops and Settings asks. */
async function withToken<T>(block: (token: string) => Promise<T>): Promise<T> {
  const token = currentToken();
  if (!token) {
    save({ needsSignIn: true });
    throw new NeedsSignIn();
  }
  try {
    return await block(token);
  } catch (error) {
    if (!(error instanceof Unauthorized)) throw error;
    clearToken();
    save({ needsSignIn: true });
    throw new NeedsSignIn();
  }
}

// One run at a time: the scheduled one, or one started from Settings (DriveBackup's Mutex).
let running = false;

// ---- Account ------------------------------------------------------------------------

/**
 * Google's popup (so: from a click), then whose Drive it is and the devices in
 * it. With none there yet this browser simply becomes the first; otherwise
 * Settings asks which it is. [then] carries on afterwards, as "Sync now" does
 * on a lost grant.
 */
export async function signIn(then?: () => void): Promise<void> {
  const token = await requestToken(get().account);
  const { email, name } = await profile(token);
  if (!email) throw new Error('Couldn’t tell which Google account that was');
  if (!get().identity.installId) save({ identity: { ...get().identity, installId: crypto.randomUUID() } });
  save((s) => ({
    account: email,
    accountName: name,
    needsSignIn: false,
    // An error that was only the lost grant is answered by this.
    lastError: s.needsSignIn ? null : s.lastError,
  }));
  await refreshDevices();
  const devices = get().devices;
  if (needsDevice() && devices.state === 'loaded' && devices.list.length === 0) chooseNewDevice();
  else then?.();
}

/** Forgets the account and withdraws the grant. Everything in Drive stays. */
export function disconnect(): void {
  if (!get().account) return;
  save((s) => ({ ...INITIAL, frequency: s.frequency, identity: s.identity, devices: { state: 'unknown' }, result: null }));
  void clearPeers().then(peersChanged);
  void revoke();
}

export const setFrequency = (frequency: BackupFrequency) => save({ frequency });

/** Renames this browser as other devices will list it; sent with the next sync. */
export function rename(name: string): void {
  const trimmed = name.trim().slice(0, 60);
  if (trimmed) save((s) => ({ identity: { ...s.identity, deviceName: trimmed } }));
}

export const dismissForkNotice = () => save({ forkedTo: null });
export const clearResult = () => save({ result: null });

// ---- Devices ------------------------------------------------------------------------

export async function refreshDevices(): Promise<void> {
  if (!get().account) {
    save({ devices: { state: 'unknown' } });
    return;
  }
  save({ devices: { state: 'checking' } });
  try {
    save({ devices: { state: 'loaded', list: await withToken(replay.devices) } });
  } catch (error) {
    save({ devices: error instanceof NeedsSignIn ? { state: 'unknown' } : { state: 'failed', message: messageOf(error) } });
  }
}

/** Backs this browser up as a device of its own: its listening stays its own, every other device's adds to it. */
export function chooseNewDevice(): void {
  if (get().identity.deviceId) return;
  save((s) => ({
    identity: { ...s.identity, deviceId: crypto.randomUUID(), deviceName: s.identity.deviceName || defaultDeviceName(), adoptedManifestAt: null },
  }));
  // The first upload, and every other device's listening down, straight away.
  void syncNow();
}

/** Makes this browser [device]: see replaySync.restoreAs. */
export async function restoreAs(device: RemoteDevice): Promise<void> {
  if (running) return;
  running = true;
  let restored = false;
  try {
    // A browser that has never synced has listening that is in no device yet, so it is kept.
    const keepLocal = get().identity.deviceId === null || !(await hasSynced());
    const { identity, summary } = await withToken((token) =>
      replay.restoreAs(token, get().identity, device, keepLocal, (progress) => save({ progress })),
    );
    save({ identity, forkedTo: null, result: { kind: 'restored', summary, device: device.name } });
    restored = true;
  } catch (error) {
    if (!(error instanceof NeedsSignIn)) save({ result: { kind: 'failed', message: messageOf(error), restoring: true } });
  } finally {
    save({ progress: null });
    running = false;
  }
  // Then a sync at once: this browser takes ownership of the device in Drive,
  // and every other device's listening comes down.
  if (restored) await runSync(true);
}

/**
 * Deletes [device]'s backup from Drive: its listening leaves the Replay
 * everywhere. This browser's own can go too; its Replay keeps everything, and
 * auto sync is switched off, or the next run would upload it all again.
 */
export async function removeDevice(device: RemoteDevice): Promise<void> {
  if (running) return;
  running = true;
  const mine = device.deviceId === get().identity.deviceId;
  try {
    await withToken((token) => replay.removeDevice(token, device));
    if (mine) {
      await clearSynced();
      save((s) => ({ frequency: 'OFF', lastSyncAt: 0, lastError: null, identity: { ...s.identity, adoptedManifestAt: null } }));
    }
  } catch (error) {
    if (!(error instanceof NeedsSignIn)) save({ result: { kind: 'failed', message: messageOf(error), restoring: false } });
  } finally {
    running = false;
  }
  await refreshDevices();
}

// ---- Running ------------------------------------------------------------------------

export async function syncNow(): Promise<void> {
  const outcome = await runSync(true);
  if (outcome) save({ result: outcome });
}

/** One sync; with [push] false, only other devices' listening comes down. Null when it didn't run or needs sign-in. */
async function runSync(push: boolean): Promise<Result | null> {
  if (!ready() || running) return null;
  running = true;
  try {
    const report = await withToken((token) =>
      replay.sync(token, get().identity, push, defaultDeviceName(), (progress) => save({ progress })),
    );
    const now = Date.now();
    save((s) => ({
      identity: report.identity,
      lastPullAt: now,
      lastSyncAt: push ? now : s.lastSyncAt,
      lastError: null,
      forkedTo: report.forkedTo ?? s.forkedTo,
      devices: { state: 'loaded', list: report.devices },
    }));
    return { kind: 'synced', uploaded: report.uploaded, downloaded: report.downloaded };
  } catch (error) {
    if (error instanceof NeedsSignIn) return null;
    const message = messageOf(error);
    save({ lastError: message });
    return { kind: 'failed', message, restoring: false };
  } finally {
    save({ progress: null });
    running = false;
  }
}

/** The schedule, while the app is open: a full sync when one is due, else a pull when other devices' listening is stale. */
function tick(): void {
  const s = get();
  if (!ready(s) || s.needsSignIn) return;
  const now = Date.now();
  if (s.frequency !== 'OFF' && now - s.lastSyncAt >= FREQUENCY_DAYS[s.frequency] * DAY_MS) void runSync(true);
  else if (now - s.lastPullAt >= CATCH_UP_MS) void runSync(false);
}

let started = false;

export function startDriveSync(): void {
  if (started) return;
  started = true;
  tick();
  setInterval(tick, 15 * 60_000);
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') tick();
  });
}
