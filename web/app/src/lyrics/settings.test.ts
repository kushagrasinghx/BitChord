// @vitest-environment happy-dom
import { beforeEach, describe, expect, it } from 'vitest';
import { useLyricsSettings } from './settings';

describe('useLyricsSettings', () => {
  beforeEach(() => {
    localStorage.clear();
    useLyricsSettings.getState().reset();
  });

  it('initializes showTranslation and showRomanization as false by default', () => {
    const { showTranslation, showRomanization } = useLyricsSettings.getState();
    expect(showTranslation).toBe(false);
    expect(showRomanization).toBe(false);
  });

  it('persists showTranslation to store and localStorage', () => {
    useLyricsSettings.getState().setShowTranslation(true);
    expect(useLyricsSettings.getState().showTranslation).toBe(true);

    const saved = JSON.parse(localStorage.getItem('bitchord:lyrics') ?? '{}');
    expect(saved.showTranslation).toBe(true);

    useLyricsSettings.getState().setShowTranslation(false);
    expect(useLyricsSettings.getState().showTranslation).toBe(false);
  });

  it('persists showRomanization to store and localStorage', () => {
    useLyricsSettings.getState().setShowRomanization(true);
    expect(useLyricsSettings.getState().showRomanization).toBe(true);

    const saved = JSON.parse(localStorage.getItem('bitchord:lyrics') ?? '{}');
    expect(saved.showRomanization).toBe(true);
  });

  it('restores default alternate preferences on reset', () => {
    useLyricsSettings.getState().setShowTranslation(true);
    useLyricsSettings.getState().setShowRomanization(true);
    useLyricsSettings.getState().reset();

    expect(useLyricsSettings.getState().showTranslation).toBe(false);
    expect(useLyricsSettings.getState().showRomanization).toBe(false);
  });
});
