import { fetchBrowse, fetchPlaylist, fetchRadio } from './api';
import { MOOD_CATEGORY_BROWSE_ID, moodById, moodFor, type Mood, type MoodId } from './moods';
import type { ArtistPage, MusicItem, Shelf, Track } from './types';

/*
 * Home's "Top Picks for You", built from what the account already tells
 * YouTube Music (shared/src/jvmSharedMain/.../data/TopPicks.kt):
 *  - the listener's top artists, ranked from their listening history (the
 *    same read Recently Played makes);
 *  - three of those artists' pages, for the latest release, their photos and
 *    the artists their fans also play;
 *  - My Supermix, Discover Mix and New Release Mix, off the home feed;
 *  - a mood, which costs nothing until it's played.
 * A card with nothing behind it is left out. Queues are only fetched on a click.
 *
 * Where the web has less than the phone: artist faces are YouTube's photos
 * cut to the card, not Apple's; an artist's station is its top song's radio
 * and their shuffle is their top songs; local Replay charts don't take part.
 */

export type TopPickKind =
  | 'latest-release'
  | 'personal-station'
  | 'mood'
  | 'artist-and-similar'
  | 'featured-artist'
  | 'new-release-mix'
  | 'discovery-station';

export interface TopPick {
  kind: TopPickKind;
  /** The release, artist or mix name; empty where the card names itself. */
  title: string;
  subtitle: string;
  /** The artist a card is billed under ("Featuring …", "… & Similar Artists", "New from …"). */
  artistName: string | null;
  /** The artist's channel (UC…), for the artist cards. */
  artistId: string | null;
  /** The cover or the artist's photo first; an artist-and-similar card adds up to two related artists. */
  images: string[];
  /** The album a latest-release card opens. */
  albumId: string | null;
  /** The playlist a mix card plays. */
  playlistId: string | null;
  /** The track whose radio a station plays, when there's no playlist behind it. */
  seedVideoId: string | null;
  mood: MoodId | null;
  /** For a discovery station built off an artist: tracks already heard, left out of its queue. */
  exclude: string[];
}

const pick = (kind: TopPickKind, fields: Partial<TopPick> = {}): TopPick => ({
  kind,
  title: '',
  subtitle: '',
  artistName: null,
  artistId: null,
  images: [],
  albumId: null,
  playlistId: null,
  seedVideoId: null,
  mood: null,
  exclude: [],
  ...fields,
});

/** How many artists get a page fetched, and so a card; drawn from the top ten of the chart. */
const ARTISTS = 3;
const ARTIST_POOL = 10;

function shuffled<T>(list: T[], random = Math.random): T[] {
  const out = [...list];
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    [out[i], out[j]] = [out[j]!, out[i]!];
  }
  return out;
}

/** [count] of [ranked] at random, the higher an artist sits the likelier, so the row moves without leaving the listener's taste. */
export function weightedSample<T>(ranked: T[], count: number, random = Math.random): T[] {
  const pool = ranked.map((value, index) => ({ value, weight: ranked.length - index }));
  const picked: T[] = [];
  while (picked.length < count && pool.length) {
    let roll = random() * pool.reduce((sum, p) => sum + p.weight, 0);
    const index = Math.max(0, pool.findIndex((p) => (roll -= p.weight) <= 0));
    picked.push(pool.splice(index, 1)[0]!.value);
  }
  return picked;
}

/**
 * The listener's artists, best first: each history row counts once, a little
 * more the more recent it is. Only artists with a channel take part, since a
 * card needs their page.
 */
export function rankArtists(history: Track[]): { id: string; name: string }[] {
  const scores = new Map<string, { name: string; score: number }>();
  history.forEach((track, index) => {
    const lead = track.artists.find((a) => a.id);
    if (!lead?.id?.startsWith('UC') || !lead.name.trim() || /^various artists$/i.test(lead.name)) return;
    const recency = 1 - index / Math.max(history.length, 1);
    const entry = scores.get(lead.id) ?? { name: lead.name, score: 0 };
    entry.score += 1 + recency * 0.5;
    scores.set(lead.id, entry);
  });
  return [...scores.entries()].sort((a, b) => b[1].score - a[1].score).map(([id, { name }]) => ({ id, name }));
}

