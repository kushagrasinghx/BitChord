import { describe, expect, it } from 'vitest';
import { nextSelection } from './trackSelection';

const order = ['a', 'b', 'c', 'd', 'e'];

describe('Track list selection', () => {
  it('toggles one row and makes it the anchor', () => {
    const one = nextSelection(null, order, 'b', false);
    expect([...one.ids]).toEqual(['b']);
    expect(one.anchor).toBe('b');
    expect(nextSelection(one, order, 'b', false).ids.size).toBe(0);
  });

  it('adds the run from the anchor on Shift, either way, keeping the anchor', () => {
    const start = nextSelection(null, order, 'd', false);
    const back = nextSelection(start, order, 'b', true);
    expect([...back.ids].sort()).toEqual(['b', 'c', 'd']);
    expect(back.anchor).toBe('d');
    const forward = nextSelection(nextSelection(null, order, 'a', false), order, 'c', true);
    expect([...forward.ids].sort()).toEqual(['a', 'b', 'c']);
  });

  it('treats Shift with no anchor as a plain toggle', () => {
    expect([...nextSelection(null, order, 'c', true).ids]).toEqual(['c']);
  });
});
