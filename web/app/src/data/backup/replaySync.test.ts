import { decodeManifest, encodeManifest, MonthHash, syncPull, syncPush, WebPref } from '@bitchord/shared-kotlin';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { gunzip, gzip, sha256Hex } from './bytes';
import type { DriveFile } from './drive';
import type { PeerMonth } from './store';

// Google Drive's app folder, in memory.
const drive = vi.hoisted(() => ({ files: new Map<string, { file: DriveFile; bytes: Uint8Array }>(), clock: 0, puts: [] as string[] }));
vi.mock('./drive', () => ({
  listAll: async () => [...drive.files.values()].map((f) => f.file),
  download: async (_token: string, id: string) => drive.files.get(id)!.bytes,
  remove: async (_token: string, id: string) => void drive.files.delete(id),
  put: async (_token: string, name: string, bytes: Uint8Array, properties: Record<string, string>, existingId: string | null) => {
    const id = existingId ?? `file${drive.files.size + 1}`;
    const file: DriveFile = { id, name, md5: await sha256Hex(bytes), modifiedAt: `t${++drive.clock}`, sizeBytes: bytes.length, properties };
    drive.files.set(id, { file, bytes });
    drive.puts.push(name);
    return file;
  },
}));

// The browser's sync store (IndexedDB), in memory.
const local = vi.hoisted(() => ({ synced: new Map<string, Uint8Array>(), peers: new Map<string, PeerMonth>() }));
vi.mock('./store', () => ({
  ownMonths: async () => new Map(),
  setOwnMonths: async () => {},
  syncedMonths: async () => new Map(local.synced),
  writeSynced: async (m: string, b: Uint8Array) => void local.synced.set(m, b),
  deleteSynced: async (m: string) => void local.synced.delete(m),
  clearSynced: async () => local.synced.clear(),
  peerMonths: async () => [...local.peers.values()],
  putPeerMonth: async (p: PeerMonth) => void local.peers.set(`${p.deviceId} ${p.month}`, p),
  removePeerMonth: async (d: string, m: string) => void local.peers.delete(`${d} ${m}`),
  removePeer: async (d: string) => [...local.peers.keys()].filter((k) => k.startsWith(`${d} `)).forEach((k) => local.peers.delete(k)),
  touchPeer: async () => {},
  peersChanged: () => {},
}));

const applied: Record<string, unknown>[] = [];
vi.mock('./prefs', () => ({
  exportSettings: () => ({ autoplay: { type: 'bool', value: 'true', values: [] } }),
  importSettings: (s: Record<string, unknown>) => applied.push(s),
}));

const { sync, restoreAs, devices } = await import('./replaySync');
const { setListeningSource } = await import('./listening');

const bytesOf = (json: object) => new TextEncoder().encode(JSON.stringify(json));
const text = (bytes: Uint8Array) => new TextDecoder().decode(bytes);
let own = new Map<string, Uint8Array>();

/** The phone's partition, as ReplaySync.sync writes it. */
async function phoneUploads(owner = 'phone-install') {
  const month = bytesOf({ month: '2026-10', totalMs: 1 });
  const put = (await import('./drive')).put;
  await put('t', 'replay-phone-2026-10.json.gz', await gzip(month), { kind: 'replay', deviceId: 'phone', month: '2026-10' }, null);
  const manifest = encodeManifest('phone', 'Pixel 8', owner, 1_700_000_000_000, '1.10.0', [new MonthHash('2026-10', await sha256Hex(month))], [
    new WebPref('theme_mode', 'string', 'DARK', []),
  ]);
  const existing = [...drive.files.values()].find((f) => f.file.name === 'device-phone.json.gz')?.file.id ?? null;
  await put('t', 'device-phone.json.gz', await gzip(new TextEncoder().encode(manifest)), { kind: 'device', deviceId: 'phone' }, existing);
}

const web = { installId: 'web-install', deviceId: 'web', deviceName: 'BitChord Web', adoptedManifestAt: null };

beforeEach(() => {
  drive.files.clear();
  drive.puts.length = 0;
  local.synced.clear();
  local.peers.clear();
  own = new Map([['2026-11', bytesOf({ month: '2026-11', totalMs: 5 })]]);
  setListeningSource({
    exportMonths: async () => new Map(own),
    importMonths: async (months) => void (own = new Map(months)),
    minus: () => null,
    plus: (a) => a,
    readable: (b) => text(b).startsWith('{"month"'),
  });
});

describe('SyncPlanner (shared/commonMain), from the web', () => {
  // app/src/test/.../SyncPlannerTest.kt's cases.
  it('uploads changed and missing months, deletes pruned ones', () => {
    const plan = syncPush(
      [new MonthHash('2026-10', 'h10'), new MonthHash('2026-11', 'h11-new'), new MonthHash('2026-09', 'h09')],
      [new MonthHash('2026-10', 'h10'), new MonthHash('2026-11', 'h11-old'), new MonthHash('2026-09', 'h09')],
      ['2026-10', '2026-11', '2023-01'],
    );
    expect([...plan.upload]).toEqual(['2026-09', '2026-11']);
    expect([...plan.delete]).toEqual(['2023-01']);
  });

  it('fetches only listed, changed months and drops unlisted ones', () => {
    const plan = syncPull(
      ['2026-10', '2026-11'],
      [new MonthHash('2026-10', 'm10'), new MonthHash('2026-11', 'm11-new'), new MonthHash('2026-12', 'm12')],
      [new MonthHash('2026-10', 'm10'), new MonthHash('2026-11', 'm11-old'), new MonthHash('2025-01', 'm01')],
    );
    expect([...plan.download]).toEqual(['2026-11']);
    expect([...plan.drop]).toEqual(['2025-01']);
  });
});

