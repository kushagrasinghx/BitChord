import { describe, expect, it, vi } from 'vitest';
import type { PrefValue } from './prefs';

const imported: Record<string, PrefValue>[] = [];
vi.mock('./prefs', () => ({
  exportSettings: () => ({ autoplay: { type: 'bool', value: 'true', values: [] } }),
  importSettings: (settings: Record<string, PrefValue>) => {
    imported.push(settings);
    return Object.keys(settings).length;
  },
}));

const { exportBackup, importBackup, monthsOf, readBackupFile, suggestedName, writeBackupFile } = await import('./backupFile');
const { setListeningSource } = await import('./listening');

// A file as the phone's Backup.writeTo writes it (kotlinx, prettyPrint, encodeDefaults).
const PHONE_FILE = `{
    "app": "bitchord",
    "version": 1,
    "versionName": "1.10.0",
    "exportedAt": "2026-11-20T10:00:00Z",
    "settings": {
        "autoplay": {
            "type": "bool",
            "value": "false",
            "values": []
        },
        "theme_mode": {
            "type": "string",
            "value": "DARK",
            "values": []
        }
    },
    "listening": [
        {
            "month": "2026-10",
            "totalMs": 120000
        },
        {
            "month": "2026-11",
            "totalMs": 60000
        }
    ]
}`;

describe('the backup file (Backup.kt BackupFile)', () => {
  it('reads the phone’s file', () => {
    const file = readBackupFile(PHONE_FILE);
    expect(file.versionName).toBe('1.10.0');
    expect(file.settings.theme_mode).toEqual({ type: 'string', value: 'DARK', values: [] });
    expect([...monthsOf(file.listening).keys()]).toEqual(['2026-10', '2026-11']);
  });

  it('reads an older file by its defaults, and refuses what isn’t one it can read', () => {
    expect(readBackupFile('{}').app).toBe('bitchord');
    expect(() => readBackupFile('not json')).toThrow('doesn’t look like a BitChord backup');
    expect(() => readBackupFile('[]')).toThrow('doesn’t look like a BitChord backup');
    expect(() => readBackupFile('{"app":"other"}')).toThrow('from another app');
    expect(() => readBackupFile('{"version":2}')).toThrow('newer version');
    expect(() => monthsOf([{ totalMs: 1 }])).toThrow('can’t be read');
  });

  it('writes the phone’s field order, indented as kotlinx indents', () => {
    const text = writeBackupFile({}, [{ month: '2026-11' }], new Date('2026-11-22T09:30:00Z'));
    expect(Object.keys(JSON.parse(text) as object)).toEqual(['app', 'version', 'versionName', 'exportedAt', 'settings', 'listening']);
    expect(text).toContain('\n    "app": "bitchord"');
    expect(JSON.parse(text)).toMatchObject({ app: 'bitchord', version: 1, exportedAt: '2026-11-22T09:30:00.000Z' });
  });

  it('is named by the day it was made', () => {
    expect(suggestedName(new Date(2026, 0, 5))).toBe('bitchord-backup-2026-01-05.json');
  });

  it('round-trips listening through the Replay seam, and applies the settings', async () => {
    let held = new Map<string, Uint8Array>();
    setListeningSource({
      exportMonths: async () => held,
      importMonths: async (months) => void (held = months),
      minus: () => null,
      plus: (a) => a,
      readable: () => true,
    });
    const summary = await importBackup(PHONE_FILE);
    expect(summary).toEqual({ months: 2, settings: 2, from: '1.10.0', at: '2026-11-20T10:00:00Z' });
    expect(imported.at(-1)?.theme_mode?.value).toBe('DARK');
    expect(new TextDecoder().decode(held.get('2026-10'))).toBe('{"month":"2026-10","totalMs":120000}');

    const out = await exportBackup();
    expect(out.months).toBe(2);
    expect(JSON.parse(out.text)).toMatchObject({
      settings: { autoplay: { type: 'bool', value: 'true', values: [] } },
      listening: [{ month: '2026-10', totalMs: 120000 }, { month: '2026-11', totalMs: 60000 }],
    });
  });
});
