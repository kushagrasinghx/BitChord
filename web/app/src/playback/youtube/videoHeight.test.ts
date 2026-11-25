import { describe, expect, it } from 'vitest';
import { autoHeight } from './videoHeight';

const ladder = [1080, 720, 480, 360, 240, 144];

describe("Auto's ceiling follows the surface the picture is drawn in", () => {
  it('feeds the mini player preview the smallest rendition, not 1080p', () => {
    expect(autoHeight(80, ladder)).toBe(144);
  });
  it('gives the full-screen sleeve what it fills', () => {
    expect(autoHeight(700, ladder)).toBe(720);
    expect(autoHeight(980, ladder)).toBe(1080);
  });
  it('never asks above the H.264 ceiling or for a height the video lacks', () => {
    expect(autoHeight(2000, [2160, 1440, 1080, 720])).toBe(1080);
    expect(autoHeight(500, [360, 240])).toBe(360);
    expect(autoHeight(0, [])).toBe(1080);
  });
});
