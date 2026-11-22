// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';

describe('theme', () => {
  beforeEach(() => {
    vi.resetModules();
    localStorage.clear();
    delete document.documentElement.dataset.theme;
  });

  it('opens dark, as the phone ships', async () => {
    const { useTheme } = await import('./theme');
    expect(useTheme.getState().mode).toBe('dark');
    expect(document.documentElement.dataset.theme).toBe('dark');
  });

  it('puts the choice on <html> and keeps it; System leaves it to the OS', async () => {
    const { useTheme } = await import('./theme');
    useTheme.getState().setMode('light');
    expect(document.documentElement.dataset.theme).toBe('light');
    expect(localStorage.getItem('bitchord:theme')).toBe('light');

    useTheme.getState().setMode('system');
    expect(document.documentElement.dataset.theme).toBeUndefined();

    vi.resetModules();
    const again = await import('./theme');
    expect(again.useTheme.getState().mode).toBe('system');
  });
});
