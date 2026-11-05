import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { unzipSync } from 'fflate';

test('published Safari download contains a loadable extension folder', () => {
  execFileSync(process.execPath, [new URL('../scripts/publish-to-site.mjs', import.meta.url).pathname]);
  const downloads = new URL('../../app/public/downloads/', import.meta.url);
  const metadata = JSON.parse(readFileSync(new URL('extension.json', downloads), 'utf8'));
  assert.ok(metadata.files.safari, 'Safari must have its own download');
  const entries = unzipSync(readFileSync(new URL(metadata.files.safari, downloads)));
  const manifestPath = Object.keys(entries).find(path => path.endsWith('/manifest.json'));
  assert.ok(manifestPath);
  const manifest = JSON.parse(new TextDecoder().decode(entries[manifestPath]));
  assert.equal(manifest.version, metadata.version);
  assert.ok(manifest.content_scripts.length);
});
