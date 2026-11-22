import type { ReplayYearStory } from '@bitchord/shared-kotlin';
import { count, formatDay, formatHour, grouped } from '../format';

/** The cards of the year story (YearPlan.kt YearSlide), in the order they are told. */
export type YearSlide =
  | 'OPENER'
  | 'MINUTES'
  | 'BUSIEST_DAY'
  | 'CLOCK'
  | 'GENRES'
  | 'ARTIST_REVEAL'
  | 'TOP_ARTISTS'
  | 'SONG_REVEAL'
  | 'TOP_SONGS'
  | 'ALBUMS'
  | 'MONTHS'
  | 'DISCOVERY'
  | 'SUMMARY';

const ORDER: YearSlide[] = [
  'OPENER', 'MINUTES', 'BUSIEST_DAY', 'CLOCK', 'GENRES', 'ARTIST_REVEAL', 'TOP_ARTISTS',
  'SONG_REVEAL', 'TOP_SONGS', 'ALBUMS', 'MONTHS', 'DISCOVERY', 'SUMMARY',
];
// Irregular steps, as on the phone: an even walk around the wheel looks like a demo.
const HUES = [0, 40, 75, 120, 160, 205, 235, 270, 300, 330, 20, 100, 180];

/** Where the card's backdrop is turned on the colour wheel (YearSlide.hue). */
export const slideHue = (slide: YearSlide) => HUES[ORDER.indexOf(slide)] ?? 0;

/**
 * How far into the card its big moment lands, in ms (YearSlide.revealAtMs).
 * The clip starts early by this much, so the reveal and the chorus arrive together.
 */
export function revealAtMs(slide: YearSlide): number {
  if (slide === 'ARTIST_REVEAL') return 3_400;
  if (slide === 'SONG_REVEAL') return 3_000;
  if (slide === 'OPENER') return 1_200;
  return 1_600;
}

/** Each card's time on screen, as on the phone. */
export const PAGE_MS = 15_000;

/** What the clock dial says about when someone listens (ListeningPersonality), as the phone words it. */
export const PERSONALITIES: Record<string, { title: string; detail: string }> = {
  NIGHT_OWL: { title: 'Night Owl', detail: 'So much of your listening happened after 10 pm.' },
  EARLY_BIRD: { title: 'Early Riser', detail: 'Your days started with music before 10 am.' },
  GOLDEN_HOUR: { title: 'Golden Hour', detail: 'Evenings were when the music came out.' },
  DAYTIME: { title: 'Daytime Drifter', detail: 'You kept the music going through the day.' },
  ALL_DAY: { title: 'Around the Clock', detail: 'Morning, noon or night, something was playing.' },
};

export interface CardRow {
  title: string;
  subtitle: string | null | undefined;
  art: string | null | undefined;
}

/**
 * What a card says, worked out once. The card on screen and the picture its
 * share button draws both read this, so the version people send never says
 * something different from the one they saw.
 */
export interface CardContent {
  /** The line that sets the card up. */
  setup: string;
  /** The reveal. */
  hero: string | null;
  /** Under the hero, in its accent colour (the "minutes" of 1,284 minutes). */
  unit: string | null;
  /** The lines after it. */
  detail: string[];
  /** A chart, for the ranked cards. */
  rows: CardRow[];
  circular: boolean;
  /** Side-by-side lists of names, for the summary. */
  columns: { title: string; names: string[] }[];
  /** The picture the card is about, when it is about one. */
  art: string | null | undefined;
}

const minutes = (ms: number) => Math.floor(ms / 60_000);

/** The hour of the day with the most listening, or null (ReplaySummary.peakHour). */
export function peakHour(hours: number[]): number | null {
  let best: number | null = null;
  hours.forEach((ms, hour) => {
    if (ms > 0 && (best === null || ms > hours[best]!)) best = hour;
  });
  return best;
}

