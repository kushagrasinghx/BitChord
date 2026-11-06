import { beforeEach, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ bridge: vi.fn(), browser: vi.fn(), fallback: vi.fn() }));
vi.mock('@/extension/fetch', () => ({ extensionFetch: mocks.bridge }));
vi.mock('@/lib/platform', () => ({ extensionBrowser: mocks.browser }));
vi.mock('shaka-player/dist/shaka-player.compiled', () => {
  class ShakaError extends Error {
    static Severity = { RECOVERABLE: 1, CRITICAL: 2 };
    static Category = { NETWORK: 1 };
    static Code = { OPERATION_ABORTED: 7001, HTTP_ERROR: 1002 };
    constructor(public severity: number, public category: number, public code: number) { super(); }
  }
  return { default: { net: { HttpFetchPlugin: { parse: mocks.fallback } }, util: {
    Error: ShakaError,
    AbortableOperation: class {
      constructor(public promise: Promise<unknown>, public abort: () => Promise<void>) {}
    },
  } } };
});

import { mediaFetch, safariMediaPlugin } from './mediaFetch';
import type shaka from 'shaka-player/dist/shaka-player.compiled';

const request = { method: 'GET', headers: { Range: 'bytes=0-2' } } as unknown as shaka.extern.Request;
const run = () => safariMediaPlugin('https://rr1.googlevideo.com/videoplayback', request, 1, vi.fn(), vi.fn(), {});

beforeEach(() => {
  vi.clearAllMocks();
  mocks.browser.mockReturnValue('safari');
});

it('leaves non-Safari requests on native fetch', async () => {
  mocks.browser.mockReturnValue('chrome');
  const fetch = vi.fn().mockResolvedValue(new Response());
  vi.stubGlobal('fetch', fetch);
  await mediaFetch('https://rr1.googlevideo.com/videoplayback');
  expect(fetch).toHaveBeenCalledOnce();
  expect(mocks.bridge).not.toHaveBeenCalled();
});

it('returns bridged range bytes to Shaka', async () => {
  mocks.bridge.mockResolvedValue(new Response(new Uint8Array([0, 255, 128]), { status: 206 }));
  const result = await run().promise;
  expect(result.status).toBe(206);
  expect([...new Uint8Array(result.data as ArrayBuffer)]).toEqual([0, 255, 128]);
});

it('maps bridge errors to recoverable Shaka network errors', async () => {
  mocks.bridge.mockRejectedValue(new Error('Extension disconnected'));
  await expect(run().promise).rejects.toMatchObject({ severity: 1, code: 1002 });
});

it('propagates abort and reports Shaka cancellation', async () => {
  mocks.bridge.mockImplementation((_url, init) => new Promise((_resolve, reject) => {
    init.signal.addEventListener('abort', () => reject(new Error('Aborted')));
  }));
  const operation = run();
  const assertion = expect(operation.promise).rejects.toMatchObject({ code: 7001 });
  await operation.abort();
  await assertion;
});
