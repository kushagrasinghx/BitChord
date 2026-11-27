// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import type { MusicItem } from '@/data/music/types';
import { subtitleOf } from './SearchRow';

const item = (over: Partial<MusicItem>): MusicItem => ({ kind: 'song', id: 'v', title: 'Creep', subtitle: '', artists: [], album: null, thumbnail: null, wide: false, durationSec: null, explicit: false, ...over });

describe('subtitleOf (a search row\'s second line)', () => {
  it("is YouTube's own line when it has one", () => {
    expect(subtitleOf(item({ subtitle: 'Song • Radiohead', artists: [{ name: 'Radiohead', id: 'UC1' }] }))).toBe('Song • Radiohead');
  });

  it('falls back to the artists, then to what kind of thing the row is', () => {
    expect(subtitleOf(item({ artists: [{ name: 'Radiohead', id: 'UC1' }, { name: 'Thom Yorke', id: null }] }))).toBe('Radiohead, Thom Yorke');
    expect(subtitleOf(item({ kind: 'album' }))).toBe('Album');
  });
});
