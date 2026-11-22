#!/usr/bin/env node
/**
 * Builds BitChord for Windows from upstream's sources, untouched:
 *
 *   1. web/extension → the Chrome MV3 build, told to serve the shell's origin.
 *   2. web/app       → the web player, as Cloudflare Pages builds it.
 *   3. windows/      → the Tauri shell around both (installer, or `--dev`).
 *
 * Everything upstream-specific is read from upstream's own files (the version
 * from build.gradle.kts, the pnpm version from web/package.json), so pulling a
 * new upstream release needs no change here.
 *
 *   node windows/scripts/build.mjs          NSIS installer in windows/src-tauri/target/release/bundle
 *   node windows/scripts/build.mjs --dev    Vite dev server + hot-reloading shell
 */
import { spawnSync } from 'node:child_process';
import { cpSync, existsSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const windowsDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const root = resolve(windowsDir, '..');
const web = join(root, 'web');
const dev = process.argv.includes('--dev');

/** Where the shell serves web/app from (WebView2 maps Tauri's protocol here), and the Vite dev server. */
const SHELL_ORIGIN = 'http://tauri.localhost';
const DEV_ORIGIN = 'http://localhost:5173';
/** The phone's own default (app/build.gradle.kts), used when nothing overrides it. */
const DEFAULT_PARTY_SERVER = 'https://bitchord-listen-together.onrender.com';

function run(cmd, args, options = {}) {
  // shell: npx and pnpm are .cmd shims on Windows, so paths with spaces need quoting by hand.
  const quoted = args.map((a) => (/\s/.test(a) && !a.startsWith('"') ? `"${a}"` : a));
  console.log(`\n> ${cmd} ${quoted.join(' ')}`);
  const result = spawnSync(cmd, quoted, { stdio: 'inherit', shell: true, ...options, env: { ...process.env, ...options.env } });
  if (result.status !== 0) process.exit(result.status ?? 1);
}

const pnpmVersion = JSON.parse(readFileSync(join(web, 'package.json'), 'utf8')).packageManager; // "pnpm@x.y.z"
const pnpm = (args, options) => run('npx', ['-y', pnpmVersion, ...args], options);

/** "1.10" (+ "-beta2" while one is set) from the line upstream bumps for every release. */
function upstreamVersion() {
  const gradle = readFileSync(join(root, 'build.gradle.kts'), 'utf8');
  const name = /extra\["bitchordVersionName"\]\s*=\s*"([^"]+)"/.exec(gradle)?.[1];
  const beta = /extra\["bitchordBetaSuffix"\]\s*=\s*"([^"]*)"/.exec(gradle)?.[1] ?? '';
  if (!name) throw new Error('bitchordVersionName not found in build.gradle.kts');
  // Tauri wants semver: 1.10 → 1.10.0, beta2 → 1.10.0-beta2.
  const semver = name.split('.').concat('0', '0').slice(0, 3).join('.');
  return beta ? `${semver}-${beta.replace(/^-/, '')}` : semver;
}

// ---- 0. Dependencies -----------------------------------------------------------

if (!existsSync(join(web, 'node_modules'))) pnpm(['--dir', web, 'install', '--frozen-lockfile']);
if (!existsSync(join(windowsDir, 'node_modules'))) run('npm', ['ci'], { cwd: windowsDir });

// ---- 1. Extension --------------------------------------------------------------

// The extension only answers pages on these origins (web/extension/site.config.ts).
pnpm(['--dir', join(web, 'extension'), 'exec', 'wxt', 'build', '-b', 'chrome'], {
  env: { BITCHORD_SITE_ORIGINS: [SHELL_ORIGIN, DEV_ORIGIN].join(',') },
});

const extensionOut = join(windowsDir, 'build', 'extension');
rmSync(extensionOut, { recursive: true, force: true });
cpSync(join(web, 'extension', '.output', 'chrome-mv3'), extensionOut, { recursive: true });

// WebView2 shows no permission prompts, so the optional hosts (lyric providers,
// Discord) a browser would ask for on grant.html are granted up front instead.
const manifestPath = join(extensionOut, 'manifest.json');
const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));

// What the shell needs from upstream's extension build; fail here, not quietly in the app.
// - It serves the shell's page: BITCHORD_SITE_ORIGINS reached web/extension/site.config.ts.
// - Its id is pinned by the manifest `key`, which is the id web/app was built to message.
if (!manifest.externally_connectable?.matches?.includes(`${SHELL_ORIGIN}/*`)) {
  throw new Error(`The extension doesn't serve ${SHELL_ORIGIN}: has BITCHORD_SITE_ORIGINS (web/extension/site.config.ts) been renamed?`);
}
if (!manifest.key) {
  throw new Error('The extension has no manifest `key`, so its id is no longer the one web/app messages (web/extension/wxt.config.ts).');
}
manifest.host_permissions = [...new Set([...(manifest.host_permissions ?? []), ...(manifest.optional_host_permissions ?? [])])];
delete manifest.optional_host_permissions;
writeFileSync(manifestPath, JSON.stringify(manifest, null, 2));

// ---- 2. Web app ----------------------------------------------------------------

const appEnv = {
  VITE_PARTY_SERVER_URL: process.env.VITE_PARTY_SERVER_URL || process.env.LISTEN_TOGETHER_SERVER || DEFAULT_PARTY_SERVER,
};

// ---- 3. Shell ------------------------------------------------------------------

const version = upstreamVersion();
const tauri = (...args) => run('npx', ['tauri', ...args], { cwd: windowsDir, env: appEnv });

// Merged over src-tauri/tauri.conf.json; a file, so no JSON goes through cmd.exe quoting.
const override = join(windowsDir, 'build', 'tauri.override.json');
const config = dev
  ? { version, build: { beforeDevCommand: `npx -y ${pnpmVersion} --dir "${join(web, 'app')}" dev` } }
  : { version };
writeFileSync(override, JSON.stringify(config, null, 2));

if (!dev) pnpm(['--dir', join(web, 'app'), 'build'], { env: appEnv });
tauri(dev ? 'dev' : 'build', '--config', override);
