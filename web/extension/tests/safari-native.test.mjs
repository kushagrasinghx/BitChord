import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

const app = fileURLToPath(new URL('../.output/safari-native/build/Build/Products/Release/BitChord.app', import.meta.url));

test('Safari development app has valid resource signatures and sandbox entitlements', {
  skip: process.platform !== 'darwin' || !existsSync(app),
}, () => {
  execFileSync('codesign', ['--verify', '--deep', '--strict', app]);
  for (const bundle of [app, `${app}/Contents/PlugIns/BitChord Extension.appex`]) {
    const xml = execFileSync('codesign', ['--display', '--entitlements', '-', '--xml', bundle], { encoding: 'utf8' });
    const entitlements = JSON.parse(execFileSync('plutil', ['-convert', 'json', '-o', '-', '-'], { input: xml, encoding: 'utf8' }));
    assert.equal(entitlements['com.apple.security.app-sandbox'], true);
    assert.equal(entitlements['com.apple.security.network.client'], true);
  }
});
