#!/usr/bin/env node
/**
 * Keeps the web player level with the Android app (web/parity.json).
 *
 * Each feature in parity.json names its Android files, its web files, and any
 * shared/commonMain code both run. A shared rule follows Android by itself (the
 * web runs it compiled, web/shared-kotlin). What can't be shared is where the two
 * can drift, so this:
 *
 *   node web/scripts/parity.mjs check              every path in the map exists (fails if not)
 *   node web/scripts/parity.mjs changed <files...> warn, as GitHub annotations, about each
 *                                                  feature whose Android side changed while
 *                                                  its web side didn't
 *
 * CI (.github/workflows/web.yml) runs both on every PR; `changed` gets the PR's
 * changed files. Warnings don't fail the build: an Android-only fix is often
 * Android-only. They say where to look, so a gap is a decision, not an accident.
 */
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repo = resolve(dirname(fileURLToPath(import.meta.url)), '../..');

export function loadMap(file = join(repo, 'web/parity.json')) {
  return JSON.parse(readFileSync(file, 'utf8')).features;
}

/** Paths in the map that don't exist in the repository. */
export function missingPaths(features, exists = (p) => existsSync(join(repo, p))) {
  return features.flatMap((f) =>
    ['android', 'web', 'shared'].flatMap((side) => (f[side] ?? []).filter((p) => !exists(p)).map((path) => ({ feature: f.id, side, path }))),
  );
}

const touches = (paths = [], changed) => paths.some((p) => changed.some((c) => c === p || c.startsWith(p.endsWith('/') ? p : `${p}/`)));

/** Features whose Android files changed while neither their web nor their shared files did. */
export function drifted(features, changed) {
  return features
    .filter((f) => touches(f.android, changed) && !touches(f.web, changed) && !touches(f.shared, changed))
    .map((f) => ({ feature: f, androidChanged: changed.filter((c) => touches(f.android, [c])) }));
}

function main([command, ...args]) {
  const features = loadMap();
  if (command === 'check') {
    const missing = missingPaths(features);
    for (const m of missing) console.log(`::error file=web/parity.json::${m.feature}: ${m.side} path ${m.path} doesn't exist; update web/parity.json`);
    if (missing.length) process.exit(1);
    console.log(`web/parity.json: ${features.length} features, every path exists.`);
  } else if (command === 'changed') {
    const gaps = drifted(features, args);
    for (const { feature, androidChanged } of gaps) {
      const where = androidChanged[0] ?? feature.android[0];
      console.log(
        `::warning file=${where}::${feature.name}: the Android side changed, its web side didn't. ` +
          `If the change should reach the web too, it lives in ${feature.web.join(', ')} (web/parity.json).`,
      );
    }
    if (!gaps.length) console.log('No Android feature changed without its web side.');
  } else {
    console.error('usage: parity.mjs check | changed <files...>');
    process.exit(2);
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main(process.argv.slice(2));
