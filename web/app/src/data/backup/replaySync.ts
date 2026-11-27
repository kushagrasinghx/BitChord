import {
  decodeManifest,
  encodeManifest,
  MonthHash,
  syncPull,
  syncPush,
  syncSupersededBy,
  WebPref,
  type WebManifest,
} from '@bitchord/shared-kotlin';
import { gunzip, gzip, sha256Hex } from './bytes';
import * as drive from './drive';
import type { DriveFile } from './drive';
import { listening } from './listening';
import { exportSettings, importSettings, type PrefValue } from './prefs';
import type { Summary } from './backupFile';
import {
  clearSynced,
  deleteSynced,
  peerMonths,
  peersChanged,
  putPeerMonth,
  removePeer,
  removePeerMonth,
  syncedMonths,
  touchPeer,
  writeSynced,
} from './store';

/**
 * Keeps this account's devices' listening in step through Google Drive, in the
 * Android app's own format (ReplaySync.kt), so this browser is one more device
 * in the phone's list.
 *
 * Each device writes only its own partition, `replay-<deviceId>-<month>` plus
 * `device-<deviceId>`, and downloads everybody else's as peers, which the
 * Replay adds in when it is read. Nothing is ever folded into anyone else's
 * partition, so every play is in exactly one place, and a sync can run any
 * number of times in any order without changing the totals. What to upload,
 * download and drop is the phone's own SyncPlanner (shared/commonMain).
 */

const KIND = 'kind';
const KIND_DEVICE = 'device';
const KIND_REPLAY = 'replay';
const DEVICE_ID = 'deviceId';
const MONTH = 'month';
/** The manifest's versionName, for the reader: the phone shows which build a device last synced with. */
const VERSION_NAME = 'web';

/** DeviceIdentity (SyncPlanner.kt): which partition this browser writes, and who owns it. */
export interface DeviceIdentity {
  installId: string;
  deviceId: string | null;
  deviceName: string;
  adoptedManifestAt: string | null;
}

/** A device found in Drive, for the device list and the chooser. */
export interface RemoteDevice {
  deviceId: string;
  name: string;
  updatedAt: number;
  months: number;
  sizeBytes: number;
  manifest: WebManifest;
}

export type Step =
  | { kind: 'preparing' }
  | { kind: 'uploading'; done: number; total: number }
  | { kind: 'downloading'; device: string; done: number; total: number }
  | { kind: 'restoring' };

export interface Report {
  identity: DeviceIdentity;
  uploaded: number;
  downloaded: number;
  devices: RemoteDevice[];
  /** Set when another device restored as this one: the name this one forked to. */
  forkedTo: string | null;
}

/** What Drive holds, sorted into devices. */
interface Listing {
  manifests: Map<string, DriveFile>;
  /** Device → month → file. */
  months: Map<string, Map<string, DriveFile>>;
}

function listingOf(files: DriveFile[]): Listing {
  const manifests = new Map<string, DriveFile>();
  const months = new Map<string, Map<string, DriveFile>>();
  for (const file of files) {
    const device = file.properties[DEVICE_ID];
    if (!device) continue;
    if (file.properties[KIND] === KIND_DEVICE) manifests.set(device, file);
    const month = file.properties[MONTH];
    if (file.properties[KIND] === KIND_REPLAY && month) {
      if (!months.has(device)) months.set(device, new Map());
      months.get(device)!.set(month, file);
    }
  }
  return { manifests, months };
}

const encoder = new TextEncoder();
const decoder = new TextDecoder();

async function readManifest(token: string, file: DriveFile): Promise<string> {
  return decoder.decode(await gunzip(await drive.download(token, file.id)));
}

const hashesOf = (map: Map<string, string | null>) => [...map].map(([month, hash]) => new MonthHash(month, hash));

const prefsOf = (settings: Record<string, PrefValue>) =>
  Object.entries(settings).map(([key, p]) => new WebPref(key, p.type, p.value, p.values));

const settingsOf = (manifest: WebManifest): Record<string, PrefValue> =>
  Object.fromEntries(manifest.settings.map((p) => [p.key, { type: p.type as PrefValue['type'], value: p.value ?? null, values: [...p.values] }]));

/** Every device in Drive, this one included, newest sync first. */
export async function devices(token: string): Promise<RemoteDevice[]> {
  return devicesOf(token, listingOf(await drive.listAll(token)));
}

