import { beforeEach, describe, expect, it, vi } from 'vitest';

const replayStore = vi.hoisted(() => ({
  own: new Map<string, string>([['2026-11', '{"month":"2026-11"}']]),
  replaced: null as Map<string, string> | null,
  peers: [] as unknown[],
}));
const syncStore = vi.hoisted(() => ({ peers: [] as unknown[], listeners: new Set<() => void>() }));

vi.mock('./store', () => ({
  ownMonths: async () => replayStore.own,
  replaceMonths: async (months: Map<string, string>) => void (replayStore.replaced = months),
  replacePeerMonths: async (peers: unknown[]) => void (replayStore.peers = peers),
}));
vi.mock('@/data/backup/store', () => ({
  ownMonths: async () => new Map(),
  setOwnMonths: async () => {},
  peerMonths: async () => syncStore.peers,
  onPeersChanged: (listener: () => void) => {
    syncStore.listeners.add(listener);
    return () => syncStore.listeners.delete(listener);
  },
}));

const { listening } = await import('@/data/backup/listening');
const { connectReplayToBackup } = await import('./backupSource');
const bytes = (text: string) => new TextEncoder().encode(text);
const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('the Replay as backup and sync see it', () => {
  beforeEach(() => {
    replayStore.replaced = null;
    replayStore.peers = [];
    syncStore.peers = [];
  });

  it("sends this browser's months in the phone's bytes, and a restore replaces them", async () => {
    connectReplayToBackup();
    const sent = await listening().exportMonths();
    expect(new TextDecoder().decode(sent.get('2026-11'))).toBe('{"month":"2026-11"}');
    await listening().importMonths(new Map([['2026-10', bytes('{"month":"2026-10"}')]]));
    expect([...replayStore.replaced!]).toEqual([['2026-10', '{"month":"2026-10"}']]);
  });

  it("puts the phone's months into the Replay as another device's, and follows sync's changes", async () => {
    syncStore.peers = [{ deviceId: 'phone', name: 'Pixel', updatedAt: 1, month: '2026-11', bytes: bytes('{"month":"2026-11"}'), md5: 'x' }];
    connectReplayToBackup();
    await settle();
    expect(replayStore.peers).toEqual([{ deviceId: 'phone', month: '2026-11', json: '{"month":"2026-11"}' }]);
    syncStore.peers = [];
    syncStore.listeners.forEach((listener) => listener());
    await settle();
    expect(replayStore.peers).toEqual([]);
  });

  it("adds and takes away months with the phone's BucketMath", () => {
    connectReplayToBackup();
    const month = (ms: number, plays: number) =>
      bytes(JSON.stringify({ month: '2026-11', tracks: [{ id: 'a', title: 'Song a', artist: 'Artist', ms, plays }], days: { 3: ms } }));
    const read = (b: Uint8Array) => JSON.parse(new TextDecoder().decode(b));
    const earlier = month(1_000, 1);
    const sum = listening().plus(earlier, month(500, 1));
    expect(read(sum).tracks[0]).toMatchObject({ id: 'a', ms: 1_500, plays: 2 });
    expect(read(sum).days).toEqual({ 3: 1_500 });
    const since = listening().minus(sum, earlier)!;
    expect(read(since).tracks[0]).toMatchObject({ ms: 500, plays: 1 });
    expect(read(listening().plus(earlier, since))).toEqual(read(sum));
    expect(listening().minus(earlier, earlier)).toBeNull();
  });
});
