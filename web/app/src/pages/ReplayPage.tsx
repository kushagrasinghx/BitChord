import type { ReplayCharts, ReplayRow, ReplaySong } from '@bitchord/shared-kotlin';
import '@/replay/replay.css';
import { ChevronRight, Clock, Play, Share, type LucideIcon } from 'lucide-react';
import { lazy, Suspense, useState, type ReactNode } from 'react';
import { PageHeader } from '@/components/PageHeader';
import { PrefetchLink } from '@/components/PrefetchLink';
import { Artwork } from '@/components/music/Artwork';
import { albumHref, artistHref } from '@/components/music/links';
import { playTrack } from '@/components/music/play';
import { ShelfScroller } from '@/components/music/ShelfView';
import { LoadError } from '@/components/music/States';
import type { Track } from '@/data/music/types';
import { useYtAuth } from '@/extension/useYtAuth';
import { partySettings } from '@/party/listenTogether';
import { count, formatDay, formatHour, formatListening, formatMinutes, grouped } from '@/replay/format';
import { PaletteMesh, useArtworkMesh } from '@/replay/PaletteMesh';
import { PERIODS, periodLabel, yearShown, type ReplayPeriod } from '@/replay/periods';
import { useReplay, useReplayYear } from '@/replay/queries';
import { ReplayCard } from '@/replay/ReplayCard';
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

/** Charts and notes keep to a readable measure in a wide window. */
const column = 'mx-(--body-gutter) max-w-[720px]';
/** headlineMedium at W800: each section's title (ReplayScreen.kt SectionTitle). */
const sectionTitle = 'pt-2 pb-2.5 type-[800_22px/26px] tracking-[-0.25px]';

/**
 * The Replay (ReplayScreen.kt): the stretch of listening picked at the top,
 * its headline figures, the top five songs, artists and albums, and the shape
 * of the listening, all counted on this browser (replay/recorder.ts) and on
 * any device synced to it. In December it also opens the year's story.
 *
 * The page is washed in the top song's colours, a still mesh held back under
 * the theme's ink, as the phone's is: a page about one stretch of listening,
 * in the colours of the record that defined it.
 */
