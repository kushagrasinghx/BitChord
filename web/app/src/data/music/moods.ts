/**
 * The moods a Mood for You card or a Find Your Mood tile can be: Apple Music's
 * own set (shared/.../data/model/TopPicks.kt TopPickMood). The first seven are
 * its Find Your Mood stations; the rest are the activities of its Moods and
 * Activities room. Each plays from one of YouTube Music's mood categories.
 *
 * [params] is the category's id on YouTube Music's Moods & moments page, the
 * same in every interface language, so a mood is found by it alone. Many of
 * Apple's moods share a category; [shelfWords] steer each to the shelves in it
 * that fit (Heartbreak to "Breakups & heartbreak", Wellbeing to "Yoga"…).
 */

// YouTube Music's mood categories, by id.
const CHILL = 'ggMPOg1uX1JOQWZFeDByc2Jm';
const COMMUTE = 'ggMPOg1uX044Z2o5WERLckpU';
const ENERGIZE = 'ggMPOg1uX2lRZUZiMnNrQnJW';
const FEEL_GOOD = 'ggMPOg1uXzZQbDB5eThLRTQ3';
const FOCUS = 'ggMPOg1uX0NvNGNhWThMYWRh';
const GAMING = 'ggMPOg1uX3NmUVV4Vzl3WGQ0';
const PARTY = 'ggMPOg1uX0pmQ0s2V0JRclZs';
const ROMANCE = 'ggMPOg1uX0FzQ2FhZWtUY211';
const SAD = 'ggMPOg1uX0JLQ0gySWZKZVY1';
const SLEEP = 'ggMPOg1uX1MxaFQ3Z0JMZkN4';
const WORKOUT = 'ggMPOg1uX09LWkhnTjRGRUJh';

/** Where a mood's id is browsed (TopPickMood.CATEGORY_BROWSE_ID). */
export const MOOD_CATEGORY_BROWSE_ID = 'FEmusic_moods_and_genres_category';

export type MoodId =
  | 'energy' | 'relax' | 'feeling-blue' | 'feel-good' | 'focus' | 'heartbreak' | 'love'
  | 'after-hours' | 'alone-time' | 'chill' | 'commuting' | 'eating-cooking' | 'evening' | 'fitness'
  | 'gaming' | 'home' | 'morning' | 'motivation' | 'outdoors' | 'party' | 'sleep' | 'social'
  | 'sports' | 'summertime' | 'holiday' | 'weekend' | 'wellbeing' | 'work';

export interface Mood {
  id: MoodId;
  title: string;
  /** The line under the title on a Mood for You card (composeResources strings mood_*_line). */
  line: string;
  params: string;
  shelfWords: string[];
  /** TopPicksShelf.kt MoodStyle: a flat colour, the emblem's pale outline, its loop length, and the eyebrow colour. */
  background: string;
  stroke: string;
  periodMs: number;
  eyebrow: string | null;
  /** One of Apple's seven Find Your Mood stations, rather than an activity. */
  core: boolean;
}

type Row = [MoodId, string, string, string, string[], string, string, number, string?];

