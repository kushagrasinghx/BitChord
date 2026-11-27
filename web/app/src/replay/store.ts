import { openDB, type DBSchema, type IDBPDatabase } from 'idb';

/**
 * Where the Replay's listening lives: one record per calendar month, holding
 * the month exactly as the phone writes it to `files/listening/2026-11.json`
 * (ListeningStats.kt's StoredBucket, encoded by the shared Kotlin). Keeping
 * the phone's bytes is what lets Drive sync move a month between the two
 * without translating it.
 *
 * Other devices' months sit in a store of their own and are only ever added in
 * when read (PeerListening.kt): this browser's months are the only ones it
 * would upload, so anything folded into them would be counted twice.
 */
interface ReplayDB extends DBSchema {
  /** This browser's months: `YYYY-MM` → the month's JSON. */
  months: { key: string; value: string };
  /** Other devices' months, as last downloaded: `deviceId/YYYY-MM`. */
  peers: { key: string; value: PeerMonth; indexes: { month: string } };
}

export interface PeerMonth {
  deviceId: string;
  month: string;
  json: string;
}

/** Three years, as on the phone (ListeningLimits.KEEP_MONTHS). */
export const KEEP_MONTHS = 36;

let dbPromise: Promise<IDBPDatabase<ReplayDB>> | null = null;

function getDb(): Promise<IDBPDatabase<ReplayDB>> {
  dbPromise ??= openDB<ReplayDB>('bitchord-replay', 1, {
    upgrade(db) {
      db.createObjectStore('months');
      db.createObjectStore('peers').createIndex('month', 'month');
    },
  });
  return dbPromise;
}

const listeners = new Set<() => void>();

/** Called whenever a month is written, so an open Replay can read again. */
export function onReplayChanged(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function changed() {
  listeners.forEach((listener) => listener());
}

export async function readMonth(month: string): Promise<string | undefined> {
  return (await getDb()).get('months', month);
}

/** Writes this browser's [month] and drops anything past [KEEP_MONTHS], oldest first. */
export async function writeMonth(month: string, json: string): Promise<void> {
  const db = await getDb();
  await db.put('months', json, month);
  const keys = await db.getAllKeys('months');
  const stale = keys.sort().slice(0, Math.max(0, keys.length - KEEP_MONTHS));
  await Promise.all(stale.map((key) => db.delete('months', key)));
  changed();
}

/** Another device's copy of [month], replacing whatever was held (PeerListening.putMonth). */
export async function putPeerMonth(peer: PeerMonth): Promise<void> {
  await (await getDb()).put('peers', peer, `${peer.deviceId}/${peer.month}`);
  changed();
}

/** This browser's months, `YYYY-MM` → JSON (ListeningStats.exportAll): what backup and sync send. */
export async function ownMonths(): Promise<Map<string, string>> {
  const db = await getDb();
  const [keys, values] = await Promise.all([db.getAllKeys('months'), db.getAll('months')]);
  return new Map(keys.map((key, i) => [key, values[i]!]));
}

/** Replaces this browser's months with [months] (ListeningStats.importAll): a restore or an imported backup. */
export async function replaceMonths(months: Map<string, string>): Promise<void> {
  const tx = (await getDb()).transaction('months', 'readwrite');
  await tx.store.clear();
  await Promise.all([...months].map(([month, json]) => tx.store.put(json, month)));
  await tx.done;
  changed();
}

/** Every other device's months, exactly [peers]: a device dropped from sync takes its months with it. */
export async function replacePeerMonths(peers: PeerMonth[]): Promise<void> {
  const tx = (await getDb()).transaction('peers', 'readwrite');
  await tx.store.clear();
  await Promise.all(peers.map((peer) => tx.store.put(peer, `${peer.deviceId}/${peer.month}`)));
  await tx.done;
  changed();
}

/** Every month with listening here or on a synced device, oldest first (ListeningStats.allMonths). */
export async function allMonths(): Promise<string[]> {
  const db = await getDb();
  const [own, peers] = await Promise.all([db.getAllKeys('months'), db.getAllKeys('peers')]);
  // A peer's key is `deviceId/month`, and a device id has no slash (PeerListening.dir).
  const peerMonths = peers.map((key) => key.slice(key.lastIndexOf('/') + 1));
  return [...new Set([...own, ...peerMonths])].sort();
}

/** [month] from this browser first, then every other device's copy (ListeningStats.bucketsOf). */
export async function bucketsOf(month: string): Promise<string[]> {
  const db = await getDb();
  const [own, peers] = await Promise.all([db.get('months', month), db.getAllFromIndex('peers', 'month', month)]);
  return [...(own ? [own] : []), ...peers.map((p) => p.json)];
}