export default function ReplayPage() {
  // Lifetime first, as the phone opens it.
  const [period, setPeriod] = useState<ReplayPeriod>('all');
  const replay = useReplay(period);
  const year = yearShown(new Date());
  const story = useReplayYear(year).data ?? null;
  const [storyOpen, setStoryOpen] = useState(false);
  const charts = replay.data?.charts ?? null;
  const label = periodLabel(period, new Date());
  const holder = useHolder();
  const palette = useArtworkMesh(charts?.songs[0]?.art);

  let body: ReactNode;
  if (replay.isError) {
    body = <LoadError error={replay.error} onRetry={() => void replay.refetch()} />;
  } else if (!replay.data) {
    body = <PlainSkeleton />;
  } else if (!charts || charts.songs.length === 0) {
    body = (
      <div className="mx-auto flex max-w-[420px] flex-col items-center px-9 py-12 text-center">
        <Clock size={44} strokeWidth={1.6} className="text-primary/40" />
        <p className="mt-4 type-title-large">Not enough listening yet</p>
        <p className="mt-2 type-callout text-primary/60">{EMPTY_DETAIL[period]}</p>
      </div>
    );
  } else {
    body = (
      <>
        <HeroCards charts={charts} label={label} holder={holder} memberSince={replay.data.memberSince} />
        {story && year !== null && <ActionRow icon={Play} label="Play your Replay" onClick={() => setStoryOpen(true)} />}
        {/* From 1200px the three charts stand side by side, each a column, rather than one long list. */}
        <div className="min-[1200px]:mx-(--body-gutter) min-[1200px]:grid min-[1200px]:grid-cols-3 min-[1200px]:gap-x-8">
          <Chart id="replay-songs" title="Top songs" rows={charts.songs.slice(0, CHART_LENGTH).map(songRow)} />
          <Chart id="replay-artists" title="Top artists" circular rows={charts.artists.slice(0, CHART_LENGTH).map(artistRow)} />
          <Chart id="replay-albums" title="Top albums" rows={charts.albums.slice(0, CHART_LENGTH).map(albumRow)} />
        </div>
        <Habits charts={charts} />
        <ShareRow charts={charts} label={label} />
      </>
    );
  }

  return (
    <div className="relative isolate pb-10">
      {/* The mesh holds still behind the page as it scrolls, a window's height of it, running up under the top bar. */}
      <div aria-hidden="true" className="sticky top-0 -z-1 -mt-(--top-bar-clearance,0px) mb-[calc(var(--top-bar-clearance,0px)-100dvh)] h-dvh overflow-hidden">
        <PaletteMesh palette={palette} />
        <div className="replay-scrim absolute inset-0" />
      </div>
      <PageHeader title="Replay" />
      <p className="mx-(--body-gutter) type-title-2-emphasized text-primary/60">{label}</p>
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

/** Whose card it is: the signed-in account, else the Listen Together name, else the phone's stand-in. */
function useHolder(): string {
  const yt = useYtAuth();
  const account = yt.state === 'ready' && yt.auth.signedIn ? yt.auth.active?.name : null;
  return account || partySettings().nickname.trim() || 'BitChord listener';
}

/**
 * The four stretches a Replay covers: weeks and months rather than a date
 * range, because that is the granularity the listening is kept at. The
 * phone's chips (ReplayScreen.kt PeriodPicker): four equal shares of a 12px
 * track in the text colour at 10%, the picked one filled with it at 92% and
 * its label inverted, eased over 160ms.
 */
function PeriodPicker({ value, onChange }: { value: ReplayPeriod; onChange: (period: ReplayPeriod) => void }) {
  return (
    <div role="radiogroup" aria-label="Period" className="mx-(--body-gutter) mt-3.5 mb-[18px] flex max-w-[440px] gap-0.5 rounded-[12px] bg-primary/10 p-[3px]">
      {PERIODS.map((p) => (
        <button
          key={p.id}
          type="button"
          role="radio"
          aria-checked={p.id === value}
          onClick={() => onChange(p.id)}
          className="min-w-0 flex-1 truncate rounded-[10px] px-1.5 py-2 type-[700_12px/14px] tracking-[0.25px] text-primary/75 transition-colors duration-160 aria-checked:bg-primary/92 aria-checked:text-page motion-reduce:transition-none"
        >
          {p.tab}
        </button>
      ))}
    </div>
  );
}

/**
 * The page's two acts, drawn alike (ReplayScreen.kt ReplayActionRow): a 14px
 * slab of the text colour at 12%, an 18px glyph, the label and a chevron.
 */
function ActionRow({ icon: Icon, label, disabled, onClick }: { icon: LucideIcon; label: string; disabled?: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className={`${column} my-[18px] flex w-[calc(100%-2*var(--body-gutter))] items-center gap-3 rounded-group bg-primary/12 px-4 py-3.5 text-start hover:bg-primary/16 disabled:opacity-50`}
    >
      <Icon size={18} strokeWidth={2.2} className="flex-none" />
      <span className="flex-1 type-title-2-emphasized">{label}</span>
      <ChevronRight size={16} strokeWidth={2.4} className="flex-none text-primary/50" />
    </button>
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

/**
 * The wallet (ReplayScreen.kt ReplayCardRow): 300px cards 12px apart, snapping
 * as they scroll, a shelf's row with its hover arrows rather than a bare
 * scrollbar under the cards.
 */
function HeroCards({ charts, label, holder, memberSince }: { charts: ReplayCharts; label: string; holder: string; memberSince: string | null }) {
  return (
    <ShelfScroller kind="replay">
      {heroCards(charts, label).map((card) => (
        <li key={card.label}>
          <ReplayCard
            label={card.label}
            value={card.value}
            detail={card.detail}
            art={card.art}
            holder={holder}
            memberSince={memberSince}
            onClick={card.target ? () => document.getElementById(card.target!)?.scrollIntoView({ behavior: 'smooth', block: 'start' }) : undefined}
          />
        </li>
      ))}
    </ShelfScroller>
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

/**
 * A rank badge (ReplayCard.kt RankBadge): number one in the accent red; the
 * rest quiet, because a chart where every position shouts has no first place.
 */
function Rank({ rank }: { rank: number }) {
  return (
    <span className={`w-7 flex-none ${rank === 1 ? 'type-[800_20px/24px] text-accent-red' : 'type-[800_16px/19px] text-primary/45'}`}>
      {rank}
    </span>
  );
}

/** One chart (ReplayScreen.kt ReplayChartRow): rank, 48px artwork, the title over its subtitle and time, the plays. */
function Chart({ id, title, rows, circular = false }: { id: string; title: string; rows: ChartRow[]; circular?: boolean }) {
  if (rows.length === 0) return null;
  return (
    <section id={id} className={`${column} mt-2 mb-5 scroll-mt-6 min-[1200px]:mx-0 min-[1200px]:max-w-none`}>
      <h2 className={sectionTitle}>{title}</h2>
      <ol>
        {rows.map((row, i) => {
          const inner = (
            <>
              <Rank rank={i + 1} />
              <span className="w-12 flex-none">
                <Artwork src={row.art ?? null} displayWidth={48} radius={circular ? '50%' : 6} />
              </span>
              <span className="ms-1 min-w-0 flex-1">
                <span className="block truncate type-body-emphasized">{row.title}</span>
                <span className="block truncate type-callout text-primary/55">
                  {[row.subtitle, formatListening(row.ms)].filter(Boolean).join(' · ')}
                </span>
              </span>
              {row.plays > 0 && (
                <span className="flex-none type-label-medium text-primary/45" title={count(row.plays, 'play')}>
                  {grouped(row.plays)}
                </span>
              )}
            </>
          );
          const rowClass = 'flex w-full items-center gap-2 rounded-row px-1 py-[7px] text-start hover:bg-primary/8';
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

/** The shape of the listening (ReplayScreen.kt Habits): three counts, then when and the biggest day. */
function Habits({ charts }: { charts: ReplayCharts }) {
  const peak = charts.hours.reduce((best, ms, hour) => (ms > 0 && ms > (charts.hours[best] ?? 0) ? hour : best), -1);
  const tiles = [
    ['Songs', charts.songs.length],
    ['Artists', charts.artists.length],
    ['Albums', charts.albums.length],
  ] as const;
  return (
    <section className={`${column} mt-2`}>
      <h2 className={sectionTitle}>Your listening pattern</h2>
      <div className="mt-1 grid max-w-[560px] grid-cols-3 gap-2.5">
        {tiles.map(([label, value]) => (
          <div key={label} className="flex flex-col items-center rounded-group bg-primary/9 py-3.5">
            <span className="type-[800_22px/26px] tracking-[-0.25px]">{grouped(value)}</span>
            <span className="type-subhead-emphasized tracking-[1px] text-primary/55">{label}</span>
          </div>
        ))}
      </div>
      {peak >= 0 && <p className="mt-2.5 type-callout text-primary/60">You listen most around {formatHour(peak)}.</p>}
      {charts.busiestDay && (
        <p className="mt-1.5 type-callout text-primary/60">
          Your biggest day was {formatDay(charts.busiestDay)} with {formatListening(charts.busiestDayMs)} of listening.
        </p>
      )}
    </section>
  );
}

/** Draws the Replay as one picture and hands it to the system's share sheet, or saves it. */
function ShareRow({ charts, label }: { charts: ReplayCharts; label: string }) {
  const [busy, setBusy] = useState(false);
  return (
    <ActionRow
      icon={Share}
      label="Share my Replay"
      disabled={busy}
      onClick={() => {
        setBusy(true);
        void import('@/replay/poster')
          .then(({ renderReplayPoster, sharePicture }) =>
            renderReplayPoster(charts, label).then((picture) => sharePicture(picture, 'replay.png', 'Share your Replay')),
          )
          .finally(() => setBusy(false));
      }}
    />
  );
}