// id, title, line, category, shelf words, background, stroke, period, eyebrow
const ROWS: Row[] = [
  ['energy', 'Energy', 'High-voltage tracks to keep you moving.', ENERGIZE, [], '#1EBD38', '#E2F6A2', 3000, '#E8FF9C'],
  ['relax', 'Relax', 'Easy, soothing sounds to slow things down.', CHILL, ['unwind', 'peaceful', 'relaxing', 'laidback'], '#31AFF3', '#B4E9FF', 5600, '#D6F3FF'],
  ['feeling-blue', 'Feeling Blue', 'Sad songs for when you need them.', SAD, ['sad', 'melanchol', 'lonely'], '#253AB2', '#6CC4FF', 3700, '#A9DBFF'],
  ['feel-good', 'Feel Good', 'Bright, upbeat songs to lift the day.', FEEL_GOOD, [], '#F2A900', '#FFE8A0', 4000, '#FFF27A'],
  ['focus', 'Focus', 'Steady music to keep you in the zone.', FOCUS, [], '#F28400', '#FFD6A4', 3600, '#FFE2B8'],
  ['heartbreak', 'Heartbreak', 'Breakup anthems, one tear at a time.', SAD, ['heartbreak', 'breakup', 'break-up', 'broken', 'heartache'], '#EA3520', '#FFA690', 2700, '#FFC9BC'],
  ['love', 'Love', 'Romantic songs for the moment.', ROMANCE, [], '#F34CA0', '#FFB8DC', 1700, '#FFD0EA'],
  ['after-hours', 'After Hours', 'Late-night grooves for when the city goes quiet.', CHILL, ['deep grooves', 'electronic', 'jazz', 'r&b'], '#3A1F78', '#C9B6FF', 4500],
  ['alone-time', 'Alone Time', 'Mellow songs for time to yourself.', CHILL, ['mellow', 'acoustic', 'laidback'], '#2C6E8C', '#A9E2F5', 4000],
  ['chill', 'Chill', 'Laid-back sounds to take it easy.', CHILL, ['chill', 'chilled'], '#138A86', '#A6F0E6', 5000],
  ['commuting', 'Commuting', 'The right soundtrack for the road.', COMMUTE, [], '#17855F', '#A8EFD0', 2400],
  ['eating-cooking', 'Eating & Cooking', 'Warm, easy music for the kitchen and the table.', CHILL, ['coffee', 'soul', 'jazz', 'r&b'], '#D9621A', '#FFD3A8', 3200],
  ['evening', 'Evening', 'Gentle songs to wind the day down.', CHILL, ['unwind', 'acoustic', 'laidback', 'jazz'], '#2A3A8C', '#B8C6FF', 6000],
  ['fitness', 'Fitness', 'Hard-hitting tracks to push you further.', WORKOUT, ['gym', 'cardio', 'essentials'], '#8DB014', '#EFFFA8', 2000],
  ['gaming', 'Gaming', 'Soundtracks to level up to.', GAMING, [], '#239A2A', '#B8FFB0', 6000],
  ['home', 'Home', 'Easy listening for a slow day in.', CHILL, ['coffee shop', 'chill pop', 'laidback'], '#0E8A6C', '#A8F2DB', 4000],
  ['morning', 'Morning', 'Bright songs to start the day right.', FEEL_GOOD, ['happy', 'confident', 'pop'], '#F08C1A', '#FFE2A6', 5000],
  ['motivation', 'Motivation', 'Anthems to get you up and going.', ENERGIZE, ['motivation', 'power', 'beast'], '#C01E4A', '#FFB3C6', 1800],
  ['outdoors', 'Outdoors', 'Music for fresh air and open roads.', WORKOUT, ['outdoor', 'running'], '#4C8A1E', '#D4F5A8', 6000],
  ['party', 'Party', 'Crowd-pleasers to keep the night going.', PARTY, [], '#C2287A', '#FFC0E0', 3000],
  ['sleep', 'Sleep', 'Soft, calming sounds to drift off to.', SLEEP, [], '#26266E', '#C2C6FF', 4500],
  ['social', 'Social', 'Songs everyone in the room will know.', PARTY, ['party today', 'chill party', 'throwback'], '#E0507A', '#FFD0DE', 3000],
  ['sports', 'Sports', 'Game-day energy, start to finish.', ENERGIZE, ['beast', 'power', 'hip-hop'], '#1C6CD0', '#B8D8FF', 3000],
  ['summertime', 'Summertime Sounds', 'Sunny songs for long, warm days.', FEEL_GOOD, ['dance', 'kicking back', 'feel-good pop'], '#FF8C00', '#FFE6A8', 8000],
  ['holiday', 'Holiday', 'Festive favourites for the season.', FEEL_GOOD, ['throwback', 'happy'], '#C62828', '#FFC8C8', 8000],
  ['weekend', 'Weekend', 'Feel-good music for your days off.', PARTY, ['party today', 'dance'], '#7A3CC2', '#E2CCFF', 2400],
  ['wellbeing', 'Wellbeing', 'Calm music for mindfulness and yoga.', CHILL, ['mindfulness', 'meditation', 'yoga'], '#36A98A', '#C6F7E6', 5000],
  ['work', 'Work', 'Steady instrumentals to get things done.', FOCUS, ['flow', 'instrumental', 'reading'], '#3E63C9', '#C4D2FF', 2000],
];

export const MOODS: Mood[] = ROWS.map(([id, title, line, params, shelfWords, background, stroke, periodMs, eyebrow], i) => ({
  id,
  title,
  line,
  params,
  shelfWords,
  background,
  stroke,
  periodMs,
  eyebrow: eyebrow ?? null,
  core: i < 7,
}));

export const moodById = (id: string): Mood | undefined => MOODS.find((m) => m.id === id);

/**
 * A mood at random, leaning on the hour (TopPicks.kt moodFor): most of the
 * time one that suits it (Focus mid-morning, Love late in the evening),
 * sometimes any of the seven.
 */
export function moodFor(now: Date, random: () => number = Math.random): Mood {
  const hour = now.getHours();
  const suited: MoodId[] =
    hour >= 5 && hour <= 8 ? ['energy', 'feel-good', 'focus']
    : hour >= 9 && hour <= 11 ? ['focus', 'feel-good', 'energy']
    : hour >= 12 && hour <= 16 ? ['feel-good', 'focus', 'energy', 'relax']
    : hour >= 17 && hour <= 19 ? ['energy', 'feel-good', 'relax']
    : hour >= 20 && hour <= 22 ? ['love', 'relax', 'heartbreak', 'feel-good']
    : ['relax', 'feeling-blue', 'heartbreak', 'love'];
  const pool = random() < 0.7 ? suited.map((id) => moodById(id)!) : MOODS.filter((m) => m.core);
  return pool[Math.floor(random() * pool.length)]!;
}
