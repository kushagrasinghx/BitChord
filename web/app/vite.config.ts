import { readFileSync } from 'node:fs';
import { fileURLToPath, URL } from 'node:url';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';

// The development extension's id, pinned by its manifest `key`
// (web/extension/dev-key/). Read at build time so the page can message it.
function devExtensionId(): string {
  try {
    return readFileSync(new URL('../extension/dev-key/extension-id.txt', import.meta.url), 'utf8').trim();
  } catch {
    return '';
  }
}

// The party server's address stays out of the repo: .env.local here, a
// Cloudflare Pages variable in production. Without it Listen Together can't
// make a party, so a production build stops instead of shipping that.
function requirePartyServer(mode: string): void {
  const env = loadEnv(mode, fileURLToPath(new URL('.', import.meta.url)), 'VITE_');
  if (!env.VITE_PARTY_SERVER_URL?.trim()) {
    throw new Error('VITE_PARTY_SERVER_URL is not set (app/.env.local, or the Cloudflare Pages environment).');
  }
}

/**
 * Streaming proxy middleware for audio streams from CDNs that serve .flac files
 * with misconfigured Content-Type headers (e.g. audio/mpeg). Rewrites the Content-Type
 * header to audio/flac so Chrome's HTMLMediaElement decodes it natively.
 */
function audioProxyPlugin() {
  return {
    name: 'audio-proxy-plugin',
    configureServer(server: any) {
      server.middlewares.use(async (req: any, res: any, next: any) => {
        if (!req.url?.includes('/api/stream-audio')) return next();
        try {
          const reqUrl = new URL(req.url, 'http://localhost:5173');
          const targetUrl = reqUrl.searchParams.get('url');
          const forcedType = reqUrl.searchParams.get('type') || 'audio/flac';
          if (!targetUrl) {
            res.statusCode = 400;
            res.end('Missing url parameter');
            return;
          }

          const headers: Record<string, string> = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36',
          };
          if (req.headers.range) {
            headers['Range'] = req.headers.range;
          }

          const upstream = await fetch(targetUrl, { headers });
          const responseHeaders: Record<string, string> = {
            'Content-Type': forcedType,
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Headers': '*',
            'Accept-Ranges': 'bytes',
          };
          const contentRange = upstream.headers.get('content-range');
          if (contentRange) responseHeaders['Content-Range'] = contentRange;
          const contentLength = upstream.headers.get('content-length');
          if (contentLength) responseHeaders['Content-Length'] = contentLength;

          res.writeHead(upstream.status, responseHeaders);

          if (upstream.body) {
            const { Readable } = await import('node:stream');
            Readable.fromWeb(upstream.body as any).pipe(res);
          } else {
            res.end();
          }
        } catch (err) {
          if (!res.headersSent) {
            res.statusCode = 502;
            res.end(String(err));
          }
        }
      });
    },
  };
}

export default defineConfig(({ command, mode }) => {
  if (command === 'build') requirePartyServer(mode);
  return {
    plugins: [react(), tailwindcss(), audioProxyPlugin()],
    define: {
      DEV_EXTENSION_ID: JSON.stringify(devExtensionId()),
    },
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    // AutoMix's analysis worker (playback/automix): essentia.js's 2021-era
    // Emscripten glue breaks under dep pre-bundling, and the worker is a module.
    optimizeDeps: { exclude: ['essentia.js'] },
    worker: { format: 'es' },
    server: {
      // The extension's externally_connectable and DNR rules name this origin
      // exactly, so the port must not drift to 5174 when 5173 is busy.
      port: 5173,
      strictPort: true,
    },
  };
});
