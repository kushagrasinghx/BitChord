import assert from 'node:assert/strict';
import { test } from 'node:test';
import { affectsSession } from '../lib/cookieChanges.ts';

test('payload-free Safari cookie notifications refresh sessions without calling the predicate', () => {
  const predicate = () => { throw new Error('No cookie to inspect'); };
  assert.equal(affectsSession(undefined, predicate), true);
  assert.equal(affectsSession(null, predicate), true);
});

test('normal cookie notifications retain service-specific filtering', () => {
  const predicate = cookie => cookie.name === 'session';
  assert.equal(affectsSession({ cookie: { name: 'session' } }, predicate), true);
  assert.equal(affectsSession({ cookie: { name: 'preference' } }, predicate), false);
});
