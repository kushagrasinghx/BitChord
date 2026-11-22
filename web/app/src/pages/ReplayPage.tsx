import type { ReplayCharts, ReplayRow, ReplaySong } from '@bitchord/shared-kotlin';
import { Clock, Play, Share } from 'lucide-react';
import { lazy, Suspense, useState, type ReactNode } from 'react';
import { PageHeader } from '@/components/PageHeader';
import { PrefetchLink } from '@/components/PrefetchLink';
import { Artwork } from '@/components/music/Artwork';
import { albumHref, artistHref } from '@/components/music/links';
import { playTrack } from '@/components/music/play';
import { LoadError, stateStyles } from '@/components/music/States';
import type { Track } from '@/data/music/types';
import { useArtworkColor } from '@/lib/artworkColor';
import { usePageTint } from '@/lib/usePageTint';
import { count, formatDay, formatHour, formatListening, formatMinutes, grouped } from '@/replay/format';
import { PERIODS, periodLabel, yearShown, type ReplayPeriod } from '@/replay/periods';
import { useReplay, useReplayYear } from '@/replay/queries';
import { PlainSkeleton } from '@/skeletons/PageSkeletons';

// The story carries its own soundtrack player and canvas cards; loaded the first time it opens.
const ReplayYearStories = lazy(() => import('@/replay/year/ReplayYearStories').then((m) => ({ default: m.ReplayYearStories })));

/**
 * How far down each chart the page goes. Five, as on the phone: a top five is
 * a result, a top ten is a list.
 */
const CHART_LENGTH = 5;

const EMPTY_DETAIL: Record<ReplayPeriod, string> = {
  week: 'Play a few more songs this week to unlock your Replay.',
  month: 'Play a few more songs this month to unlock your Replay.',
  year: 'Listen a little more and BitChord will turn your history into a Replay.',
  all: 'Listen a little more and BitChord will turn your history into a Replay.',
};

const action = 'inline-flex h-9 items-center gap-2 rounded-pill px-[18px] type-title-3-emphasized transition-[filter] hover:brightness-110';
const primaryAction = `${action} bg-key-bg text-white`;
const secondaryAction = `${action} bg-quaternary text-primary disabled:opacity-50`;

/**
 * The Replay (ReplayScreen.kt): the stretch of listening picked at the top,
 * its headline figures, the top five songs, artists and albums, and the shape
 * of the listening, all counted on this browser (replay/recorder.ts) and on
 * any device synced to it. In December it also opens the year's story.
 *
 * The page takes the top song's colour, as the phone's takes its mesh: a page
 * about one stretch of listening, in the colours of the record that defined it.
 */
export default function ReplayPage() {
  // Lifetime first, as the phone opens it.
  const [period, setPeriod] = useState<ReplayPeriod>('all');
  const replay = useReplay(period);
  const year = yearShown(new Date());
  const story = useReplayYear(year).data ?? null;
  const [storyOpen, setStoryOpen] = useState(false);
  const charts = replay.data?.charts ?? null;
  usePageTint(useArtworkColor(charts?.songs[0]?.art));

  let body: ReactNode;
  if (replay.isError) {
    body = <LoadError error={replay.error} onRetry={() => void replay.refetch()} />;
  } else if (!replay.data) {
    body = <PlainSkeleton />;
  } else if (!charts || charts.songs.length === 0) {
    body = (
      <div className={stateStyles.state}>
        <Clock size={44} strokeWidth={1.6} className="mb-2 text-tertiary" />
        <p className={stateStyles.title}>Not enough listening yet</p>
        <p className={stateStyles.detail}>{EMPTY_DETAIL[period]}</p>
      </div>
    );
  } else {
    body = (
      <>
        <HeroCards charts={charts} label={periodLabel(period, new Date())} />
        <div className="mx-(--body-gutter) mt-6 flex flex-wrap gap-3">
          {story && year !== null && (
            <button className={primaryAction} onClick={() => setStoryOpen(true)}>
              <Play size={16} fill="currentColor" strokeWidth={0} />
              Open your Replay {year}
            </button>
          )}
          <ShareButton charts={charts} label={periodLabel(period, new Date())} />
        </div>
        <Chart id="replay-songs" title="Top songs" rows={charts.songs.slice(0, CHART_LENGTH).map(songRow)} />
        <Chart id="replay-artists" title="Top artists" circular rows={charts.artists.slice(0, CHART_LENGTH).map(artistRow)} />
        <Chart id="replay-albums" title="Top albums" rows={charts.albums.slice(0, CHART_LENGTH).map(albumRow)} />
        <Habits charts={charts} />
      </>
    );
  }

  return (
    <div className="pb-10">
      <PageHeader title="Replay" />
      <PeriodPicker value={period} onChange={setPeriod} />
      {body}
      {storyOpen && story && year !== null && (
        <Suspense fallback={null}>
          <ReplayYearStories story={story} year={year} onClose={() => setStoryOpen(false)} />
        </Suspense>
      )}
    </div>
  );
}