export function cardContent(story: ReplayYearStory, slide: YearSlide, year: number): CardContent {
  const { charts } = story;
  const base: CardContent = { setup: '', hero: null, unit: null, detail: [], rows: [], circular: false, columns: [], art: null };
  switch (slide) {
    case 'OPENER':
      return { ...base, setup: `Your ${year} in BitChord`, hero: String(year), detail: ['Let’s look back at what you played.'] };
    case 'MINUTES': {
      const total = minutes(charts.totalMs);
      const days = total / 1_440;
      const amount = days >= 1 ? `${days.toFixed(1)} days` : `${grouped(charts.totalMs / 3_600_000)} hours`;
      return {
        ...base,
        setup: 'This year you listened for',
        hero: grouped(total),
        unit: total === 1 ? 'minute' : 'minutes',
        detail: [`That’s ${amount} of music.`],
      };
    }
    case 'BUSIEST_DAY':
      return {
        ...base,
        setup: 'Your biggest day was',
        hero: charts.busiestDay ? formatDay(charts.busiestDay) : null,
        detail: [
          `${grouped(minutes(charts.busiestDayMs))} minutes of music in one day`,
          ...(story.busiestDaySong ? [`Mostly ${story.busiestDaySong.title}`] : []),
        ],
        art: story.busiestDaySong?.art ?? null,
      };
    case 'CLOCK': {
      const peak = peakHour(charts.hours);
      const personality = story.personality ? PERSONALITIES[story.personality] : null;
      return {
        ...base,
        setup: 'You came alive around',
        hero: peak === null ? null : formatHour(peak),
        detail: personality ? [personality.title, personality.detail] : [],
      };
    }
    case 'GENRES':
      // The web looks up no genres, so YearPlan never tells this card here; kept for completeness.
      return { ...base, setup: 'Your sound this year' };
    case 'ARTIST_REVEAL': {
      const top = charts.artists[0];
      return {
        ...base,
        setup: 'Your top artist this year was…',
        hero: top?.title ?? null,
        detail: top ? [`${grouped(minutes(top.ms))} minutes together  ·  ${count(top.plays, 'play')}`] : [],
        rows: charts.artists.slice(1, 5).reverse().map((a) => ({ title: a.title, subtitle: null, art: a.art })),
        circular: true,
        art: top?.art ?? null,
      };
    }
    case 'TOP_ARTISTS':
      return {
        ...base,
        setup: 'Your top artists',
        rows: charts.artists.slice(0, 5).map((a) => ({ title: a.title, subtitle: null, art: a.art })),
        circular: true,
      };
    case 'SONG_REVEAL': {
      const top = charts.songs[0];
      return {
        ...base,
        setup: 'And your song of the year…',
        hero: top?.title ?? null,
        detail: top ? [top.artist, `Played ${grouped(top.plays)} ${top.plays === 1 ? 'time' : 'times'}`] : [],
        art: top?.art ?? null,
      };
    }
    case 'TOP_SONGS':
      return {
        ...base,
        setup: 'The rest of your top songs',
        rows: charts.songs.slice(1, 5).map((s) => ({ title: s.title, subtitle: s.artist || null, art: s.art })),
      };
    case 'ALBUMS':
      return {
        ...base,
        setup: 'Albums you lived in',
        rows: charts.albums.slice(0, 5).map((a) => ({ title: a.title, subtitle: a.subtitle, art: a.art })),
      };
    case 'MONTHS':
      return {
        ...base,
        setup: 'Your year, month by month',
        rows: story.monthTops.map((top) => ({ title: top.song.title, subtitle: monthName(top.month), art: top.song.art })),
      };
    case 'DISCOVERY':
      return {
        ...base,
        setup: 'This year you explored',
        detail: [count(charts.songs.length, 'song'), count(charts.artists.length, 'artist'), count(charts.albums.length, 'album')],
      };
    case 'SUMMARY':
      return {
        ...base,
        setup: `Your ${year}`,
        hero: grouped(minutes(charts.totalMs)),
        unit: 'Minutes listened',
        columns: [
          { title: 'Top artists', names: charts.artists.slice(0, 5).map((a) => a.title) },
          { title: 'Top songs', names: charts.songs.slice(0, 5).map((s) => s.title) },
        ],
        art: charts.artists[0]?.art ?? charts.songs[0]?.art ?? null,
      };
  }
}

/** `2026-03` as "MAR", as the ribbon labels its months. */
export function monthName(month: string): string {
  const [y, m] = month.split('-').map(Number);
  return new Date(y ?? 2000, (m ?? 1) - 1, 1).toLocaleDateString(undefined, { month: 'short' }).toUpperCase();
}
