import { useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { LogoMark } from '@/components/icons/LogoMark';
import { shelfKinds, shelfList, shelfSection } from '@/components/shelf';
import { useAlbumMotion } from '@/data/apple/motionArt';
import { MOODS, moodById, type Mood } from '@/data/music/moods';
import { moodQueue, topPickQueue, type TopPick } from '@/data/music/topPicks';
import type { ArtistPage, Track } from '@/data/music/types';
import { artworkAtPixels } from '@/lib/artwork';
import { useCanvasLoop } from './canvasLoop';
import { MotionArtwork } from './MotionArtwork';
import { playList } from './play';
import { ShelfScroller, ShelfTitle } from './ShelfView';
import { drawDiscoveryStation, drawMood, drawPersonalStation, STATION_PERIOD_MS, stillPhase } from './topPicksArt';

/*
 * Home's "Top Picks for You" and "Find Your Mood"
 * (sharedUi/.../screens/TopPicksShelf.kt): Apple Music's lead row of tall
 * cards, each a station, a mood, an artist or a new release picked for this
 * listener, and its row of mood tiles.
 */

/** A card while its queue is being fetched; the row stays usable. */
const pending = 'aria-busy:opacity-70 aria-busy:cursor-progress';
// 3:4, radius 12, the artwork's 1px rim, white captions.
const cardFrame = `relative isolate block aspect-[3/4] w-full overflow-hidden rounded-card bg-surface-variant text-start text-white artwork-stroke transition-[scale,opacity] duration-150 ease-out active:scale-[.98] ${pending}`;
/**
 * One or two picks on a wide window (a guest gets one mood) would leave a
 * 280px card and a row of black: from 1000px they share the row instead, as
 * landscape cards a set height, their art laid out for the wider shape.
 */
const wideFrame = 'lg:aspect-auto lg:h-[clamp(240px,19vw,300px)]';
const FEW = 2;

/**
 * A drawn picture on a loop of [periodMs] (loopPhase): the canvas repaints
 * itself and nothing else, and holds still off screen. [still] draws the one
 * frame at [stillAt] of the loop and never asks for another.
 */
function LoopArt({ periodMs, draw, still = false, stillAt = 0 }: { periodMs: number; draw: (ctx: CanvasRenderingContext2D, w: number, h: number, phase: number) => void; still?: boolean; stillAt?: number }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  useCanvasLoop(canvas, (ctx, w, h, seconds) => draw(ctx, w, h, ((seconds * 1000) / periodMs) % 1), {
    running: !still,
    stillSeconds: (stillAt * periodMs) / 1000,
  });
  return <canvas ref={canvas} className="absolute inset-0 -z-1 size-full" aria-hidden="true" />;
}

function MoodArt({ mood, square = false, still = false }: { mood: Mood; square?: boolean; still?: boolean }) {
  return <LoopArt periodMs={mood.periodMs} still={still} stillAt={stillPhase(mood.id)} draw={(ctx, w, h, phase) => drawMood(ctx, w, h, phase, mood, square)} />;
}

/** The BitChord mark, top-right on the cards the app draws itself. */
function Brand({ size }: { size: 'card' | 'tile' }) {
  return size === 'card' ? (
    <LogoMark size={(15 * 730) / 484} className="absolute top-3 right-3 text-white" />
  ) : (
    <LogoMark size={(11 * 730) / 484} className="absolute top-2 right-2 text-white" />
  );
}

/**
 * Artwork cut to the card's shape by Google's image server (croppedAt): an
 * artist photo asked for at a portrait or a square with `-p` comes back
 * cropped around its subject, which a centre crop of a banner is not.
 */
const GOOGLE_SIZE = /=w\d+-h\d+[^/?]*$/;
function croppedAt(url: string, width: number, height = width): string {
  return GOOGLE_SIZE.test(url) ? url.replace(GOOGLE_SIZE, `=w${width}-h${height}-p-l90-rj`) : artworkAtPixels(url, Math.max(width, height));
}

/** A photo or cover filling its box, with no frame of its own. */
function Img({ src, className = '', style }: { src: string; className?: string; style?: CSSProperties }) {
  return <img src={src} alt="" className={`object-cover ${className}`} style={style} referrerPolicy="no-referrer" loading="lazy" decoding="async" draggable={false} />;
}

/**
 * A release, as Apple Music draws one: the cover whole as a square at the top,
 * its lower edge running on out of focus behind the caption, toned down toward
 * the caption so white reads on any cover. A motion cover, when Apple has one,
 * plays muted over the square and dissolves into the blur the same way.
 */
function ReleaseArt({ url, motion, wide }: { url: string | undefined; motion: { album: string; artist: string } | null; wide: boolean }) {
  const clip = useAlbumMotion(motion?.album, motion?.artist);
  // Landscape, the cover stands whole at the end, the full height, and dissolves leftward into its blur.
  const square = `absolute inset-x-0 top-0 aspect-square w-full [mask-image:linear-gradient(black_78%,transparent)] ${wide ? 'lg:inset-x-auto lg:right-0 lg:h-full lg:w-auto lg:[mask-image:linear-gradient(to_left,black_70%,transparent)]' : ''}`;
  return (
    <div className="absolute inset-0 -z-1 bg-black">
      {url && <Img src={croppedAt(url, 96)} className={`absolute inset-x-0 bottom-0 h-[42%] w-full scale-125 blur-[18px] ${wide ? 'lg:h-full' : ''}`} />}
      <div className="absolute inset-0 bg-[linear-gradient(transparent_0%,rgb(0_0_0/0.08)_60%,rgb(0_0_0/0.38)_100%)]" />
      {url && <Img src={croppedAt(url, 600)} className={square} />}
      {clip && (
        <div className={square}>
          <MotionArtwork motion={clip} className="size-full object-cover opacity-0 transition-opacity duration-500 data-ready:opacity-100" />
        </div>
      )}
    </div>
  );
}

/** Vivid backdrops for the artist collage, one per artist and the same one every day. */
const COLLAGE: [string, string, string][] = [
  ['#8F3F00', '#FF6A0F', '#FFC48A'],
  ['#2D8A20', '#4FD22C', '#CBFFA3'],
  ['#173E9E', '#2F7BFF', '#C0D8FF'],
  ['#4A1E99', '#8A4BFF', '#DCC8FF'],
  ['#0B6E6E', '#14B8A6', '#B5FFF3'],
  ['#8E1426', '#F0364F', '#FFC4CC'],
];
/** Kotlin's String.hashCode, so an artist gets the colour the phone gives them. */
function javaHash(text: string): number {
  let h = 0;
  for (let i = 0; i < text.length; i++) h = (Math.imul(31, h) + text.charCodeAt(i)) | 0;
  return h;
}
const collageOf = (name: string) => COLLAGE[Math.abs(javaHash(name)) % COLLAGE.length]!;

/**
 * The artist in a large circle with up to two related artists in smaller ones
 * beneath, on a colour that brightens toward the caption. Still: an artist's
 * clip cut down to a circle among other faces reads as a small moving tile.
 */
function CollageArt({ images, colors, wide }: { images: string[]; colors: [string, string, string]; wide: boolean }) {
  // Centre x, centre y (shares of the card) and diameter (of its width).
  const slots =
    images.length === 1
      ? [[0.5, 0.34, 0.66]]
      : images.length === 2
        ? [[0.4, 0.28, 0.6], [0.72, 0.52, 0.36]]
        : [[0.37, 0.257, 0.594], [0.76, 0.48, 0.333], [0.457, 0.608, 0.29]];
  return (
    <div className="absolute inset-0 -z-1" style={{ background: `linear-gradient(${colors[0]}, ${colors[1]})` }}>
      {/* The circles are placed on a 3:4 card; a landscape one keeps that 3:4 at its end for them. */}
      <div className={`absolute inset-0 ${wide ? 'lg:left-auto lg:aspect-[3/4]' : ''}`}>
        {images.slice(0, 3).map((url, i) => {
          const [x, y, d] = slots[i]!;
          return (
            <Img
              key={url}
              src={croppedAt(url, d! > 0.5 ? 400 : 240)}
              className="absolute aspect-square rounded-full bg-[rgb(0_0_0/0.18)]"
              // The card is 3:4, so a share of its height is 4/3 that share of its width.
              style={{ width: `${d! * 100}%`, left: `${(x! - d! / 2) * 100}%`, top: `${(y! - (d! * 3) / 8) * 100}%` }}
            />
          );
        })}
      </div>
    </div>
  );
}

interface CardText {
  eyebrow: string;
  title: string;
  subtitle: string;
}

function cardText(target: TopPick, listenerName: string | null): CardText {
  const artist = target.artistName ?? '';
  switch (target.kind) {
    case 'latest-release':
      return { eyebrow: `New from ${artist}`, title: target.title, subtitle: target.subtitle };
    case 'personal-station': {
      const first = listenerName?.split(' ')[0]?.trim();
      return { eyebrow: 'Made for You', title: first ? `${first}’s Station` : 'Your Station', subtitle: '' };
    }
    case 'mood': {
      const mood = moodById(target.mood ?? 'feel-good')!;
      return { eyebrow: 'Mood for You', title: mood.title, subtitle: mood.line };
    }
    case 'artist-and-similar':
      return { eyebrow: `Featuring ${artist}`, title: `${artist} & Similar Artists`, subtitle: '' };
    case 'featured-artist':
      return { eyebrow: `Featuring ${artist}`, title: target.title, subtitle: target.subtitle };
    case 'new-release-mix':
      return { eyebrow: 'Made for You', title: target.title, subtitle: target.subtitle };
    case 'discovery-station':
      return { eyebrow: 'Made for You', title: 'Discovery Station', subtitle: '' };
  }
}

/** The line above the title on a photo card: white, let through. */
const PHOTO_EYEBROW = 'rgb(255 255 255 / 0.62)';

/**
 * The caption: a 13px coloured line, the title 17 semibold, and an optional
 * 13px line, 16px in from the edges; on a landscape card a size up, kept to the
 * start, clear of the art at the end.
 */
function Caption({ text, eyebrow, wide }: { text: CardText; eyebrow: string; wide: boolean }) {
  return (
    <span className={`absolute inset-x-0 bottom-0 flex flex-col px-4 pb-4 ${wide ? 'lg:max-w-[min(62%,560px)] lg:px-6 lg:pb-6' : ''}`}>
      <span className={`truncate type-[400_13px/16px] ${wide ? 'lg:type-[500_15px/19px]' : ''}`} style={{ color: eyebrow }}>
        {text.eyebrow}
      </span>
      <span className={`line-clamp-2 type-[600_17px/21px] ${wide ? 'lg:type-[700_28px/32px] lg:tracking-[-0.3px]' : ''}`}>{text.title}</span>
      {text.subtitle && <span className={`line-clamp-2 type-[400_13px/16px] text-white/86 ${wide ? 'lg:mt-1 lg:type-[400_15px/19px]' : ''}`}>{text.subtitle}</span>}
    </span>
  );
}

/**
 * Under every caption, a shade the art's own colour can't wash out: by the
 * caption's first line it is half black, which keeps white, the pale eyebrows
 * and the 86% subtitle at 4.5:1 or better on the brightest mood (Feel Good's
 * #F2A900) and station colours.
 */
const captionShade = 'pointer-events-none absolute inset-x-0 bottom-0 h-[58%] bg-[linear-gradient(transparent,rgb(0_0_0/0.1)_14%,rgb(0_0_0/0.3)_30%,rgb(0_0_0/0.5)_48%,rgb(0_0_0/0.6))]';

function TopPickCard({
  target,
  listenerName,
  lead,
  wide,
  onPlay,
}: {
  target: TopPick;
  listenerName: string | null;
  lead: boolean;
  /** One of only one or two picks: landscape from 1000px. */
  wide: boolean;
  onPlay: (target: TopPick, title: string) => Promise<void>;
}) {
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const text = cardText(target, listenerName);
  let art: ReactNode;
  let eyebrow = PHOTO_EYEBROW;
  let branded = true;
  switch (target.kind) {
    case 'personal-station':
      art = <LoopArt periodMs={STATION_PERIOD_MS} draw={drawPersonalStation} />;
      eyebrow = '#FFD2DA';
      break;
    case 'discovery-station':
      art = <LoopArt periodMs={STATION_PERIOD_MS} draw={drawDiscoveryStation} />;
      eyebrow = '#CDBDFF';
      break;
    case 'mood': {
      const mood = moodById(target.mood ?? 'feel-good')!;
      art = <MoodArt mood={mood} />;
      eyebrow = mood.eyebrow ?? `color-mix(in srgb, ${mood.stroke} 65%, white)`;
      break;
    }
    case 'artist-and-similar': {
      const colors = collageOf(target.artistName ?? '');
      art = <CollageArt images={target.images} colors={colors} wide={wide} />;
      eyebrow = colors[2];
      break;
    }
    case 'latest-release':
    case 'new-release-mix':
      // Only the row's lead, a real release, looks for a motion cover: one clip playing at a time.
      art = <ReleaseArt url={target.images[0]} wide={wide} motion={lead && target.kind === 'latest-release' ? { album: target.title, artist: target.artistName ?? '' } : null} />;
      branded = false;
      break;
    case 'featured-artist':
      art = (
        <div className="absolute inset-0 -z-1">
          {/* A landscape card asks for a square, which crops to either shape around the subject's face. */}
          {target.images[0] && (
            <Img src={wide ? croppedAt(target.images[0], 1000) : croppedAt(target.images[0], 420, 560)} className="size-full object-[50%_30%]" />
          )}
          {/* The caption sits on the photo's own darkened foot, not on a band of black. */}
          <div className="absolute inset-0 bg-[linear-gradient(transparent_45%,rgb(0_0_0/0.45)_72%,rgb(0_0_0/0.82)_100%)]" />
        </div>
      );
      branded = false;
      break;
  }
  const open = async () => {
    if (target.kind === 'latest-release') {
      if (target.albumId) void navigate(`/album/${encodeURIComponent(target.albumId)}`);
      return;
    }
    setBusy(true);
    try {
      await onPlay(target, text.title);
    } finally {
      setBusy(false);
    }
  };
  return (
    <button
      type="button"
      className={`${cardFrame} ${wide ? wideFrame : ''}`}
      aria-busy={busy || undefined}
      aria-label={`${text.eyebrow}: ${text.title}`}
      onClick={() => void open()}
    >
      {art}
      <span className={captionShade} aria-hidden="true" />
      {branded && <Brand size="card" />}
      <Caption text={text} eyebrow={eyebrow} wide={wide} />
    </button>
  );
}

/** Starts a queue in the player, as the phone does on a tap; a station that can't be built leaves the queue as it was. */
async function startQueue(load: () => Promise<Track[]>, title: string) {
  try {
    const tracks = await load();
    if (tracks.length) playList(tracks, 0, { source: { title, type: 'radio' } });
  } catch (error) {
    console.warn(`[top picks] ${title} could not start`, error);
  }
}

/**
 * The row: cards min(60% of the row, 280px) wide, 14px apart, snapping. One
 * or two picks share the row as landscape cards from 1000px (wideFrame).
 */
export function TopPicksShelf({
  picks,
  listenerName,
  history,
  artist,
}: {
  picks: TopPick[];
  listenerName: string | null;
  history: Track[];
  artist: (id: string) => Promise<ArtistPage>;
}) {
  const play = (target: TopPick, title: string) => startQueue(() => topPickQueue(target, history, artist), title);
  const few = picks.length <= FEW;
  const cards = picks.map((target, i) => (
    <li key={`${i}:${target.kind}:${target.title}`}>
      <TopPickCard target={target} listenerName={listenerName} lead={i === 0} wide={few} onPlay={play} />
    </li>
  ));
  return (
    <section className={shelfSection} aria-label="Top Picks for You">
      <ShelfTitle title="Top Picks for You" />
      {few ? (
        // The shelf's own row below 1000px; from there one or two landscape cards, each at most 640px so a
        // lone pick reads as a feature, not a banner across a 1920px window.
        <ul className={`${shelfList} ${shelfKinds['top-picks']} lg:grid-flow-row lg:auto-cols-auto ${picks.length > 1 ? 'lg:grid-cols-[repeat(2,minmax(0,640px))]' : 'lg:grid-cols-[minmax(0,640px)]'}`}>
          {cards}
        </ul>
      ) : (
        <ShelfScroller kind="top-picks">{cards}</ShelfScroller>
      )}
    </section>
  );
}

/**
 * A mood's square tile and its name under it, sized like a shelf card. Still:
 * a shelf of tiles each breathing on its own loop is a page that never stops
 * redrawing; the motion is Top Picks' own, one card at a time.
 */
function MoodTile({ mood }: { mood: Mood }) {
  const [busy, setBusy] = useState(false);
  const play = async () => {
    setBusy(true);
    await startQueue(() => moodQueue(mood), mood.title);
    setBusy(false);
  };
  return (
    <button type="button" className={`group/mood flex w-full min-w-0 flex-col text-start ${pending}`} aria-busy={busy || undefined} onClick={() => void play()}>
      <span className="relative isolate block aspect-square w-full overflow-hidden rounded-card artwork-stroke transition-[scale] duration-150 ease-out group-active/mood:scale-[.98]">
        <MoodArt mood={mood} square still />
        <Brand size="tile" />
      </span>
      <span className="mt-2.5 truncate type-title-2-emphasized">{mood.title}</span>
    </button>
  );
}

/** Apple Music's Find Your Mood: its seven moods as tiles, and Show all for the activities too. */
export function FindYourMoodShelf() {
  return (
    <section className={shelfSection} aria-label="Find Your Mood">
      <ShelfTitle title="Find Your Mood" to="/moods" />
      <ShelfScroller kind="square">
        {MOODS.filter((m) => m.core).map((mood) => (
          <li key={mood.id}>
            <MoodTile mood={mood} />
          </li>
        ))}
      </ShelfScroller>
    </section>
  );
}

/**
 * Every mood and activity (MoodGridPage), laid out as a Show all grid is:
 * Apple's seven moods first, cards at least 140px, 12px across and 20px down.
 */
export function MoodGrid() {
  return (
    <ul className="mx-(--body-gutter) mt-3.5 mb-10 grid grid-cols-[repeat(auto-fill,minmax(140px,1fr))] gap-x-3 gap-y-5">
      {MOODS.map((mood) => (
        <li key={mood.id}>
          <MoodTile mood={mood} />
        </li>
      ))}
    </ul>
  );
}
