import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { fileURLToPath, URL } from 'node:url';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv, type Plugin } from 'vite';
import { precacheList } from './src/pwa/precache.ts';

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

// Emits dist/sw.js from pwa/sw.js with this build's precache list. The version
// is a hash of that list, whose names are content hashes, so an unchanged
// shell keeps the installed worker.
function serviceWorker(): Plugin {
  return {
    name: 'bitchord-service-worker',
    apply: 'build',
    generateBundle(_options, bundle) {
      const files = Object.values(bundle).map((file) => ({
        fileName: file.fileName,
        size: file.type === 'chunk' ? Buffer.byteLength(file.code) : Buffer.byteLength(file.source),
      }));
      const precache = ['/', '/manifest.webmanifest', '/favicon.svg', '/icons/icon-192.png', ...precacheList(files)];
      const version = createHash('sha256').update(precache.join('\n')).digest('hex').slice(0, 12);
      const template = readFileSync(new URL('./pwa/sw.js', import.meta.url), 'utf8');
      this.emitFile({
        type: 'asset',
        fileName: 'sw.js',
        source: template.replace('__VERSION__', JSON.stringify(version)).replace('__PRECACHE__', JSON.stringify(precache)),
      });
    },
  };
}

export default defineConfig(({ command, mode }) => {
  if (command === 'build') requirePartyServer(mode);
  return {
    plugins: [react(), tailwindcss(), serviceWorker()],
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
