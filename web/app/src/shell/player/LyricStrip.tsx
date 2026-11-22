import { ChevronRight, Music2 } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { isGap } from '@/lyrics/model';
import { adjustedLyricsPosition, useLyricsOffset } from '@/lyrics/offset';
import { useLyrics } from '@/lyrics/useLyrics';
import { getPlayhead, usePlayer } from '@/state/player';
import { currentLineIndex, INTRO_LINES } from './lyricLines';

const POLL_MS = 100;

/**
 * The one lyric line over the scrubber (PlayerLyrics.kt CurrentLyricStrip):
 * titleMedium (16/600) in a fixed 20px line box, so a taller script never
 * shifts the credits above it, nudged 6px down toward the bar. Before the
 * first words, a line about the intro; in a gap, "Instrumental". Lyrics with
 * no timing offer "Lyrics ›" instead. Clicking it opens the lyrics. Each new
 * line fades up in place of the last.
 */
export function LyricStrip({ onOpen }: { onOpen: () => void }) {
  const lyrics = useLyrics();
  const videoId = usePlayer((s) => s.nowPlaying?.videoId ?? '');
  const lines = lyrics.data?.lines ?? null;
  const synced = !!lyrics.data?.synced;
  const offsetMs = useLyricsOffset((s) => s.offsetMs);
  const [index, setIndex] = useState(-1);
  // One intro line per track, as the phone picks it.
  const intro = useMemo(() => INTRO_LINES[Math.floor(Math.random() * INTRO_LINES.length)]!, [videoId]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!lines || !synced) return;
    // Shifted as the lyrics pane is, so the strip and the pane agree (PlayerLyrics.kt adjustedLyricsPosition).
    const tick = () => setIndex(currentLineIndex(lines, adjustedLyricsPosition((getPlayhead()?.sec ?? 0) * 1000, offsetMs)));
    tick();
    const timer = setInterval(tick, POLL_MS);
    return () => clearInterval(timer);
  }, [lines, synced, offsetMs]);

  let text: string | null = null;
  if (lines?.length && synced) {
    const current = lines[index];
    const firstSung = lines.findIndex((l) => !isGap(l));
    if (!current || isGap(current)) text = firstSung >= 0 && index < firstSung ? intro : 'Instrumental';
    else text = current.text;
  }

  return (
    <div className="flex h-7 translate-y-1.5 items-center">
      {text !== null ? (
        <button
          key={`${videoId}-${index}`}
          className="min-w-0 animate-[fade-in_280ms_ease-out] truncate text-start type-[600_16px/20px] text-white motion-reduce:animate-none"
          dir="auto"
          onClick={onOpen}
        >
          {text}
        </button>
      ) : lines?.length ? (
        <button className="flex min-w-0 items-center gap-1.5 type-[600_16px/20px] text-white" onClick={onOpen}>
          <Music2 size={16} aria-hidden="true" />
          Lyrics
          <ChevronRight size={14} className="text-white/50" aria-hidden="true" />
        </button>
      ) : null}
    </div>
  );
}
