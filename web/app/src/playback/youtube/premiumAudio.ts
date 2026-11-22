import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/**
 * Settings › YouTube Premium Audio (AppSettings.youtubePremiumAudio): the
 * signed-in account has Premium, so ask as it first, which is the only way to
 * be offered Premium's ~256 kbps Opus (itag 774) instead of the anonymous 251.
 * Asked rather than detected, as on the phone: nothing in YouTube's answers
 * confirms the entitlement, and claiming it without Premium only costs the
 * faster anonymous route. Off by default. Its own module so Settings doesn't
 * load the resolver.
 */
export const usePremiumAudio = create<{ on: boolean; setOn: (on: boolean) => void }>()(
  persist((set) => ({ on: false, setOn: (on) => set({ on }) }), { name: 'bitchord:premium-audio', version: 1 }),
);