async function devicesOf(token: string, listing: Listing): Promise<RemoteDevice[]> {
  const found = await Promise.all(
    [...listing.manifests].map(async ([id, file]): Promise<RemoteDevice | null> => {
      const manifest = await readManifest(token, file).then(decodeManifest, () => null);
      if (!manifest) return null;
      const files = listing.months.get(id) ?? new Map<string, DriveFile>();
      let sizeBytes = file.sizeBytes;
      for (const f of files.values()) sizeBytes += f.sizeBytes;
      return {
        deviceId: id,
        name: manifest.name,
        updatedAt: manifest.updatedAt,
        months: manifest.months.filter((m) => files.has(m.month)).length,
        sizeBytes,
        manifest,
      };
    }),
  );
  return found.filter((d): d is RemoteDevice => d !== null).sort((a, b) => b.updatedAt - a.updatedAt);
}

/**
 * One sync (ReplaySync.sync). With [push], this device's changed months and
 * then its manifest go up first; then every other device's changed months
 * come down.
 */
export async function sync(
  token: string,
  identity: DeviceIdentity,
  push: boolean,
  defaultName: string,
  onStep: (step: Step) => void,
): Promise<Report> {
  onStep({ kind: 'preparing' });
  let me = identity;
  if (!me.deviceId) throw new Error('Choose which device this is first');
  let listing = listingOf(await drive.listAll(token));
  let forkedTo: string | null = null;

  // Another device has restored as this one since this one last synced.
  // Whatever was played here since that last sync is this browser's alone, so
  // it carries on as a device of its own with exactly that, not the whole
  // history, which is now the other device's.
  const ownFile = listing.manifests.get(me.deviceId);
  const ownText = ownFile ? await readManifest(token, ownFile).catch(() => null) : null;
  if (syncSupersededBy(me.installId, me.adoptedManifestAt, ownText, ownFile?.modifiedAt ?? null)) {
    const source = listening();
    const synced = await syncedMonths();
    const unsynced = new Map<string, Uint8Array>();
    for (const [month, bytes] of await source.exportMonths()) {
      const before = synced.get(month);
      const left = before ? source.minus(bytes, before) : bytes;
      if (left) unsynced.set(month, left);
    }
    await source.importMonths(unsynced);
    await clearSynced();
    const name = `${me.deviceName.trim() || defaultName} (2)`;
    me = { ...me, deviceId: crypto.randomUUID(), deviceName: name, adoptedManifestAt: null };
    forkedTo = name;
  }
  const myId = me.deviceId!;

  let uploaded = 0;
  if (push) {
    const own = await listening().exportMonths();
    const hashes = new Map<string, string>();
    for (const [month, bytes] of own) hashes.set(month, await sha256Hex(bytes));
    const synced = new Map<string, string>();
    for (const [month, bytes] of await syncedMonths()) if (hashes.has(month)) synced.set(month, await sha256Hex(bytes));
    const remoteOwn = listing.months.get(myId) ?? new Map<string, DriveFile>();
    const plan = syncPush(hashesOf(hashes), hashesOf(synced), [...remoteOwn.keys()]);
    for (const [index, month] of plan.upload.entries()) {
      onStep({ kind: 'uploading', done: index, total: plan.upload.length });
      const bytes = own.get(month)!;
      await drive.put(
        token,
        `replay-${myId}-${month}.json.gz`,
        await gzip(bytes),
        { [KIND]: KIND_REPLAY, [DEVICE_ID]: myId, [MONTH]: month },
        remoteOwn.get(month)?.id ?? null,
      );
      await writeSynced(month, bytes);
      uploaded++;
    }
    for (const month of plan.delete) {
      const file = remoteOwn.get(month);
      if (file) await drive.remove(token, file.id);
      await deleteSynced(month);
    }
    // Last, so a peer never reads a month list ahead of its months.
    onStep({ kind: 'uploading', done: plan.upload.length, total: plan.upload.length });
    const manifest = encodeManifest(myId, me.deviceName, me.installId, Date.now(), VERSION_NAME, hashesOf(hashes), prefsOf(exportSettings()));
    const written = await drive.put(
      token,
      `device-${myId}.json.gz`,
      await gzip(encoder.encode(manifest)),
      { [KIND]: KIND_DEVICE, [DEVICE_ID]: myId },
      listing.manifests.get(myId)?.id ?? null,
    );
    // This install now owns the manifest outright.
    me = { ...me, adoptedManifestAt: written.modifiedAt };
    listing = listingOf(await drive.listAll(token));
  }

  const found = await devicesOf(token, listing);
  const downloaded = await pull(token, listing, found, myId, onStep);
  return { identity: me, uploaded, downloaded, devices: found, forkedTo };
}

