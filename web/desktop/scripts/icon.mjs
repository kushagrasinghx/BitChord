// Renders the desktop app's icon (build/icon.png, which electron-builder turns
// into .ico, .icns and the Linux sizes) from the site's app icon
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
