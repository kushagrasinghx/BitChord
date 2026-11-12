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

function unwrapCssLayers(css: string): string {
  // Strip layer declarations like `@layer theme, base, utilities;`
  css = css.replace(/@layer\s+[^;{]+;/g, '');
  // Strip modern @property definitions not understood by Chromium < 85
  css = css.replace(/@property\s+[^{]+{[^}]+}/g, '');
  let changed = true;
  while (changed) {
    changed = false;
    let i = 0;
    let result = '';
    while (i < css.length) {
      const idx = css.indexOf('@layer', i);
      if (idx === -1) {
        result += css.slice(i);
        break;
      }
      result += css.slice(i, idx);
      const open = css.indexOf('{', idx);
      if (open === -1) {
        result += css.slice(idx);
        break;
      }
      let depth = 1;
      let j = open + 1;
      while (j < css.length && depth > 0) {
        if (css[j] === '{') depth++;
        else if (css[j] === '}') depth--;
        j++;
      }
      if (depth === 0) {
        changed = true;
        const inner = css.slice(open + 1, j - 1);
        result += inner;
        i = j;
      } else {
        result += css.slice(idx);
        break;
      }
    }
    css = result;
  }
  return css;
}

function tizenClassicScript() {
  return {
    name: 'tizen-classic-script',
    transformIndexHtml(html: string) {
      return html
        .replace('<head>', '<head>\n    <script src="./tizen-polyfills.js"></script>\n    <link rel="stylesheet" href="./assets/style.css" />')
        .replace(/<meta name="viewport"[^>]*>/, '<meta name="viewport" content="width=1920, height=1080, initial-scale=1.0, user-scalable=no" />\n    <style>html, body { width: 100vw !important; height: 100vh !important; min-width: 100% !important; min-height: 100% !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important; } #root { width: 100% !important; height: 100% !important; overflow: hidden !important; }</style>')
        .replace(/<script type="module" crossorigin/g, '<script defer')
        .replace(/<script type="module"/g, '<script defer')
        .replace(/<script>\s*\/\/ The extension trusts[\s\S]*?<\/script>/, '');
    },
    generateBundle(this: any, _options: unknown, bundle: Record<string, any>) {
      const cssRegex = /(var\s+e\s*=\s*document\.createElement\([`'"]style[`'"]\);\s*e\.textContent\s*=\s*`)([\s\S]*?)(`\s*,\s*document\.head\.appendChild\(e\);?)/;
      for (const file of Object.values(bundle)) {
        if (file.type === 'chunk' && file.code) {
          const match = file.code.match(cssRegex);
          if (match) {
            const unwrapped = unwrapCssLayers(match[2]);
            file.code = file.code.replace(cssRegex, `${match[1]}${unwrapped}${match[3]}`);
            this.emitFile({
              type: 'asset',
              fileName: 'assets/style.css',
              source: unwrapped,
            });
          }
        }
      }
    },
  };
}

export default defineConfig(({ command, mode }) => {
  if (command === 'build') requirePartyServer(mode);
  return {
    base: './',
    plugins: [react(), tailwindcss(), tizenClassicScript()],
    define: {
      DEV_EXTENSION_ID: JSON.stringify(devExtensionId()),
    },
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    build: {
      target: 'chrome56',
      rollupOptions: {
        output: {
          format: 'iife',
          name: 'BitChord',
          inlineDynamicImports: true,
        },
      },
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
