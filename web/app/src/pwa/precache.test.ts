import { describe, expect, it } from 'vitest';
import { precacheList, PRECACHE_MAX_BYTES } from './precache';

describe('precacheList', () => {
  it('keeps hashed shell files and drops heavy, source-map and non-asset files', () => {
    expect(
      precacheList([
        { fileName: 'assets/index-abc.js', size: 300_000 },
        { fileName: 'assets/index-abc.css', size: 40_000 },
        { fileName: 'assets/sf-pro-display-bold-x.woff2', size: 60_000 },
        { fileName: 'assets/ort-wasm-simd-threaded-x.wasm', size: 14_000_000 },
        { fileName: 'assets/analysis.worker-x.js', size: 2_600_000 },
        { fileName: 'assets/index-abc.js.map', size: 200_000 },
        { fileName: 'index.html', size: 2_000 },
      ]),
    ).toEqual(['/assets/index-abc.css', '/assets/index-abc.js', '/assets/sf-pro-display-bold-x.woff2']);
  });

  it('includes a file exactly at the size limit', () => {
    expect(precacheList([{ fileName: 'assets/a-1.js', size: PRECACHE_MAX_BYTES }])).toEqual(['/assets/a-1.js']);
  });
});