/**
 * The four stretches a Replay covers: weeks and months rather than a date
 * range, because that is the granularity the listening is kept at.
 */
function PeriodPicker({ value, onChange }: { value: ReplayPeriod; onChange: (period: ReplayPeriod) => void }) {
  return (
    <div role="tablist" aria-label="Period" className="mx-(--body-gutter) mt-4 inline-flex rounded-[9px] bg-segmented p-0.5">
      {PERIODS.map((p) => (
        <button
          key={p.id}
          role="tab"
          aria-selected={p.id === value}
          className="min-w-[88px] rounded-[7px] px-3 py-1 type-body-emphasized text-primary transition-[background-color] duration-150 aria-selected:bg-segmented-selected aria-selected:shadow-[0_1px_3px_rgb(0_0_0/0.2)]"
          onClick={() => onChange(p.id)}
        >
          {p.tab}
        </button>
      ))}
    </div>
  );
}

// ---- The headline cards --------------------------------------------------------------

interface HeroCard {
  label: string;
  value: string;
  detail: string;
  art: string | null | undefined;
  /** The chart it opens onto, as the phone's cards open their story page. */
  target: string | null;
}

/**
 * The headline facts, minutes first because it needs no context to mean
 * something. A category with nothing in it is left out rather than shown empty.
 */
function heroCards(charts: ReplayCharts, label: string): HeroCard[] {
  const [song] = charts.songs;
  const [artist] = charts.artists;
  const [album] = charts.albums;
  return [
    {
      label: 'Minutes listened',
      value: formatMinutes(charts.totalMs),
      detail: `${count(charts.totalPlays, 'play')} · ${label}`,
      art: song?.art ?? null,
      target: null,
    },
    ...(song
      ? [{ label: 'Top song', value: song.title, detail: `${song.artist} · ${count(song.plays, 'play')}`, art: song.art, target: 'replay-songs' }]
      : []),
    ...(artist
      ? [{ label: 'Top artist', value: artist.title, detail: `${formatListening(artist.ms)} · ${count(artist.plays, 'play')}`, art: artist.art, target: 'replay-artists' }]
      : []),
    ...(album
      ? [{ label: 'Top album', value: album.title, detail: [album.subtitle, formatListening(album.ms)].filter(Boolean).join(' · '), art: album.art, target: 'replay-albums' }]
      : []),
  ];
}

function HeroCards({ charts, label }: { charts: ReplayCharts; label: string }) {
  return (
    <div className="mx-(--body-gutter) mt-6 grid grid-cols-[repeat(auto-fill,minmax(220px,1fr))] gap-4">
      {heroCards(charts, label).map((card) => (
        <button
          key={card.label}
          className="group relative isolate flex aspect-[4/3] flex-col justify-end overflow-hidden rounded-artwork-lg p-4 text-start text-white"
          disabled={!card.target}
          onClick={() => card.target && document.getElementById(card.target)?.scrollIntoView({ behavior: 'smooth', block: 'start' })}
        >
          <div className="absolute inset-0 -z-1 transition-transform duration-500 group-enabled:group-hover:scale-105">
            <Artwork src={card.art ?? null} displayWidth={280} className="size-full" />
          </div>
          <div className="absolute inset-0 -z-1 bg-linear-to-t from-black/85 via-black/45 to-black/10" />
          <span className="type-caption-2 tracking-wide text-white/70 uppercase">{card.label}</span>
          <span className="mt-0.5 line-clamp-2 type-title-1-emphasized">{card.value}</span>
          <span className="mt-1 truncate type-callout text-white/75">{card.detail}</span>
        </button>
      ))}
    </div>
  );
}

// ---- Charts --------------------------------------------------------------------------

interface ChartRow {
  key: string;
  title: string;
  subtitle: string | null | undefined;
  art: string | null | undefined;
  ms: number;
  plays: number;
  /** Where the row goes, or what it plays. */
  href: string | null;
  onPlay?: () => void;
}

/** A chart song as a queue track: the listening files keep the credit as one line, and the lead's channel. */
const replayTrack = (song: ReplaySong): Track => ({
  videoId: song.videoId,
  title: song.title,
  artists: [{ name: song.artist, id: song.artistId ?? null }],
  album: song.album ? { name: song.album, id: song.albumId ?? null } : null,
  durationSec: null,
  explicit: false,
  thumbnail: song.art ?? null,
  index: null,
  plays: null,
});

