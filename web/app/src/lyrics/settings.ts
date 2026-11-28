import { create } from 'zustand';
import { LYRICS_SOURCES, orderedSources, type LyricsSourceId } from './sources';

/**
 * Which lyric providers are asked, and in what order (the desktop's lyric
 * settings). Kept in localStorage; a source added in a later build lands
 * where a fresh install would put it (sources.orderedSources).
 */
interface LyricsSettings {
  order: LyricsSourceId[];
  disabled: LyricsSourceId[];
  /** Keep looking past a line-timed answer for a word-timed one (LyricsRepository.prioritizeSyllableSync). */
  preferSyllables: boolean;
  setEnabled: (id: LyricsSourceId, on: boolean) => void;
  move: (id: LyricsSourceId, by: -1 | 1) => void;
  setPreferSyllables: (on: boolean) => void;
  reset: () => void;
}

const KEY = 'bitchord:lyrics';
type Saved = Pick<LyricsSettings, 'order' | 'disabled' | 'preferSyllables'>;

const DEFAULTS: Saved = {
  order: LYRICS_SOURCES.map((s) => s.id),
  disabled: [],
  preferSyllables: false,
};

function load(): Saved {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) ?? '{}') as Partial<Saved>;
    return { ...DEFAULTS, ...saved, order: orderedSources(saved.order ?? DEFAULTS.order) };
  } catch {
    return DEFAULTS;
  }
}

export const useLyricsSettings = create<LyricsSettings>()((set, get) => {
  const save = (change: Partial<Saved>) => {
    set(change);
    const { order, disabled, preferSyllables } = get();
    localStorage.setItem(KEY, JSON.stringify({ order, disabled, preferSyllables }));
  };
  return {
    ...load(),
    setEnabled: (id, on) => save({ disabled: on ? get().disabled.filter((d) => d !== id) : [...new Set([...get().disabled, id])] }),
    move: (id, by) => {
      const order = [...get().order];
      const at = order.indexOf(id);
      const to = at + by;
      if (at < 0 || to < 0 || to >= order.length) return;
      [order[at], order[to]] = [order[to]!, order[at]!];
      save({ order });
    },
    setPreferSyllables: (preferSyllables) => save({ preferSyllables }),
    reset: () => save(DEFAULTS),
  };
});

/** The sources that will be asked, in the order they're asked. */
export function enabledSources(): LyricsSourceId[] {
  const { order, disabled } = useLyricsSettings.getState();
  return order.filter((id) => !disabled.includes(id));
}
