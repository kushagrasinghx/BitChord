/**
 * Build files the service worker stores at install: the hashed app shell.
 * Anything over the limit (today the ONNX runtime wasm and AutoMix's analysis
 * worker) is cached the first time it is used instead (pwa/sw.js).
 */
export const PRECACHE_MAX_BYTES = 1024 * 1024;

const SHELL_FILE = /^assets\/[^/]+\.(?:js|css|woff2|svg|png|webp)$/;

export function precacheList(files: { fileName: string; size: number }[]): string[] {
  return files
    .filter((f) => SHELL_FILE.test(f.fileName) && f.size <= PRECACHE_MAX_BYTES)
    .map((f) => `/${f.fileName}`)
    .sort();
}
