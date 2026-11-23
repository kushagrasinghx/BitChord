import { describe, expect, it } from 'vitest';
import { artworkAtPixels, sizedArtwork } from './artwork';

describe('artwork', () => {
  describe('artworkAtPixels', () => {
    it('resizes googleusercontent images with exact width, height and quality', () => {
      const url = 'https://lh3.googleusercontent.com/abc123xyz=w120-h120-l90-rj';
      expect(artworkAtPixels(url, 512)).toBe('https://lh3.googleusercontent.com/abc123xyz=w512-h512-l90-rj');
    });

    it('resizes ggpht images', () => {
      const url = 'https://yt3.ggpht.com/ytc/test=s88-c-k-c0x00ffffff-no-rj';
      expect(artworkAtPixels(url, 256)).toBe('https://yt3.ggpht.com/ytc/test=w256-h256-l90-rj');
    });

    it('upgrades i.ytimg.com video thumbnails to maxresdefault for >= 720px', () => {
      const url = 'https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg?sqp=test';
      expect(artworkAtPixels(url, 1200)).toBe('https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg');
    });

    it('upgrades i.ytimg.com video thumbnails to hq720 for 480-719px', () => {
      const url = 'https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg?sqp=test';
      expect(artworkAtPixels(url, 544)).toBe('https://i.ytimg.com/vi/dQw4w9WgXcQ/hq720.jpg');
    });

    it('leaves i.ytimg.com low-res thumbnails untouched for small sizes < 480px', () => {
      const url = 'https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg?sqp=test';
      expect(artworkAtPixels(url, 160)).toBe('https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg?sqp=test');
    });

    it('leaves non-resizable third-party URLs untouched', () => {
      const url = 'https://cdn.example.com/cover.png';
      expect(artworkAtPixels(url, 512)).toBe('https://cdn.example.com/cover.png');
    });
  });

  describe('sizedArtwork', () => {
    it('snaps googleusercontent square images to standard cached sizes', () => {
      const url = 'https://lh3.googleusercontent.com/abc123xyz=w120-h120';
      // At cssPx = 50 and DPR <= 2, needed is <= 100, which snaps to 120
      const result = sizedArtwork(url, 50, 1);
      expect(result).toMatch(/=w(120|226)-h\1-l90-rj$/);
    });

    it('upgrades high-res ytimg requests in sizedArtwork', () => {
      const url = 'https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg';
      const result = sizedArtwork(url, 600, 1);
      expect(result).toBe('https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg');
    });

    it('leaves non-square googleusercontent images untouched', () => {
      const url = 'https://lh3.googleusercontent.com/hero=w1200-h400';
      expect(sizedArtwork(url, 600, 16 / 9)).toBe(url);
    });
  });
});
