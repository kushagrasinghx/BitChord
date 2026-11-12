// Pins the development extension's Chromium id.
//
// Chrome derives an unpacked extension's id from its install path unless the
// manifest carries a `key`, so every developer would get a different id and
// the page couldn't find the extension. This writes a public key (for the
// manifest) and the id it yields (for the web app). The private key is
// thrown away: unpacked loading never needs it, and the Chrome Web Store
// assigns its own id to the published build.
//
//   node scripts/gen-dev-key.mjs   (only to rotate; the committed key is fine)

import { createHash, generateKeyPairSync } from 'node:crypto';
import { writeFileSync } from 'node:fs';

const { publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const der = publicKey.export({ type: 'spki', format: 'der' });

// The id is the first 128 bits of SHA-256(DER), written in the letters a–p.
const id = [...createHash('sha256').update(der).digest('hex').slice(0, 32)]
  .map((c) => String.fromCharCode('a'.charCodeAt(0) + parseInt(c, 16)))
  .join('');

writeFileSync(new URL('../dev-key/public-key.txt', import.meta.url), `${der.toString('base64')}\n`);
writeFileSync(new URL('../dev-key/extension-id.txt', import.meta.url), `${id}\n`);
console.log(`Development extension id: ${id}`);
