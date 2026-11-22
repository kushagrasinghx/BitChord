import { describe, expect, it } from 'vitest';
import { placeMenu } from './menuPlacement';

const viewport = { width: 1000, height: 800 };
const size = { width: 256, height: 300 };
const button = { left: 900, right: 932, top: 740, bottom: 772, width: 32 };

describe('placeMenu', () => {
  it('hangs above a button low in the window, trailing edges lined up, growing from that corner', () => {
    expect(placeMenu(button, size, viewport, 'above-end')).toEqual({ left: 676, top: 434, maxHeight: 726, origin: 'right bottom' });
  });

  const card = { left: 50, right: 270, top: 50, bottom: 270 };
  it('opens at the pointer on a card, growing from it', () => {
    expect(placeMenu(card, size, viewport, 'below-end', { x: 100, y: 100 })).toMatchObject({ left: 100, top: 100, origin: 'left top' });
  });

  it('flips to whichever side of the pointer has room', () => {
    const low = { left: 600, right: 960, top: 500, bottom: 780 };
    expect(placeMenu(low, size, viewport, 'below-end', { x: 900, y: 700 })).toMatchObject({ left: 644, top: 400, origin: 'right bottom' });
  });

  it('hangs off a row rather than covering it', () => {
    const row = { left: 0, right: 1000, top: 100, bottom: 156 };
    expect(placeMenu(row, size, viewport, 'below-end', { x: 100, y: 120 })).toMatchObject({ top: 162, origin: 'left top' });
    const bar = { left: 300, right: 968, top: 724, bottom: 780 };
    expect(placeMenu(bar, size, viewport, 'below-end', { x: 400, y: 750 })).toMatchObject({ top: 418, origin: 'left bottom' });
  });

  it('scrolls on the roomier side of a row rather than covering it when neither side fits', () => {
    const row = { left: 0, right: 1000, top: 300, bottom: 356 };
    const tall = { width: 256, height: 500 };
    expect(placeMenu(row, tall, viewport, 'below-end', { x: 100, y: 320 })).toEqual({ left: 100, top: 362, maxHeight: 430, origin: 'left top' });
    const high = { left: 0, right: 1000, top: 500, bottom: 556 };
    expect(placeMenu(high, tall, viewport, 'below-end', { x: 100, y: 520 })).toEqual({ left: 100, top: 8, maxHeight: 486, origin: 'left bottom' });
  });
});
