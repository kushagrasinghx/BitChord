import { beforeEach, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ call: vi.fn(), browser: vi.fn(), fetch: vi.fn() }));
vi.mock('@/extension/bridge', () => ({ getBridge: () => ({ call: mocks.call }) }));
vi.mock('@/lib/platform', () => ({ extensionBrowser: mocks.browser }));
vi.mock('@/extension/useYtAuth', () => ({}));
vi.mock('youtubei.js/web', () => ({
  Platform: { shim: {} },
  UniversalCache: class {},
  Innertube: { create: async ({ fetch }: { fetch: typeof globalThis.fetch }) => {
    await fetch('https://www.youtube.com/iframe_api');
    return { session: { context: { client: {} } } };
  } },
}));

beforeEach(() => {
  vi.resetModules();
  vi.clearAllMocks();
  vi.stubGlobal('indexedDB', { databases: async () => [{}] });
  vi.stubGlobal('fetch', mocks.fetch);
  mocks.call.mockResolvedValue({ status: 200, headers: {}, body: 'bootstrap', bodyEncoding: 'text' });
});

it('loads Safari YouTube bootstrap through the anonymous extension bridge', async () => {
  mocks.browser.mockReturnValue('safari');
  await (await import('./innertube')).getInnertube();
  expect(mocks.call).toHaveBeenCalledWith('yt.fetch', expect.objectContaining({
    url: 'https://www.youtube.com/iframe_api', auth: false, method: 'GET',
  }), expect.anything());
  expect(mocks.fetch).not.toHaveBeenCalled();
});

it('keeps Chromium bootstrap on the existing page fetch path', async () => {
  mocks.browser.mockReturnValue('chrome');
  await (await import('./innertube')).getInnertube();
  expect(mocks.fetch).toHaveBeenCalledOnce();
  expect(mocks.call).not.toHaveBeenCalled();
});
