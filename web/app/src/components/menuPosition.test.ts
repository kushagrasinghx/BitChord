import { describe, expect, it } from 'vitest';
import { pointPosition } from './menuPosition';

describe('a right-click menu', () => {
  const viewport = { width: 1000, height: 800 };
  const size = { width: 200, height: 300 };

  it('opens with its corner at the click', () => {
    expect(pointPosition({ x: 100, y: 120 }, size, viewport)).toMatchObject({ left: 100, top: 120 });
  });

  it('moves in from the right and bottom edges instead of overflowing', () => {
    expect(pointPosition({ x: 950, y: 700 }, size, viewport)).toMatchObject({ left: 792, top: 492 });
  });

  it('stays 8px inside a window too small for it', () => {
    expect(pointPosition({ x: 5, y: 5 }, { width: 2000, height: 2000 }, viewport)).toMatchObject({ left: 8, top: 8, maxHeight: 784 });
  });
});