type Mix = 'supermix' | 'discover' | 'new-release';
const playlistOf = (item: MusicItem) => item.id.replace(/^VL/, '');

/**
 * The account's mixes off the home feed: all RDTMAK… playlists, told apart by
 * their English titles. In another language only the Supermix is placed, as
 * the lead mix, which is where YouTube puts it.
 */
export function findMixes(shelves: Shelf[]): Map<Mix, MusicItem> {
  const mixes = shelves.flatMap((s) => s.items).filter((item) => playlistOf(item).startsWith('RDTMAK'));
  const found = new Map<Mix, MusicItem>();
  for (const item of mixes) {
    const title = item.title.toLowerCase();
    const kind: Mix | null = title.includes('supermix') ? 'supermix' : title.includes('discover') ? 'discover' : title.includes('new release') ? 'new-release' : null;
    if (kind && !found.has(kind)) found.set(kind, item);
  }
  if (!found.size && mixes[0]) found.set('supermix', mixes[0]);
  return found;
}

const YEAR = /\b(19|20)\d{2}\b/g;
const SINGLE = /\b(Single|EP)\b/;

/**
 * The newest release among [artists]' pages. Each release shelf is newest
 * first, so only its lead card is a candidate. Within a year a single wins: a
 * bare-year "album" on a big artist's page is as often one of YouTube's own
 * compilations as a record. On a tie, the higher-ranked artist.
 */
export function latestRelease(artists: ArtistPage[]): TopPick | null {
  let best: { artist: ArtistPage; item: MusicItem; year: number; single: boolean } | null = null;
  for (const artist of artists) {
    for (const shelf of artist.shelves) {
      const item = shelf.items[0];
      if (!item?.id.startsWith('MPRE')) continue;
      const year = Number([...item.subtitle.matchAll(YEAR)].pop()?.[0] ?? 0);
      const single = SINGLE.test(item.subtitle);
      if (!best || year > best.year || (year === best.year && single && !best.single)) best = { artist, item, year, single };
    }
  }
  if (!best) return null;
  const { artist, item } = best;
  return pick('latest-release', {
    title: item.title,
    subtitle: item.subtitle,
    artistName: artist.name,
    images: item.thumbnail ? [item.thumbnail] : [],
    albumId: item.id,
  });
}

/** The page's "Fans might also like": artists other than this one, by name, at most two faces. */
function relatedFaces(artist: ArtistPage): string[] {
  const own = artist.name.trim().toLowerCase();
  const shelf = artist.shelves.find((s) => s.items.length && s.items.every((i) => i.kind === 'artist'));
  const seen = new Set<string>();
  return (shelf?.items ?? [])
    .filter((i) => i.id !== artist.id && i.thumbnail && !seen.has(i.title.toLowerCase()) && seen.add(i.title.toLowerCase()) && i.title.toLowerCase() !== own)
    .slice(0, 2)
    .map((i) => i.thumbnail!);
}

function andSimilar(artist: ArtistPage): TopPick | null {
  const seed = artist.topSongs[0];
  if (!artist.heroImage || !seed) return null;
  return pick('artist-and-similar', {
    title: artist.name,
    artistName: artist.name,
    artistId: artist.id,
    images: [artist.heroImage, ...relatedFaces(artist)],
    seedVideoId: seed.videoId,
  });
}

function featured(artist: ArtistPage): TopPick | null {
  if (!artist.heroImage || !artist.topSongs.length) return null;
  return pick('featured-artist', {
    title: artist.name,
    subtitle: artist.audience ?? '',
    artistName: artist.name,
    artistId: artist.id,
    images: [artist.heroImage],
  });
}

