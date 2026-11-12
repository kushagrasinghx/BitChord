// Renders the extension's toolbar and store icons from the site's app icon
// (web/app/public/favicon.svg), so both carry the same BitChord logo. WXT
// picks up public/icon/<size>.png on its own. The PNGs are committed; run this
// again only when the logo changes:
//
//   corepack pnpm --dir web/extension icons

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { Resvg } from '@resvg/resvg-js';

const source = readFileSync(new URL('../../app/public/favicon.svg', import.meta.url), 'utf8');
const target = fileURLToPath(new URL('../public/icon/', import.meta.url));
mkdirSync(target, { recursive: true });

for (const size of [16, 32, 48, 96, 128]) {
  const png = new Resvg(source, { fitTo: { mode: 'width', value: size } }).render().asPng();
  writeFileSync(`${target}${size}.png`, png);
  console.log(`public/icon/${size}.png`);
}
