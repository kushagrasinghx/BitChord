// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { MusicItem } from './types';

const item = (id: string, kind: MusicItem['kind'] = 'playlist') =>
  ({ kind, id, title: id, subtitle: '', artists: [], album: null, thumbnail: null, wide: false, durationSec: null, explicit: false }) as MusicItem;

describe('pinned playlists', () => {
  beforeEach(() => {
    vi.resetModules();
    localStorage.clear();
  });

  it('pins in order, unpins, and keeps the pins across reloads', async () => {
    const { usePinnedPlaylists } = await import('./pinnedPlaylists');
    const { toggle } = usePinnedPlaylists.getState();
    expect(toggle('VLPLa')).toBe(true);
    expect(toggle('PLb')).toBe(true);
    expect(toggle('PLa')).toBe(false); // The same playlist without its VL.
    expect(usePinnedPlaylists.getState().ids).toEqual(['PLb']);

    vi.resetModules();
    const again = await import('./pinnedPlaylists');
    expect(again.usePinnedPlaylists.getState().ids).toEqual(['PLb']);
  });

  it('refuses a pin past the cap rather than dropping the oldest', async () => {
    const { usePinnedPlaylists, MAX_PINNED_PLAYLISTS } = await import('./pinnedPlaylists');
    const { toggle } = usePinnedPlaylists.getState();
    for (let i = 0; i < MAX_PINNED_PLAYLISTS; i++) toggle(`PL${i}`);
    expect(toggle('PLextra')).toBe(false);
    expect(usePinnedPlaylists.getState().ids).toEqual(['PL0', 'PL1', 'PL2', 'PL3', 'PL4']);
  });

  it('moves pinned playlists to the front in pin order', async () => {
    const { pinnedFirst } = await import('./pinnedPlaylists');
    const items = [item('VLPLa'), item('VLPLb'), item('VLPLc'), item('MPREb_x', 'album')];
    expect(pinnedFirst(items, ['PLc', 'PLmissing', 'PLa']).map((i) => i.id)).toEqual(['VLPLc', 'VLPLa', 'VLPLb', 'MPREb_x']);
    expect(pinnedFirst(items, [])).toBe(items);
  });
});
