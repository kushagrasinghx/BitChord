// Bundles the desktop app's main process and preload (src/) into out/, with
// the extension's lib code they share. With --with-app it first builds the
// site (web/app) and copies it to renderer/, which is what the app serves.
//
//   node scripts/build.mjs              main + preload only
//   node scripts/build.mjs --with-app   the site too (needs VITE_PARTY_SERVER_URL, as web/app's build does)

import { cpSync, existsSync, readFileSync, rmSync } from 'node:fs';
import { createRequire } from 'node:module';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { build } from 'esbuild';

const root = fileURLToPath(new URL('..', import.meta.url));
const appDir = fileURLToPath(new URL('../../app/', import.meta.url));
const extensionPackage = JSON.parse(readFileSync(new URL('../../extension/package.json', import.meta.url), 'utf8'));

if (process.argv.includes('--with-app')) {
  // web/app's own Vite, with its own config (and its check for the party server).
  const vitePath = createRequire(`${appDir}package.json`).resolve('vite');
  const vite = await import(pathToFileURL(vitePath).href);
  await vite.build({ root: appDir, configFile: `${appDir}vite.config.ts`, mode: 'production' });

  const renderer = `${root}renderer`;
  rmSync(renderer, { recursive: true, force: true });
  cpSync(`${appDir}dist`, renderer, { recursive: true });
  // The extension's zips are for browsers; the desktop app is its own bridge.
  rmSync(`${renderer}/downloads`, { recursive: true, force: true });
  console.log('renderer/ ← web/app/dist');
}

const common = {
  bundle: true,
  sourcemap: true,
  external: ['electron'],
  logLevel: 'info',
  define: {
    __BRIDGE_VERSION__: JSON.stringify(extensionPackage.version),
    // The extension's lib code, as its Chromium build compiles it.
    'import.meta.env.FIREFOX': 'false',
    __DISCORD_PRESENCE__: 'true',
  },
};

await build({
  ...common,
  entryPoints: [`${root}src/main.ts`],
  outfile: `${root}out/main.cjs`,
  platform: 'node',
  target: 'node22',
  format: 'cjs',
});

await build({
  ...common,
  entryPoints: [`${root}src/preload.ts`],
  outfile: `${root}out/preload.cjs`,
  // A sandboxed preload: browser APIs plus `require('electron')`.
  platform: 'browser',
  target: 'chrome130',
  format: 'cjs',
});

if (!existsSync(`${root}renderer/index.html`)) {
  console.warn('\nrenderer/ is empty: run with --with-app to build the site into it, or use `pnpm dev` against the Vite server.');
}
