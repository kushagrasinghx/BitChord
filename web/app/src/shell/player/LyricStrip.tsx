import { ChevronRight, Music2 } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { isGap } from '@/lyrics/model';
import { adjustedLyricsPosition, useLyricsOffset } from '@/lyrics/offset';
import { useLyrics } from '@/lyrics/useLyrics';
import { getPlayhead, usePlayer } from '@/state/player';
import { currentLineIndex, INTRO_LINES, LOADING_LINES, wordSung } from './lyricLines';

/** PlayerLyrics.kt LYRICS_UNAVAILABLE_HOLD_MS: how long "Lyrics not available" stays before it fades (over 900ms). */
const UNAVAILABLE_HOLD_MS = 5000;

/*
 * A word lit as far as it has been sung, white over the strip's unsung 55%
 * (PlayerLyrics.kt UNSUNG_ALPHA_STRIP). The text is the gradient's cut-out,
 * and --sung, written each frame, is where white stops.
 */
const word =
  'bg-[linear-gradient(90deg,#fff_var(--sung),rgb(255_255_255/0.55)_var(--sung))] bg-clip-text text-transparent [--sung:0%] rtl:bg-[linear-gradient(270deg,#fff_var(--sung),rgb(255_255_255/0.55)_var(--sung))] motion-reduce:[--sung:100%]';

/**
 * The one lyric line over the scrubber (PlayerLyrics.kt CurrentLyricStrip):
 * titleMedium (16/600) in a fixed 20px line box, so a taller script never
 * shifts the credits above it, nudged 6px down toward the bar, with the ›
 * that says it opens the lyrics. A word-timed line sweeps word by word, as
 * on the phone; a line-timed one is simply white. Before the first words, a
 * line about the intro; in a gap, "Instrumental" at half strength. Lyrics
 * with no timing offer "Lyrics ›" instead; while the lookup runs, a line
 * saying so at 55%, and when it finds nothing, "Lyrics not available" for
 * five seconds before it fades. Each new line fades up in place of the last.
 */
export function LyricStrip({ onOpen }: { onOpen: () => void }) {
  const lyrics = useLyrics();
  const videoId = usePlayer((s) => s.nowPlaying?.videoId ?? '');
  const lines = lyrics.data?.lines ?? null;
  const synced = !!lyrics.data?.synced;
  const offsetMs = useLyricsOffset((s) => s.offsetMs);
  const [index, setIndex] = useState(-1);
  const words = useRef<(HTMLSpanElement | null)[]>([]);
  // One intro line per track, as the phone picks it.
  const intro = useMemo(() => INTRO_LINES[Math.floor(Math.random() * INTRO_LINES.length)]!, [videoId]); // eslint-disable-line react-hooks/exhaustive-deps
  const loading = useMemo(() => LOADING_LINES[Math.floor(Math.random() * LOADING_LINES.length)]!, [videoId]); // eslint-disable-line react-hooks/exhaustive-deps
  // "Lyrics not available" registers, then fades rather than lingering for the whole song (LyricsUnavailableLine).
  const [goneFor, setGoneFor] = useState<string | null>(null);
  const empty = !lyrics.isFetching && !lines?.length;
  useEffect(() => {
    if (!empty) return;
    const timer = setTimeout(() => setGoneFor(videoId), UNAVAILABLE_HOLD_MS);
    return () => clearTimeout(timer);
  }, [empty, videoId]);

  // Every frame: which line, and how far through its words. setIndex only re-renders when the line changes.
  useEffect(() => {
    if (!lines || !synced) return;
    let frame = 0;
    const tick = () => {
      // Shifted as the lyrics pane is, so the strip and the pane agree (PlayerLyrics.kt adjustedLyricsPosition).
      const ms = adjustedLyricsPosition((getPlayhead()?.sec ?? 0) * 1000, offsetMs);
      const at = currentLineIndex(lines, ms);
      setIndex(at);
      lines[at]?.words.forEach((w, i) => words.current[i]?.style.setProperty('--sung', `${wordSung(w, ms) * 100}%`));
      frame = requestAnimationFrame(tick);
    };
    tick();
    return () => cancelAnimationFrame(frame);
  }, [lines, synced, offsetMs]);

  const chevron = <ChevronRight size={14} className="flex-none text-white/50 rtl:-scale-x-100" aria-hidden="true" />;

  if (lines?.length && synced) {
    const current = lines[index];
    const firstSung = lines.findIndex((l) => !isGap(l));
    const gap = !current || isGap(current);
    return (
      <div className="flex h-7 translate-y-1.5 items-center">
        <button
          key={`${videoId}-${index}`}
          className="flex min-w-0 animate-[fade-in_280ms_ease-out] items-center gap-1.5 text-white motion-reduce:animate-none"
          dir="auto"
          onClick={onOpen}
        >
          <span className={`min-w-0 truncate text-start type-[600_16px/20px] ${gap ? 'text-white/50' : ''}`}>
            {gap
              ? firstSung >= 0 && index < firstSung
                ? intro
                : 'Instrumental'
              : current.words.length
                ? current.words.map((w, i) => (
                    <span key={i}>
                      {i > 0 && ' '}
                      <span ref={(el) => void (words.current[i] = el)} className={word}>
                        {w.text}
                      </span>
                    </span>
                  ))
                : current.text}
          </span>
          {chevron}
        </button>
      </div>
    );
  }

  return (
    <div className="flex h-7 translate-y-1.5 items-center">
      {lines?.length ? (
        <button className="flex min-w-0 items-center gap-1.5 type-[600_16px/20px] text-white" onClick={onOpen}>
          <Music2 size={16} aria-hidden="true" />
          Lyrics
          {chevron}
        </button>
      ) : (
        <span
          className={`min-w-0 truncate type-[600_16px/20px] text-white/55 transition-opacity duration-900 ${empty && goneFor === videoId ? 'opacity-0' : ''}`}
          aria-live="polite"
        >
          {empty ? 'Lyrics not available' : loading}
        </span>
      )}
    </div>
  );
}
