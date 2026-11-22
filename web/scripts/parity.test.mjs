// node --test web/scripts/parity.test.mjs
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { drifted, loadMap, missingPaths } from './parity.mjs';

const features = [
  { id: 'sleep', name: 'Sleep timer', android: ['app/src/SleepTimer.kt'], web: ['web/app/src/sleepTimer.ts'], shared: ['shared/src/commonMain/SleepFade.kt'] },
  { id: 'radio', name: 'Radio', android: ['sharedUi/src/MixesScreen.kt', 'app/src/radio/'], web: ['web/app/src/pages/RadioPage.tsx'] },
];

test('an Android-only change to a feature is flagged, with the files that changed', () => {
  const gaps = drifted(features, ['app/src/radio/Mixes.kt', 'README.md']);
  assert.deepEqual(gaps.map((g) => [g.feature.id, g.androidChanged]), [['radio', ['app/src/radio/Mixes.kt']]]);
});

test('changing the web side too, or the shared code both run, is not a gap', () => {
  assert.deepEqual(drifted(features, ['sharedUi/src/MixesScreen.kt', 'web/app/src/pages/RadioPage.tsx']), []);
  assert.deepEqual(drifted(features, ['app/src/SleepTimer.kt', 'shared/src/commonMain/SleepFade.kt']), []);
});

test('a path that no longer exists is reported', () => {
  const missing = missingPaths(features, (p) => p !== 'web/app/src/sleepTimer.ts');
  assert.deepEqual(missing, [{ feature: 'sleep', side: 'web', path: 'web/app/src/sleepTimer.ts' }]);
});

test("the repository's own map is complete", () => {
  assert.deepEqual(missingPaths(loadMap()), []);
});
