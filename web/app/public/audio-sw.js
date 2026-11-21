// BitChord Audio Service Worker Cleanup
// Unregisters any previously active service worker so audio requests stream directly through the proxy server.
self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    self.registration.unregister().then(() => {
      return self.clients.claim();
    }),
  );
});

// Pass all network and audio requests through directly to the Vite proxy server
self.addEventListener('fetch', () => {
  // Do not call event.respondWith - fall through immediately to network
});
