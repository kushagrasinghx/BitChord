// BitChord Audio Stream Service Worker
// Fixes misconfigured upstream CDNs that serve FLAC audio with audio/mpeg headers.
self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (url.pathname.includes('/stream-audio')) {
    const targetUrl = url.searchParams.get('url');
    const forcedType = url.searchParams.get('type') || 'audio/flac';
    if (!targetUrl) return;

    event.respondWith(
      (async () => {
        try {
          const upstream = await fetch(targetUrl, {
            headers: event.request.headers,
          });
          const headers = new Headers(upstream.headers);
          headers.set('Content-Type', forcedType);
          headers.set('Access-Control-Allow-Origin', '*');
          headers.set('Accept-Ranges', 'bytes');
          return new Response(upstream.body, {
            status: upstream.status,
            statusText: upstream.statusText,
            headers,
          });
        } catch (err) {
          return new Response(String(err), { status: 502 });
        }
      })(),
    );
  }
});
