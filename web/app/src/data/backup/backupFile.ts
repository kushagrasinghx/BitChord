import { listening } from './listening';
import { exportSettings, importSettings, type PrefValue } from './prefs';

/**
 * Settings › Backup & Restore › Export and Import: the phone's own backup file
 * (Backup.kt's BackupFile), so a file made here restores on the phone and the
 * other way round. Settings this player has are applied by key (prefs.ts) and
 * the phone's others ride along to the next export; listening goes to and
 * from the Replay store (listening.ts).
 */

const APP_TAG = 'bitchord';
/** Backup.SCHEMA_VERSION: a file from a newer schema is refused rather than partly read. */
const SCHEMA_VERSION = 1;
/** Which build wrote it, for the reader, as the phone writes its versionName. */
const VERSION_NAME = 'web';

export interface BackupFile {
  app: string;
  version: number;
  versionName: string;
  exportedAt: string;
  settings: Record<string, PrefValue>;
  /** StoredBucket objects, one per month; opaque here. */
  listening: unknown[];
}

/** What an import turned out to contain, for the line shown afterwards (Backup.Summary). */
export interface Summary {
  months: number;
  settings: number;
  from: string;
  at: string;
}

/** Backup.suggestedName: dated, so successive exports don't collide. */
export function suggestedName(now = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `bitchord-backup-${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}.json`;
}

/** The file, pretty-printed as the phone's (kotlinx's prettyPrint indents by four). */
export function writeBackupFile(settings: Record<string, PrefValue>, listening: unknown[], now = new Date()): string {
  const file: BackupFile = { app: APP_TAG, version: SCHEMA_VERSION, versionName: VERSION_NAME, exportedAt: now.toISOString(), settings, listening };
  return JSON.stringify(file, null, 4);
}

/**
 * Backup.readFrom's checks, before anything is applied: a file that isn't one
 * of ours, or is from a schema this build can't read, is refused whole. Every
 * field has a default, as in the Kotlin class, so an older file reads as-is.
 */
export function readBackupFile(text: string): BackupFile {
  let json: Partial<BackupFile>;
  try {
    json = JSON.parse(text) as Partial<BackupFile>;
    if (typeof json !== 'object' || json === null || Array.isArray(json)) throw new Error();
  } catch {
    throw new Error('That doesn’t look like a BitChord backup');
  }
  const file: BackupFile = {
    app: json.app ?? APP_TAG,
    version: json.version ?? SCHEMA_VERSION,
    versionName: json.versionName ?? '',
    exportedAt: json.exportedAt ?? '',
    settings: json.settings ?? {},
    listening: json.listening ?? [],
  };
  if (file.app !== APP_TAG) throw new Error('That backup is from another app');
  if (file.version > SCHEMA_VERSION) throw new Error('That backup was written by a newer version of BitChord');
  if (!Array.isArray(file.listening) || typeof file.settings !== 'object') throw new Error('That doesn’t look like a BitChord backup');
  return file;
}

const encoder = new TextEncoder();
const decoder = new TextDecoder();

/** Months as the Replay store holds them (canonical bytes), keyed by their own `month`. */
export function monthsOf(listening: unknown[]): Map<string, Uint8Array> {
  const months = new Map<string, Uint8Array>();
  for (const bucket of listening) {
    const month = (bucket as { month?: unknown } | null)?.month;
    if (typeof month !== 'string') throw new Error('That backup’s listening can’t be read');
    months.set(month, encoder.encode(JSON.stringify(bucket)));
  }
  return months;
}

/** Backup.writeTo: this player's settings and this device's listening, as a file to save. */
export async function exportBackup(): Promise<{ text: string; months: number }> {
  const months = [...(await listening().exportMonths())].sort(([a], [b]) => a.localeCompare(b));
  const buckets = months.map(([, bytes]) => JSON.parse(decoder.decode(bytes)) as unknown);
  return { text: writeBackupFile(exportSettings(), buckets), months: buckets.length };
}

/** Backup.readFrom: checked whole, then this device's listening is replaced and the settings applied. */
export async function importBackup(text: string): Promise<Summary> {
  const file = readBackupFile(text);
  const months = monthsOf(file.listening);
  await listening().importMonths(months);
  const settings = importSettings(file.settings);
  return { months: months.size, settings, from: file.versionName, at: file.exportedAt };
}
