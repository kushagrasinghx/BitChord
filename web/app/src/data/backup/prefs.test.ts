// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { create } from 'zustand';

const player = vi.hoisted(() => ({ state: null as unknown as { getState: () => Record<string, unknown> } }));
vi.mock('@/state/player', () => {
  const usePlayer = create<Record<string, unknown>>()((set, get) => ({
    autoplay: true,
    shuffle: false,
    repeat: 'off',
    crossfadeSec: 0,
    showNerdStats: false,
    setAutoplay: (autoplay: boolean) => set({ autoplay }),
    toggleShuffle: () => set({ shuffle: !get().shuffle }),
    cycleRepeat: () => set({ repeat: ({ off: 'all', all: 'one', one: 'off' } as Record<string, string>)[get().repeat as string] }),
    setCrossfade: (crossfadeSec: number) => set({ crossfadeSec }),
    setShowNerdStats: (showNerdStats: boolean) => set({ showNerdStats }),
  }));
  player.state = usePlayer;
  return { usePlayer, CROSSFADE_CHOICES: [0, 3, 6, 9, 12] };
});
vi.mock('@/lyrics/settings', () => ({
  useLyricsSettings: create<{ preferSyllables: boolean; setPreferSyllables: (v: boolean) => void }>()((set) => ({
    preferSyllables: false,
    setPreferSyllables: (preferSyllables) => set({ preferSyllables }),
  })),
}));

const { exportSettings, importSettings } = await import('./prefs');
const { usePremiumAudio } = await import('@/playback/youtube/premiumAudio');

describe('settings in a backup (AppSettings keys, PrefValue types)', () => {
  it('writes this player’s settings under the phone’s keys and types', () => {
    expect(exportSettings()).toMatchObject({
      autoplay: { type: 'bool', value: 'true', values: [] },
      repeat_mode: { type: 'int', value: '0', values: [] },
      crossfade_seconds: { type: 'int', value: '0', values: [] },
      youtube_premium_audio: { type: 'bool', value: 'false', values: [] },
    });
  });

  it('applies what it has, skips what it can’t read, and carries the rest to the next export', () => {
    const count = importSettings({
      autoplay: { type: 'bool', value: 'false', values: [] },
      shuffle_enabled: { type: 'bool', value: 'true', values: [] },
      repeat_mode: { type: 'int', value: '1', values: [] },
      crossfade_seconds: { type: 'int', value: '5', values: [] },
      show_nerd_stats: { type: 'string', value: 'true', values: [] },
      youtube_premium_audio: { type: 'bool', value: 'true', values: [] },
      theme_mode: { type: 'string', value: 'DARK', values: [] },
      liked_tabs: { type: 'stringSet', value: null, values: ['a', 'b'] },
    });
    expect(count).toBe(8);
    expect(player.state.getState()).toMatchObject({ autoplay: false, shuffle: true, repeat: 'one', crossfadeSec: 6, showNerdStats: false });
    expect(usePremiumAudio.getState().on).toBe(true);
    const out = exportSettings();
    expect(out.theme_mode).toEqual({ type: 'string', value: 'DARK', values: [] });
    expect(out.liked_tabs).toEqual({ type: 'stringSet', value: null, values: ['a', 'b'] });
    expect(out.repeat_mode?.value).toBe('1');
  });
});