const songRow = (song: ReplaySong, i: number): ChartRow => ({
  key: `${i}-${song.videoId}`,
  title: song.title,
  subtitle: song.artist || null,
  art: song.art,
  ms: song.ms,
  plays: song.plays,
  href: null,
  onPlay: () => playTrack(replayTrack(song), null, { title: 'Replay', type: 'other' }),
});

const artistRow = (artist: ReplayRow, i: number): ChartRow => ({
  key: `${i}-${artist.title}`,
  title: artist.title,
  subtitle: null,
  art: artist.art,
  ms: artist.ms,
  plays: artist.plays,
  href: artistHref(artist.browseId ?? null),
});

const albumRow = (album: ReplayRow, i: number): ChartRow => ({
  key: `${i}-${album.title}|${album.subtitle ?? ''}`,
  title: album.title,
  subtitle: album.subtitle,
  art: album.art,
  ms: album.ms,
  plays: album.plays,
  href: albumHref(album.browseId ?? null),
});

function Chart({ id, title, rows, circular = false }: { id: string; title: string; rows: ChartRow[]; circular?: boolean }) {
  if (rows.length === 0) return null;
  return (
    <section id={id} className="mx-(--body-gutter) mt-10 scroll-mt-6">
      <h2 className="mb-2 type-title-2-emphasized">{title}</h2>
      <ol>
        {rows.map((row, i) => {
          const inner = (
            <>
              <span className="w-6 flex-none text-center type-title-3-emphasized text-secondary">{i + 1}</span>
              <span className="w-12 flex-none">
                <Artwork src={row.art ?? null} displayWidth={48} radius={circular ? '50%' : 6} />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate type-body-emphasized">{row.title}</span>
                {row.subtitle && <span className="block truncate type-callout text-secondary">{row.subtitle}</span>}
              </span>
              <span className="flex-none text-end type-callout text-secondary">
                {formatListening(row.ms)}
                <span className="block type-caption-2">{count(row.plays, 'play')}</span>
              </span>
            </>
          );
          const rowClass = 'flex w-full items-center gap-3 rounded-row px-2 py-2 text-start hover:bg-tracklist-hover';
          return (
            <li key={row.key}>
              {row.href ? (
                <PrefetchLink to={row.href} className={rowClass}>
                  {inner}
                </PrefetchLink>
              ) : row.onPlay ? (
                <button className={rowClass} onClick={row.onPlay} aria-label={`Play ${row.title}`}>
                  {inner}
                </button>
              ) : (
                <div className={rowClass}>{inner}</div>
              )}
            </li>
          );
        })}
      </ol>
    </section>
  );
}

// ---- The tail ------------------------------------------------------------------------

function Habits({ charts }: { charts: ReplayCharts }) {
  const peak = charts.hours.reduce((best, ms, hour) => (ms > 0 && ms > (charts.hours[best] ?? 0) ? hour : best), -1);
  const tiles = [
    ['Songs', charts.songs.length],
    ['Artists', charts.artists.length],
    ['Albums', charts.albums.length],
  ] as const;
  return (
    <section className="mx-(--body-gutter) mt-10">
      <h2 className="mb-3 type-title-2-emphasized">Your listening pattern</h2>
      <div className="grid max-w-[560px] grid-cols-3 gap-2.5">
        {tiles.map(([label, value]) => (
          <div key={label} className="flex flex-col items-center rounded-[14px] bg-quinary py-3.5">
            <span className="type-title-1-emphasized">{grouped(value)}</span>
            <span className="type-caption-2 tracking-wide text-secondary uppercase">{label}</span>
          </div>
        ))}
      </div>
      {peak >= 0 && <p className="mt-3 type-body text-secondary">You listen most around {formatHour(peak)}.</p>}
      {charts.busiestDay && (
        <p className="mt-1.5 type-body text-secondary">
          Your biggest day was {formatDay(charts.busiestDay)} with {formatListening(charts.busiestDayMs)} of listening.
        </p>
      )}
    </section>
  );
}

/** Draws the Replay as one picture and hands it to the system's share sheet, or saves it. */
function ShareButton({ charts, label }: { charts: ReplayCharts; label: string }) {
  const [busy, setBusy] = useState(false);
  return (
    <button
      className={secondaryAction}
      disabled={busy}
      onClick={() => {
        setBusy(true);
        void import('@/replay/poster')
          .then(({ renderReplayPoster, sharePicture }) =>
            renderReplayPoster(charts, label).then((picture) => sharePicture(picture, 'replay.png', 'Share your Replay')),
          )
          .finally(() => setBusy(false));
      }}
    >
      <Share size={16} strokeWidth={2.2} />
      Share my Replay
    </button>
  );
}
