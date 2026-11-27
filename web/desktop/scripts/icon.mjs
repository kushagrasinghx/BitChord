// Renders the desktop app's icon (build/icon.png, which electron-builder turns
// into .ico, .icns and the Linux sizes) and its tray icon (build/tray.png at
// 16 px, with @2x and @3x for high-density screens) from the site's app icon
// (web/app/public/favicon.svg). The PNG is committed; run this again only
// when the logo changes:
//
//   corepack pnpm --dir web/desktop icon

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { Resvg } from '@resvg/resvg-js';

const source = readFileSync(new URL('../../app/public/favicon.svg', import.meta.url), 'utf8');
mkdirSync(new URL('../build/', import.meta.url), { recursive: true });
const png = new Resvg(source, { fitTo: { mode: 'width', value: 1024 } }).render().asPng();
writeFileSync(new URL('../build/icon.png', import.meta.url), png);
console.log('build/icon.png');

for (const [scale, suffix] of [[1, ''], [2, '@2x'], [3, '@3x']]) {
  const tray = new Resvg(source, { fitTo: { mode: 'width', value: 16 * scale } }).render().asPng();
  writeFileSync(new URL(`../build/tray${suffix}.png`, import.meta.url), tray);
  console.log(`build/tray${suffix}.png`);
}
