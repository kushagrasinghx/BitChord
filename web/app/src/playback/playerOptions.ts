import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/** The phone's player options (AppSettings show_best_part, show_total_duration), kept by this page. */
interface PlayerOptions {
  /** The Best Part button on the full-screen player's artwork. */
  showBestPart: boolean;
  /** The seek line's right-hand time: the song's length instead of the time left. */
  showTotalDuration: boolean;
  setShowBestPart: (on: boolean) => void;
  setShowTotalDuration: (on: boolean) => void;
}

export const usePlayerOptions = create<PlayerOptions>()(
  persist(
    (set) => ({
      showBestPart: true,
      showTotalDuration: false,
      setShowBestPart: (showBestPart) => set({ showBestPart }),
      setShowTotalDuration: (showTotalDuration) => set({ showTotalDuration }),
    }),
    { name: 'bitchord:player-options', version: 1 },
  ),
);
