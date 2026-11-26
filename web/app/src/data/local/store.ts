import { openDB, type DBSchema, type IDBPDatabase } from 'idb';
import type { LocalFile, ScanResult } from './scan';

/**
 * Local Music's index, kept in this browser: the chosen folder's handle
 * (Android keeps a tree URI in AppSettings.localMusicFolderUri), one record
 * per audio file, and the covers, each stored once however many tracks share it.
 * The handle survives a restart; the permission to read through it may not,
 * which is what Library's "Allow access" row is for.
 */
interface LocalDB extends DBSchema {
  files: { key: string; value: LocalFile };
  art: { key: string; value: Blob };
  folder: { key: 'root'; value: FileSystemDirectoryHandle };
}

let dbPromise: Promise<IDBPDatabase<LocalDB>> | null = null;

function getDb(): Promise<IDBPDatabase<LocalDB>> {
  dbPromise ??= openDB<LocalDB>('bitchord-local', 1, {
    upgrade(db) {
      db.createObjectStore('files', { keyPath: 'path' });
      db.createObjectStore('art');
      db.createObjectStore('folder');
    },
  });
  return dbPromise;
}

export async function readRoot(): Promise<FileSystemDirectoryHandle | null> {
  return (await (await getDb()).get('folder', 'root')) ?? null;
}

/** The index as last saved: every file, and every cover by key. */
export async function readIndex(): Promise<{ files: LocalFile[]; art: Map<string, Blob> }> {
  const db = await getDb();
  const [files, keys, blobs] = await Promise.all([db.getAll('files'), db.getAllKeys('art'), db.getAll('art')]);
  return { files, art: new Map(keys.map((key, i) => [key, blobs[i]!])) };
}

/** A new folder: its handle, and an empty index for the first scan to fill. */
export async function saveRoot(root: FileSystemDirectoryHandle | null): Promise<void> {
  const tx = (await getDb()).transaction(['folder', 'files', 'art'], 'readwrite');
  await Promise.all([
    root ? tx.objectStore('folder').put(root, 'root') : tx.objectStore('folder').clear(),
    tx.objectStore('files').clear(),
    tx.objectStore('art').clear(),
  ]);
  await tx.done;
}

/** Replaces the index with a scan's: its files, its new covers, and no cover nothing points at any more. */
export async function saveScan({ files, art }: ScanResult): Promise<void> {
  const tx = (await getDb()).transaction(['files', 'art'], 'readwrite');
  const fileStore = tx.objectStore('files');
  const artStore = tx.objectStore('art');
  const used = new Set(files.map((f) => f.art).filter((key): key is string => key !== null));
  const stale = (await artStore.getAllKeys()).filter((key) => !used.has(key));
  await Promise.all([
    fileStore.clear().then(() => Promise.all(files.map((f) => fileStore.put(f)))),
    ...[...art].filter(([key]) => used.has(key)).map(([key, blob]) => artStore.put(blob, key)),
    ...stale.map((key) => artStore.delete(key)),
  ]);
  await tx.done;
}
