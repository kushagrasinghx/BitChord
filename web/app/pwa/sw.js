// BitChord's service worker. vite.config.ts (serviceWorker plugin) fills in
// the version and precache list below at build time; the dev server serves none.
const VERSION = __VERSION__;
const PRECACHE = __PRECACHE__;
const CACHE = `bitchord-${VERSION}`;

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(PRECACHE)));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      await self.registration.navigationPreload?.enable();
      const keys = await caches.keys();
      await Promise.all(keys.filter((k) => k.startsWith('bitchord-') && k !== CACHE).map((k) => caches.delete(k)));
      await self.clients.claim();
    })(),
  );
});

// Sent only when the person chooses "Reload to update", so a deploy never stops a song.
self.addEventListener('message', (event) => {
  if (event.data === 'skip-waiting') self.skipWaiting();
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  // Only this origin's pages and build files. APIs, media streams, the
  // extension's traffic and /models/ go to the network untouched.
  if (url.origin !== self.location.origin) return;
  if (request.mode === 'navigate') event.respondWith(page(event));
  else if (url.pathname.startsWith('/assets/') || PRECACHE.includes(url.pathname)) event.respondWith(asset(event));
});

// Network first so a deploy shows at once; the cached shell when offline.
async function page(event) {
  try {
    return (await event.preloadResponse) ?? (await fetch(event.request));
  } catch {
    return (await caches.match('/', { cacheName: CACHE })) ?? Response.error();
  }
}

// Hashed files never change, so the cache answers first. Files left out of
// the precache (AutoMix's engine) are kept the first time they load.
// ignoreVary: module scripts and crossorigin CSS carry an Origin header the
// precache requests didn't, so a host's `Vary: Origin` would miss every file.
async function asset(event) {
  const { request } = event;
  const cached = await caches.match(request, { cacheName: CACHE, ignoreVary: true });
  if (cached) return cached;
  const response = await fetch(request);
  if (response.ok && new URL(request.url).pathname.startsWith('/assets/')) {
    const copy = response.clone();
    event.waitUntil(caches.open(CACHE).then((cache) => cache.put(request, copy)));
  }
  return response;
}
