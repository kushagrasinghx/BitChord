import { describe, expect, it } from 'vitest';
import { sniffMimeType } from './store';

describe('a downloaded file’s type', () => {
  it('is read from its first bytes', () => {
    expect(sniffMimeType(new Uint8Array([0x1a, 0x45, 0xdf, 0xa3, 0x01]))).toBe('audio/webm');
    expect(sniffMimeType(new Uint8Array([0, 0, 0, 0x18, ...new TextEncoder().encode('ftypdash')]))).toBe('audio/mp4');
    expect(sniffMimeType(new TextEncoder().encode('<html>'))).toBeNull();
  });
});
