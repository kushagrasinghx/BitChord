import { describe, expect, it } from 'vitest';
import { sizedArtwork, thumbnailFallback } from './artwork';

describe('artwork sizes', () => {
  it('asks for the YouTube thumbnail rendition that suits the size (Models.kt artworkAt)', () => {
    expect(sizedArtwork('https://i.ytimg.com/vi/abc/hqdefault.jpg', 600)).toBe('https://i.ytimg.com/vi/abc/maxresdefault.jpg');
    expect(sizedArtwork('https://i.ytimg.com/vi/abc/hqdefault.jpg', 40)).toBe('https://i.ytimg.com/vi/abc/hqdefault.jpg');
  });

  it('falls back to hqdefault, and remembers a rendition YouTube said it lacks', () => {
    const large = 'https://i.ytimg.com/vi/old/maxresdefault.jpg';
    // A dropped connection falls back once without being remembered.
    expect(thumbnailFallback(large, null, true)).toBe('https://i.ytimg.com/vi/old/hqdefault.jpg');
    expect(sizedArtwork('https://i.ytimg.com/vi/old/hqdefault.jpg', 600)).toBe(large);
    // YouTube's 120×90 "no thumbnail" stand-in is an answer about the video.
    const standIn = { naturalWidth: 120 } as HTMLImageElement;
    expect(thumbnailFallback(large, standIn, false)).toBe('https://i.ytimg.com/vi/old/hqdefault.jpg');
    expect(sizedArtwork('https://i.ytimg.com/vi/old/hqdefault.jpg', 600)).toBe('https://i.ytimg.com/vi/old/hqdefault.jpg');
    expect(thumbnailFallback(large, { naturalWidth: 1280 } as HTMLImageElement, false)).toBeNull();
  });

  it('leaves Google-hosted artwork on its standard sizes', () => {
    expect(sizedArtwork('https://lh3.googleusercontent.com/x=w60-h60', 100)).toMatch(/=w\d+-h\d+-l90-rj$/);
  });
});