/** Brings every other device's changed months down, and drops what has gone. */
async function pull(token: string, listing: Listing, found: RemoteDevice[], myId: string, onStep: (step: Step) => void): Promise<number> {
  let downloaded = 0;
  let changed = false;
  const held = new Map<string, Map<string, string>>();
  for (const peer of await peerMonths()) {
    if (!held.has(peer.deviceId)) held.set(peer.deviceId, new Map());
    held.get(peer.deviceId)!.set(peer.month, peer.md5);
  }
  // Gone from Drive, or now this browser's own partition.
  for (const id of held.keys()) {
    if (id === myId || !found.some((d) => d.deviceId === id)) {
      await removePeer(id);
      changed = true;
    }
  }
  for (const device of found) {
    if (device.deviceId === myId) continue;
    const files = listing.months.get(device.deviceId) ?? new Map<string, DriveFile>();
    const plan = syncPull(
      device.manifest.months.map((m) => m.month),
      [...files].map(([month, file]) => new MonthHash(month, file.md5)),
      hashesOf(held.get(device.deviceId) ?? new Map()),
    );
    await touchPeer(device.deviceId, device.name, device.updatedAt);
    for (const [index, month] of plan.download.entries()) {
      onStep({ kind: 'downloading', device: device.name, done: index, total: plan.download.length });
      const file = files.get(month)!;
      const bytes = await gunzip(await drive.download(token, file.id));
      // Checked before it is kept: a month this build can't read is left out
      // rather than breaking the Replay it would be added to.
      if (listening().readable(bytes)) {
        await putPeerMonth({ deviceId: device.deviceId, name: device.name, updatedAt: device.updatedAt, month, bytes, md5: file.md5 ?? '' });
        downloaded++;
        changed = true;
      }
    }
    for (const month of plan.drop) {
      await removePeerMonth(device.deviceId, month);
      changed = true;
    }
  }
  if (changed) peersChanged();
  return downloaded;
}

/**
 * Makes this browser [device] (ReplaySync.restoreAs): its months and settings
 * replace this browser's, and this install becomes the one that writes them.
 *
 * With [keepLocal] (a browser that has never synced, whose listening is in no
 * partition yet) this browser's own months are added to the adopted ones
 * rather than lost: counter by counter the larger of the two, so an older copy
 * of this very device isn't counted twice.
 */
export async function restoreAs(
  token: string,
  identity: DeviceIdentity,
  device: RemoteDevice,
  keepLocal: boolean,
  onStep: (step: Step) => void,
): Promise<{ identity: DeviceIdentity; summary: Summary }> {
  const listing = listingOf(await drive.listAll(token));
  const manifestFile = listing.manifests.get(device.deviceId);
  if (!manifestFile) throw new Error('That device is no longer in Google Drive');
  const manifest = decodeManifest(await readManifest(token, manifestFile));
  const files = listing.months.get(device.deviceId) ?? new Map<string, DriveFile>();
  const months = manifest.months.map((m) => m.month).filter((m) => files.has(m)).sort();

  const source = listening();
  const remote = new Map<string, Uint8Array>();
  for (const [index, month] of months.entries()) {
    onStep({ kind: 'downloading', device: manifest.name, done: index, total: months.length });
    const bytes = await gunzip(await drive.download(token, files.get(month)!.id));
    if (!source.readable(bytes)) throw new Error(`${month} of that device’s listening can’t be read`);
    remote.set(month, bytes);
  }
  onStep({ kind: 'restoring' });

  const local = keepLocal ? await source.exportMonths() : new Map<string, Uint8Array>();
  const combined = new Map<string, Uint8Array>();
  for (const month of [...new Set([...remote.keys(), ...local.keys()])].sort()) {
    const a = remote.get(month);
    const b = local.get(month);
    const extra = a && b ? source.minus(b, a) : null;
    combined.set(month, a && extra ? source.plus(a, extra) : (a ?? b)!);
  }
  await source.importMonths(combined);
  importSettings(settingsOf(manifest));

  // What is in Drive for this partition is exactly what came down, so the
  // next sync uploads only what this browser adds to it.
  await clearSynced();
  for (const [month, bytes] of remote) await writeSynced(month, bytes);
  await removePeer(device.deviceId);
  peersChanged();

  return {
    identity: { ...identity, deviceId: device.deviceId, deviceName: manifest.name, adoptedManifestAt: manifestFile.modifiedAt },
    summary: { months: combined.size, settings: manifest.settings.length, from: manifest.versionName, at: String(manifest.updatedAt) },
  };
}

/** Deletes [device]'s files from Drive, so its listening leaves every Replay. */
export async function removeDevice(token: string, device: RemoteDevice): Promise<void> {
  const listing = listingOf(await drive.listAll(token));
  for (const file of listing.months.get(device.deviceId)?.values() ?? []) await drive.remove(token, file.id);
  const manifest = listing.manifests.get(device.deviceId);
  if (manifest) await drive.remove(token, manifest.id);
  await removePeer(device.deviceId);
  peersChanged();
}
