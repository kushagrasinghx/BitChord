import { mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';

type Changes = Record<string, { oldValue?: unknown; newValue?: unknown }>;
type Listener = (changes: Changes, area: 'local') => void;

/**
 * `browser.storage.local`, kept in one JSON file under the app's user data.
 * What the extension stores there is small: preferences, the Spotify device
 * id, the Discord account.
 */
export class FileStorage {
  private data: Record<string, unknown> = {};
  private readonly listeners = new Set<Listener>();
  private writing: ReturnType<typeof setTimeout> | undefined;

  constructor(private readonly file: string) {
    try {
      this.data = JSON.parse(readFileSync(file, 'utf8')) as Record<string, unknown>;
    } catch {
      this.data = {};
    }
  }

  async get(keys?: string | string[] | Record<string, unknown> | null): Promise<Record<string, unknown>> {
    if (keys === undefined || keys === null) return structuredClone(this.data);
    if (typeof keys === 'string') keys = [keys];
    if (Array.isArray(keys)) {
      const out: Record<string, unknown> = {};
      for (const key of keys) if (key in this.data) out[key] = structuredClone(this.data[key]);
      return out;
    }
    const out: Record<string, unknown> = {};
    for (const [key, fallback] of Object.entries(keys)) out[key] = key in this.data ? structuredClone(this.data[key]) : fallback;
    return out;
  }

  async set(items: Record<string, unknown>): Promise<void> {
    const changes: Changes = {};
    for (const [key, value] of Object.entries(items)) {
      changes[key] = { oldValue: this.data[key], newValue: value };
      this.data[key] = structuredClone(value);
    }
    this.changed(changes);
  }

  async remove(keys: string | string[]): Promise<void> {
    const changes: Changes = {};
    for (const key of typeof keys === 'string' ? [keys] : keys) {
      if (!(key in this.data)) continue;
      changes[key] = { oldValue: this.data[key] };
      delete this.data[key];
    }
    this.changed(changes);
  }

  async clear(): Promise<void> {
    await this.remove(Object.keys(this.data));
  }

  onChanged(listener: Listener): void {
    this.listeners.add(listener);
  }

  /** Writes now, for quitting. */
  flush(): void {
    clearTimeout(this.writing);
    this.writing = undefined;
    mkdirSync(dirname(this.file), { recursive: true });
    const temp = `${this.file}.tmp`;
    writeFileSync(temp, JSON.stringify(this.data));
    renameSync(temp, this.file);
  }

  private changed(changes: Changes): void {
    if (!Object.keys(changes).length) return;
    clearTimeout(this.writing);
    this.writing = setTimeout(() => this.flush(), 250);
    for (const listener of this.listeners) {
      try {
        listener(changes, 'local');
      } catch (error) {
        console.error('BitChord: storage listener', error);
      }
    }
  }
}
