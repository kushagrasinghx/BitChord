import type { Location } from 'react-router';
import { describe, expect, it } from 'vitest';
import { originTab } from './tabs';

const at = (pathname: string, key: string, state: unknown = null) => ({ pathname, key, state, search: '', hash: '' }) as Location;

describe('originTab', () => {
  it('lights the tab a page was pushed from, and none for tabless pages', () => {
    expect(originTab(at('/album/x', 'direct'))).toBe(-1);
    expect(originTab(at('/search', 'a'))).toBe(4);
    expect(originTab(at('/album/x', 'b'))).toBe(4);
    expect(originTab(at('/artist/y', 'c'))).toBe(4);
    expect(originTab(at('/settings', 'd'))).toBe(-1);
    expect(originTab(at('/album/z', 'e'))).toBe(-1);
    expect(originTab(at('/home', 'f'))).toBe(0);
    expect(originTab(at('/playlist/p', 'g', { sidebar: true }))).toBe(-1);
    // Back to an entry already seen lights what it lit then.
    expect(originTab(at('/album/x', 'b'))).toBe(4);
  });
});
