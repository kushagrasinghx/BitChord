import { describe, expect, it } from 'vitest';
import { APP_TITLE, documentTitle } from './documentTitle';

describe('documentTitle', () => {
  it('falls back to the app title when nothing is playing', () => {
    expect(documentTitle(null)).toBe('BitChord');
    expect(documentTitle(undefined)).toBe('BitChord');
  });

  it('shows the now-playing title before the app name', () => {
    expect(documentTitle({ title: 'Midnight City' })).toBe('Midnight City - BitChord');
  });

  it('matches the index.html fallback', () => {
    expect(APP_TITLE).toBe('BitChord');
  });

  it('ignores a blank title', () => {
    expect(documentTitle({ title: '' })).toBe('BitChord');
    expect(documentTitle({ title: '   ' })).toBe('BitChord');
  });

  it('trims surrounding whitespace from the title', () => {
    expect(documentTitle({ title: '  Midnight City  ' })).toBe('Midnight City - BitChord');
  });
});
