import { afterEach, expect, test, vi } from 'vitest';
import { extensionBrowser } from './platform';

afterEach(() => vi.unstubAllGlobals());

test.each([
  ['Mozilla/5.0 (Macintosh) AppleWebKit/605.1.15 Version/27.0 Safari/605.1.15', 'safari'],
  ['Mozilla/5.0 Chrome/145.0.0.0 Safari/537.36', 'chrome'],
  ['Mozilla/5.0 Firefox/158.0', 'firefox'],
])('chooses the correct extension download for %s', (userAgent, expected) => {
  vi.stubGlobal('navigator', { userAgent });
  expect(extensionBrowser()).toBe(expected);
});
