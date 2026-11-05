import assert from 'node:assert/strict';
import { test } from 'node:test';
import { youtubeRules } from '../lib/rules.ts';

test('Safari rules omit unsupported X-Origin without dropping Origin, Referer or CORS', () => {
  const rules = youtubeRules(['localhost'], true);
  assert.deepEqual(rules[0].action.requestHeaders, [
    { header: 'Origin', operation: 'set', value: 'https://music.youtube.com' },
    { header: 'Referer', operation: 'set', value: 'https://music.youtube.com/' },
  ]);
  assert.deepEqual(rules[0].condition.initiatorDomains, ['localhost']);
  assert.deepEqual(youtubeRules(['localhost'])[0].condition.initiatorDomains, ['localhost']);
  assert.ok(rules[0].action.responseHeaders.some(header => header.header === 'Access-Control-Allow-Origin'));
  assert.ok(youtubeRules(['localhost'])[0].action.requestHeaders.some(header => header.header === 'X-Origin'));
});