export interface TopPicksInput {
  shelves: Shelf[];
  history: Track[];
  /** Fetches an artist's page; the caller shares it with the artist route's cache. */
  artist: (id: string) => Promise<ArtistPage>;
  now?: Date;
}

/** The row, the new release first as on Apple Music, the rest in a different order every build. */
export async function buildTopPicks({ shelves, history, artist, now = new Date() }: TopPicksInput): Promise<TopPick[]> {
  const ranked = weightedSample(rankArtists(history).slice(0, ARTIST_POOL), ARTISTS);
  const pages = await Promise.all(ranked.map((a) => artist(a.id).catch(() => null)));
  // One person can be two channels (their own and a "Topic" one); two would be the same face twice.
  const names = new Set<string>();
  const artists = pages.filter((p): p is ArtistPage => !!p && !names.has(p.name.toLowerCase()) && !!names.add(p.name.toLowerCase()));
  const mixes = findMixes(shelves);
  const supermix = mixes.get('supermix');
  const discover = mixes.get('discover');
  const newRelease = mixes.get('new-release');
  const seed = history[0];
  const rest = [
    supermix || seed ? pick('personal-station', supermix ? { playlistId: supermix.id } : { seedVideoId: seed!.videoId }) : null,
    pick('mood', { mood: moodFor(now).id }),
    artists[0] ? andSimilar(artists[0]) : null,
    artists[1] ? featured(artists[1]) : null,
    newRelease
      ? pick('new-release-mix', { title: newRelease.title, subtitle: newRelease.subtitle, images: newRelease.thumbnail ? [newRelease.thumbnail] : [], playlistId: newRelease.id })
      : null,
    artists[2] ? andSimilar(artists[2]) : null,
    discover
      ? pick('discovery-station', { playlistId: discover.id })
      : artists[0]?.topSongs[0]
        ? pick('discovery-station', { artistName: artists[0].name, seedVideoId: artists[0].topSongs[0].videoId, exclude: history.slice(0, 200).map((t) => t.videoId) })
        : null,
  ].filter((p): p is TopPick => !!p);
  const lead = latestRelease(artists);
  return [...(lead ? [lead] : []), ...shuffled(rest)];
}

// ---- Queues ------------------------------------------------------------------------

const SEED_POOL = 40;
const SEED_RADIOS = 3;
const STATION_FROM_MIX = 30;
const STATION_SIZE = 60;
const MIN_FILTERED_QUEUE = 12;

const radioTracks = async (videoId: string) => (await fetchRadio(videoId)).candidates.map((c) => c.track);

/**
 * A Made for You station that sounds different every time (TopPicks.kt
 * freshStation): a random half of the mix, blended with the radios of a few
 * tracks from the listener's history, all shuffled. A discovery station
 * leaves out everything already heard, and its seed artist.
 */
async function freshStation(target: TopPick, history: Track[]): Promise<Track[]> {
  const mixAsync = target.playlistId ? fetchPlaylist(target.playlistId).then((r) => r.page.tracks) : radioTracks(target.seedVideoId!);
  const seeds = shuffled(history.slice(0, SEED_POOL)).slice(0, SEED_RADIOS);
  const radios = await Promise.all(seeds.map((s) => radioTracks(s.videoId).catch(() => [])));
  const mix = await mixAsync.catch((error: unknown) => {
    if (!seeds.length) throw error;
    return [] as Track[];
  });
  const discovery = target.kind === 'discovery-station';
  const heard = new Set(discovery ? [...history.map((t) => t.videoId), ...target.exclude] : []);
  const seedArtist = discovery ? target.artistName?.toLowerCase() : null;
  const keep = (t: Track) => !heard.has(t.videoId) && (!seedArtist || !t.artists.some((a) => a.name.toLowerCase().includes(seedArtist)));
  const core = shuffled(mix.filter(keep)).slice(0, STATION_FROM_MIX);
  const taken = new Set(core.map((t) => t.videoId));
  const extra = shuffled(radios.flat().filter((t) => keep(t) && !taken.has(t.videoId) && taken.add(t.videoId))).slice(0, STATION_SIZE - core.length);
  const station = shuffled([...core, ...extra]);
  if (station.length >= MIN_FILTERED_QUEUE) return station;
  if (mix.length) return shuffled(mix);
  throw new Error('This station came back empty.');
}

