import './ReplayButton.css';
import { useQuery } from '@tanstack/react-query';
import { Play } from 'lucide-react';
import { lazy, Suspense, useEffect, useState, type CSSProperties } from 'react';
import { useNavigate } from 'react-router';
import { meshPalette } from '@/lib/meshPalette';
import { pillColor } from './colors';
import { yearShown } from './periods';
import { useReplayYear } from './queries';
import { allMonths } from './store';

const ReplayYearStories = lazy(() => import('./year/ReplayYearStories').then((m) => ({ default: m.ReplayYearStories })));

/** Home's first moments are the app opening; a year of months can wait this long (HOME_SETTLE_MS). */
const SETTLE_MS = 900;
const GLYPH_STAGGER = 45;
const OUT_STAGGER = 30;
const OUT_MS = 240;
const LOOP_PAUSE_MS = 5_000;

/**
 * Home's way into the Replay, beside the title (ReplayYearButton.kt). Through
 * December, `REPLAY'26 ▶` opens the year's story, as on the phone; the rest of
 * the year, once anything has been listened to, `REPLAY ▶` opens the Replay
 * page. Coloured from the top song, so it already looks like what it opens.
 */
export function ReplayButton() {
  const navigate = useNavigate();
  const [settled, setSettled] = useState(false);
  const [open, setOpen] = useState(false);
  const [phase, setPhase] = useState<'in' | 'out'>('in');
  const [round, setRound] = useState(0);
  const [fill, setFill] = useState('#e5243b');
  const year = yearShown(new Date());
  const months = useQuery({ queryKey: ['replay', 'months'], queryFn: allMonths, staleTime: Infinity });
  const story = useReplayYear(settled ? year : null).data ?? null;
  const art = story?.charts.songs[0]?.art ?? null;

  useEffect(() => {
    const timer = setTimeout(() => setSettled(true), SETTLE_MS);
    return () => clearTimeout(timer);
  }, []);

  useEffect(() => {
    if (!art) return;
    let live = true;
    void meshPalette(art).then(
      (cells) => live && setFill(pillColor(cells)),
      () => undefined,
    );
    return () => {
      live = false;
    };
  }, [art]);

  const glyphs = story && year !== null ? `REPLAY'${String(year % 100).padStart(2, '0')}` : 'REPLAY';

  // Something that moves all the time on a home screen stops being noticed; now and then is what the eye goes back to.
  useEffect(() => {
    const timer = setTimeout(
      () => {
        if (phase === 'in') setPhase('out');
        else {
          setPhase('in');
          setRound((r) => r + 1);
        }
      },
      phase === 'in' ? LOOP_PAUSE_MS : glyphs.length * OUT_STAGGER + OUT_MS,
    );
    return () => clearTimeout(timer);
  }, [phase, glyphs.length]);

  if (!months.data?.length && !story) return null;
  const label = story && year !== null ? `Open your Replay ${year}` : 'Open your Replay';
  return (
    <>
      <button
        className={`inline-flex h-[30px] flex-none items-center overflow-hidden rounded-full ps-[13px] pe-[9px] text-[15px] leading-none font-black text-white transition-transform active:scale-96 ${phase === 'out' ? 'rb-out' : ''}`}
        style={{ backgroundColor: fill }}
        aria-label={label}
        onClick={() => (story ? setOpen(true) : navigate('/replay'))}
      >
        <span key={round} className="flex items-center">
          {glyphs.split('').map((char, i) => (
            <span
              key={i}
              className="rb-glyph"
              aria-hidden="true"
              style={
                {
                  '--rb-from': i % 2 === 0 ? -1 : 1,
                  animationDelay: `${phase === 'in' ? i * GLYPH_STAGGER : i * OUT_STAGGER}ms`,
                } as CSSProperties
              }
            >
              {char}
            </span>
          ))}
          <Play
            size={18}
            fill="currentColor"
            strokeWidth={0}
            className="rb-play ms-[5px]"
            style={{ animationDelay: phase === 'in' ? `${glyphs.length * GLYPH_STAGGER + 190}ms` : '0ms' }}
          />
        </span>
      </button>
      {open && story && year !== null && (
        <Suspense fallback={null}>
          <ReplayYearStories story={story} year={year} onClose={() => setOpen(false)} />
        </Suspense>
      )}
    </>
  );
}
