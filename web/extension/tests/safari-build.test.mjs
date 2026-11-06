import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const root = new URL('../.output/safari-mv3/', import.meta.url);
const manifest = JSON.parse(readFileSync(new URL('manifest.json', root), 'utf8'));

test('Safari requests the host-scoped permission required to modify headers', () => {
  assert.ok(manifest.permissions.includes('declarativeNetRequestWithHostAccess'));
});

test('Safari can reach the background through a relay on trusted sites', () => {
  const relay = manifest.content_scripts?.find(script => script.js.some(file => file.includes('relay')));
  assert.ok(relay, 'Safari requires the page relay');
  assert.ok(relay.matches.includes('https://bitchord.in/*'));
  assert.ok(!relay.matches.includes('<all_urls>'));
  for (const file of relay.js) assert.ok(readFileSync(new URL(file, root)).length);
});

test('Safari package omits Chromium identity and unverified Discord integration', () => {
  assert.equal(manifest.manifest_version, 3);
  assert.equal(manifest.key, undefined);
  assert.equal(manifest.externally_connectable, undefined);
  assert.ok(!manifest.permissions.includes('scripting'));
  assert.ok(!manifest.optional_host_permissions.includes('https://discord.com/*'));
});

test('Safari uses an inspectable nonpersistent background page', () => {
  assert.deepEqual(manifest.background.scripts, ['background.js']);
  assert.equal(manifest.background.persistent, false);
  assert.equal(manifest.background.service_worker, undefined);
});
