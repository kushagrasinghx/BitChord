import { describe, expect, it } from 'vitest';
import { isStaleBuildError, reloadForNewBuild } from './staleBuild';

describe('a page from an older build', () => {
  it('is told apart from other errors, in every browser', () => {
    expect(isStaleBuildError(new TypeError('Failed to fetch dynamically imported module: https://x/assets/AlbumPage-1a2b.js'))).toBe(true);
    expect(isStaleBuildError(new TypeError('error loading dynamically imported module: https://x/a.js'))).toBe(true);
    expect(isStaleBuildError(new TypeError('Importing a module script failed.'))).toBe(true);
    expect(isStaleBuildError(new TypeError("Cannot read properties of undefined (reading 'title')"))).toBe(false);
    expect(isStaleBuildError(null)).toBe(false);
  });

  it('reloads once, not in a loop', () => {
    const store = new Map<string, string>();
    const storage = { getItem: (k: string) => store.get(k) ?? null, setItem: (k: string, v: string) => void store.set(k, v) };
    let reloads = 0;
    const reload = () => void reloads++;
    expect(reloadForNewBuild(1_000_000, storage, reload)).toBe(true);
    expect(reloadForNewBuild(1_005_000, storage, reload)).toBe(false);
    expect(reloadForNewBuild(1_100_000, storage, reload)).toBe(true);
    expect(reloads).toBe(2);
  });
});
