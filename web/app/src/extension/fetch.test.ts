import { beforeEach, expect, it, vi } from 'vitest';
import { extensionFetch } from './fetch';

const call = vi.hoisted(() => vi.fn());
vi.mock('./bridge', () => ({ getBridge: () => ({ call }) }));

beforeEach(() => {
  call.mockReset();
  call.mockResolvedValue({ status: 206, headers: { 'content-type': 'audio/mp4' },
    body: 'AP+A', bodyEncoding: 'base64', url: 'https://rr2.googlevideo.com/videoplayback' });
});

it('preserves binary media bodies, range headers, response bytes and final URL', async () => {
  const controller = new AbortController();
  const response = await extensionFetch('https://rr1.googlevideo.com/videoplayback', {
    method: 'POST', body: new Uint8Array([0, 255, 128]), headers: { Range: 'bytes=0-1023' }, signal: controller.signal,
  }, false);
  expect(call).toHaveBeenCalledWith('yt.fetch', expect.objectContaining({
    method: 'POST', headers: { range: 'bytes=0-1023' }, body: 'AP+A', bodyEncoding: 'base64', auth: false,
  }), { signal: controller.signal });
  expect([...new Uint8Array(await response.arrayBuffer())]).toEqual([0, 255, 128]);
  expect(response.status).toBe(206);
  expect(response.url).toBe('https://rr2.googlevideo.com/videoplayback');
});

it('returns no body for HEAD even when the bridge serializes an empty payload', async () => {
  const response = await extensionFetch('https://rr1.googlevideo.com/videoplayback', { method: 'HEAD' }, false);
  expect(response.body).toBeNull();
});
