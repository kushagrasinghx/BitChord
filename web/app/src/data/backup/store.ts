import { openDB, type DBSchema, type IDBPDatabase } from 'idb';

/**
 * What sync keeps in this browser, as the phone keeps it: the copy of each
 * month last uploaded (ReplaySync's listening-synced folder), every other
 * device's months (PeerListening), and, until the Replay store takes over,
 * this device's own months as they came from a backup (listening.ts).
 *
 * A month is opaque here: BucketMath.canonicalBytes, the JSON the phone writes.
 */

export interface PeerMonth {
  deviceId: string;
  name: string;
  updatedAt: number;
  month: string;
  bytes: Uint8Array;
  /** Drive's MD5 of the file it came from: what says it changed. */
  md5: string;
}

interface SyncDB extends DBSchema {
  /** Month → this device's listening, when no Replay store is registered. */
  own: { key: string; value: Uint8Array };
  /** Month → the bytes last uploaded for it. */
  synced: { key: string; value: Uint8Array };
  /** `${deviceId} ${month}` → that device's month. */
  peers: { key: string; value: PeerMonth; indexes: { device: string } };
}

let dbPromise: Promise<IDBPDatabase<SyncDB>> | null = null;

function db(): Promise<IDBPDatabase<SyncDB>> {
  dbPromise ??= openDB<SyncDB>('bitchord-sync', 1, {
    upgrade(db) {
      db.createObjectStore('own');
      db.createObjectStore('synced');
      db.createObjectStore('peers').createIndex('device', 'deviceId');
    },
  });
  return dbPromise;
}

async function allOf(store: 'own' | 'synced'): Promise<Map<string, Uint8Array>> {
  const tx = (await db()).transaction(store);
  const [keys, values] = await Promise.all([tx.store.getAllKeys(), tx.store.getAll()]);
  return new Map(keys.map((k, i) => [k, values[i]!]));
}

async function replaceAll(store: 'own' | 'synced', months: Map<string, Uint8Array>): Promise<void> {
  const tx = (await db()).transaction(store, 'readwrite');
  await tx.store.clear();
  for (const [month, bytes] of months) await tx.store.put(bytes, month);
  await tx.done;
}

export const ownMonths = () => allOf('own');
export const setOwnMonths = (months: Map<string, Uint8Array>) => replaceAll('own', months);

// ---- Last-uploaded copies -------------------------------------------------------

export const syncedMonths = () => allOf('synced');

export async function writeSynced(month: string, bytes: Uint8Array): Promise<void> {
  await (await db()).put('synced', bytes, month);
}

export async function deleteSynced(month: string): Promise<void> {
  await (await db()).delete('synced', month);
}

/** Forgets what was last uploaded: a new partition starts from nothing. */
export async function clearSynced(): Promise<void> {
  await (await db()).clear('synced');
}

export async function hasSynced(): Promise<boolean> {
  return (await (await db()).count('synced')) > 0;
}

// ---- Other devices' listening ---------------------------------------------------

const listeners = new Set<() => void>();

/** Called whenever another device's listening changes here, for the Replay to re-read. */
export function onPeersChanged(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function peersChanged(): void {
  for (const listener of listeners) listener();
}

/** Every other device's months, for the Replay to add in (never folded into this device's own). */
export async function peerMonths(): Promise<PeerMonth[]> {
  return (await db()).getAll('peers');
}

export async function putPeerMonth(month: PeerMonth): Promise<void> {
  await (await db()).put('peers', month, `${month.deviceId} ${month.month}`);
}

export async function removePeerMonth(deviceId: string, month: string): Promise<void> {
  await (await db()).delete('peers', `${deviceId} ${month}`);
}

/** PeerListening.touch: the device's current name and sync time on the months already held. */
export async function touchPeer(deviceId: string, name: string, updatedAt: number): Promise<void> {
  const tx = (await db()).transaction('peers', 'readwrite');
  let cursor = await tx.store.index('device').openCursor(deviceId);
  while (cursor) {
    if (cursor.value.name !== name || cursor.value.updatedAt !== updatedAt) await cursor.update({ ...cursor.value, name, updatedAt });
    cursor = await cursor.continue();
  }
  await tx.done;
}

export async function removePeer(deviceId: string): Promise<void> {
  const tx = (await db()).transaction('peers', 'readwrite');
  for (const key of await tx.store.index('device').getAllKeys(deviceId)) await tx.store.delete(key);
  await tx.done;
}

export async function clearPeers(): Promise<void> {
  await (await db()).clear('peers');
}