/** Words that steer a mood toward the listener's own scene (TopPicks.kt regionHints). */
export function regionHints(locale = typeof navigator === 'undefined' ? '' : navigator.language): string[] {
  const country = locale.split('-')[1]?.toUpperCase() ?? '';
  if (country === 'IN') return ['bollywood', 'indian', 'hindi', 'punjabi', 'desi'];
  if (country === 'PK') return ['pakistani', 'urdu', 'punjabi'];
  if (country === 'KR') return ['k-pop', 'korean'];
  if (country === 'JP') return ['j-pop', 'japanese'];
  if (country === 'BR') return ['brazil', 'sertanejo', 'funk'];
  if (['MX', 'ES', 'AR', 'CO', 'CL', 'PE'].includes(country)) return ['latin', 'latino', 'reggaeton'];
  return [];
}

/** The mood category's shelves that fit the mood best: its own words count double, the listener's scene once. */
export function bestMoodShelves(shelves: Shelf[], mood: Mood, hints: string[]): Shelf[] {
  const playlists = (s: Shelf) => s.items.filter((i) => i.kind === 'playlist' || i.id.startsWith('VL'));
  const candidates = shelves.filter((s) => playlists(s).length);
  const score = (s: Shelf) => {
    const title = s.title.toLowerCase();
    return 2 * mood.shelfWords.filter((w) => title.includes(w)).length + hints.filter((h) => h && title.includes(h)).length;
  };
  const best = Math.max(...candidates.map(score));
  return candidates.filter((s) => score(s) === best);
}

/**
 * A mood's queue (TopPicks.kt moodQueue): two or three of the mood's own
 * playlists played as they are and shuffled together. Not a playlist radio,
 * which wanders off to whatever sounds like its seed.
 */
export async function moodQueue(mood: Mood, hints = regionHints()): Promise<Track[]> {
  const page = await fetchBrowse({ browseId: MOOD_CATEGORY_BROWSE_ID, params: mood.params });
  const shelves = bestMoodShelves(page.shelves, mood, hints);
  const shelf = shelves[Math.floor(Math.random() * shelves.length)];
  if (!shelf) throw new Error(`No playlists for ${mood.title}.`);
  const chosen = shuffled(shelf.items.filter((i) => i.kind === 'playlist' || i.id.startsWith('VL')).slice(0, 6)).slice(0, 3);
  const lists = await Promise.all(chosen.map((i) => fetchPlaylist(i.id).then((r) => r.page.tracks, () => [] as Track[])));
  const seen = new Set<string>();
  const songs = shuffled(lists.flat().filter((t) => !seen.has(t.videoId) && seen.add(t.videoId))).slice(0, 60);
  if (!songs.length) throw new Error(`${mood.title} playlists were empty.`);
  return songs;
}

/** The queue a station card starts. Never asked of a latest release, which opens its album. */
export async function topPickQueue(target: TopPick, history: Track[], artist: (id: string) => Promise<ArtistPage>): Promise<Track[]> {
  switch (target.kind) {
    case 'mood':
      return moodQueue(moodById(target.mood ?? 'feel-good')!);
    case 'personal-station':
    case 'discovery-station':
      return freshStation(target, history);
    case 'new-release-mix':
      return (await fetchPlaylist(target.playlistId!)).page.tracks;
    case 'artist-and-similar':
      return radioTracks(target.seedVideoId!);
    case 'featured-artist':
      return shuffled((await artist(target.artistId!)).topSongs);
    case 'latest-release':
      return [];
  }
}
