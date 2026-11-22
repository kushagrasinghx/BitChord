import { useLyricsSettings } from '@/lyrics/settings';
import { useOfflineSettings } from '@/playback/offline/settings';
import { usePremiumAudio } from '@/playback/youtube/premiumAudio';
import { CROSSFADE_CHOICES, usePlayer, type RepeatMode } from '@/state/player';

/**
 * The phone's preferences in a backup or a device manifest (AppSettings,
 * written as Backup.kt's PrefValue): each value a string, with the type it has
 * to be restored as, so a phone reading a file this browser wrote puts every
 * value back as exactly the type it expects.
 */
export interface PrefValue {
  type: 'bool' | 'int' | 'long' | 'float' | 'string' | 'stringSet';
  value: string | null;
  values: string[];
}

/** One phone preference this player has too, under the phone's key and type. */
interface Mapped {
  key: string;
  type: 'bool' | 'int';
  read: () => boolean | number;
  write: (value: boolean | number) => void;
}

// Media3's Player.REPEAT_MODE_*, which is what the phone stores.
const REPEAT_TO_ANDROID: Record<RepeatMode, number> = { off: 0, one: 1, all: 2 };

const player = () => usePlayer.getState();

const MAPPED: Mapped[] = [
  { key: 'autoplay', type: 'bool', read: () => player().autoplay, write: (v) => v !== player().autoplay && player().setAutoplay(!!v) },
  { key: 'shuffle_enabled', type: 'bool', read: () => player().shuffle, write: (v) => v !== player().shuffle && player().toggleShuffle() },
  {
    key: 'repeat_mode',
    type: 'int',
    read: () => REPEAT_TO_ANDROID[player().repeat],
    write: (v) => {
      // Two steps at most round off → all → one.
      for (let i = 0; i < 2 && REPEAT_TO_ANDROID[player().repeat] !== v; i++) player().cycleRepeat();
    },
  },
  {
    key: 'crossfade_seconds',
    type: 'int',
    read: () => player().crossfadeSec,
    write: (v) => {
      // The phone's slider is finer than this player's choices: the nearest one.
      const nearest = CROSSFADE_CHOICES.reduce((a, b) => (Math.abs(b - Number(v)) < Math.abs(a - Number(v)) ? b : a));
      if (nearest !== player().crossfadeSec) player().setCrossfade(nearest);
    },
  },
  { key: 'show_nerd_stats', type: 'bool', read: () => player().showNerdStats, write: (v) => player().setShowNerdStats(!!v) },
  {
    key: 'prioritize_syllable_sync',
    type: 'bool',
    read: () => useLyricsSettings.getState().preferSyllables,
    write: (v) => useLyricsSettings.getState().setPreferSyllables(!!v),
  },
  { key: 'youtube_premium_audio', type: 'bool', read: () => usePremiumAudio.getState().on, write: (v) => usePremiumAudio.getState().setOn(!!v) },
  {
    key: 'download_liked_songs',
    type: 'bool',
    read: () => useOfflineSettings.getState().keepLiked,
    write: (v) => useOfflineSettings.getState().setKeepLiked(!!v),
  },
];

const MAPPED_KEYS = new Set(MAPPED.map((m) => m.key));

/**
 * The phone's settings this player has nothing for, from the last backup or
 * device brought in here, carried untouched into the next backup this browser
 * writes, so a round trip through the web doesn't strip the phone's settings.
 */
const CARRIED_KEY = 'bitchord:backup:carried';

function carried(): Record<string, PrefValue> {
  try {
    return JSON.parse(localStorage.getItem(CARRIED_KEY) ?? '{}') as Record<string, PrefValue>;
  } catch {
    return {};
  }
}

function encode(m: Mapped): PrefValue {
  const value = m.read();
  return { type: m.type, value: m.type === 'bool' ? String(!!value) : String(Math.round(Number(value))), values: [] };
}

/** PrefValue.decoded, for the two types mapped here; anything unreadable is skipped rather than guessed at. */
function decode(m: Mapped, pref: PrefValue): boolean | number | null {
  if (pref.type !== m.type || pref.value === null) return null;
  if (m.type === 'bool') return pref.value === 'true' ? true : pref.value === 'false' ? false : null;
  return /^-?\d+$/.test(pref.value) ? Number(pref.value) : null;
}

/** What a backup or manifest written here holds: this player's settings, and the phone's carried ones. */
export function exportSettings(): Record<string, PrefValue> {
  return { ...carried(), ...Object.fromEntries(MAPPED.map((m) => [m.key, encode(m)])) };
}

/** Applies the settings this player has, and keeps the rest for the next export. Returns how many there were. */
export function importSettings(settings: Record<string, PrefValue>): number {
  for (const m of MAPPED) {
    const pref = settings[m.key];
    const value = pref ? decode(m, pref) : null;
    if (value !== null) m.write(value);
  }
  const rest = Object.fromEntries(Object.entries(settings).filter(([key]) => !MAPPED_KEYS.has(key)));
  localStorage.setItem(CARRIED_KEY, JSON.stringify(rest));
  return Object.keys(settings).length;
}
