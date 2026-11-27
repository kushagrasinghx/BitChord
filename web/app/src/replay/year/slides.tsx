import type { ReplayYearStory } from '@bitchord/shared-kotlin';
import { createContext, useContext, useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { Artwork } from '@/components/music/Artwork';
import { grouped } from '../format';
import { cardContent, peakHour, revealAtMs, type CardContent, type CardRow, type YearSlide } from './content';

/*
 * One card of the year story (ReplayYearSlides.kt), drawn over the backdrop the
 * story has already laid down. Every card has the same three beats: the line
 * that sets it up, the reveal at revealAtMs (when its clip's chorus lands), and
 * the detail after it. Sizes are in cqh/cqw of the 9:16 card, the phone's sp
 * over its 405 × 720 card, so the card scales as one picture with the window.
 */

/** Whether the card is held (a finger on it, the window hidden): what JS-driven motion reads. */
export const HeldContext = createContext(false);

const at = (ms: number): CSSProperties => ({ animationDelay: `${ms}ms` });

/** A line that sets a card up, arriving word by word from the top. */
function Setup({ text, start = 120 }: { text: string; start?: number }) {
  return (
    <h2 className="text-[4.2cqh] leading-[1.13] font-extrabold tracking-[-0.02em] text-white" aria-label={text}>
      {text.split(' ').map((word, i) => (
        <span key={i} className="ry-word me-[0.25em]" style={at(start + i * 70)} aria-hidden="true">
          {word}
        </span>
      ))}
    </h2>
  );
}

function Detail({ children, start, className = '' }: { children: ReactNode; start: number; className?: string }) {
  return (
    <p className={`ry-enter text-[2.65cqh] leading-[1.25] font-semibold text-white ${className}`} style={at(start)}>
      {children}
    </p>
  );
}

const easeOutCubic = (t: number) => 1 - (1 - t) ** 3;

/** A figure counting up from nothing on the card's clock: it stops while the card is held. */
function CountUp({ target, start, duration, className, style }: { target: number; start: number; duration: number; className: string; style?: CSSProperties }) {
  const held = useContext(HeldContext);
  const heldRef = useRef(held);
  heldRef.current = held;
  const reduced = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
  const [value, setValue] = useState(reduced ? target : 0);
  useEffect(() => {
    if (reduced) return;
    let elapsed = 0;
    let last = performance.now();
    let frame = requestAnimationFrame(function tick(now) {
      if (!heldRef.current) elapsed += now - last;
      last = now;
      const p = Math.min(1, Math.max(0, (elapsed - start) / duration));
      setValue(Math.round(target * easeOutCubic(p)));
      if (p < 1) frame = requestAnimationFrame(tick);
    });
    return () => cancelAnimationFrame(frame);
  }, [target, start, duration, reduced]);
  return (
    <span className={className} style={style}>
      {grouped(value)}
    </span>
  );
}

const huge = 'font-black leading-[0.95] tracking-[-0.04em] text-white';

function Cover({ art, size, circular = false, className = '', style }: { art: string | null | undefined; size: string; circular?: boolean; className?: string; style?: CSSProperties }) {
  return (
    <div className={`flex-none shadow-[0_12px_40px_rgb(0_0_0/0.45)] ${circular ? 'rounded-full' : 'rounded-[1.2cqh]'} ${className}`} style={{ width: size, ...style }}>
      <Artwork src={art ?? null} displayWidth={320} radius={circular ? '50%' : '1.2cqh'} />
    </div>
  );
}

/** A chart of up to five, cascading in a row at a time, the first one larger. */
function RankList({ rows, circular, accent, first = 1 }: { rows: CardRow[]; circular: boolean; accent: string; first?: number }) {
  return (
    <ol className="mt-[4cqh] flex flex-col gap-[2cqh]">
      {rows.map((row, i) => (
        <li key={i} className="ry-enter flex items-center gap-[3.5cqw]" style={at(500 + i * 260)}>
          <span className="w-[6cqw] flex-none text-[3.6cqh] font-black" style={{ color: accent }}>
            {first + i}
          </span>
          <Cover art={row.art} size={i === 0 ? '15cqh' : '10cqh'} circular={circular} />
          <span className="min-w-0">
            <span className={`block truncate font-extrabold text-white ${i === 0 ? 'text-[3.4cqh]' : 'text-[2.6cqh]'}`}>{row.title}</span>
            {row.subtitle && <span className="block truncate text-[2cqh] font-semibold text-white/70">{row.subtitle}</span>}
          </span>
        </li>
      ))}
    </ol>
  );
}

/** The year's covers as a tilted wall of three rows drifting in turn left and right, behind the year. */
function CoverWall({ story }: { story: ReplayYearStory }) {
  const art = [...new Set([...story.charts.songs.map((s) => s.art), ...story.charts.albums.map((a) => a.art)].filter((a): a is string => !!a))].slice(0, 21);
  if (!art.length) return null;
  const rows = [0, 1, 2].map((r) => {
    const own = art.filter((_, i) => i % 3 === r);
    return own.length ? own : art;
  });
  return (
    <div
      className="ry-enter pointer-events-none absolute inset-x-[-15%] inset-y-0 flex -rotate-9 flex-col justify-center gap-[1.6cqh] opacity-60 [mask-image:linear-gradient(transparent,black_20%,black_80%,transparent)]"
      style={at(60)}
    >
      {rows.map((covers, r) => {
        // Enough copies to fill the row twice over, so a half-width drift loops without a seam.
        const run = Array.from({ length: Math.max(2, Math.ceil(8 / covers.length)) }, () => covers).flat();
        return (
          <div key={r} className={`flex w-max gap-[1.6cqh] ${r % 2 ? 'ry-drift-back' : 'ry-drift'}`}>
            {[...run, ...run].map((cover, i) => (
              <Cover key={i} art={cover} size="13cqh" />
            ))}
          </div>
        );
      })}
    </div>
  );
}

/** The whole year as twelve rows of days, each lit by how much was played, a month at a time; then the busiest day flares. */
function YearGrid({ story, year, accent }: { story: ReplayYearStory; year: number; accent: string }) {
  const days = new Map(story.dayKeys.map((key, i) => [key, story.dayMs[i] ?? 0]));
  const max = Math.max(1, ...story.dayMs);
  return (
    <div className="my-[3cqh] flex flex-col gap-[0.5cqh]">
      {Array.from({ length: 12 }, (_, m) => {
        const length = new Date(year, m + 1, 0).getDate();
        return (
          <div key={m} className="ry-light flex gap-[0.4cqw]" style={at(300 + m * 70)}>
            {Array.from({ length: 31 }, (_, d) => {
              const key = `${year}-${String(m + 1).padStart(2, '0')}-${String(d + 1).padStart(2, '0')}`;
              const share = d < length ? (days.get(key) ?? 0) / max : -1;
              const busiest = key === story.charts.busiestDay;
              return (
                <span
                  key={d}
                  className={`aspect-square flex-1 rounded-[0.3cqh] ${busiest ? 'ry-flare relative z-1' : ''}`}
                  style={{
                    background: share < 0 ? 'transparent' : share === 0 ? 'rgb(255 255 255 / 0.1)' : accent,
                    opacity: share <= 0 || busiest ? 1 : 0.25 + 0.75 * share,
                    color: accent,
                    ...(busiest ? at(revealAtMs('BUSIEST_DAY')) : {}),
                  }}
                />
              );
            })}
          </div>
        );
      })}
    </div>
  );
}

/** The day as a dial of twenty-four bars, each as tall as that hour's listening. */
function ClockDial({ hours, accent }: { hours: number[]; accent: string }) {
  const max = Math.max(1, ...hours);
  const peak = peakHour(hours);
  return (
    <svg viewBox="-100 -100 200 200" className="mx-auto my-[3cqh] block w-[62cqw]" aria-hidden="true">
      <circle r="38" fill="none" stroke="rgb(255 255 255 / 0.15)" strokeWidth="1" />
      {hours.map((ms, hour) => {
        const length = 8 + 52 * (ms / max);
        return (
          <g key={hour} transform={`rotate(${hour * 15})`}>
            <rect
              className="ry-grow"
              x="-3.5"
              y={-42 - length}
              width="7"
              height={length}
              rx="3.5"
              fill={hour === peak ? accent : 'rgb(255 255 255 / 0.55)'}
              style={{ ...at(300 + hour * 40), transformBox: 'fill-box' }}
            />
          </g>
        );
      })}
      {[0, 6, 12, 18].map((hour) => (
        <text key={hour} x={Math.sin((hour / 24) * Math.PI * 2) * 26} y={-Math.cos((hour / 24) * Math.PI * 2) * 26 + 4} textAnchor="middle" fontSize="10" fontWeight="700" fill="rgb(255 255 255 / 0.6)">
          {hour}
        </text>
      ))}
    </svg>
  );
}

export function SlideContent({ story, slide, year, accent }: { story: ReplayYearStory; slide: YearSlide; year: number; accent: string }) {
  const content = cardContent(story, slide, year);
  const reveal = revealAtMs(slide);
  return (
    <div className="relative flex h-full flex-col px-[5.4cqw] pt-[13cqh] pb-[10cqh]">
      <Body story={story} slide={slide} year={year} accent={accent} content={content} reveal={reveal} />
    </div>
  );
}

interface BodyProps {
  story: ReplayYearStory;
  slide: YearSlide;
  year: number;
  accent: string;
  content: CardContent;
  reveal: number;
}

function Body({ story, slide, year, accent, content, reveal }: BodyProps) {
  const { charts } = story;
  switch (slide) {
    case 'OPENER':
      return (
        <>
          <Setup text={content.setup} />
          <div className="relative my-[2cqh] flex flex-1 items-center justify-center">
            <CoverWall story={story} />
            <div className="absolute inset-0 bg-[radial-gradient(closest-side,rgb(0_0_0/0.68),rgb(0_0_0/0.3)_70%,transparent)]" />
            <div className="relative flex [text-shadow:0_6px_28px_rgb(0_0_0/0.6)]">
              {String(year)
                .split('')
                .map((digit, i) => (
                  <span key={i} className={`ry-slam text-[15.5cqh] ${huge}`} style={at(380 + i * 190)}>
                    {digit}
                  </span>
                ))}
            </div>
          </div>
          <Detail start={reveal + 300} className="font-medium text-white/80">
            {content.detail[0]}
          </Detail>
        </>
      );
    case 'MINUTES': {
      const target = Math.floor(charts.totalMs / 60_000);
      return (
        <>
          <Setup text={content.setup} />
          <div className="flex flex-1 flex-col justify-center">
            <CountUp target={target} start={reveal} duration={1_800} className={`ry-pop ${grouped(target).length > 6 ? 'text-[11.7cqh]' : 'text-[14.4cqh]'} ${huge}`} style={at(reveal)} />
            <span className={`ry-enter text-[5.6cqh] ${huge}`} style={{ ...at(reveal + 250), color: accent }}>
              {content.unit}
            </span>
          </div>
          <Detail start={reveal + 2_250} className="text-[3.6cqh]! font-bold">
            {content.detail[0]}
          </Detail>
        </>
      );
    }
    case 'BUSIEST_DAY':
      return (
        <>
          <Setup text={content.setup} />
          <YearGrid story={story} year={year} accent={accent} />
          <p className={`ry-pop text-[7.5cqh] ${huge}`} style={at(reveal)}>
            {content.hero}
          </p>
          <div className="mt-[2cqh] flex flex-col gap-[1cqh]">
            {content.detail.map((line, i) => (
              <Detail key={i} start={reveal + 500 + i * 400} className={i === 1 ? 'text-white/80' : ''}>
                {line}
              </Detail>
            ))}
          </div>
        </>
      );
    case 'CLOCK':
      return (
        <>
          <Setup text={content.setup} />
          <ClockDial hours={charts.hours} accent={accent} />
          <p className={`ry-pop text-[9cqh] ${huge}`} style={{ ...at(reveal), color: accent }}>
            {content.hero}
          </p>
          {content.detail[0] && (
            <p className={`ry-enter mt-[1.5cqh] text-[4.4cqh] ${huge}`} style={at(reveal + 600)}>
              {content.detail[0]}
            </p>
          )}
          {content.detail[1] && <Detail start={reveal + 1_000}>{content.detail[1]}</Detail>}
        </>
      );
    case 'ARTIST_REVEAL':
      return (
        <>
          <Setup text={content.setup} />
          <div className="relative flex flex-1 items-center justify-center">
            {/* The countdown: the rest of the top five flicker past before the one. */}
            {content.rows.map((row, i) => (
              <p key={i} className={`ry-flicker absolute text-center text-[5cqh] ${huge} text-white/70`} style={at(700 + i * 620)}>
                {row.title}
              </p>
            ))}
            <Cover art={content.art} size="40cqh" circular className="ry-pop" style={at(reveal)} />
          </div>
          <p className={`ry-enter text-[6.4cqh] ${huge}`} style={at(reveal + 420)}>
            {content.hero}
          </p>
          <Detail start={reveal + 900} className="mt-[1cqh] text-white/80">
            {content.detail[0]}
          </Detail>
        </>
      );
    case 'SONG_REVEAL':
      return (
        <>
          <Setup text={content.setup} />
          <div className="relative flex flex-1 items-center justify-center">
            {/* The wait: a blank sleeve, breathing, until the record turns over. */}
            <div className="ry-leave absolute" style={at(reveal)}>
              <div className="ry-breathe flex size-[40cqh] items-center justify-center rounded-[1.2cqh] bg-white/12 shadow-[0_12px_40px_rgb(0_0_0/0.45)]">
                <span className={`text-[15cqh] ${huge} text-white/75`}>?</span>
              </div>
            </div>
            <Cover art={content.art} size="40cqh" className="ry-flip" style={at(reveal)} />
          </div>
          <p className={`ry-enter line-clamp-2 text-[6cqh] ${huge}`} style={at(reveal + 300)}>
            {content.hero}
          </p>
          {content.detail.map((line, i) => (
            <Detail key={i} start={reveal + 700 + i * 300} className={i === 0 ? 'mt-[1cqh] text-white/80' : ''}>
              {i === 1 ? <span style={{ color: accent }}>{line}</span> : line}
            </Detail>
          ))}
        </>
      );
    case 'TOP_ARTISTS':
    case 'TOP_SONGS':
    case 'ALBUMS':
    case 'GENRES':
      return (
        <>
          <Setup text={content.setup} />
          <RankList rows={content.rows} circular={content.circular} accent={accent} first={slide === 'TOP_SONGS' ? 2 : 1} />
        </>
      );
    case 'MONTHS':
      return (
        <>
          <Setup text={content.setup} />
          <div className="my-auto overflow-hidden">
            {/* Each card is 21.7cqh wide with a 2cqh gap: the ribbon travels until the last is in view. */}
            <div
              className="ry-ribbon flex w-max gap-[2cqh] py-[2cqh]"
              style={{ '--ry-travel': `min(0px, calc(89.2cqw - ${content.rows.length * 23.7 - 2}cqh))` } as CSSProperties}
            >
              {content.rows.map((row, i) => (
                <div key={i} className="ry-enter w-[21.7cqh]" style={{ ...at(500 + i * 90), rotate: `${i % 2 ? 2 : -2}deg` }}>
                  <p className={`text-[3.9cqh] ${huge} tracking-normal`} style={{ color: accent }}>
                    {row.subtitle}
                  </p>
                  <Cover art={row.art} size="21.7cqh" className="mt-[0.8cqh]" />
                  <p className="mt-[0.8cqh] truncate text-[2.1cqh] font-bold text-white">{row.title}</p>
                </div>
              ))}
            </div>
          </div>
        </>
      );
    case 'DISCOVERY': {
      const counts = [
        [charts.songs.length, 'song'],
        [charts.artists.length, 'artist'],
        [charts.albums.length, 'album'],
      ] as const;
      return (
        <>
          <Setup text={content.setup} />
          <div className="flex flex-1 flex-col justify-center gap-[3cqh]">
            {counts.map(([n, word], i) => (
              <div key={word} className="ry-enter flex items-baseline gap-[3cqw]" style={at(reveal + i * 450)}>
                <CountUp target={n} start={reveal + i * 450} duration={1_200} className={`text-[10cqh] ${huge}`} />
                <span className={`text-[4.4cqh] ${huge}`} style={{ color: accent }}>
                  {n === 1 ? word : `${word}s`}
                </span>
              </div>
            ))}
          </div>
        </>
      );
    }
    case 'SUMMARY':
      return (
        <>
          <div className="flex items-center gap-[4cqw]">
            <Cover art={content.art} size="14cqh" circular className="ry-pop" style={at(200)} />
            <p className={`ry-enter text-[6cqh] ${huge}`} style={at(400)}>
              {content.setup}
            </p>
          </div>
          <div className="mt-[4cqh] grid grid-cols-2 gap-[4cqw]">
            {content.columns.map((column, c) => (
              <div key={column.title} className="ry-enter min-w-0" style={at(800 + c * 300)}>
                <p className="text-[2cqh] font-bold text-white/70">{column.title}</p>
                <ol className="mt-[1.2cqh] flex flex-col gap-[1.4cqh]">
                  {column.names.map((name, i) => (
                    <li key={i} className="flex gap-[1.5cqw] text-[2.4cqh] leading-[1.2] font-bold text-white">
                      <span style={{ color: accent }}>{i + 1}</span>
                      <span className="truncate">{name}</span>
                    </li>
                  ))}
                </ol>
              </div>
            ))}
          </div>
          <div className="ry-enter mt-auto" style={at(1_900)}>
            <p className="text-[2cqh] font-bold text-white/70">{content.unit}</p>
            <p className={`text-[4.2cqh] ${huge} tracking-[-0.02em]`}>{content.hero}</p>
          </div>
        </>
      );
  }
}