describe('Drive sync (ReplaySync.kt), from the web', () => {
  it('writes its own partition in the phone’s format, manifest last, and takes the phone’s months as a peer', async () => {
    await phoneUploads();
    drive.puts.length = 0;
    const report = await sync('t', web, true, 'BitChord Web', () => {});

    expect(drive.puts).toEqual(['replay-web-2026-11.json.gz', 'device-web.json.gz']);
    const files = [...drive.files.values()].map((f) => f.file);
    expect(files.find((f) => f.name === 'replay-web-2026-11.json.gz')?.properties).toEqual({ kind: 'replay', deviceId: 'web', month: '2026-11' });
    const manifestFile = [...drive.files.values()].find((f) => f.file.name === 'device-web.json.gz')!;
    expect(manifestFile.file.properties).toEqual({ kind: 'device', deviceId: 'web' });
    const json = JSON.parse(text(await gunzip(manifestFile.bytes))) as Record<string, unknown>;
    // DeviceManifest's own fields, in its order, defaults written (encodeDefaults).
    expect(Object.keys(json)).toEqual(['schema', 'deviceId', 'name', 'ownerInstall', 'updatedAt', 'versionName', 'months', 'settings']);
    expect(json).toMatchObject({
      schema: 1,
      deviceId: 'web',
      name: 'BitChord Web',
      ownerInstall: 'web-install',
      months: { '2026-11': await sha256Hex(own.get('2026-11')!) },
      settings: { autoplay: { type: 'bool', value: 'true', values: [] } },
    });

    expect(report).toMatchObject({ uploaded: 1, downloaded: 1, forkedTo: null });
    expect(report.identity.adoptedManifestAt).toBe(manifestFile.file.modifiedAt);
    expect(report.devices.map((d) => [d.deviceId, d.name, d.months])).toEqual([
      ['web', 'BitChord Web', 1],
      ['phone', 'Pixel 8', 1],
    ]);
    expect(text(local.peers.get('phone 2026-10')!.bytes)).toBe('{"month":"2026-10","totalMs":1}');
    // The phone's listening is a peer, never folded into this device's own.
    expect([...own.keys()]).toEqual(['2026-11']);

    // Again, with nothing changed: nothing moves.
    drive.puts.length = 0;
    const again = await sync('t', report.identity, true, 'BitChord Web', () => {});
    expect(drive.puts).toEqual(['device-web.json.gz']);
    expect([again.uploaded, again.downloaded]).toEqual([0, 0]);
  });

  it('carries on as a device of its own when another install restored as this one', async () => {
    const first = await sync('t', web, true, 'BitChord Web', () => {});
    // Another browser restores as "web" and syncs: the manifest now has another owner.
    const id = [...drive.files.values()].find((f) => f.file.name === 'device-web.json.gz')!.file.id;
    const theirs = encodeManifest('web', 'BitChord Web', 'other-install', Date.now(), 'web', [], []);
    const { put } = await import('./drive');
    await put('t', 'device-web.json.gz', await gzip(new TextEncoder().encode(theirs)), { kind: 'device', deviceId: 'web' }, id);

    const report = await sync('t', first.identity, true, 'BitChord Web', () => {});
    expect(report.forkedTo).toBe('BitChord Web (2)');
    expect(report.identity.deviceId).not.toBe('web');
    expect(report.devices.map((d) => d.name).sort()).toEqual(['BitChord Web', 'BitChord Web (2)']);
  });

  it('restores as the phone: its months and settings, and its partition from then on', async () => {
    await phoneUploads();
    const phone = (await devices('t')).find((d) => d.deviceId === 'phone')!;
    expect(decodeManifest(text(await gunzip([...drive.files.values()].find((f) => f.file.name === 'device-phone.json.gz')!.bytes))).versionName).toBe('1.10.0');

    const { identity, summary } = await restoreAs('t', { ...web, deviceId: null }, phone, false, () => {});
    expect(identity).toMatchObject({ deviceId: 'phone', deviceName: 'Pixel 8' });
    expect([...own.keys()]).toEqual(['2026-10']);
    expect(applied.at(-1)).toEqual({ theme_mode: { type: 'string', value: 'DARK', values: [] } });
    expect(summary).toMatchObject({ months: 1, settings: 1, from: '1.10.0' });
    // What came down is what is in Drive, so the next sync uploads nothing of it.
    drive.puts.length = 0;
    await sync('t', identity, true, 'BitChord Web', () => {});
    expect(drive.puts).toEqual(['device-phone.json.gz']);
  });
});
